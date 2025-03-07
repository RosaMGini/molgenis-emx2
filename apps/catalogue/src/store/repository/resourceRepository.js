import { request } from "graphql-request";
import resources from "../query/resources.gql";
let cache = null;

const fetchResources = async () => {
  if (!cache) {
    cache = (await request("graphql", resources).catch((e) => console.error(e)))
      .Resources;
  }

  return cache;
};

export { fetchResources };
