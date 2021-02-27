package org.molgenis.emx2.io.emx2;

import static org.molgenis.emx2.Column.column;
import static org.molgenis.emx2.ColumnType.STRING;
import static org.molgenis.emx2.TableMetadata.table;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.molgenis.emx2.*;
import org.molgenis.emx2.io.readers.CsvTableReader;
import org.molgenis.emx2.io.tablestore.TableStore;

public class Emx2 {

  public static final String TABLE_NAME = "tableName";
  public static final String COLUMN_NAME = "columnName";
  public static final String DESCRIPTION = "description";
  public static final String TABLE_EXTENDS = "tableExtends";
  public static final String COLUMN_TYPE = "columnType";
  public static final String KEY = "key";
  public static final String REF_SCHEMA = "refSchema";
  public static final String REF_TABLE = "refTable";
  public static final String REF_LINK = "refLink";
  public static final String MAPPED_BY = "mappedBy";
  public static final String REQUIRED = "required";
  private static final String VALIDATION = "validation";
  private static final String JSONLD_TYPE = "jsonldType";

  private Emx2() {
    // hidden
  }

  public static void inputMetadata(TableStore store, Schema schema) {
    SchemaMetadata emx2Schema = Emx2.fromRowList(store.readTable("molgenis"));
    schema.migrate(emx2Schema);
  }

  public static SchemaMetadata fromRowList(Iterable<Row> rows) {
    SchemaMetadata schema = new SchemaMetadata();
    int lineNo = 1;

    for (Row r : rows) {
      String tableName = r.getString(TABLE_NAME);
      if (tableName == null) {
        throw new MolgenisException(
            "Parsing of sheet molgenis failed: Required column "
                + TABLE_NAME
                + " is empty on line "
                + lineNo);
      }

      if (schema.getTableMetadata(tableName) == null) {
        schema.create(table(tableName));
      }

      // load table metadata, this is when columnName is empty
      if (r.getString(COLUMN_NAME) == null) {
        schema.getTableMetadata(tableName).setDescription(r.getString(DESCRIPTION));
        schema.getTableMetadata(tableName).setInherit(r.getString(TABLE_EXTENDS));
        schema.getTableMetadata(tableName).setImportSchema(r.getString(REF_SCHEMA));
        schema.getTableMetadata(tableName).setJsonldType(r.getString(JSONLD_TYPE));
      }

      // load column metadata
      else {
        try {
          if (r.getString(TABLE_EXTENDS) != null) {
            throw new MolgenisException(
                "Parsing of sheet molgenis failed: Column "
                    + TABLE_EXTENDS
                    + " not supported for columns at "
                    + lineNo);
          }

          Column column = column(r.getString(COLUMN_NAME));
          if (r.notNull(COLUMN_TYPE))
            column.setType(ColumnType.valueOf(r.getString(COLUMN_TYPE).toUpperCase()));

          if (r.notNull(KEY)) column.setKey(r.getInteger(KEY));
          if (r.notNull(REF_SCHEMA)) column.setRefSchema(r.getString(REF_SCHEMA));
          if (r.notNull(REF_TABLE)) column.setRefTable(r.getString(REF_TABLE));
          if (r.notNull(REF_LINK)) column.setRefLink(r.getString(REF_LINK));
          if (r.notNull(MAPPED_BY)) column.setMappedBy(r.getString(MAPPED_BY));
          if (r.notNull(REQUIRED)) column.setRequired(r.getBoolean(REQUIRED));
          if (r.notNull(DESCRIPTION)) column.setDescription(r.getString(DESCRIPTION));
          if (r.notNull(VALIDATION)) column.setValidationExpression(r.getString(VALIDATION));
          if (r.notNull(JSONLD_TYPE)) column.setJsonldType(r.getString(JSONLD_TYPE));

          schema.getTableMetadata(tableName).add(column);
        } catch (Exception e) {
          throw new MolgenisException("Error on line " + lineNo + ":" + e.getMessage());
        }
      }
      lineNo++;
    }
    return schema;
  }

  public static void outputMetadata(TableStore store, Schema schema) {
    outputMetadata(store, schema.getMetadata());
  }

  public static void outputMetadata(TableStore store, SchemaMetadata schema) {
    store.writeTable("molgenis", toRowList(schema));
  }

  public static List<Row> toRowList(SchemaMetadata schema) {
    List<Row> result = new ArrayList<>();

    // deterministic order (TODO make user define order)
    List<String> tableNames = new ArrayList<>();
    tableNames.addAll(schema.getTableNames());
    Collections.sort(tableNames);

    for (String tableName : tableNames) {
      TableMetadata t = schema.getTableMetadata(tableName);

      Row row = new Row();
      row.setString(TABLE_NAME, t.getTableName());
      if (t.getInherit() != null) row.setString(TABLE_EXTENDS, t.getInherit());
      if (t.getDescription() != null) row.setString(DESCRIPTION, t.getDescription());
      // todo, also trim in case of array, make the simple array
      if (t.getJsonldType() != null)
        row.setString(JSONLD_TYPE, t.getJsonldType().replaceAll("(?:^\")|(?:\"$)", ""));

      result.add(row);

      List<String> columnNames = new ArrayList<>(t.getColumnNames());
      for (String columnName : columnNames) {

        Column c = t.getColumn(columnName);

        // only non-inherited
        if (c.getTableName().equals(t.getTableName())) {
          row = new Row();

          row.setString(TABLE_NAME, t.getTableName());
          row.setString(COLUMN_NAME, c.getName());
          if (!c.getColumnType().equals(STRING))
            row.setString(COLUMN_TYPE, c.getColumnType().toString().toLowerCase());
          if (c.isRequired()) row.setBool(REQUIRED, c.isRequired());
          if (c.getKey() > 0) row.setInt(KEY, c.getKey());
          if (!c.getRefSchema().equals(c.getSchemaName()))
            row.setString(REF_SCHEMA, c.getRefTableName());
          if (c.getRefTableName() != null) row.setString(REF_TABLE, c.getRefTableName());
          if (c.getRefLink() != null) row.setString(REF_LINK, c.getRefLink());
          if (c.getMappedBy() != null) row.setString(MAPPED_BY, c.getMappedBy());
          if (c.getDescription() != null) row.set(DESCRIPTION, c.getDescription());
          if (c.getValidationExpression() != null) row.set(VALIDATION, c.getValidationExpression());
          if (c.getJsonldType() != null)
            row.set(JSONLD_TYPE, c.getJsonldType().replaceAll("(?:^\")|(?:\"$)", ""));

          result.add(row);
        }
      }
    }
    return result;
  }

  public static SchemaMetadata loadEmx2File(File file, Character separator) throws IOException {
    return fromRowList(CsvTableReader.read(file, separator));
  }
}
