## REMOVED Requirements

### Requirement: Host-only invitee replacement endpoint

**Reason**: The replace-all `PUT /api/1/meetings/{id}/invitees` endpoint is
split into two explicit endpoints. Adding invitees is now
`POST /api/1/meetings/{id}/invitees` (see the `add-meeting-invitees` capability)
and removing invitees is now `POST /api/1/meetings/{id}/invitees:batchDelete`
(see the `remove-meeting-invitees` capability).

**Migration**: Replace a single `PUT` call carrying the full desired list with:
call `POST /api/1/meetings/{id}/invitees` with only the invitees to add, and
call `POST /api/1/meetings/{id}/invitees:batchDelete` with the ids of the
invitees to remove. Obtain invitee ids from the meeting-detail response, whose
invitee entries now include an `id` field.

### Requirement: Status-gated invitee management

**Reason**: The status gate now lives on each split endpoint. `SCHEDULED`-only
gating for creation is defined in `add-meeting-invitees` and for removal in
`remove-meeting-invitees`.

**Migration**: No client action beyond adopting the split endpoints; the
`SCHEDULED`-only rule is preserved on both.

### Requirement: Input validation for invitee replacement

**Reason**: Validation is now defined per split endpoint. Creation validation
(non-blank valid `email`, non-blank `accountId`, non-blank `displayName`,
in-request duplicate `accountId` rejection, non-empty list) lives in
`add-meeting-invitees`; removal validation (non-empty `inviteeIds`) lives in
`remove-meeting-invitees`. The former "empty invitee list removes all invitees"
behavior is removed: an empty list is now a `400 VALIDATION_ERROR` on both
endpoints rather than a mass-removal.

**Migration**: To remove all invitees, submit their ids explicitly to
`:batchDelete` instead of sending an empty replacement list.

### Requirement: AccountId-based invitee diffing

**Reason**: The diff-based create/update/remove model is removed. There is no
longer a display-name update path through this API; adding and removing are now
distinct explicit operations. An existing invitee's `displayName`, `email`,
`role`, and `rsvp` are not mutated by these endpoints.

**Migration**: Adding an `accountId` that is already active now returns
`409 INVITEE_ALREADY_EXISTS` instead of silently updating its display name.
Display-name changes are no longer supported through the invitee API.

### Requirement: Change-sensitive batch invitee events

**Reason**: The three-way event model is reduced to two. The
`meet.meeting.invitations.updated` event is removed entirely because no consumer
subscribes to it and the display-name update path is gone. The
`meet.meeting.invitations.created` event is now published by the add endpoint
and `meet.meeting.invitations.deleted` by the remove endpoint. Per-endpoint
event requirements are defined in `add-meeting-invitees` and
`remove-meeting-invitees`.

**Migration**: Consumers SHALL stop subscribing to
`meet.meeting.invitations.updated`; it is no longer published. The `created` and
`deleted` topics and their payloads are unchanged.
