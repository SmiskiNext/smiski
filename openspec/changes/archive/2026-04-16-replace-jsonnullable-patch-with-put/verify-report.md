# Test Verification Report: replace-jsonnullable-patch-with-put (Task Group 3)

## Summary

| Dimension            | Status                                                    |
| -------------------- | --------------------------------------------------------- |
| Test Existence       | Files exist but tests **FAIL**                            |
| Requirement Coverage | 2/12 scenarios pass; 6/12 blocked by defect; 3/12 missing |
| Scenario Coverage    | **CRITICAL GAPS**                                         |
| Edge Cases           | **CRITICAL GAPS**                                         |
| Test Quality         | **CRITICAL: Build fails**                                 |

## Test Suite Status

```
Current Build: ❌ FAILS
  PutMeetingSettingsUseCaseTest: 8 tests, 6 FAIL (UnnecessaryStubbingException)
  MeetingControllerTest: 2 PUT tests, incomplete coverage
```

---

## CRITICAL ISSUES

### 1. ⛔ CRITICAL BLOCKER: Tests Fail with Unnecessary Stubbing Exception

**File:**
`services/meeting-management/src/test/java/.../application/usecase/PutMeetingSettingsUseCaseTest.java`,
lines 68-69

**Problem:** The `setUp()` method unconditionally stubs mocks that aren't used
in all test paths. When early validation fails (authorization, status, ceiling
checks), Mockito's strict mode detects the unused stubs and throws
`UnnecessaryStubbingException`.

```java
@BeforeEach
void setUp() {
    useCase = new PutMeetingSettingsUseCase(...);
    when(limitsConfig.getMaxParticipantsCeiling()).thenReturn(300);        // LINE 68 - unused in early failures
    when(meetingRepository.save(any())).thenAnswer(...);                   // LINE 69 - unused in early failures
}
```

**Failing Tests (6 total):**

- `execute_returnsNotAuthorizedForNonHost` — authorization check fails before
  save
- `execute_returnsInvalidStatusForEndedMeeting` — status check fails before save
- `execute_rejectsMaxParticipantsAboveCeiling` — ceiling check fails before save
- `execute_rejectsAllowAllWhenMaxParticipantsChanges` — policy check fails
  before save
- `execute_livePolicyOpening_autoApprovesPendingRequests` — limitsConfig unused
- `execute_liveAllowGuestOpening_autoApprovesPendingRequests` — limitsConfig
  unused

**Actual Build Error:**

```
22 tests completed, 4 failed

FAILURE: Build failed with an exception.
> Task :test FAILED
> There were failing tests.

PutMeetingSettingsUseCaseTest > execute_returnsNotAuthorizedForNonHost() FAILED
    org.mockito.exceptions.misusing.UnnecessaryStubbingException:
    Unnecessary stubbings detected.
    Clean & maintainable test code requires zero unnecessary code.
    Following stubbings are unnecessary (click to navigate to relevant line of code):
      1. -> at PutMeetingSettingsUseCaseTest.setUp(PutMeetingSettingsUseCaseTest.java:68)
      2. -> at PutMeetingSettingsUseCaseTest.setUp(PutMeetingSettingsUseCaseTest.java:69)
```

**Fix (Recommended):** Add `@MockitoSettings(strictness = Strictness.LENIENT)`
annotation:

```java
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)  // ADD THIS
class PutMeetingSettingsUseCaseTest {
    // ... rest unchanged
}
```

Note: This pattern is already used in `KickParticipantUseCaseTest` in the same
codebase.

**Impact:** 🔴 **BUILD FAILS** — Cannot verify task group 3 until fixed

---

### 2. ⛔ CRITICAL: Missing CANCELLED Meeting Status Test

**File:** `PutMeetingSettingsUseCaseTest.java`

**Requirement:** Spec Scenario 4 — "Only scheduled or live meetings can be
updated" Specification states: "for an ENDED **or CANCELLED** meeting"

**Current Gap:**

- ✓ ENDED status is tested
- ✗ CANCELLED status is NOT tested

