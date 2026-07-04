# ADDED Requirements

## Requirement: In-app video playback

The system SHALL provide in-app video playback for meeting recordings using
Media3 ExoPlayer.

### Scenario: Start recording playback

- **WHEN** user taps a recording item in meeting detail
- **THEN** system SHALL initialize Media3 ExoPlayer with the recording URL
- **AND** system SHALL display PlayerView with video content
- **AND** system SHALL display playback controls (play/pause, seek bar,
  fullscreen)

### Scenario: Playback controls

- **WHEN** video is playing
- **THEN** user SHALL be able to pause by tapping pause button
- **AND** user SHALL be able to seek by dragging seek bar
- **AND** user SHALL be able to toggle fullscreen mode

## Requirement: Playback error handling

The system SHALL handle video playback errors gracefully.

### Scenario: Recording URL fails to load

- **WHEN** video playback fails (invalid URL, network error, unsupported format)
- **THEN** system SHALL display error message "Unable to play recording"
- **AND** system SHALL display "Retry" button

### Scenario: Retry failed playback

- **WHEN** user taps "Retry" button after playback error
- **THEN** system SHALL attempt to reload and play the video

## Requirement: Playback lifecycle management

The system SHALL properly manage video playback lifecycle to prevent resource
leaks.

### Scenario: Fragment goes to background

- **WHEN** MeetingDetailFragment goes to background (onPause)
- **THEN** system SHALL pause video playback
- **AND** system SHALL NOT release player resources

### Scenario: Fragment returns to foreground

- **WHEN** MeetingDetailFragment returns to foreground (onResume)
- **THEN** system SHALL resume from paused position if video was playing

### Scenario: Fragment destroyed

- **WHEN** MeetingDetailFragment is destroyed (onDestroyView)
- **THEN** system SHALL release all ExoPlayer resources
- **AND** system SHALL prevent memory leaks

## Requirement: Playback state persistence

The system SHALL maintain playback position during configuration changes.

### Scenario: Screen rotation during playback

- **WHEN** user rotates screen while video is playing
- **THEN** system SHALL preserve current playback position
- **AND** system SHALL resume playback from the same position after rotation
