## MODIFIED Requirements

### Requirement: The web app handles approved, pending, denied, and failed join outcomes

The join-meeting flow SHALL map `requestJoin` responses into explicit
user-visible outcomes, SHALL preserve the validated join inputs across
non-terminal failures, and SHALL only hand off to the meeting-room page after
approval. An approved response from the backend MAY reflect an implicit
lifecycle elevation when the requester is the meeting host and the meeting was
previously `SCHEDULED`; the client SHALL treat this case identically to any
other approved response.

#### Scenario: Approved join request navigates to meeting room

- **WHEN** `requestJoin` returns `status === 'APPROVED'` with `token` and
  `roomName`
- **THEN** the system SHALL store or pass the approved credentials in a
  web-accessible handoff channel
- **THEN** the handoff state SHALL also preserve the resolved backend meeting
  identifier for the approved room
- **THEN** the system SHALL navigate to `/workspace/meeting-room`

#### Scenario: Host approval after implicit elevation behaves like any approval

- **WHEN** an authenticated host requests join on a meeting whose previously
  observed status was `SCHEDULED` and the backend returns
  `status === 'APPROVED'` with `token` and `roomName`
- **THEN** the join flow SHALL proceed exactly as for any other approved
  response without special-casing the prior status

#### Scenario: Pending join request enters waiting approval state

- **WHEN** `requestJoin` returns `status === 'PENDING'` with a `requestId`
- **THEN** the flow SHALL transition to a waiting-approval state
- **THEN** the system SHALL subscribe to join-request events for that
  `requestId`
- **THEN** the waiting UI SHALL remain visible until approval, denial, expiry,
  or terminal failure occurs

#### Scenario: Denied join request shows mapped feedback

- **WHEN** `requestJoin` returns `status === 'DENIED'`
- **THEN** the system SHALL keep the user in the join flow instead of navigating
  away
- **THEN** invalid password outcomes SHALL be shown inline on the password field
- **THEN** guest-not-allowed, meeting-full, and meeting-not-live outcomes SHALL
  be shown through dialog, toast, or equivalent non-inline messaging
- **THEN** the most recent join-form values SHALL remain available for retry
  where retry is allowed

#### Scenario: Transport failure preserves retry path

- **WHEN** a network or unexpected client error happens during lookup or join
  submission
- **THEN** the flow SHALL enter an error state
- **THEN** the user SHALL receive retryable feedback without losing the current
  join attempt inputs

## REMOVED Requirements

### Requirement: Web meeting-room handoff preserves the active meeting identifier

**Reason**: The instant-meeting flow no longer obtains room credentials at
create time, so the instant-launch handoff scenario it required is no longer
meaningful. The approved-join handoff scenario is preserved by the existing join
flow and is documented within that requirement instead.

**Migration**: Hosts launching an instant meeting now navigate through the
green-room route (see the `web-meeting-creation` capability), which performs
`requestJoin` and stores room handoff credentials using the same path as the
authenticated join flow. No client code outside the create and join flows relied
on this requirement.
