# MODIFIED Requirements

## Requirement: Participant merge behavior and fallback

The Android participant merge logic SHALL preserve call-state correctness while
enriching role metadata.

### Scenario: Merge uses stable identity-only participant matching

- **WHEN** combining LiveKit participant entries with backend participant
  records
- **THEN** the system SHALL use stable participant identity/id matching as the
  sole role-resolution key
- **THEN** it SHALL NOT use display-name fallback matching for role assignment

### Scenario: Unmatched LiveKit participant defaults role

- **WHEN** a LiveKit participant has no corresponding backend participant record
- **THEN** the merged participant model SHALL assign role `PARTICIPANT`
- **THEN** the participant SHALL remain visible with current media state

### Scenario: Enrichment failure does not block participant list

- **WHEN** `GET /api/v1/meetings/{id}/participants` fails
- **THEN** `ParticipantsViewModel` SHALL still publish LiveKit-only participant
  list
- **THEN** role badge rendering SHALL degrade gracefully without blocking
  bottom-sheet usage
