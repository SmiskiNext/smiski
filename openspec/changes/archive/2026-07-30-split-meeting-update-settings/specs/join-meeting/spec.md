## ADDED Requirements

### Requirement: Per-source media publish restriction on participant tokens

The participant join token SHALL restrict publishing per track source using an
explicit allowed-sources grant derived from the meeting's settings (`microphone`
when `allowMicrophone`, `camera` when `allowVideo`, `screen_share` and
`screen_share_audio` when `allowScreenShare`), instead of a single
undifferentiated publish flag. When no media source is allowed by settings, the
token SHALL grant no publish permission rather than an unrestricted one. This
requirement applies to `PARTICIPANT` tokens; the `HOST` token SHALL retain full,
unrestricted publish permission regardless of settings.

#### Scenario: Screen share disabled excludes the source from the token

- **WHEN** a participant is issued a join token for a meeting whose settings
  have `allowScreenShare=false` and at least one other media source enabled
- **THEN** the issued token's allowed publish sources exclude screen share and
  screen-share audio

#### Scenario: All media sources disabled grants no publish permission

- **WHEN** a participant is issued a join token for a meeting whose settings
  have `allowMicrophone`, `allowVideo`, and `allowScreenShare` all disabled
- **THEN** the issued token grants no publish permission for any media source

#### Scenario: Host token is unrestricted regardless of settings

- **WHEN** a host is issued a join token for a meeting whose settings disable
  one or more media sources
- **THEN** the issued host token retains full publish permission unaffected by
  those settings
