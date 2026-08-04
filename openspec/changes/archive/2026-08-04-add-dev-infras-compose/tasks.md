## 1. Service configuration corrections

- [x] 1.1 Add `app.cursor.secret: ${CURSOR_SECRET:...}` to
      `services/tenant/src/main/resources/application.yaml` so the shared
      `CursorEncoder` bean resolves under the default profile
- [x] 1.2 Change the datasource port in
      `services/tenant/src/main/resources/application-dev.yaml` from `8283` to
      `8281`
- [x] 1.3 Change the datasource port in
      `services/notification/src/main/resources/application-dev.yaml` from
      `5432` to `8283` ← (verify: no dev profile port collides, and none targets
      a removed component's port)

## 2. Dead configuration removal

- [x] 2.1 Remove the `app.livekit.recording` block from
      `services/meet/src/main/resources/application.yaml`
- [x] 2.2 Remove the `recording` overrides from
      `services/meet/src/main/resources/application-dev.yaml` and
      `application-staging.yaml`
- [x] 2.3 Remove the `recording` block from
      `services/meet/src/integrationTest/resources/application-test.yaml`
- [x] 2.4 Remove `implementation(libs.aws.s3)` from
      `services/meet/build.gradle.kts`
- [x] 2.5 Remove `invite-invalidated-consumer-group` from
      `services/notification/src/main/resources/application.yaml` and its
      integration-test override ← (verify: no remaining reference to removed
      keys anywhere outside `build/` and `bin/`)

## 3. Envoy route table

- [x] 3.1 Add an exact-path route for `/api/1/webhooks/livekit` targeting
      `meet_cluster`, with FIT validation and external authorization disabled
- [x] 3.2 Add an exact-path route for `/api/1/webhooks/resend/inbound` targeting
      `notification_cluster`, with both filters disabled
- [x] 3.3 Add an anchored regex route matching only
      `/api/1/meetings/{id}/events` and
      `/api/1/meetings/{id}/join-requests/{requestId}/events`, targeting
      `notification_cluster`, with both filters disabled and the per-route
      response timeout disabled
- [x] 3.4 Add a prefix route for `/api/1/issues` targeting `meet_cluster` with
      both filters left active
- [x] 3.5 Remove the `/api/1/notifications` prefix route
- [x] 3.6 Order all routes so the exact-path and regex routes precede the
      `/api/1/meetings` prefix route
- [x] 3.7 Raise the connection manager's stream idle timeout above the
      notification service's heartbeat interval and stream lifetime ← (verify:
      route order yields the owning service for every endpoint in the inventory;
      both filters are disabled on all three bypassed routes, not just one)

## 4. Compose stack

- [x] 4.1 Add three Postgres services publishing host ports `8281` (tenant),
      `8282` (meet), `8283` (notification), each with a readiness health check
      and a named volume
- [x] 4.2 Add Kafka in KRaft mode with an internal listener on `9092` and a host
      listener on `9094`, with a broker health check
- [x] 4.3 Add Valkey with a ping health check
- [x] 4.4 Add the real-time media server and its dedicated Redis, publishing
      signalling and media ports to the host, with the advertised host address
      supplied as a required variable
- [x] 4.5 Add the `gateway` service built from `services/gateway`. Its scratch
      image ships no shell and no probe binary, so declare no health check and
      compensate with an Envoy `retry_policy`, documenting both
- [x] 4.6 Add the `envoy` service mounting `envoy.yaml` and the Lua script,
      publishing the gateway port and the admin port, ordered after `gateway`
- [x] 4.7 Add `tenant`, `meet`, and `notification` referencing the published
      image coordinates with an overridable version, each depending on its
      datastores being healthy; declare no health check, since the Paketo run
      image cannot reach the unprefixed health endpoint from inside the
      container
- [x] 4.8 Point the media-server webhook URL at the `meet` service directly
      rather than through the gateway ← (verify: service names match the Envoy
      cluster hostnames exactly; every service waits for its dependencies; no
      object-storage or egress container is present)

## 5. Environment template and documentation

- [x] 5.1 Add `services/docker/.env.example` enumerating every variable the
      stack consumes, grouped by component, marking those with no default as
      required
- [x] 5.2 Add `../../../../services/docker/AGENTS.md` with the host port map,
      the image build prerequisite, and startup and teardown commands
- [x] 5.3 Document the gateway route table in the README, stating each route's
      owning service and authentication level
- [x] 5.4 Document the accepted risk prominently: the event-stream routes are
      unauthenticated, a join-request stream delivers a media room token, and
      the stack is restricted to local development ← (verify: template covers
      every consumed variable; required variables are marked; the security
      warning is present and states the local-only restriction)

