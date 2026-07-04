import { defineConfig } from "@hey-api/openapi-ts";

export default defineConfig({
  input: "../../openapi/unified-openapi.yaml",
  output: {
    path: "src/generated",
  },
  client: "@hey-api/client-fetch",
  plugins: ["@hey-api/typescript", "@hey-api/sdk"],
});