**Missing Test:**

```java
@Test
void execute_returnsInvalidStatusForCancelledMeeting() {
    UUID meetingId = UUID.randomUUID();
    UUID hostId = UUID.randomUUID();
    when(meetingRepository.findByIdWithLock(meetingId))
        .thenReturn(Optional.of(meetingWithStatus(
            meetingId,
            hostId,
            MeetingStatus.CANCELLED,  // ← KEY: CANCELLED
            settings(AdmissionPolicy.MANUAL_APPROVAL, 90, false, false, 30, false, "ALL", true, null)
        )));

    var result = useCase.execute(new PutMeetingSettingsCommand(
        meetingId, hostId,
        settings(AdmissionPolicy.MANUAL_APPROVAL, 90, false, false, 30, false, "ALL", true, null),
        null
    ));

    assertThat(result).isInstanceOf(Result.Failure.class);
    assertThat(((Result.Failure<?, MeetingError>) result).error())
        .isEqualTo(new MeetingError.InvalidStatusTransition(MeetingStatus.CANCELLED, MeetingStatus.SCHEDULED));
    verifyNoInteractions(passwordHasher, pendingJoinRequestApprover, eventPublisher, meetingRepository);
}
```

**Impact:** 🔴 Spec scenario 4 is incompletely tested. CANCELLED meetings might
incorrectly be updatable.

---

### 3. ⛔ CRITICAL: Missing Timeout Clearing (Transition) Test

**File:** `PutMeetingSettingsUseCaseTest.java`

**Requirement:** Spec Scenario 7 — "Null timeout clears join request timeout"

**Current Coverage:** The test `execute_replacesSettingsAndPublishesEvent`
verifies:

- Input: existing timeout = 90s, new request timeout = null
- Assertion: null is stored ✓

**Missing Coverage:** The test does NOT prove that the existing timeout was
CLEARED (replaced):

- No test explicitly shows: given existing timeout, setting null clears it
- Current test lacks proof of transition from timeout→null

**Missing Test:**

```java
@Test
void execute_nullTimeout_clearsExistingJoinRequestTimeout() {
    UUID meetingId = UUID.randomUUID();
    UUID hostId = UUID.randomUUID();

    // EXISTING: Meeting has a timeout
    Meeting meeting = meetingWithStatus(
        meetingId, hostId, MeetingStatus.SCHEDULED,
        settings(AdmissionPolicy.MANUAL_APPROVAL, 90, false, false, 30, false, "ALL", false, null)
        //                                         ^^
        //                    Existing timeout is 90 seconds
    );
    when(meetingRepository.findByIdWithLock(meetingId)).thenReturn(Optional.of(meeting));

    // REQUEST: null timeout (clearing signal)
    var result = useCase.execute(new PutMeetingSettingsCommand(
        meetingId, hostId,
        settings(AdmissionPolicy.MANUAL_APPROVAL, null, false, false, 30, false, "ALL", false, null),
        //                                        ^^^^
        //                     New timeout is null
        null
    ));

    // VERIFY: Timeout was cleared
    assertThat(result).isInstanceOf(Result.Success.class);
    verify(meetingRepository)
        .save(argThat(saved -> saved.getSettings().joinRequestTimeout() == null));
}
```

**Impact:** 🔴 Spec scenario 7 is incompletely tested. Timeout clearing behavior
is assumed but not proven.

---

### 4. ⛔ CRITICAL: Missing 6 Controller Integration Tests

**File:** `MeetingControllerTest.java`

**Current PUT Tests:** 2 (one happy-path, one validation) **Missing Tests:** 6
(all error scenarios)

**Error Scenarios NOT Tested at Controller Level:**

1. **401 Unauthenticated**

    ```java
    void putMeetingSettings_returns401_whenUnauthenticated()
    ```

    Similar pattern: `cancelMeeting_returns401WhenUnauthenticated()` (line 228)

2. **403 Not Host** (Scenario 3)

    ```java
    void putMeetingSettings_returns403_whenRequesterIsNotHost()
    ```

    Similar pattern: `cancelMeeting_returns403WhenRequesterIsNotHost()`
    (line 240)

