package org.molgenis.emx2.sql;

import static org.jooq.impl.DSL.*;
import static org.molgenis.emx2.ColumnType.*;
import static org.molgenis.emx2.Constants.*;
import static org.molgenis.emx2.MutationType.*;
import static org.molgenis.emx2.sql.SqlDatabase.ADMIN;
import static org.molgenis.emx2.sql.SqlTypeUtils.getTypedValue;

import java.io.StringReader;
import java.io.Writer;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.jooq.*;
import org.molgenis.emx2.*;
import org.molgenis.emx2.Query;
import org.molgenis.emx2.Row;
import org.molgenis.emx2.Table;
import org.postgresql.copy.CopyManager;
import org.postgresql.core.BaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class SqlTable implements Table {
  private SqlDatabase db;
  private SqlTableMetadata metadata;
  private static Logger logger = LoggerFactory.getLogger(SqlTable.class);

  SqlTable(SqlDatabase db, SqlTableMetadata metadata) {
    this.db = db;
    this.metadata = metadata;
  }

  @Override
  public org.molgenis.emx2.Schema getSchema() {
    return new SqlSchema(db, (SqlSchemaMetadata) metadata.getSchema());
  }

  @Override
  public SqlTableMetadata getMetadata() {
    return metadata;
  }

  public void copyOut(Writer writer) {
    db.getJooq()
        .connection(
            connection -> {
              try {
                CopyManager cm = new CopyManager(connection.unwrap(BaseConnection.class));
                String selectQuery =
                    "select "
                        + this.getMetadata().getLocalColumnNames().stream()
                            .map(c -> "\"" + c + "\"")
                            .collect(Collectors.joining(","))
                        + " from \""
                        + getSchema().getMetadata().getName()
                        + "\".\""
                        + getName()
                        + "\"";
                cm.copyOut(
                    "COPY (" + selectQuery + " ) TO STDOUT WITH (FORMAT CSV,HEADER )", writer);
              } catch (Exception e) {
                throw new MolgenisException("copyOut failed: ", e);
              }
            });
  }

  public void copyIn(Iterable<Row> rows) {
    db.getJooq()
        .connection(
            connection -> {
              try {
                CopyManager cm = new CopyManager(connection.unwrap(BaseConnection.class));

                // must be batched
                StringBuilder tmp = new StringBuilder();
                tmp.append(
                    this.getMetadata().getLocalColumnNames().stream()
                            .map(c -> "\"" + c + "\"")
                            .collect(Collectors.joining(","))
                        + "\n");
                for (Row row : rows) {
                  StringBuilder line = new StringBuilder();
                  for (Column c : this.getMetadata().getStoredColumns()) {
                    if (!row.containsName(c.getName())) {
                      line.append(",");
                    } else {
                      Object value = getTypedValue(row, c);
                      line.append(value + ",");
                    }
                  }
                  tmp.append(line.substring(0, line.length() - 1) + "\n");
                }

                String tableName =
                    "\"" + getSchema().getMetadata().getName() + "\".\"" + getName() + "\"";

                String columnNames =
                    "("
                        + this.getMetadata().getLocalColumnNames().stream()
                            .map(c -> "\"" + c + "\"")
                            .collect(Collectors.joining(","))
                        + ")";
                String sql = "COPY " + tableName + columnNames + " FROM STDIN (FORMAT CSV,HEADER )";
                cm.copyIn(sql, new StringReader(tmp.toString()));
              } catch (Exception e) {
                throw new MolgenisException("copyOut failed: ", e);
              }
            });
  }

  @Override
  public int insert(Row... rows) {
    return insert(Arrays.asList(rows));
  }

  @Override
  public int insert(Iterable<Row> rows) {
    try {
      return executeTransaction(db, getSchema().getName(), getName(), rows, INSERT);
    } catch (Exception e) {
      throw new SqlMolgenisException("Update into table '" + getName() + "' failed.", e);
    }
  }

  @Override
  public int update(Row... rows) {
    return update(Arrays.asList(rows));
  }

  @Override
  public int update(Iterable<Row> rows) {
    try {
      return this.executeTransaction(db, getSchema().getName(), getName(), rows, UPDATE);
    } catch (Exception e) {
      throw new SqlMolgenisException("Update into table '" + getName() + "' failed.", e);
    }
  }

  @Override
  public int save(Row... rows) {
    return save(Arrays.asList(rows));
  }

  @Override
  public int save(Iterable<Row> rows) {
    try {
      return this.executeTransaction(db, getSchema().getName(), getName(), rows, SAVE);
    } catch (Exception e) {
      throw new SqlMolgenisException("Upsert into table '" + getName() + "' failed.", e);
    }
  }

  @Override
  public void truncate() {
    db.tx(
        database -> {
          truncateTransaction((SqlDatabase) database, getSchema().getName(), getName());
        });
  }

  // use static to ensure we don't touch 'this' until transaction completed
  private static void truncateTransaction(
      SqlDatabase database, String schemaName, String tableName) {
    // if part of inheritance tree then only delete the relevant part
    SqlTable t = database.getSchema(schemaName).getTable(tableName);
    if (t.getMetadata().getLocalColumn(MG_TABLECLASS) != null) {
      t.truncate(t.getMgTableClass(t.getMetadata()));
    }
    // in normal table it is a real truncate
    else {
      database.getJooq().truncate(t.getJooqTable()).execute();
    }
    // in case inherited we must also truncate parent
    if (t.getMetadata().getInherit() != null) {
      t.getInheritedTable().truncate(t.getMgTableClass(t.getMetadata()));
    }
  }

  private void truncate(String mg_table) {
    if (getMetadata().getInherit() != null) {
      getInheritedTable().truncate(mg_table);
    }
    db.getJooq().deleteFrom(getJooqTable()).where(field(MG_TABLECLASS).equal(mg_table)).execute();
  }

  private static String getMgTableClass(TableMetadata table) {
    return table.getSchemaName() + "." + table.getTableName();
  }

  private static int executeTransaction(
      Database db,
      String schemaName,
      String tableName,
      Iterable<Row> rows,
      MutationType transactionType) {
    long start = System.currentTimeMillis();
    final AtomicInteger count = new AtomicInteger(0);
    final Map<String, List<Row>> subclassRows = new LinkedHashMap<>();
    final Map<String, Set<String>> columnsProvided = new LinkedHashMap<>();

    SqlSchema schema = (SqlSchema) db.getSchema(schemaName);
    SqlTable table = schema.getTable(tableName);
    String tableClass = getMgTableClass(table.getMetadata());

    // validate
    if (table.getMetadata().getPrimaryKeys().isEmpty())
      throw new MolgenisException(
          "Transaction failed: Table "
              + table.getName()
              + " cannot process row insert/update/delete requests because no primary key is defined");

    db.tx(
        db2 -> {
          for (Row row : rows) {

            // set table class if not set, and see for first time
            if (row.notNull(MG_TABLECLASS)
                && !subclassRows.containsKey(row.getString(MG_TABLECLASS))) {

              // validate
              String rowTableName = row.getString(MG_TABLECLASS);
              if (!rowTableName.contains(".")) {
                if (schema.getTable(rowTableName) != null) {
                  row.setString(MG_TABLECLASS, schemaName + "." + rowTableName);
                } else {
                  throw new MolgenisException(
                      MG_TABLECLASS
                          + " value failed in row "
                          + count.get()
                          + ": found '"
                          + rowTableName
                          + "'");
                }
              } else {
                String rowSchemaName = rowTableName.split("\\.")[0];
                String rowTableName2 = rowTableName.split("\\.")[1];
                if (db.getSchema(rowSchemaName) == null
                    || db.getSchema(rowSchemaName).getTable(rowTableName2) == null) {
                  throw new MolgenisException(
                      "invalid value in column '"
                          + MG_TABLECLASS
                          + "' on row "
                          + count.get()
                          + ": found '"
                          + rowTableName
                          + "'");
                }
              }
            } else {
              row.set(MG_TABLECLASS, tableClass);
            }

            // create batches for each table class
            String subclassName = row.getString(MG_TABLECLASS);
            if (!subclassRows.containsKey(subclassName)) {
              subclassRows.put(subclassName, new ArrayList<>());
            }

            // check columns provided didn't change
            if (columnsProvided.get(subclassName) == null) {
              columnsProvided.put(subclassName, new LinkedHashSet<>(row.getColumnNames()));
            }

            // execute batch if 1000 rows, or columns provided changes
            if (columnsProvidedAreDifferent(columnsProvided.get(subclassName), row)
                || subclassRows.get(subclassName).size() >= 1000) {
              executeBatch(
                  (SqlSchema) db2.getSchema(subclassName.split("\\.")[0]),
                  transactionType,
                  count,
                  subclassRows,
                  subclassName,
                  columnsProvided.get(subclassName));
              // reset columns provided
              columnsProvided.get(subclassName).clear();
              columnsProvided.get(subclassName).addAll(row.getColumnNames());
            }

            // add to batch list, and execute if batch is large enough
            subclassRows.get(subclassName).add(row);
          }

          // execute any remaining batches
          for (Map.Entry<String, List<Row>> batch : subclassRows.entrySet()) {
            if (batch.getValue().size() > 0) {
              executeBatch(
                  (SqlSchema) db2.getSchema(batch.getKey().split("\\.")[0]),
                  transactionType,
                  count,
                  subclassRows,
                  batch.getKey(),
                  columnsProvided.get(batch.getKey()));
            }
          }
        });

    log(
        db.getActiveUser(),
        table.getJooqTable().getName(),
        start,
        count,
        transactionType.name().toLowerCase() + "d (incl subclass if applicable)");
    return count.get();
  }

  private static void checkRequired(Row row, Collection<Column> columns) {
    for (Column c : columns) {
      if (c.isRequired() && row.isNull(c.getName(), c.getColumnType())) {
        throw new MolgenisException("column '" + c.getName() + "' is required in " + row);
      }
    }
  }

  private static boolean columnsProvidedAreDifferent(Set<String> columnsProvided, Row row) {
    if (columnsProvided.size() == 0 || columnsProvided.equals(row.getColumnNames())) {
      return false;
    } else {
      return true;
    }
  }

  private static void executeBatch(
      SqlSchema schema,
      MutationType transactionType,
      AtomicInteger count,
      Map<String, List<Row>> subclassRows,
      String subclassName,
      Set<String> columnsProvided) {

    // execute
    SqlTable table = schema.getTable(subclassName.split("\\.")[1]);
    if (UPDATE.equals(transactionType)) {
      count.set(
          count.get() + table.updateBatch(table, subclassRows.get(subclassName), columnsProvided));
    } else if (SAVE.equals(transactionType)) {
      count.set(
          count.get()
              + table.insertBatch(table, subclassRows.get(subclassName), true, columnsProvided));
    } else if (INSERT.equals(transactionType)) {
      count.set(
          count.get()
              + table.insertBatch(table, subclassRows.get(subclassName), false, columnsProvided));
    } else {
      throw new MolgenisException(
          "Internal error in executeBatch: transaction type "
              + transactionType
              + " not allowed here");
    }
    // clear the list
    subclassRows.get(subclassName).clear();
  }

  private static int insertBatch(
      SqlTable table, List<Row> rows, boolean updateOnConflict, Set<String> updateColumns) {
    boolean inherit = table.getMetadata().getInherit() != null;
    if (inherit) {
      SqlTable inheritedTable = table.getInheritedTable();
      inheritedTable.insertBatch(inheritedTable, rows, updateOnConflict, updateColumns);
    }

    // get metadata
    Set<Column> columns = table.getColumnsToBeUpdated(updateColumns);
    List<Column> allColumns = table.getMetadata().getMutationColumns();
    List<Field> insertFields =
        columns.stream().map(c -> c.getJooqField()).collect(Collectors.toList());
    if (!inherit) {
      insertFields.add(field(name(MG_INSERTEDBY)));
      insertFields.add(field(name(MG_INSERTEDON)));
      insertFields.add(field(name(MG_UPDATEDBY)));
      insertFields.add(field(name(MG_UPDATEDON)));
    }

    // define the insert step
    InsertValuesStepN<org.jooq.Record> step =
        table.getJooq().insertInto(table.getJooqTable(), insertFields.toArray(new Field[0]));

    // add all the rows as steps
    String user = table.getSchema().getDatabase().getActiveUser();
    if (user == null) {
      user = ADMIN;
    }
    LocalDateTime now = LocalDateTime.now();
    for (Row row : rows) {
      // when insert, we should include all columns, not only 'updateColumns'
      if (!row.isDraft()) {
        checkRequired(row, allColumns);
      }
      // get values
      Map values = SqlTypeUtils.getValuesAsMap(row, columns);
      if (!inherit) {
        values.put(MG_INSERTEDBY, user);
        values.put(MG_INSERTEDON, now);
        values.put(MG_UPDATEDBY, user);
        values.put(MG_UPDATEDON, now);
      }
      step.values(values.values());
    }

    // optionally, add conflict clause
    if (updateOnConflict) {
      InsertOnDuplicateSetStep<org.jooq.Record> step2 =
          step.onConflict(table.getMetadata().getPrimaryKeyFields().toArray(new Field[0]))
              .doUpdate();
      for (Column column : columns) {
        step2.set(
            column.getJooqField(),
            (Object) field(unquotedName("excluded.\"" + column.getName() + "\"")));
      }
      if (!inherit) {
        step2.set(field(name(MG_UPDATEDBY)), user);
        step2.set(field(name(MG_UPDATEDON)), now);
      }
    }

    return step.execute();
  }

  private Set<Column> getColumnsToBeUpdated(Set<String> updateColumns) {
    return getMetadata().getMutationColumns().stream()
        .filter(
            c ->
                !(c.getName().equals(MG_INSERTEDBY)
                        || c.getName().equals(MG_INSERTEDON)
                        || c.getName().equals(MG_UPDATEDBY)
                        || c.getName().equals(MG_UPDATEDON))
                    && (c.getComputed() != null
                        || updateColumns.size() == 0
                        || updateColumns.contains(c.getName())))
        .collect(Collectors.toSet());
  }

  private static int updateBatch(SqlTable table, List<Row> rows, Set<String> updateColumns) {
    boolean inherit = table.getMetadata().getInherit() != null;
    if (inherit) {
      SqlTable inheritedTable = table.getInheritedTable();
      inheritedTable.updateBatch(inheritedTable, rows, updateColumns);
    }

    // get metadata
    Set<Column> columns = table.getColumnsToBeUpdated(updateColumns);
    List<Column> pkeyFields = table.getMetadata().getPrimaryKeyColumns();

    // create batch of updates
    List<UpdateConditionStep> list = new ArrayList();
    String user = table.getSchema().getDatabase().getActiveUser();
    if (user == null) {
      user = ADMIN;
    }
    LocalDateTime now = LocalDateTime.now();
    for (Row row : rows) {
      Map values = SqlTypeUtils.getValuesAsMap(row, columns);
      if (!inherit) {
        values.put(MG_UPDATEDBY, user);
        values.put(MG_UPDATEDON, now);
      }

      if (!row.isDraft()) {
        checkRequired(row, columns);
      }

      list.add(
          table
              .getJooq()
              .update(table.getJooqTable())
              .set(values)
              .where(table.getUpdateCondition(row, pkeyFields)));
    }

    return Arrays.stream(table.getJooq().batch(list).execute()).reduce(Integer::sum).getAsInt();
  }

  private Condition getUpdateCondition(Row row, List<Column> pkeyFields) {
    List<Condition> result = new ArrayList<>();
    for (Column key : pkeyFields) {
      if (key.isReference()) {
        for (Reference ref : key.getReferences()) {
          result.add(ref.getJooqField().eq(row.get(ref.getName(), ref.getPrimitiveType())));
        }
      } else {
        result.add(key.getJooqField().eq(row.get(key)));
      }
    }
    return and(result);
  }

  @Override
  public int delete(Iterable<Row> rows) {
    long start = System.currentTimeMillis();

    AtomicInteger count = new AtomicInteger(0);
    try {
      db.tx(
          db2 -> {
            SqlTable table = (SqlTable) db2.getSchema(getSchema().getName()).getTable(getName());

            // delete in batches
            int batchSize = 100000;
            List<Row> batch = new ArrayList<>();
            for (Row row : rows) {
              batch.add(row);
              count.set(count.get() + 1);
              if (count.get() % batchSize == 0) {
                deleteBatch(table, batch);
                batch.clear();
              }
            }

            // delete remaining elements
            deleteBatch(table, batch);

            // finally delete in superclass
            if (table.getMetadata().getInherit() != null) {
              table.getInheritedTable().delete(rows);
            }
          });
    } catch (Exception e) {
      throw new SqlMolgenisException("Delete into table " + getName() + " failed.   ", e);
    }

    log(db.getActiveUser(), getName(), start, count, "deleted");

    return count.get();
  }

  @Override
  public Query select(SelectColumn... columns) {
    return query().select(columns);
  }

  @Override
  public Query agg(SelectColumn columns) {
    return agg().select(columns);
  }

  public Query where(Filter... filters) {
    return query().where(filters);
  }

  // @Override
  public Query search(String terms) {
    return query().search(terms);
  }

  @Override
  public int delete(Row... rows) {
    return delete(Arrays.asList(rows));
  }

  private static void deleteBatch(SqlTable table, Collection<Row> rows) {
    if (!rows.isEmpty()) {
      List<String> keyNames =
          table.getMetadata().getPrimaryKeyFields().stream()
              .map(Field::getName)
              .collect(Collectors.toList());

      // in case no primary key is defined, use all columns
      if (keyNames == null) {
        throw new MolgenisException(
            "Delete on table " + table.getName() + " failed: no primary key set");
      }
      Condition whereCondition = table.getWhereConditionForBatchDelete(rows);
      table.getJooq().deleteFrom(table.getJooqTable()).where(whereCondition).execute();
    }
  }

  private DSLContext getJooq() {
    return ((SqlDatabase) getSchema().getDatabase()).getJooq();
  }

  private Condition getWhereConditionForBatchDelete(Collection<Row> rows) {
    List<Condition> conditions = new ArrayList<>();
    for (Row r : rows) {
      List<Condition> rowCondition = new ArrayList<>();
      if (getMetadata().getPrimaryKeys().isEmpty()) {
        // when no key, use all columns as id
        for (Column keyPart : getMetadata().getStoredColumns()) {
          rowCondition.add(getColumnCondition(r, keyPart));
        }
      } else {
        for (Column keyPart : getMetadata().getPrimaryKeyColumns()) {
          rowCondition.add(getColumnCondition(r, keyPart));
        }
      }
      conditions.add(and(rowCondition));
    }
    return or(conditions);
  }

  private Condition getColumnCondition(Row r, Column key) {
    List<Condition> columnCondition = new ArrayList<>();
    if (REF.equals(key.getColumnType()) || REF_ARRAY.equals(key.getColumnType())
    //       || MREF.equals(key.getColumnType())
    ) {
      for (Reference ref : key.getReferences()) {
        if (!ref.isOverlapping()) {
          columnCondition.add(
              ref.getJooqField()
                  .eq(cast(r.get(ref.getName(), ref.getPrimitiveType()), ref.getJooqField())));
        }
      }
    } else if (REFBACK.equals(key.getColumnType())) {
      // do nothing
    } else {
      columnCondition.add(
          key.getJooqField()
              .eq(cast(r.get(key.getName(), key.getColumnType()), key.getJooqField())));
    }
    return and(columnCondition);
  }

  @Override
  public Query query() {
    return new SqlQuery((SqlSchemaMetadata) this.getMetadata().getSchema(), this.getName());
  }

  @Override
  public Query agg() {
    return new SqlQuery(
        (SqlSchemaMetadata) this.getMetadata().getSchema(), this.getName() + "_agg");
  }

  @Override
  public List<Row> retrieveRows() {
    return this.query().retrieveRows();
  }

  @Override
  public String getName() {
    return getMetadata().getTableName();
  }

  protected org.jooq.Table<org.jooq.Record> getJooqTable() {
    return table(name(metadata.getSchema().getName(), metadata.getTableName()));
  }

  @Override
  public SqlTable getInheritedTable() {
    if (getMetadata().getImportSchema() != null) {
      return (SqlTable)
          getSchema()
              .getDatabase()
              .getSchema(getMetadata().getImportSchema())
              .getTable(getMetadata().getInherit());
    } else {
      return (SqlTable) getSchema().getTable(getMetadata().getInherit());
    }
  }

  private static void log(
      String user, String table, long start, AtomicInteger count, String message) {
    if (user == null) user = "molgenis";
    if (logger.isInfoEnabled()) {
      logger.info(
          "{} {} {} rows into table {} in {}ms",
          user,
          message,
          count.get(),
          table,
          (System.currentTimeMillis() - start));
    }
  }
}
