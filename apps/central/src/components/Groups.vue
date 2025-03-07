<template>
  <Spinner v-if="loading" />
  <div v-else class="container">
    <h1>Welcome to MOLGENIS</h1>
    <MessageError v-if="graphqlError">{{ graphqlError }}</MessageError>
    <IconBar>
      <MessageWarning v-if="count == 0 && session.email == 'anonymous'">
        No public databases found. You might have more luck when you sign in.
      </MessageWarning>
      <MessageWarning v-else-if="count == 0 && session.email != 'admin'">
        You don't have permission to view any database. Please ask a database
        owner for permission to see their data.
      </MessageWarning>
    </IconBar>
    <div v-if="count > 0 || search">
      <InputSearch placholder="search by name" v-model="search" />
      <label>{{ count }} databases found</label>
      <table class="table table-hover table-bordered bg-white">
        <thead>
          <th style="width: 1px">
            <IconAction
              v-if="session && session.email == 'admin'"
              icon="plus"
              @click="openCreateSchema"
            />
          </th>
          <th>name</th>
          <th>description</th>
        </thead>
        <tbody>
          <tr v-for="schema in schemasFiltered" :key="schema.name">
            <td>
              <div style="display: flex">
                <IconDanger
                  v-if="session && session.email == 'admin'"
                  icon="trash"
                  @click="openDeleteSchema(schema.name)"
                />
              </div>
            </td>
            <td>
              <a href="#" @click.prevent="openGroup(schema.name)">{{
                schema.name
              }}</a>
            </td>
            <td>{{ schema.description }}</td>
          </tr>
        </tbody>
      </table>
      <SchemaCreateModal v-if="showCreateSchema" @close="closeCreateSchema" />
      <SchemaDeleteModal
        v-if="showDeleteSchema"
        @close="closeDeleteSchema"
        :schemaName="showDeleteSchema"
      />
    </div>
  </div>
</template>

<script>
import { request } from "graphql-request";

import SchemaCreateModal from "./SchemaCreateModal";
import SchemaDeleteModal from "./SchemaDeleteModal";
import {
  IconAction,
  IconBar,
  IconDanger,
  Spinner,
  MessageWarning,
  InputSearch,
} from "@mswertz/emx2-styleguide";

export default {
  components: {
    Spinner,
    SchemaCreateModal,
    SchemaDeleteModal,
    IconBar,
    IconAction,
    IconDanger,
    MessageWarning,
    InputSearch,
  },
  props: {
    session: Object,
  },
  data: function () {
    return {
      schemas: [],
      loading: false,
      showCreateSchema: false,
      showDeleteSchema: false,
      graphqlError: null,
      search: null,
    };
  },
  computed: {
    count() {
      return this.schemasFiltered.length;
    },
    schemasFiltered() {
      if (this.search && this.search.trim().length > 0) {
        let terms = this.search.toLowerCase().split(" ");
        return this.schemas.filter((s) =>
          terms.every(
            (v) =>
              s.name.toLowerCase().includes(v) ||
              (s.description && s.description.toLowerCase().includes(v))
          )
        );
      }
      return this.schemas;
    },
  },
  created() {
    this.getSchemaList();
  },
  methods: {
    openGroup(name) {
      window.open("/" + name + "/tables/", "_self");
    },
    openCreateSchema() {
      this.showCreateSchema = true;
    },
    closeCreateSchema() {
      this.showCreateSchema = false;
      this.getSchemaList();
    },
    openDeleteSchema(schemaName) {
      this.showDeleteSchema = schemaName;
    },
    closeDeleteSchema() {
      this.showDeleteSchema = null;
      this.getSchemaList();
    },
    getSchemaList() {
      this.loading = true;
      request("graphql", "{Schemas{name}}")
        .then((data) => {
          this.schemas = data.Schemas;
          this.loading = false;
        })
        .catch(
          (error) =>
            (this.graphqlError = "internal server graphqlError" + error)
        );
    },
  },
};
</script>
