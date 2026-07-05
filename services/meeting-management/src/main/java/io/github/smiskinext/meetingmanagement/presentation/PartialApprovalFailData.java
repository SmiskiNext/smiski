package io.github.smiskinext.meetingmanagement.presentation;

import io.github.smiskinext.shared.domain.ErrorCode;
import io.github.smiskinext.shared.infrastructure.web.Violation;
import io.github.smiskinext.meetingmanagement.domain.MeetingError;
import io.github.smiskinext.shared.infrastructure.web.FailData;

import java.util.List;
import java.util.UUID;

/**
 * Extended fail payload for {@link MeetingError.PartialApprovalFailure}.
 *
 * <p>Preserves the standard {@code message}/{@code code}/{@code errors} shape of
 * {@link FailData} while adding the
 * domain-specific {@code approvedCount} and {@code failedIds} fields that clients need
 * to display which approvals succeeded and which failed.
 *
 * <pre>{@code
 * {
 *   "status": "fail",
 *   "data": {
 *     "message": "Partial approval: 3 approved, 2 failed",
 *     "code":    "PARTIAL_APPROVAL_FAILURE",
 *     "errors":  [],
 *     "approvedCount": 3,
 *     "failedIds": ["uuid-1", "uuid-2"]
 *   }
 * }
 * }</pre>
 *
 * @param message       human-readable summary
 * @param code          machine-readable error code
 * @param errors        field-level violations (always empty for this error)
 * @param approvedCount number of requests that were successfully approved
 * @param failedIds     request IDs that failed approval
 */
record PartialApprovalFailData(
        String message,
        ErrorCode code,
        List<Violation> errors,
        int approvedCount,
        List<UUID> failedIds) {}
