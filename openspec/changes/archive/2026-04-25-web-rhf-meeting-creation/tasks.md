# Tasks

## 1. Build shared RHF and meeting-form foundations

- [x] 1.1 Add `frontends/web/src/components/ui/form.tsx` with the
      shadcn-compatible RHF form primitives used by all migrated forms.
- [x] 1.2 Create `frontends/web/src/lib/schemas/meeting.ts` with shared meeting
      settings, instant meeting, and schedule meeting zod schemas plus
      backend-aligned defaults.
- [x] 1.3 Create the reusable `MeetingSettingsForm` component and `InviteeInput`
      field so instant and scheduled meeting flows share the same settings and
      invitee UX. ← (verify: both components bind correctly to RHF state,
      enforce shared schema constraints, and expose accessible labels for
      invitee removal)

## 2. Migrate existing web forms to RHF + zod

- [x] 2.1 Refactor `frontends/web/src/components/auth/form.tsx` and
      `frontends/web/src/components/auth/index.tsx` to use RHF with
      login/register schemas, field-level error rendering, and server-error
      mapping through form state.
- [x] 2.2 Refactor `frontends/web/src/components/join-meeting/join-form.tsx` to
      use RHF for step-based field management while keeping the existing
      `use-join-meeting.ts` reducer workflow unchanged.
- [x] 2.3 Delete `frontends/web/src/components/auth-screen.tsx` and remove any
      remaining references to the legacy auth screen. ← (verify: routed auth
      still works, join flow preserves user inputs across retryable errors, and
      no imports reference the deleted file)

## 3. Implement instant meeting creation flow

- [x] 3.1 Create
      `frontends/web/src/components/create-meeting/use-create-meeting.ts` with
      reducer-driven create, start, retry, and reset states for the instant
      meeting workflow.
- [x] 3.2 Build
      `frontends/web/src/components/create-meeting/instant-meeting-dialog.tsx`,
      `success-dialog.tsx`, `new-meeting-dropdown.tsx`, and `index.tsx` to
      render the instant meeting form, success handoff, and shared new-meeting
      entry menu.
- [x] 3.3 Integrate the new meeting entry points into
      `frontends/web/src/components/workspace-home-screen.tsx` and
      `frontends/web/src/components/home-screen.tsx` where applicable. ←
      (verify: supported surfaces show exactly instant/schedule actions, instant
      success exposes copy-link plus redirect behavior, and failures keep the
      dialog retryable without losing form values)

## 4. Activate scheduled meeting creation on the existing workspace page

- [x] 4.1 Refactor `frontends/web/src/components/workspace-schedule-screen.tsx`
      to use RHF with the shared schedule schema, computed start/end times,
      reusable settings form, invitee chip input, and API
      submission/loading/error states.
- [x] 4.2 Add and wire translation keys in `frontends/web/src/messages/en.json`
      and `frontends/web/src/messages/vi.json` for meeting creation, schedule
      form, shared settings, success messaging, and new-meeting menu copy. ←
      (verify: schedule submission builds the expected SDK payload, success
      dialog shows meeting details, and all new UI strings come from English and
      Vietnamese locale files)

## 5. Validate the completed web meeting-creation change

- [x] 5.1 Run the project's typecheck command and fix any type errors introduced
      by the RHF, schema, and meeting-creation changes.
- [x] 5.2 Run the production build and fix any build-time issues in routes,
      client components, or generated SDK integrations.
- [x] 5.3 Run the project's lint or formatting checks and resolve any remaining
      issues. ← (verify: typecheck, build, and lint all pass with the final
      frontend implementation)
