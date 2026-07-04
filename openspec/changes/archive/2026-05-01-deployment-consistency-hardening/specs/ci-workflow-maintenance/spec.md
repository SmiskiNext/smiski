# MODIFIED Requirements

## Requirement: ci-workflow-maintenance

The CI workflow infrastructure SHALL include a dedicated image build pipeline
alongside the existing lint, test, and release workflows, completing the
delivery automation surface.

### Scenario: Image build workflow coexists with existing CI workflows

- **WHEN** the `.github/workflows/` directory is inspected
- **THEN** it SHALL contain `build-images.yml` in addition to the existing lint,
  test, and release workflow files

### Scenario: Image build workflow does not interfere with existing workflows

- **WHEN** the existing lint or test CI workflows run
- **THEN** they SHALL complete without modification or dependency on the new
  `build-images.yml` workflow
