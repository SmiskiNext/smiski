## 1. Test stack overlay foundation

- [x] 1.1 Replace `services/test/compose.yaml` with an overlay declaring
      `name: smiski-test` and `include: ../docker/compose.yaml`, carrying no
      copied service definitions
- [x] 1.2 Delete the duplicated assets the overlay supersedes:
      `services/test/envoy/lua/`, `services/test/observability/loki.yaml`,
      `services/test/observability/config.alloy`,
      `services/test/observability/grafana/`
- [x] 1.3 Override the `livekit_server_config` entry to add `prometheus_port`
      and an `rtc.turn_servers` entry pointing at Coturn over TCP
- [x] 1.4 Add a second `redis_exporter` instance targeting `livekit-redis` under
      the observability profile
- [x] 1.5 Write `services/test/.env.example` covering the TURN shared secret,
      harness port, JWKS path and key-material path, with `.gitignore` entries
      for generated private keys
- [x] 1.6 Confirm the resolved configuration contains every development-stack
      service and that volumes and networks are prefixed separately ← (verify:
      `docker compose -f services/test/compose.yaml config` succeeds; no file
      under `services/docker/` modified; volume names distinct from the dev
      stack)

## 2. Authentication path

- [x] 2.1 Add `services/test/envoy/envoy.yaml` as an overlay that swaps
      `remote_jwks` for `local_jwks` reading a generated key file, and removes
      the now-unused JWKS cluster
- [x] 2.2 Keep the Lua claim filter, the external authorization filter and all
      route matching identical to the development stack
- [x] 2.3 Build the `mock-jira` container returning fixed bulk-permission
      responses, and point `JIRA_API_BASE` at it
- [x] 2.4 Validate the Envoy overlay without starting the stack ← (verify:
      `envoy --mode validate` exits zero; jwt_authn, lua, ext_authz and router
      filters all present in that order)

## 3. TURN relay

- [x] 3.1 Add the `coturn` container configured for shared-secret authentication
      with TURN over TCP on 3478
- [x] 3.2 Wire the same shared secret into both Coturn and the media server's
      advertised TURN configuration
- [x] 3.3 Add Prometheus scrape jobs for the media server and Coturn to
      `services/test/observability/prometheus.yml` ← (verify:
      `promtool check config` exits zero; jobs for media server, Coturn and the
      second cache exporter present)

## 4. Developer CLI

- [x] 4.1 Create `scripts/src/index.ts` as the entry point the package manifest
      already declares, using the installed `citty` toolchain
- [x] 4.2 Implement `keygen` — generate an RSA keypair, write the public key set
      where the Envoy overlay reads it, keep the private key out of version
      control
- [x] 4.3 Implement `token` — sign a Forge Invocation Token carrying `iss`,
      `aud`, `principal`, `context.cloudId`, `app.id`, `app.apiBaseUrl` and
      `app.environment.id`, deriving the claim set from the gateway parser
- [x] 4.4 Implement `seed` — insert one tenant and N meetings under both
      admission policies, print the created identifiers, and be safe to re-run
- [x] 4.5 Implement `impair` — apply and remove the baseline, mobile-network and
      blocked-UDP profiles inside target containers, reversibly
- [x] 4.6 Implement `collect` — query the metrics range API and write one file
      per test case, recording network profile, cache state, admission policy
      and variant
- [x] 4.7 Confirm the CLI runs through the declared entry point ← (verify: entry
      point resolves and executes; `pnpm --dir scripts lint` and `typecheck`
      both pass)

## 5. Load generation

- [x] 5.1 Implement `loadtest tokens` driving the load generator from a pinned
      image through the gateway, issuing the configured request count within the
      configured window
- [x] 5.2 Support the single-meeting and multiple-meeting variants as separate
      runs with separately reported results
- [x] 5.3 Support an empty-cache and a populated-cache pass, clearing the
      gateway cache before the empty-cache pass
- [x] 5.4 Report success rate, failure reason distribution, and median and tail
      response times per variant
