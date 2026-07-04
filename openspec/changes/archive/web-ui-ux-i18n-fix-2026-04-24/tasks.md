# Tasks

## 1. Translation wiring and hardcoded text cleanup

- [x] 1.1 Update `frontends/web/src/components/auth-screen.tsx` to replace the
      hardcoded brand name with `common("brand")`, remove the dead
      `switchPrefix` conditional, and add `showPassword` / `hidePassword` labels
      for the password visibility toggle.
- [x] 1.2 Update `frontends/web/src/components/splash-screen.tsx` to remove the
      duplicate hardcoded `ZeroMeeting` text and rely on the translated title
      only.
- [x] 1.3 Update `frontends/web/src/components/green-room-screen.tsx` and
      `frontends/web/src/components/meeting-room-screen.tsx` to source the brand
      name from `workspace.common.brand` instead of hardcoded `Zero Meeting`
      strings.
- [x] 1.4 Update `frontends/web/src/components/meeting-room-screen.tsx` to
      replace the split active-session participant text with a single pluralised
      `t("activeSession", { count: PARTICIPANTS.length })` call and add
      state-aware `controlMicOff` / `controlVideoOff` labels for media toggles.
      ← (verify: all audited hardcoded brand/status text is removed, meeting
      participant count localises correctly in both locales, and toggle labels
      reflect the next action)

## 2. Accessibility semantics and keyboard behavior

- [x] 2.1 Update `frontends/web/src/components/home-screen.tsx` to wrap locale
      buttons in a `role="group"`, add `aria-label={t("localeGroup")}`, add
      `aria-pressed` on each locale toggle, and add
      `tabIndex={hasJoinValue ? 0 : -1}` to the disabled join link pattern.
- [x] 2.2 Update `frontends/web/src/components/workspace-home-screen.tsx` to
      replace the FAB `+` glyph with a consistent SVG plus icon, add
      `aria-label={t("createMeeting")}`, and add
      `tabIndex={meetingCode.trim() ? 0 : -1}` to the join link.
- [x] 2.3 Update `frontends/web/src/components/workspace-shell.tsx` to add
      `aria-label` values to icon-only header buttons and
      `aria-current={isActive ? "page" : undefined}` to active navigation links.
- [x] 2.4 Update `frontends/web/src/components/green-room-screen.tsx` and
      `frontends/web/src/components/meeting-room-screen.tsx` to add `aria-label`
      values for help, settings, and user-profile icon buttons. ← (verify:
      keyboard focus skips disabled links, icon-only buttons announce meaningful
      labels, and current navigation state is exposed to assistive tech)

## 3. Semantic form controls and navigation correctness

- [x] 3.1 Update `frontends/web/src/components/workspace-schedule-screen.tsx` to
      use `type="date"` for the date field and `type="time"` for the time field.
- [x] 3.2 Replace the static duration display in
      `frontends/web/src/components/workspace-schedule-screen.tsx` with a
      translated `<select>` offering 15, 30, 45, 60, 90, and 120 minute options.
- [x] 3.3 Update `frontends/web/src/components/workspace-profile-screen.tsx` to
      derive avatar initials from the translated name, pass the current year
      into the translated copyright string, and render Account Settings and
      Meeting History cards as links while keeping Help & Support and Sign Out
      as buttons. ← (verify: schedule inputs expose native date/time/select
      behavior, translated duration options render correctly, and profile
      navigation uses correct link-versus-button semantics)

## 4. Route entry and translation key updates

- [x] 4.1 Update `frontends/web/src/app/[locale]/page.tsx` to redirect to
      `/{locale}/home` instead of rendering the default Next.js scaffold.
- [x] 4.2 Add the remaining audit keys to `frontends/web/src/messages/en.json`
      and `frontends/web/src/messages/vi.json`: `auth.login.showPassword`,
      `auth.login.hidePassword`, `workspace.home.createMeeting`,
      `workspace.schedule.duration15`, `duration30`, `duration45`, `duration60`,
      `duration90`, `duration120`, `meetingRoom.controlMicOff`,
      `meetingRoom.controlVideoOff`, `meetingRoom.activeSession`,
      `home.localeGroup`, and parameterised `auth.common.copyright`.
- [x] 4.3 Confirm the new keys match component usage in both locales and that
      the locale root redirect lands on the translated home flow without showing
      the scaffold. ← (verify: no missing-message runtime errors occur in
      English or Vietnamese, and visiting `/{locale}` redirects to
      `/{locale}/home`)