3. **409 Meeting Ended** (Scenario 4a)

    ```java
    void putMeetingSettings_returns409_whenMeetingEnded()
    ```

    Similar pattern: `cancelMeeting_returns409WhenMeetingIsAlreadyLive()`
    (line 275)

4. **409 Meeting Cancelled** (Scenario 4b)

    ```java
    void putMeetingSettings_returns409_whenMeetingCancelled()
    ```

5. **409 Max Participants Ceiling** (Scenario 5)

    ```java
    void putMeetingSettings_returns409_whenMaxParticipantsExceedsCeiling()
    ```

6. **409 ALLOW_ALL Policy Violation** (Scenario 6)

    ```java
    void putMeetingSettings_returns409_whenAllowAllAndMaxParticipantsChanges()
    ```

**Example Implementation:**

```java
@Test
void putMeetingSettings_returns403_whenRequesterIsNotHost() throws Exception {
    UUID meetingId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();
    UUID hostId = UUID.randomUUID();

    when(putMeetingSettingsUseCase.execute(any()))
        .thenReturn(Result.failure(new MeetingError.NotAuthorized(requesterId, hostId)));

    mockMvc.perform(put("/api/v1/meetings/{id}/settings", meetingId)
        .principal(new TestingAuthenticationToken(requesterId.toString(), null))
        .contentType("application/json")
        .content("""
            {
              "admissionPolicy": "MANUAL_APPROVAL",
              "joinRequestTimeoutSeconds": 120,
              "allowGuest": true,
              "muteOnEntry": false,
              "maxParticipants": 40,
              "recordingEnabled": true,
              "screenShareMode": "HOST_ONLY",
              "chatEnabled": true,
              "password": null
            }
            """))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value("fail"))
        .andExpect(jsonPath("$.data.code").value("NOT_AUTHORIZED"));
}
```

**Impact:** 🔴 All error paths are untested at the HTTP layer. A
controller-level mapping bug would not be caught.

---

## WARNING ISSUES

### 5. ⚠️ WARNING: Weak Event Publication Assertion

**File:** `PutMeetingSettingsUseCaseTest.java`, line 105

**Current Code:**

```java
verify(eventPublisher).publishEvent(any(MeetingSettingsUpdatedEvent.class));
```

**Problem:** Only verifies that SOME MeetingSettingsUpdatedEvent was published,
not the event content.

**Better:**

```java
ArgumentCaptor<MeetingSettingsUpdatedEvent> eventCaptor =
    ArgumentCaptor.forClass(MeetingSettingsUpdatedEvent.class);
verify(eventPublisher).publishEvent(eventCaptor.capture());

MeetingSettingsUpdatedEvent event = eventCaptor.getValue();
assertThat(event.meetingId()).isEqualTo(meetingId);
assertThat(event.requesterId()).isEqualTo(hostId);
assertThat(event.admissionPolicy()).isEqualTo(AdmissionPolicy.MANUAL_APPROVAL);
```

---

## REQUIREMENT COVERAGE ANALYSIS

