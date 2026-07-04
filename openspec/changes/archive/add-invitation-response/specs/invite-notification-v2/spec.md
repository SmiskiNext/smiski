## ADDED Requirements

### Requirement: Notify host when invitee responds to invitation

The notification service SHALL consume InviteeAcceptedEvent and
InviteeDeclinedEvent from Kafka and send a notification email to the meeting
host.

#### Scenario: Host receives email when invitee accepts

- **WHEN** the notification service receives an InviteeAcceptedEvent from topic
  `meeting-management.invitee.accepted`
- **THEN** the service SHALL resolve the host email and invitee display name
  from the event payload (inviterId for host lookup, aggregateId for invitee
  lookup)
- **AND** the service SHALL send an email to the host with subject containing
  the invitee name and "accepted"
- **AND** the email body SHALL include the meeting title and the invitee's
  response

#### Scenario: Host receives email when invitee declines

- **WHEN** the notification service receives an InviteeDeclinedEvent from topic
  `meeting-management.invitee.declined`
- **THEN** the service SHALL resolve the host email and invitee display name
  from the event payload
- **AND** the service SHALL send an email to the host with subject containing
  the invitee name and "declined"
- **AND** the email body SHALL include the meeting title and the invitee's
  response

#### Scenario: Event processing failure is logged without crashing

- **WHEN** the notification service fails to process an invitee response event
  (e.g., host email resolution fails)
- **THEN** the failure SHALL be logged with the event ID and error details
- **AND** the consumer SHALL NOT crash or block subsequent event processing
