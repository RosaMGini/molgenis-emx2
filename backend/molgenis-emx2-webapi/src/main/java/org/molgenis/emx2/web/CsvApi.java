package org.molgenis.emx2.web;

import static io.swagger.models.ModelImpl.OBJECT;
import static org.molgenis.emx2.web.Constants.ACCEPT_CSV;
import static org.molgenis.emx2.web.Constants.ACCEPT_FORMDATA;
import static org.molgenis.emx2.web.MolgenisWebservice.getSchema;
import static org.molgenis.emx2.web.OpenApiYamlGenerator.*;
import static spark.Spark.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.swagger.util.Yaml;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.FileSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import javax.servlet.MultipartConfigElement;
import org.jetbrains.annotations.NotNull;
import org.molgenis.emx2.*;
import org.molgenis.emx2.io.emx2.Emx2;
import org.molgenis.emx2.io.readers.CsvTableReader;
import org.molgenis.emx2.io.readers.CsvTableWriter;
import org.molgenis.emx2.io.tablestore.TableStoreForCsvFile;
import spark.Request;
import spark.Response;

public class CsvApi {

  public static final String MUTATION_REQUEST = "mutationRequestType";
  public static final String ERROR_MESSAGE = "errorMessageType";
  public static final String SUCCESS_MESSAGE = "successMessageType";
  public static final String CSV_OUTPUT = "csvOutputType";
  public static final String META = "_meta";

  private CsvApi() {
    // hide constructor
  }

  public static void create() {

    // schema level operations
    final String schemaPath = "/:schema/api/csv";
    get(schemaPath, CsvApi::getMetadata);
    patch(schemaPath, CsvApi::mergeMetadata);
    delete(schemaPath, CsvApi::discardMetadata);

    // table level operations
    final String tablePath = "/:schema/api/csv/:table";
    get(tablePath, CsvApi::tableRetrieve);
    post(tablePath, CsvApi::tableInsert);
    patch(tablePath, CsvApi::tableUpdate);
    delete(tablePath, CsvApi::tableDelete);

    // open api
    get("/:schema/api/openapi/csv", OpenApiUiFactory::getOpenApiUserInterface);
    get("/:schema/api/openapi/csv/openapi.yaml", CsvApi::getOpenApiYaml);
  }

  private static String discardMetadata(Request request, Response response) throws IOException {
    SchemaMetadata schema = Emx2.loadEmx2File(getUploadedFile(request), getSeperator(request));
    getSchema(request).discard(schema);
    response.status(200);
    return "remove metadata items success";
  }

  static String mergeMetadata(Request request, Response response) throws IOException {
    SchemaMetadata schema = Emx2.loadEmx2File(getUploadedFile(request), getSeperator(request));
    getSchema(request).migrate(schema);
    response.status(200);
    return "add/update metadata success";
  }

  static String getMetadata(Request request, Response response) throws IOException {
    Schema schema = getSchema(request);
    StringWriter writer = new StringWriter();
    Emx2.outputMetadata(new TableStoreForCsvFile(null), schema);
    response.status(200);
    return writer.toString();
  }

  private static String tableRetrieve(Request request, Response response) throws IOException {
    List<Row> rows = MolgenisWebservice.getTable(request).retrieveRows();
    StringWriter writer = new StringWriter();
    CsvTableWriter.write(rows, writer, getSeperator(request));
    response.type(ACCEPT_CSV);
    response.status(200);
    return writer.toString();
  }

  private static String tableInsert(Request request, Response response) throws IOException {
    // check if uploaded file
    File uploadFile = getUploadedFile(request);
    int count = MolgenisWebservice.getTable(request).insert(csvToRows(uploadFile, request));
    response.status(200);
    response.type(ACCEPT_CSV);
    return "" + count;
  }

  private static String tableUpdate(Request request, Response response) throws IOException {
    // check if uploaded file
    File uploadFile = getUploadedFile(request);
    int count = MolgenisWebservice.getTable(request).update(csvToRows(uploadFile, request));
    response.status(200);
    response.type(ACCEPT_CSV);
    return "" + count;
  }

  private static String tableDelete(Request request, Response response) throws IOException {
    File uploadFile = getUploadedFile(request);
    int count = MolgenisWebservice.getTable(request).delete(csvToRows(uploadFile, request));
    response.type(ACCEPT_CSV);
    response.status(200);
    return "" + count;
  }

  private static Iterable<Row> csvToRows(File file, Request request) throws IOException {
    return CsvTableReader.read(file, getSeperator(request));
  }

  private static Character getSeperator(Request request) {
    Character separator = ',';
    if ("TAB".equals(request.queryParams("separator"))) {
      separator = '\t';
    }
    return separator;
  }

