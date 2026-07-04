## MODIFIED Requirements

### Requirement: VideoCallActivity as Separate Task

The `VideoCallActivity` SHALL run as a separate Android task for video call
isolation.

#### Scenario: Manifest configuration

- **WHEN** `VideoCallActivity` is declared in `AndroidManifest.xml`
- **THEN** it SHALL have
  `android:taskAffinity="io.github.phunguy65.zms.videocall"`
- **THEN** it SHALL have `android:launchMode="singleInstance"`
- **THEN** it SHALL have
  `android:configChanges="orientation|screenSize|smallestScreenSize|keyboardHidden"`
- **THEN** it SHALL have `android:supportsPictureInPicture="true"`

#### Scenario: Separate recents card

- **WHEN** `VideoCallActivity` is running
- **THEN** it SHALL appear as a separate card in Android Recents
- **THEN** pressing Back SHALL close only the call, not the main app

#### Scenario: Launch from main app meeting flows

- **WHEN** user successfully starts an instant meeting from the dashboard or
  joins a meeting from the main app
- **THEN** system creates Intent to `VideoCallActivity.class`
- **THEN** Intent has flag `FLAG_ACTIVITY_NEW_TASK`
- **THEN** authenticated launches from instant meeting creation SHALL include
  the created meeting short code in `EXTRA_MEETING_CODE`
