# Purpose

Define how the Android app serializes API payloads with Jackson so Retrofit,
generated DTOs, and release builds correctly support `JsonNullable` PATCH
semantics.

# Requirements

## Requirement: Jackson replaces Gson as Retrofit serializer

The Android app SHALL use Jackson with JacksonConverterFactory for Retrofit JSON
serialization instead of Gson.

### Scenario: Retrofit uses Jackson converter

- **WHEN** Retrofit is configured in NetworkModule
- **THEN** it SHALL use JacksonConverterFactory.create(objectMapper)
- **AND** NOT use GsonConverterFactory

### Scenario: ObjectMapper has JsonNullable support

- **WHEN** ObjectMapper is created
- **THEN** it SHALL register JsonNullableModule
- **AND** set serialization inclusion to NON_NULL

## Requirement: JsonNullable fields serialize correctly for PATCH requests

JsonNullable fields SHALL serialize according to PATCH semantics: present values
serialize as-is, null values serialize as JSON null, undefined values are
omitted.

### Scenario: JsonNullable with value serializes to value

- **WHEN** a DTO field is `JsonNullable.of("new value")`
- **THEN** the JSON output SHALL contain `"fieldName": "new value"`

### Scenario: JsonNullable with null serializes to null

- **WHEN** a DTO field is `JsonNullable.of(null)`
- **THEN** the JSON output SHALL contain `"fieldName": null`

### Scenario: JsonNullable undefined omits field

- **WHEN** a DTO field is `JsonNullable.undefined()`
- **THEN** the JSON output SHALL NOT contain the field at all

## Requirement: OpenAPI generator configured for Jackson

The OpenAPI generator SHALL be configured to generate Jackson-compatible DTOs
with JsonNullable support.

### Scenario: OpenAPI config uses Jackson serialization

- **WHEN** openApiGenerate task runs
- **THEN** configOptions SHALL include `"serializationLibrary": "jackson"`
- **AND** configOptions SHALL include `"openApiNullable": "true"`

### Scenario: Generated DTOs use Jackson annotations

- **WHEN** DTOs are generated
- **THEN** they SHALL use `@JsonProperty` annotations
- **AND** nullable fields SHALL be wrapped in `JsonNullable<T>`

## Requirement: ProGuard rules preserve Jackson classes

Release builds SHALL include ProGuard rules to prevent R8 from stripping Jackson
and DTO classes.

### Scenario: DTO classes preserved in release build

- **WHEN** the app is built with minification enabled
- **THEN** all classes in `io.github.phunguy65.zms.data.remote.dto` SHALL be
  preserved

### Scenario: Jackson annotations preserved

- **WHEN** the app is built with minification enabled
- **THEN** classes with `@JsonCreator` and `@JsonProperty` annotations SHALL
  have those annotations preserved

### Scenario: JsonNullable classes preserved

- **WHEN** the app is built with minification enabled
- **THEN** all classes in `org.openapitools.jackson.nullable` SHALL be preserved

## Requirement: Dependencies updated in version catalog

All new dependencies SHALL be declared in the Gradle version catalog.

### Scenario: Firebase Storage in catalog

- **WHEN** firebase-storage is needed
- **THEN** it SHALL be declared in `gradle/libs.versions.toml` as
  `firebase-storage = { module = "com.google.firebase:firebase-storage" }`

### Scenario: Jackson converter in catalog

- **WHEN** Jackson converter is needed
- **THEN** it SHALL be declared with version matching Retrofit version (2.12.0)

### Scenario: Jackson databind in catalog

- **WHEN** Jackson databind is needed
- **THEN** it SHALL be declared with version 2.15.2 or compatible
