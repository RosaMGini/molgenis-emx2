package org.molgenis.emx2.io;

import org.molgenis.Row;
import org.molgenis.emx2.io.csv.CsvRowReader;
import org.molgenis.emx2.io.csv.CsvRowWriter;

import java.io.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RowStoreForCsvInMemory implements RowStore {
  private final Map<String, String> store;

  public RowStoreForCsvInMemory() {
    store = new LinkedHashMap<>();
  }

  @Override
  public void write(String name, List<Row> rows) throws IOException {
    Writer writer = new StringWriter();
    Writer bufferedWriter = new BufferedWriter(writer);
    String existing = "";
    if (store.containsKey(name)) existing = store.get(name);
    CsvRowWriter.writeCsv(rows, writer);
    store.put(name, existing + writer.toString());
  }

  @Override
  public List<Row> read(String name) throws IOException {
    if (!store.containsKey(name))
      throw new IOException("CsvStringStore with name " + name + " doesn't exist");
    Reader reader = new BufferedReader(new StringReader(store.get(name)));
    return CsvRowReader.readList(reader);
  }

  public String viewContents(String name) {
    return store.get(name);
  }
}
