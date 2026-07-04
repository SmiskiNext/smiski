## MODIFIED Requirements

### Requirement: A host's join request implicitly transitions a scheduled meeting to live

The meeting-management service SHALL transition a meeting from `SCHEDULED` to
`LIVE` as part of processing a join request when the requester is the host of
that meeting. The transition SHALL be performed inside the same transaction that
processes the join, under the existing row-level lock on the meeting aggregate,
and SHALL register a `MeetingStartedEvent` exactly once for the transition that
actually occurred. The host's join SHALL receive an approved response with a
LiveKit token regardless of the meeting's admission policy, because the host is
the only party authorized to approve join requests on their own meeting and
SHALL NOT be required to approve themselves.

#### Scenario: Host join on a scheduled meeting elevates and approves

- **WHEN** the host of a meeting whose status is `SCHEDULED` calls the
  request-join endpoint and the meeting's admission policy and password rules
  permit immediate approval
- **THEN** the meeting status SHALL become `LIVE` before the join is evaluated
- **THEN** the service SHALL register a `MeetingStartedEvent` for the transition
- **THEN** the response SHALL be the normal approved join response, including a
  LiveKit token and room name

#### Scenario: Host join on a scheduled meeting with manual-approval admission elevates and approves with a LiveKit token

- **WHEN** the host of a meeting whose status is `SCHEDULED` and whose admission
  policy is `MANUAL_APPROVAL` calls the request-join endpoint
- **THEN** the meeting status SHALL become `LIVE` before the join is evaluated
- **THEN** the service SHALL register a `MeetingStartedEvent` for the transition
- **THEN** the service SHALL NOT create a pending `JoinRequest` for the host
- **THEN** the response SHALL include `JoinRequestStatus.APPROVED`, a LiveKit
  token, and the room name

#### Scenario: Host join on an already live meeting does not re-elevate

- **WHEN** the host of a meeting whose status is `LIVE` calls the request-join
  endpoint
- **THEN** the meeting status SHALL remain `LIVE`
- **THEN** the service SHALL NOT register a new `MeetingStartedEvent`
- **THEN** the response SHALL follow the existing join logic for a live meeting

#### Scenario: Host join on a live meeting with manual-approval admission approves with a LiveKit token

- **WHEN** the host of a meeting whose status is `LIVE` and whose admission
  policy is `MANUAL_APPROVAL` calls the request-join endpoint
- **THEN** the meeting status SHALL remain `LIVE`
- **THEN** the service SHALL NOT register a new `MeetingStartedEvent`
- **THEN** the service SHALL NOT create a pending `JoinRequest` for the host
- **THEN** the response SHALL include `JoinRequestStatus.APPROVED`, a LiveKit
  token, and the room name

#### Scenario: Host join on an ended meeting fails

- **WHEN** the host of a meeting whose status is `ENDED` calls the request-join
  endpoint
- **THEN** the service SHALL fail the request with the
  `INVALID_STATUS_TRANSITION` error code
- **THEN** the meeting status SHALL remain `ENDED`
- **THEN** no `MeetingStartedEvent` SHALL be registered

#### Scenario: Host join on a cancelled meeting fails

- **WHEN** the host of a meeting whose status is `CANCELLED` calls the
  request-join endpoint
- **THEN** the service SHALL fail the request with the
  `INVALID_STATUS_TRANSITION` error code
- **THEN** the meeting status SHALL remain `CANCELLED`
- **THEN** no `MeetingStartedEvent` SHALL be registered

#### Scenario: Non-host join on a scheduled meeting fails

- **WHEN** a caller who is not the host of a `SCHEDULED` meeting calls the
  request-join endpoint
- **THEN** the service SHALL fail the request with the
  `INVALID_STATUS_TRANSITION` error code
- **THEN** the meeting status SHALL remain `SCHEDULED`
- **THEN** no `MeetingStartedEvent` SHALL be registered

#### Scenario: Non-host join on a live meeting with manual-approval admission stays pending

- **WHEN** a caller who is not the host of a `LIVE` meeting whose admission
  policy is `MANUAL_APPROVAL` calls the request-join endpoint
- **THEN** the service SHALL create a pending `JoinRequest` for the caller
- **THEN** the response SHALL include `JoinRequestStatus.PENDING`
- **THEN** the response SHALL NOT include a LiveKit token or room name
- **THEN** the meeting status SHALL remain `LIVE`

#### Scenario: Concurrent host requests publish the started event exactly once

- **WHEN** two concurrent join requests are made by the host on the same
  `SCHEDULED` meeting
- **THEN** exactly one of the requests SHALL perform the SCHEDULED → LIVE
  transition and register one `MeetingStartedEvent`
- **THEN** the other request SHALL observe the meeting as already `LIVE` and
  proceed without re-elevation
- **THEN** both requests SHALL receive a successful join response
