<template>
  <div class="table-responsive">
    <table class="table table-bordered table-sm overflow-auto">
      <thead>
        <th>Name</th>
        <th>Description</th>
        <th>noParticipants</th>
        <th>geographicRegion</th>
        <th>ageCategories</th>
        <th>Disease</th>
        <th>Other inclusion criteria</th>
      </thead>
      <tbody>
        <tr v-for="c in subcohorts" :key="c.name">
          <td>{{ c.name }}</td>
          <td>{{ c.description ? c.description : "N/A" }}</td>
          <td>{{ c.noParticipants }}</td>
          <td>
            <OntologyTerms :terms="c.geographicRegion" :color="color" />
          </td>
          <td>
            <OntologyTerms :terms="c.ageCategories" :color="color" />
          </td>
          <td>
            <OntologyTerms :terms="c.disease" :color="color" />
          </td>
          <td>{{ c.inclusionCriteria ? c.inclusionCriteria : "N/A" }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
<script>
import OntologyTerms from "./OntologyTerms";

export default {
  components: { OntologyTerms },
  props: {
    subcohorts: Array,
    color: String,
  },
};
</script>