- [x] 5.5 Implement `loadtest room` driving the media load simulator from a
      pinned image at the configured participant, publisher and resolution
      counts ← (verify: both commands run end to end against a started stack;
      results distinguish variants; failures are counted rather than averaged
      away)

## 6. Browser QoS harness

- [x] 6.1 Add a static harness page served from the test stack that joins with a
      pasted token, independent of the production application
- [x] 6.2 Add a relay-only toggle that constrains connectivity to relayed
      candidates
- [x] 6.3 Add a screen-share control that records start and stop times
- [x] 6.4 Sample transport statistics at a fixed interval — round-trip time,
      jitter, packet loss, dropped frames, bitrate both directions — and expose
      an export
- [x] 6.5 Surface the negotiated candidate type in the export ← (verify:
      relay-only run records a relayed candidate type; export covers a sustained
      session end to end; no production application source modified)

## 7. Documentation

- [x] 7.1 Write `services/test/AGENTS.md` covering startup, per-case
      preconditions, and which steps are manual
- [x] 7.2 Record each divergence between the test plan and the system's actual
      behavior together with its resolution
- [x] 7.3 State preconditions whose omission yields missing data rather than an
      error, naming the symptom ← (verify: an operator can run any case from the
      document alone; every divergence named in the proposal appears with a
      resolution)

## 8. Scenario verification

- [x] 8.0 Pin the test network to a fixed subnet, assign the media server a
      static address, remove the inherited `--node-ip` argument so the overlay's
      own `node_ip` setting takes effect, and confirm relay-only connectivity
      establishes with a relayed candidate type (see design D5d)
- [x] 8.1 Verify a locally signed token is accepted end to end, and that an
      unsigned and a badly signed token are both rejected before reaching the
      backend
- [x] 8.2 Verify client-supplied identity headers are replaced by values derived
      from verified claims
- [x] 8.3 Verify a token missing a required claim is rejected with the missing
      claim named
- [x] 8.4 Verify a token whose tenant claim does not match the meeting owner
      surfaces as a mismatch rather than a measurement failure
- [x] 8.5 Verify permission lookup succeeds from the mock with no route to the
      real project-management API
- [x] 8.6 Verify media connects under blocked UDP with a relayed candidate type,
      and that setup time is recorded against the 3 second budget
- [x] 8.7 Verify an unreachable TURN server produces a reported relay failure
      rather than a passing result
- [x] 8.8 Verify a network profile applies, is observable in traffic behavior,
      and is fully removed afterwards
- [x] 8.9 Verify token success rate and cached approval state are measured in
      separate runs under their respective admission policies
- [x] 8.10 Verify the response-time threshold is applied to the populated-cache
      multiple-meeting variant, with other variants recorded as context
- [x] 8.11 Verify the room capacity run reports connection failures as counted
      observations, and that a manual screen-share interval is correlatable with
      server metrics
- [x] 8.12 Verify both cache instances appear as separate series and that
      load-generator consumption is attributable to its own container
- [x] 8.13 Verify each export names its network profile, cache state, admission
      policy, variant, and any substituted metric ← (verify: every scenario in
      `specs/load-test-harness/spec.md` has been exercised; substituted metrics
      are labelled rather than presented as originals)

## 9. Final validation

- [x] 9.1 Run `docker compose -f services/test/compose.yaml config` both plainly
      and with `COMPOSE_PROFILES=observability`, since profile-gated services
      are not validated by the plain form
- [x] 9.2 Run Envoy configuration validation and `promtool check config`, then
      confirm on a running stack that every scrape target reports up — file
      validation alone cannot detect an unmounted or unexpanded scrape
      configuration
- [x] 9.3 Run `pnpm --dir scripts lint` and `pnpm --dir scripts typecheck`
- [x] 9.4 Run `pnpm lint` and `pnpm format` at the workspace root
- [x] 9.5 Confirm the untouched-paths guarantee holds ← (verify: `git status`
      shows no modification under `services/docker/`, `services/*/src/`,
      `services/gateway/` or `app/`)
