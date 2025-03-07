<script>
import { request } from "graphql-request";
import TableMetadataMixin from "./TableMetadataMixin";

export default {
  extends: TableMetadataMixin,
  props: {
    /** Name of the table within graphql endpoint */
    table: String,
    /** pass filters conform TableMixin */
    filter: {},
  },
  data: function () {
    return {
      data: [],
      count: 0,
      offset: 0,
      limit: 20,
      searchTerms: null,
    };
  },
  computed: {
    //filter can be passed as prop or overridden in subclass
    graphqlFilter() {
      if (this.filter) {
        return this.filter;
      } else return {};
    },
    graphql() {
      if (this.tableMetadata == undefined) {
        return "";
      }
      let search =
        this.searchTerms != null && this.searchTerms !== ""
          ? ',search:"' + this.searchTerms + '"'
          : "";
      return `query ${this.table}($filter:${this.table}Filter){
              ${this.table}(filter:$filter,limit:${this.limit},offset:${this.offset}${search}){${this.columnNames}}
              ${this.table}_agg(filter:$filter${search}){count}}`;
    },
    tableMetadata() {
      return this.getTable(this.table);
    },
    columnNames() {
      let result = "";
      if (this.tableMetadata != null) {
        this.tableMetadata.columns.forEach((col) => {
          if (
            ["REF", "REF_ARRAY", "REFBACK", "MREF"].includes(col.columnType)
          ) {
            result = result + " " + col.name + "{" + this.refGraphql(col) + "}";
          } else if (col.columnType == "FILE") {
            result = result + " " + col.name + "{id,size,extension,url}";
          } else if (col.columnType != "CONSTANT") {
            result = result + " " + col.name;
          }
        });
      }
      return result;
    },
  },
  methods: {
    reload() {
      if (this.tableMetadata != undefined) {
        this.loading = true;
        this.graphqlError = null;
        request(this.graphqlURL, this.graphql, { filter: this.graphqlFilter })
          .then((data) => {
            this.data = data[this.table];
            this.count = data[this.table + "_agg"]["count"];
            this.loading = false;
          })
          .catch((error) => {
            this.graphqlError = "internal server graphqlError" + error;
            this.loading = false;
          });
      }
    },
    refGraphql(column) {
      let graphqlString = "";
      this.getTable(column.refTable).columns.forEach((c) => {
        if (c.key == 1) {
          graphqlString += c.name + " ";
          if (["REF", "REF_ARRAY", "REFBACK", "MREF"].includes(c.columnType)) {
            graphqlString += "{" + this.refGraphql(c) + "}";
          }
        }
      });
      return graphqlString;
    },
    getTable(table) {
      let result = undefined;
      if (this.schema != null && this.schema.tables != null) {
        this.schema.tables.forEach((t) => {
          if (t.name == table) {
            result = t;
          }
        });
        if (!result) {
          this.graphqlError = "Table " + table + " not found";
        }
      }
      if (result) return result;
    },
    getPkey(row) {
      //we only have pkey when the record has been saved
      if (!row["mg_insertedOn"]) return null;
      let result = {};
      if (this.tableMetadata != null) {
        this.tableMetadata.columns.forEach((col) => {
          if (col.key == 1 && row[col.name]) {
            result[col.name] = row[col.name];
          }
        });
      }
      return result;
    },
  },
  watch: {
    searchTerms: "reload",
    graphqlFilter: {
      deep: true,
      handler() {
        this.reload();
      },
    },
    table: "reload",
    schema: "reload",
    limit: "reload",
  },
};
</script>

<docs>
```
<!-- normally you don't need provide graphqlURL because default 'graphql' just works -->
<TableMixin table="Pet" graphqlURL="/pet store/graphql"/>
```
</docs>
