# Why

The Android pre-join flow can already submit backend join requests, but it
cannot determine whether a short code belongs to a password-protected meeting
before sending the request. This leaves protected meetings without a usable
client-side path even though the backend and generated Android DTOs already
support passwords.

## What Changes

- Extend the Android meeting/domain/data layers to fetch meeting details by
  short code and map `settings.requirePassword` into domain state
- Update the Android join request contract to accept a nullable password and
  include it when the target meeting requires one
- Upgrade `CallViewModel` and `PreJoinFragment` to preflight check meeting info,
  reveal a password field with loading and animation when needed, and handle
  not-found, network, and invalid-password feedback
- Add English and Vietnamese string resources plus Android test coverage for the
  mapper and protected join state transitions

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `android-livekit-room-join`: update Android join behavior to resolve meeting
  info by short code, submit passwords for protected meetings, and handle
  lookup/password failures before room entry
- `android-videocall-shell`: extend `PreJoinFragment` and `CallViewModel`
  requirements for password-gated join UX, loading states, inline validation,
  and stale-code resets
- `android-i18n-main`: extend video-call string and translation requirements for
  password labels, helper and error text, and the checking state

## Impact

**Code Changes:**

- `frontends/android-app/app/src/main/java/.../domain/model/MeetingSettings.java`
- `frontends/android-app/app/src/main/java/.../data/mapper/MeetingMapper.java`
- `frontends/android-app/app/src/main/java/.../domain/repository/MeetingRepository.java`
- `frontends/android-app/app/src/main/java/.../data/repository/MeetingRepositoryImpl.java`
- `frontends/android-app/app/src/main/java/.../domain/repository/JoinRoomRepository.java`
- `frontends/android-app/app/src/main/java/.../data/repository/JoinRoomRepositoryImpl.java`
- `frontends/android-app/app/src/main/java/.../presentation/videocall/CallViewModel.java`
- `frontends/android-app/app/src/main/java/.../presentation/videocall/PreJoinFragment.java`
- `frontends/android-app/app/src/main/res/layout/fragment_prejoin.xml`
- `frontends/android-app/app/src/main/res/values/strings.xml`
- `frontends/android-app/app/src/main/res/values-vi/strings.xml`
- Android unit and integration tests covering meeting mapping and the protected
  join flow

**APIs Used:**

- `GET /api/v1/meetings:byShortCode?code={shortCode}`
- `POST /api/v1.0/meetings/{id}:requestJoin` with optional `password`

**Systems Affected:**

- Android pre-join flow for guest and authenticated users
- Android meeting lookup and join repositories
- Android localized resources and join-flow test coverage
