## 1. Proto contract

- [x] 1.1 Add a repeated invitee field to the `MeetingInfoUpdated` message in
      `services/proto/src/main/proto/io/github/smiskinext/event/meet/v1/meeting_snapshot.proto`,
      with a nested invitee message carrying `account_id`, `email`,
      `display_name`, and `status` (mirror
      `MeetingInvitationsCreated.InviteeInfo`)
- [x] 1.2 Run `./services/gradlew bufFormatApply` and regenerate proto sources ←
      (verify: proto compiles, generated `MeetingInfoUpdated` exposes the
      invitee list)

## 2. Meet service — enrich and rename the event

- [x] 2.1 Add a `List<InviteeInfo>` field (account id, email, display name,
      status) to `MeetingInfoUpdatedEvent` and a nested `InviteeInfo` record,
      matching `MeetingInvitationsCreatedEvent`
- [x] 2.2 Change `MeetingInfoUpdatedEvent.topic()` to
      `meet.meeting.info.updated` and `eventType()` to
      `io.github.smiskinext.meet.meeting.info.updated.v1`
- [x] 2.3 Update `Meeting.updateInfo(...)` to accept the current invitee list
      and pass it into the registered `MeetingInfoUpdatedEvent`, preserving the
      existing no-op and status guards
- [x] 2.4 Update `MeetingInfoUpdatedEventProtoMapper` to map the invitee list
      into the proto message
- [x] 2.5 Inject `MeetingInviteeRepository` into
      `UpdateMeetingApplicationService`, load invitees by meeting id, and pass
      them into `Meeting.updateInfo(...)` ← (verify: event carries current
      invitees; no-op update still publishes nothing)
- [x] 2.6 Update `UpdateMeetingControllerIntegrationTest` (and any other test)
      asserting the old topic/type string to the new `meet.meeting.info.updated`
      value ← (verify: meet build + integrationTest pass)

## 3. Notification service — update-email consumer

- [x] 3.1 Add a fixed update-email consumer group to `EmailConsumerProperties`
      and `application.yaml` (e.g. `notification-meeting-info-updated`)
- [x] 3.2 Create `MeetingInfoUpdatedEmailConsumer` subscribing to
      `meet.meeting.info.updated` on the new group using
      `emailKafkaListenerContainerFactory`, parsing the `MeetingInfoUpdated`
      proto from the CloudEvent
- [x] 3.3 Implement the time-change gate: compare old vs new snapshot start
      time, end time, and zone id; skip (no email) when none changed
- [x] 3.4 When time changed, build an `InvitationCalendar` from the new snapshot
      plus the event invitees and send a `METHOD:REQUEST` email per invitee via
      `IcsGenerator.buildRequest` and `EmailSender`
- [x] 3.5 Skip cleanly (no email, no crash) when the event carries a time change
      but no invitees, and log-and-skip malformed events so the consumer keeps
      running ← (verify: consumer matches calendar-invitation-email spec
      scenarios — time change emails per invitee, non-time change emails none,
      malformed/no-invitee handled)

## 4. Verification

- [x] 4.1 Run `./services/gradlew spotlessApply`
- [x] 4.2 Run `./services/gradlew -p services/meet build`
- [x] 4.3 Run `./services/gradlew -p services/notification build` ← (verify:
      both services build with unit + integration tests green)
