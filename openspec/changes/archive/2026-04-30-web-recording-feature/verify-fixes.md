## [2026-04-30] Round 1 (from apply auto-verify)

### Verifier

- Fixed: Moved `clearPendingTimeout` and `schedulePendingTimeout` to
  `useCallback` in `use-recording-state.ts` to satisfy Biome exhaustive
  dependency linting (`useExhaustiveDependencies`) — the function was used
  inside a `useEffect` without being listed as a dependency.
- Fixed: Added `setRecordingConfirmOpen(false)` to the recording state
  transition effect in `index.tsx` so the confirm dialog auto-closes when
  `recordingState` transitions from `starting` to `recording`, completing the
  end-to-end confirm flow correctly.
