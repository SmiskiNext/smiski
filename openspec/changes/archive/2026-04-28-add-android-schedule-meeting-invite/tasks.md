# Tasks

## 1. Add invitee-capable schedule request plumbing

- [x] 1.1 Extend `ScheduleMeetingRequest` with a nullable `List<String>`
      invitees field, constructor parameter, and getter.
- [x] 1.2 Update `MeetingRepositoryImpl.buildScheduleMeetingRequest()` to map
      domain invitee emails into `MeetingManagementInviteeRequest` objects only
      for schedule creation payloads. ← (verify: create requests include
      nullable invitees correctly and existing non-invitee fields still map
      exactly as before)

## 2. Add invitee state and validation in the schedule ViewModel

- [x] 2.1 Add invitee list state to `ScheduleViewModel` with exposed
      `LiveData<List<String>>` and one-shot add-validation error events.
- [x] 2.2 Implement `addInvitee(String email)` with format validation,
      case-insensitive duplicate detection, max-10 enforcement, and success-path
      state updates.
- [x] 2.3 Implement `removeInvitee(String email)` and ensure `scheduleMeeting()`
      passes the current invitee list only for create-mode submissions. ←
      (verify: create mode sends the current invitee list, edit mode omits it,
      and add/remove state survives normal fragment observation flows)

## 3. Add schedule form invitee UI resources

- [x] 3.1 Update `fragment_schedule.xml` to add the invite attendees section,
      invitee input row, chip group, count helper text, and divider in the
      required position.
- [x] 3.2 Add the required invitee labels, validation strings, count format, and
      remove-content-description resources to `strings.xml`. ← (verify: layout
      structure matches the spec placement and all visible/accessibility strings
      needed by the new UI are present)

## 4. Wire invitee interactions in ScheduleFragment

- [x] 4.1 Initialize the new invitee views in `ScheduleFragment` and hook add
      actions from both the button tap and the email-field Done IME action.
- [x] 4.2 Observe invitee list and validation error events to rebuild chips,
      update count text, clear successful input state, and show inline
      `TextInputLayout` errors.
- [x] 4.3 Toggle invitee input enabled state at the 10-item cap and hide or
      disable the invitee section in edit mode before submission is allowed.
- [x] 4.4 Pass invitees through the create submission path while preserving the
      existing schedule success and error observers. ← (verify: create mode
      supports end-to-end add/remove/submit behavior, edit mode does not expose
      invitee editing, and validation/backend errors surface through the
      intended UI channels)
