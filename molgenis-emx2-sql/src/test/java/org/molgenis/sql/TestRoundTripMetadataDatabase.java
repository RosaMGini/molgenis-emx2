package org.molgenis.sql;

import org.junit.BeforeClass;
import org.junit.Test;
import org.molgenis.data.Database;
import org.molgenis.MolgenisException;
import org.molgenis.data.Schema;
import org.molgenis.emx2.examples.*;
import org.molgenis.emx2.examples.synthetic.*;

import static junit.framework.TestCase.fail;
import static org.molgenis.emx2.examples.CompareTools.reloadAndCompare;

public class TestRoundTripMetadataDatabase {

  static final String SCHEMA_NAME = "TestRoundTripMetadataDatabase";

  static Database database;

  @BeforeClass
  public static void setup() throws MolgenisException {
    database = DatabaseFactory.getTestDatabase("molgenis", "molgenis");
  }

  @Test
  public void testProductComponentsPartsModel() throws MolgenisException {
    Schema schema = database.createSchema(SCHEMA_NAME + "1");
    ProductComponentPartsExample.create(schema.getMetadata());
    try {
      reloadAndCompare(database, schema);
    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  @Test
  public void testSimpleTypesTest() throws MolgenisException {
    Schema schema = database.createSchema(SCHEMA_NAME + "2");
    SimpleTypeTestExample.createSimpleTypeTest(schema.getMetadata());
    try {
      reloadAndCompare(database, schema);
    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  @Test
  public void testArrayTypesTest() throws MolgenisException {
    Schema schema = database.createSchema(SCHEMA_NAME + "3");
    ArrayTypeTestExample.createSimpleTypeTest(schema.getMetadata());
    try {
      reloadAndCompare(database, schema);
    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  @Test
  public void testRefAndRefArrayTypesTest() throws MolgenisException {
    Schema schema = database.createSchema(SCHEMA_NAME + "4");
    RefAndRefArrayTestExample.createRefAndRefArrayTestExample(schema.getMetadata());
    try {
      reloadAndCompare(database, schema);
    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  @Test
  public void testCompsiteRefs() throws MolgenisException {
    Schema schema = database.createSchema(SCHEMA_NAME + "5");
    CompositeRefExample.createCompositeRefExample(schema.getMetadata());
    try {
      reloadAndCompare(database, schema);
    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  @Test
  public void testCompsitePrimaryKeys() throws MolgenisException {
    Schema schema = database.createSchema(SCHEMA_NAME + "6");
    CompositePrimaryKeyExample.createCompositePrimaryKeyExample(schema.getMetadata());
    try {
      reloadAndCompare(database, schema);
    } catch (Exception e) {
      fail(e.getMessage());
    }
  }
}
