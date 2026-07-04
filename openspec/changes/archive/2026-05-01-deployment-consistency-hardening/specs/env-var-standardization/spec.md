# ADDED Requirements

## Requirement: chat-management uses environment-overridable configuration

The `chat-management` service SHALL use the `${ENV_VAR:default}` placeholder
pattern for all infrastructure connection strings and log level configuration,
consistent with the pattern used by user-management, meeting-management, and
notification services.

### Scenario: MongoDB URI is sourced from environment

- **WHEN** the `chat-management` service starts
- **THEN** it SHALL read the MongoDB URI from the `SPRING_DATA_MONGODB_URI`
  environment variable, falling back to
  `mongodb://localhost:27017/chat_management` when the variable is absent

### Scenario: Kafka bootstrap servers are sourced from environment

- **WHEN** the `chat-management` service starts
- **THEN** it SHALL read Kafka bootstrap servers from the
  `KAFKA_BOOTSTRAP_SERVERS` environment variable, falling back to
  `localhost:9092` when the variable is absent

### Scenario: Root log level is sourced from environment

- **WHEN** the `chat-management` service starts
- **THEN** it SHALL read the root log level from the `SPRING_LOG_LEVEL_ROOT`
  environment variable, falling back to `INFO` when the variable is absent

### Scenario: Overrides take effect without code changes

- **WHEN** `SPRING_DATA_MONGODB_URI` is set to a non-default value in the pod
  environment
- **THEN** the service SHALL connect to that URI without requiring a rebuild or
  config file modification

## Requirement: meeting-management gRPC negotiation type is environment-overridable

The `meeting-management` service SHALL allow the gRPC negotiation type for the
`user-management` channel to be configured via an environment variable so that
TLS can be enabled in production without code changes.

### Scenario: Negotiation type defaults to plaintext for local development

- **WHEN** the `GRPC_NEGOTIATION_TYPE` environment variable is not set
- **THEN** the gRPC channel to user-management SHALL use `plaintext` negotiation

### Scenario: Negotiation type can be overridden to TLS in production

- **WHEN** the `GRPC_NEGOTIATION_TYPE` environment variable is set to `tls`
- **THEN** the gRPC channel to user-management SHALL use TLS negotiation without
  requiring any code change or rebuild