  private static File getUploadedFile(Request request) {

    try {
      File tempFile = File.createTempFile(MolgenisWebservice.TEMPFILES_DELETE_ON_EXIT, ".tmp");
      tempFile.deleteOnExit();
      request.attribute(
          "org.eclipse.jetty.multipartConfig",
          new MultipartConfigElement(tempFile.getAbsolutePath()));
      if (request.raw().getPart("file") == null) {
        return null;
      }
      try (InputStream input = request.raw().getPart("file").getInputStream()) {
        Files.copy(input, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return tempFile;
      } catch (Exception e) {
        throw new MolgenisException("File upload failed", e);
      }
    } catch (Exception e) {
      throw new MolgenisException("File upload failed", e);
    }
  }

  public static String getOpenApiYaml(Request request, Response response)
      throws JsonProcessingException {

    SchemaMetadata schema = getSchema(request).getMetadata();
    OpenAPI api = getOpenAPI(schema);
    response.status(200);
    return Yaml.mapper()
        .configure(SerializationFeature.WRITE_ENUMS_USING_TO_STRING, true)
        .writeValueAsString(api);
  }

  @NotNull
  static OpenAPI getOpenAPI(SchemaMetadata schema) {
    OpenAPI api = new OpenAPI();
    api.info(
        new Info()
            .title("CSV API for: " + schema.getName())
            .description(
                "Use these operations to upload/download data and metadata in CSV format. In order to see the documentation you must have permissions. So first login."));

    // components
    Components components = new Components();
    components.addRequestBodies(MUTATION_REQUEST, getCsvRequestBody());

    components.addResponses(
        ERROR_MESSAGE,
        new ApiResponse().description(BAD_REQUEST_MESSAGE).content(getMessageContent()));
    components.addResponses(
        SUCCESS_MESSAGE, new ApiResponse().description("Success").content(getMessageContent()));
    components.addResponses(
        CSV_OUTPUT,
        new ApiResponse()
            .description("Success")
            .content(
                new Content()
                    .addMediaType(ACCEPT_CSV, new MediaType().schema(new StringSchema()))));
    api.components(components);

    // requests
    RequestBody mutationRequest = new RequestBody().$ref(MUTATION_REQUEST);

    // csv
    ApiResponses mutationResponses =
        new ApiResponses()
            .addApiResponse(OK, new ApiResponse().$ref(SUCCESS_MESSAGE))
            .addApiResponse(BAD_REQUEST, new ApiResponse().$ref(ERROR_MESSAGE));

    ApiResponses queryResponses =
        new ApiResponses()
            .addApiResponse(OK, new ApiResponse().$ref(CSV_OUTPUT))
            .addApiResponse(BAD_REQUEST, new ApiResponse().$ref(ERROR_MESSAGE));
    api.path(
        "/api/csv/" + schema.getName(),
        getCsvSchemaOperations(mutationRequest, mutationResponses, queryResponses));
    for (TableMetadata table : schema.getTables()) {
      api.path(
          "/api/csv/" + schema.getName() + "/" + table.getTableName(),
          getCsvTableOperations(mutationRequest, mutationResponses, queryResponses, table));
    }
    return api;
  }

  private static RequestBody getCsvRequestBody() {
    return new RequestBody()
        .content(
            new Content()
                .addMediaType(
                    ACCEPT_FORMDATA,
                    new MediaType()
                        .schema(
                            new io.swagger.v3.oas.models.media.Schema()
                                .type(OBJECT)
                                .addProperties(
                                    "file", new FileSchema().description("upload csv from file"))
                                .addProperties(
                                    "separator",
                                    new StringSchema().addEnumItem("TAB").addEnumItem("COMMA")))));
  }

  @NotNull
  private static PathItem getCsvSchemaOperations(
      RequestBody mutationRequest, ApiResponses mutationResponses, ApiResponses queryResponses) {
    PathItem schemaPath = new PathItem();
    schemaPath.get(
        new Operation()
            .summary("Get table metadata in EMX2 format")
            .responses(queryResponses)
            .addTagsItem(META));
    schemaPath.patch(
        new Operation()
            .summary("Add/update table metadata using EMX2 format")
            .requestBody(mutationRequest)
            .responses(mutationResponses)
            .addTagsItem(META));
    schemaPath.delete(
        new Operation()
            .summary(
                "Remove/discard tables and or colums using EMX2 format (properties are ignored)")
            .requestBody(mutationRequest)
            .responses(mutationResponses)
            .addTagsItem(META));
    return schemaPath;
  }

  @NotNull
  private static PathItem getCsvTableOperations(
      RequestBody mutationRequest,
      ApiResponses mutationResponses,
      ApiResponses queryResponses,
      TableMetadata table) {
    PathItem tablePath = new PathItem();
    tablePath.get(
        new Operation()
            .summary("Get table rows in csv format")
            .responses(queryResponses)
            .addTagsItem(table.getTableName()));
    tablePath.post(
        new Operation()
            .summary("Insert csv rows into table")
            .requestBody(mutationRequest)
            .responses(mutationResponses)
            .addTagsItem(table.getTableName()));
    tablePath.patch(
        new Operation()
            .summary("Update csv rows into table (ignores columns not provided)")
            .requestBody(mutationRequest)
            .responses(mutationResponses)
            .addTagsItem(table.getTableName()));
    tablePath.delete(
        new Operation()
            .summary("Delete csv rows from table")
            .requestBody(mutationRequest)
            .responses(mutationResponses)
            .addTagsItem(table.getTableName()));
    return tablePath;
  }
}