## 6. Repository documentation corrections

- [x] 6.1 Correct `services/AGENTS.md`: `notification` has a database and Flyway
      migrations; remove `record` from the service list and the Flyway section
- [x] 6.2 Replace the non-functional CLI commands in `AGENTS.md` and `CLAUDE.md`
      with the compose commands
- [x] 6.3 Correct the quick-start section of `AGENTS.md` to the actual startup
      sequence, including the image build step ← (verify: no document instructs
      a command that cannot run)

## 7. Verification

- [x] 7.1 Run `pnpm format` and `pnpm lint`
- [x] 7.2 Validate the Envoy configuration with `envoy --mode validate`
- [x] 7.3 Validate the compose definition with `docker compose config`
- [x] 7.4 Run `./services/gradlew -p services/meet build` to confirm the removed
      recording configuration and AWS dependency break nothing
- [x] 7.5 Run `./services/gradlew -p services/tenant test` and
      `./services/gradlew -p services/notification test`
- [x] 7.6 Exercise the route table against stub upstreams: every route reaches
      its owning cluster, the three bypassed routes return a backend response
      while the three authenticated routes are rejected before reaching a
      backend, and no sibling or deeper path widens the unauthenticated surface
- [x] 7.7 Confirm the gateway readiness endpoint responds on the admin port
- [x] 7.8 Confirm an event-stream request through the gateway returns
      `text/event-stream`, is delivered frame by frame, and outlives the default
      per-route response timeout
- [x] 7.9 Confirm a request to the issue-scoped meeting collection is matched by
      its own route and subjected to authentication, rather than answered with
      not-found ← (verify: every scenario in `specs/dev-infras/spec.md` and the
      Envoy delta has been exercised or has a stated reason for deferral)
- [x] 7.10 Full-stack runtime check with real service images: the three Java
      images built successfully and the stack started — all six infrastructure
      containers plus `gateway` and `envoy` reached a running and healthy state,
      and Flyway migrated each database. The three Spring containers then exited
      1 on a pre-existing defect unrelated to this change: the convention plugin
      applies `org.graalvm.buildtools.native`
      (`build-logic/src/main/kotlin/io.github.smiskinext.plugin.service.base.gradle.kts:12`),
      so `bootBuildImage` produced a GraalVM native image lacking reachability
      metadata. The three services failed in **two distinct ways**, not one:
      `tenant` aborted during Hibernate `SessionFactory` initialisation with
      `MissingReflectionRegistrationError` for `java.util.UUID[]` at
      `MultiIdEntityLoaderArrayParam.<init>`, while `meet` and `notification`
      aborted earlier with
      `IntegrationException: Error activating Bean     Validation integration`
      caused by
      `Invalid logger interface     org.hibernate.validator.internal.util.logging.Log (implementation not     found)`.
      This change does not touch `build-logic`, and the same images are what
      `.github/workflows/release.yml` publishes, so the defect predates this
      work. See the follow-up task below.

## 8. Follow-up (out of scope for this change)

- [x] 8.1 Stop applying `org.graalvm.buildtools.native` in the service
      convention plugins so `bootBuildImage` produces a JVM image, while keeping
      JVM AOT. Registering `java.util.UUID[]` for reflection was rejected: it
      addresses only `tenant`'s failure and leaves the separate Hibernate
      Validator logger failure in `meet` and `notification`, and nothing in the
      project asked for native images.

- [ ] 8.2 Authenticate the meeting event streams before any network-reachable
      deployment, using either short-lived stream tickets verified through Envoy
      `jwt_authn` `from_params`, or Forge Realtime. See `design.md` Decision 7.

- [ ] 8.3 Resolve two debts left behind while making the stack boot. First,
      `notification` freezes `CacheConfig` to permanently absent in its AOT
      output, for a different reason than the `tenant` hole closed in 8.1: its
      `@SpringBootApplication` declares no `scanBasePackages` (unlike `tenant`
      and `meet`, which both list `io.github.smiskinext.shared`), and
      `CacheConfig` is a plain `@Configuration` that is not listed in `shared`'s
      `AutoConfiguration.imports`. Adding redis properties alone will therefore
      not enable caching there. Second, the Resend `@NotBlank` constraints on
      `EmailProperties.apiKey` and `WebhookProperties.signingSecret` were
      deliberately left in place; `.env.example` supplies fake placeholders so
      the stack boots. Decide whether local dev should instead be able to run
      with these genuinely unset, which would mean relaxing the constraints and
      moving the failure to send time.
