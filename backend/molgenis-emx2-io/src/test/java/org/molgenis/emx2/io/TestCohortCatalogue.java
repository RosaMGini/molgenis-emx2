package org.molgenis.emx2.io;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;
import org.molgenis.emx2.Database;
import org.molgenis.emx2.Row;
import org.molgenis.emx2.Schema;
import org.molgenis.emx2.SchemaMetadata;
import org.molgenis.emx2.io.emx2.Emx2;
import org.molgenis.emx2.io.readers.CsvTableReader;
import org.molgenis.emx2.sql.TestDatabaseFactory;
import org.molgenis.emx2.utils.StopWatch;

/** representative import file for testing */
public class TestCohortCatalogue {

  static Database database;
  static Schema cohortsSchema;
  static Schema conceptionSchema;

  @BeforeClass
  public static void setup() {
    database = TestDatabaseFactory.getTestDatabase();
    conceptionSchema = database.dropCreateSchema("Conception");
    cohortsSchema = database.dropCreateSchema("CohortNetwork");
  }

  @Test
  public void importTest() throws IOException {
    StopWatch.print("begin");

    // load data model
    SchemaMetadata schema =
        Emx2.fromRowList(CsvTableReader.read(new File("../../data/datacatalogue/molgenis.csv")));
    cohortsSchema.migrate(schema);

    ImportExcelTask task2 =
        new ImportExcelTask(
            new File("../../data/datacatalogue/Cohorts.xlsx").toPath(), cohortsSchema);
    task2.run();

    ImportExcelTask task3 =
        new ImportExcelTask(
            new File("../../data/datacatalogue/Cohorts_CoreVariables.xlsx").toPath(),
            cohortsSchema);
    task3.run();

    assertEquals(52, TestCohortCatalogue.cohortsSchema.getTableNames().size());

    // export import schema to compare
  }

  @Test
  public void importStagingSchemas() {
    // import ontologies
    Schema ontologiesSchema = database.dropCreateSchema("CatalogueOntologies");
    new ImportDirectoryTask(
            new File("../../data/datacatalogue/CatalogueOntologies").toPath(), ontologiesSchema)
        .run();

    // import cdm that uses schemaRef to ontologies
    Schema cdmSchema = database.dropCreateSchema("Catalogue_cdm");
    new ImportDirectoryTask(new File("../../data/datacatalogue/Catalogue_cdm").toPath(), cdmSchema)
        .run();

    // export cdm and then import again, to validate it works
    List<Row> metadata = Emx2.toRowList(cdmSchema.getMetadata());
    Schema cdmSchema2 = database.dropCreateSchema("Catalogue_cdm2");
    cdmSchema2.migrate(Emx2.fromRowList(metadata));
  }
}
