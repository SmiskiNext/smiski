# Tasks

## 1. Build the upcoming meetings data module

- [x] 1.1 Create `frontends/web/src/components/upcoming-meetings/` with a barrel
      export and shared types/interfaces for the upcoming meetings feature.
- [x] 1.2 Implement `use-upcoming-meetings.ts` to fetch `listHostMeetings()`,
      filter to scheduled future meetings, sort by ascending `startTime`, and
      expose the existing loading/success/empty/error discriminated-union state.
- [x] 1.3 Add centralized action handlers in the hook for meeting selection,
      copy-link feedback, cancellation state, and post-cancel list mutation or
      refresh behavior using the existing API error conventions. ← (verify: hook
      state transitions match spec scenarios for loading, empty, success, error,
      and successful/failed cancellation)

## 2. Implement upcoming meeting presentation and actions

- [x] 2.1 Implement `upcoming-meeting-list.tsx` to render localized loading,
      empty, error, and success states using the hook and delegate item
      rendering to meeting cards.
- [x] 2.2 Implement `upcoming-meeting-card.tsx` to show fallback title,
      formatted date/time range, status badge, description excerpt, short code,
      and action buttons for start/join, copy link, settings, and cancel.
- [x] 2.3 Wire the card primary action to locale-aware green-room navigation and
      ensure nested action buttons do not trigger the card detail-open
      interaction. ← (verify: scheduled meetings show Start, live meetings show
      Join, navigation uses `/${locale}/workspace/green-room?code=...`, and
      action clicks do not open the detail view)

## 3. Add meeting detail and cancel confirmation flows

- [x] 3.1 Implement `meeting-detail-dialog.tsx` to show full meeting metadata,
      badges, full description, copyable short code, read-only settings summary,
      and the shared host actions.
- [x] 3.2 Implement `cancel-meeting-dialog.tsx` with localized confirmation copy
      and submission states that call the shared cancel handler only after
      explicit confirmation.
- [x] 3.3 Integrate the detail and cancel dialogs with the list and card flows
      so both surfaces reuse the same action behavior and selected-meeting
      state. ← (verify: card click opens detail, detail shows expected fields,
      and cancel from either surface updates the rendered upcoming list
      correctly)

## 4. Integrate with workspace home and localization

- [x] 4.1 Simplify `frontends/web/src/components/workspace-home-screen.tsx` to
      consume the new `UpcomingMeetingList` module instead of inline
      host-meeting rendering logic.
- [x] 4.2 Add the required `workspace.home` translation keys to
      `frontends/web/src/messages/en.json` and
      `frontends/web/src/messages/vi.json` for card labels, detail labels,
      confirmation dialog text, and action feedback.
- [x] 4.3 Validate the end-to-end web experience for both supported locales,
      including loading, empty, success, copy-link, settings handoff, and
      cancellation feedback. ← (verify: all new user-visible strings are
      localized in English and Vietnamese and no home-screen regression remains
      after integration)
