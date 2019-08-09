package org.molgenis;

import org.junit.Test;
import org.molgenis.beans.SchemaMetadata;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.*;
import static org.molgenis.Type.*;

public class TestRows {

  @Test
  public void test1() throws MolgenisException {
    List<Type> types = Arrays.asList(STRING, INT, DECIMAL, BOOL, UUID, TEXT, DATE, DATETIME);

    SchemaMetadata m = new SchemaMetadata("test1");
    addContents(m, types);

    SchemaMetadata m2 = new SchemaMetadata("test1");
    addContents(m2, types);

    // System.out.println("No diff: " + m.diff(m2));

    assertNotNull(m.getTableNames().contains("TypeTest"));
    assertEquals(1, m.getTableNames().size());

    // System.out.println("model print: " + m.print());
    Table t = m.getTable("TypeTest");
    assertEquals("TypeTest", t.getName());
    assertEquals(3 * types.size(), t.getColumns().size());
    assertEquals(BOOL, t.getColumn("testBOOL").getType());

    // System.out.println("table print " + t.toString() + "\n: " + t.print());

    m2.createTableIfNotExists("OtherTable");
    // System.out.println("Now we expect diff: " + m.diff(m2));

    m.dropTable("TypeTest");
    try {
      m.getTable("TypeTest");
      fail("Table should have been dropped");
    } catch (Exception e) {
      // this is expected
    }
    assertEquals(0, m.getTableNames().size());
  }

  @Test
  public void testSimpleTypes() {
    org.molgenis.Row r = new Row();

    // int
    r.setString("test", "1");
    assertEquals(1, (int) r.getInteger("test"));
    assertNull(r.getInteger("testnull"));
    try {
      r.setString("test", "a");
      assertEquals(1, (int) r.getInteger("test"));
      fail("shouldn't be able to get 'a' to int ");
    } catch (Exception e) {
    }

    // decimal
    r.setString("test", "1.0");
    assertEquals(1.0, (double) r.getDecimal("test"));
    assertNull(r.getDecimal("testnull"));
    try {
      r.setString("test", "a");
      r.getDecimal("test");
      fail("shouldn't be able to get 'a' to decimal ");
    } catch (Exception e) {
    }

    // bool
    r.setBool("test", true);
    assertTrue(r.getBoolean("test"));

    r.setBool("testnull", null);
    assertNull(r.getBoolean("testnull"));

    r.setString("test", "true");
    assertTrue(r.getBoolean("test"));

    try {
      r.setString("test", "a");
      r.getBoolean("test");
      fail("shouldn't be able to get 'a' to boolean ");
    } catch (Exception e) {
    }

    // uuid
    java.util.UUID uuid = java.util.UUID.randomUUID();
    r.setString("test", uuid.toString());
    assertEquals(uuid, r.getUuid("test"));
    assertNull(r.getUuid("testnull"));
    try {
      r.setString("test", "a");
      r.getUuid("test");
      fail("shouldn't be able to get 'a' to uuid ");
    } catch (Exception e) {
    }

    // date
    String dateString = "2012-10-03";
    r.setString("test", dateString);
    LocalDate date = LocalDate.parse(dateString);
    assertEquals(date, r.getDate("test"));
    assertNull(r.getDate("testnull"));
    try {
      r.setString("test", "a");
      r.getDate("test");
      fail("shouldn't be able to get 'a' to date ");
    } catch (Exception e) {
    }

    // datetime
    String dateTimeString = "2012-10-03T18:00";
    r.setString("test", dateTimeString);
    LocalDateTime dateTime = LocalDateTime.parse(dateTimeString);
    assertEquals(dateTime, r.getDateTime("test"));
    assertNull(r.getDate("testnull"));
    try {
      r.setString("test", "a");
      r.getDateTime("test");
      fail("shouldn't be able to get 'a' to datetime ");
    } catch (Exception e) {
    }

    // string if not a string
    r.setInt("test", 1);
    assertEquals("1", r.getString("test"));
    assertNull(r.getString("testnull"));
  }

  @Test
  public void testArrayTypes() {
    Row r = new Row();

    // null should return in empty array
    assertTrue(r.getUuidArray("test") instanceof UUID[]);
    assertTrue(r.getStringArray("test") instanceof String[]);
    assertTrue(r.getIntegerArray("test") instanceof Integer[]);
    assertTrue(r.getBooleanArray("test") instanceof Boolean[]);
    assertTrue(r.getDecimalArray("test") instanceof Double[]);
    assertTrue(r.getDateArray("test") instanceof LocalDate[]);
    assertTrue(r.getDateTimeArray("test") instanceof LocalDateTime[]);
    assertTrue(r.getTextArray("test") instanceof String[]);

    // cast UUID[1] from some Object
    r.setString("test", "cfb11a12-dad6-4b98-a48b-9a32f60a742f");
    assertEquals(
        new java.util.UUID[] {java.util.UUID.fromString("cfb11a12-dad6-4b98-a48b-9a32f60a742f")}
            [0].toString(),
        r.getUuidArray("test")[0].toString());

    // cast String[] from some object
    r.setInt("test", 9);
    assertEquals("9", r.getStringArray("test")[0]);

    // cast int from some object
    r.setDecimal("test", 9.3);
    try {
      assertEquals(9, r.getIntegerArray("test"));
      fail("cannot convert, should fail");
    } catch (Exception e) {
    }

    r.set("test", new Boolean[] {true, false});
    assertArrayEquals(new Boolean[] {true, false}, r.getBooleanArray("test"));

    r.set("test", new String[] {"true", "false"});
    assertArrayEquals(new Boolean[] {true, false}, r.getBooleanArray("test"));

    r.set("test", "true");
    assertArrayEquals(new Boolean[] {true}, r.getBooleanArray("test"));

    r.setString("test", "9.3");
    assertArrayEquals(new Double[] {9.3}, r.getDecimalArray("test"));

    OffsetDateTime odt = OffsetDateTime.of(2018, 12, 12, 12, 12, 12, 12, ZoneOffset.UTC);
    r.set("test", odt);
    assertArrayEquals(new LocalDateTime[] {odt.toLocalDateTime()}, r.getDateTimeArray("test"));
  }

  private void addContents(SchemaMetadata m, List<Type> types) throws MolgenisException {
    Table t = m.createTableIfNotExists("TypeTest");
    for (Type type : types) {
      t.addColumn("test" + type, type);
      t.addColumn("test" + type + "_nullable", type).nullable(true);
      t.addColumn("test" + type + "+readonly", type).setReadonly(true);
    }
  }
}