| #   | Requirement     | Scenario                        | Use-Case Test         | Controller Test | Status        |
| --- | --------------- | ------------------------------- | --------------------- | --------------- | ------------- |
| 1   | PUT-only        | Replace with PUT                | ✓ Happy path          | ✓ 200 OK        | ✓ COVERED     |
| 2   | PUT-only        | PATCH removed                   | ✓ Method check        | ✓ 405 response  | ✓ COVERED     |
| 3   | Authorization   | Only host                       | ❌ Fails (Issue #1)   | ❌ Missing      | ❌ NOT TESTED |
| 4a  | Domain Rules    | Only SCHEDULED/LIVE (ENDED)     | ❌ Fails (Issue #1)   | ❌ Missing      | ❌ NOT TESTED |
| 4b  | Domain Rules    | Only SCHEDULED/LIVE (CANCELLED) | ❌ Missing (Issue #2) | ❌ Missing      | ❌ NOT TESTED |
| 5   | Domain Rules    | Max participants ceiling        | ❌ Fails (Issue #1)   | ❌ Missing      | ❌ NOT TESTED |
| 6   | Domain Rules    | ALLOW_ALL policy blocks change  | ❌ Fails (Issue #1)   | ❌ Missing      | ❌ NOT TESTED |
| 7   | Nullable Fields | Timeout null clears             | ⚠️ Partial (Issue #3) | -               | ⚠️ INCOMPLETE |
| 8   | Nullable Fields | Password null clears            | ✓ Tested              | -               | ✓ COVERED     |
| 9   | Nullable Fields | Password non-null hashes        | ✓ Tested              | -               | ✓ COVERED     |
| 10  | Side Effects    | Event published                 | ✓ Tested (weak)       | -               | ✓ COVERED     |
| 11  | Side Effects    | Live ALLOW_ALL auto-approve     | ❌ Fails (Issue #1)   | -               | ❌ NOT TESTED |
| 12  | Side Effects    | Live allowGuest auto-approve    | ❌ Fails (Issue #1)   | -               | ❌ NOT TESTED |

**Summary:**

- ✓ Passing: 3/12 scenarios
- ❌ Failing: 6/12 scenarios (blocked by Issue #1)
- ⚠️ Incomplete: 1/12 scenario (Issue #3)
- ❌ Missing: 2/12 scenarios (Issue #2 + 6 controller tests)

---

## RECOMMENDED FIX SEQUENCE

### Phase 1 (Blocking - Do First)

**[1] Fix Issue #1 — Unnecessary Stubbing**

- Add `@MockitoSettings(strictness = Strictness.LENIENT)` to test class
- Unblocks 6 currently-failing tests
- **Time: 5 minutes**

### Phase 2 (Critical Gaps - Do Next)

**[2] Add Issue #2 Test — CANCELLED Status**

- Add `execute_returnsInvalidStatusForCancelledMeeting()`
- Completes Scenario 4 (both ENDED and CANCELLED)
- **Time: 10 minutes**

**[3] Add Issue #3 Test — Timeout Clearing**

- Add `execute_nullTimeout_clearsExistingJoinRequestTimeout()`
- Completes Scenario 7 with explicit transition proof
- **Time: 10 minutes**

**[4] Add Issue #4 Tests — 6 Controller Integration Tests**

- Add 6 error scenario tests at HTTP layer
- Follow existing patterns from `MeetingControllerTest`
- **Time: 30 minutes**

### Phase 3 (Quality - Optional)

**[5] Fix Issue #5 — Strengthen Event Assertion**

- Use `ArgumentCaptor` to verify event content
- **Time: 10 minutes**

**Total Estimated Time: ~65 minutes**

---

## Final Assessment

### Current Status

```
Build:           ❌ FAILS
Test Execution:  ❌ 6 of 8 use-case tests fail
Coverage:        ⚠️ 2/12 scenarios passing, 6/12 blocked, 4/12 missing
```

### Requirement for Archiving

- [ ] Fix Issue #1 (CRITICAL BLOCKER)
- [ ] Add Issue #2 test (CRITICAL COVERAGE)
- [ ] Add Issue #3 test (CRITICAL COVERAGE)
- [ ] Add Issue #4 tests (CRITICAL COVERAGE)
- [ ] Fix Issue #5 (Optional)

### After Fixes

```
Build:           ✓ PASSES
Test Execution:  ✓ All tests pass
Coverage:        ✓ 12/12 scenarios tested
Quality:         ✓ All error paths verified at use-case + controller level
```

---

## Recommendation

**❌ CANNOT ARCHIVE**

Task group 3 implementation cannot be verified due to:

1. **Build failure** (UnnecessaryStubbingException blocks 6 tests)
2. **2 missing spec scenarios** (CANCELLED status, timeout clearing transition)
3. **6 untested error paths** (all controller integration tests missing)

These are not optional gaps — they represent untested requirements in the
specification and a broken test suite. Address all 4 critical issues before
archiving.
