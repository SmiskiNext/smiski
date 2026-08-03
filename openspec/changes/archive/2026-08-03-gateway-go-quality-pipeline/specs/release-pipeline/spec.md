## MODIFIED Requirements

### Requirement: Release quality gate

Before publishing any artifact, the workflow SHALL run
`./services/gradlew -p services/<name> assemble test integrationTest` for all
four backend services (`tenant`, `meet`, `record`, `notification`) on a
Docker-enabled runner, and SHALL additionally build and test the gateway Go
module. The workflow SHALL NOT build or push any image, create any tag, or
create any release if any of these tasks fail.

#### Scenario: All services pass

- **WHEN** assemble and the `test`/`integrationTest` source sets pass for all
  four services, and the gateway module builds and its tests pass
- **THEN** the workflow proceeds to image publishing

#### Scenario: A service fails its gate

- **WHEN** any unit, ArchUnit, or integration test fails for any service
- **THEN** the workflow fails and no image, tag, or release is produced

#### Scenario: Gateway fails its gate

- **WHEN** the gateway module fails to compile or any of its tests fail
- **THEN** the workflow fails and no image, tag, or release is produced,
  including the images of the passing Java services

### Requirement: Container image publishing

For each of the four Java services, the workflow SHALL build the image with
Spring Boot `bootBuildImage` using the resolved version, then push it to
`ghcr.io/smiskinext/<service>` tagged with both the resolved `X.Y.Z` and
`latest`. For the gateway, which has no Gradle build, the workflow SHALL build
the image from its Dockerfile in a dedicated job and push it to
`ghcr.io/smiskinext/gateway` tagged with the same resolved `X.Y.Z` and `latest`.
The workflow SHALL authenticate to GHCR before pushing. All published images
SHALL carry the same resolved version.

#### Scenario: Image pushed with both tags

- **WHEN** the version resolves to `1.4.0` and the gate has passed
- **THEN** each service image is available at
  `ghcr.io/smiskinext/<service>:1.4.0` and `ghcr.io/smiskinext/<service>:latest`

#### Scenario: Gateway image pushed with both tags

- **WHEN** the version resolves to `1.4.0` and the gate has passed
- **THEN** the gateway image is available at `ghcr.io/smiskinext/gateway:1.4.0`
  and `ghcr.io/smiskinext/gateway:latest`

#### Scenario: Synchronized versions

- **WHEN** a release publishes images for all services
- **THEN** every service image, including the gateway, shares the same `X.Y.Z`
  tag

#### Scenario: Gateway image build failure aborts the release

- **WHEN** the gateway image fails to build from its Dockerfile
- **THEN** the workflow fails and does not create the git tag or GitHub Release

#### Scenario: Push failure aborts the release

- **WHEN** authentication to GHCR fails or an image push fails
- **THEN** the workflow fails and does not create the git tag or GitHub Release

## ADDED Requirements

### Requirement: Gateway container build reproducibility

The Go version used by the gateway container build SHALL match the version
required by the module, so the build does not implicitly resolve a different
toolchain at build time. The image build SHALL succeed without network access to
a toolchain distribution service beyond its declared base image and module
dependencies.

#### Scenario: Base image matches the module requirement

- **WHEN** the Go version of the container build's base image is compared with
  the `go` directive in the gateway `go.mod`
- **THEN** the base image satisfies the module requirement without an implicit
  toolchain upgrade

#### Scenario: Mismatched base image is rejected

- **WHEN** the base image declares a Go version older than the module requires
- **THEN** the build is treated as misconfigured, because it would otherwise
  depend on an implicit toolchain download that is slow and fails in a
  network-restricted builder

#### Scenario: Compiled binary is excluded from build context

- **WHEN** the image is built from a working tree containing a locally compiled
  binary
- **THEN** that binary is excluded from the build context and does not enter the
  image
