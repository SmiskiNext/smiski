# ADDED Requirements

## Requirement: Container images are built and pushed automatically on changes to service sources

The CI system SHALL include a GitHub Actions workflow
(`.github/workflows/build-images.yml`) that is triggered on push to `main` when
files under `services/<service-name>/**` or `build-logic/**` change. For each
changed service, the workflow SHALL build the container image using
`bootBuildImage` and push it to `ghcr.io/phunguy65/zms/<service-name>` tagged
with the git SHA.

### Scenario: Push to main with service source changes triggers image build

- **WHEN** a commit is pushed to `main` that modifies files under
  `services/user-management/**`
- **THEN** the CI workflow SHALL build and push a new image for
  `user-management` to `ghcr.io/phunguy65/zms/user-management`

### Scenario: Push to main with no service source changes does not trigger image build

- **WHEN** a commit is pushed to `main` that modifies only files outside of
  `services/**` and `build-logic/**`
- **THEN** no image build or push job SHALL execute

### Scenario: Each service has an independent path-filtered job

- **WHEN** a commit is pushed to `main` that modifies only
  `services/chat-management/**`
- **THEN** only the chat-management image build job SHALL run; user-management,
  meeting-management, and notification jobs SHALL be skipped

### Scenario: Images are tagged with the git SHA

- **WHEN** an image build job completes successfully
- **THEN** the pushed image SHALL be tagged with the full git commit SHA of the
  triggering push

### Scenario: Workflow fails with a meaningful error when required GitHub secret is absent

- **WHEN** the `GHCR_TOKEN` secret (or equivalent write-access token) is not
  configured in the repository
- **THEN** the workflow job SHALL fail at the login step with a descriptive
  error message rather than a silent or obscure authentication failure

## Requirement: Only the four deployable services have image build jobs

The image build workflow SHALL define jobs exclusively for `user-management`,
`meeting-management`, `chat-management`, and `notification`. The `proto` and
`shared` library modules SHALL NOT have image build jobs since they are not
deployable services.

### Scenario: Proto and shared modules have no image build jobs

- **WHEN** `.github/workflows/build-images.yml` is inspected
- **THEN** it SHALL NOT contain any job that references `proto` or `shared` as a
  build target

### Scenario: All four service jobs are present in the workflow

- **WHEN** `.github/workflows/build-images.yml` is inspected
- **THEN** it SHALL contain one image build job each for user-management,
  meeting-management, chat-management, and notification
