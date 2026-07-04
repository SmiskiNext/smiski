## [2026-04-22] Round 1 (from apply auto-verify)

### Verifier

- Fixed: Updated AccountSettingsViewModelTest constructor calls to include the
  new DeleteAccountUseCase mock parameter, preventing compilation failure after
  ViewModel constructor signature change
- Fixed: Changed AlertDialog button references from MaterialButton cast to
  android.widget.Button to avoid potential ClassCastException at runtime
