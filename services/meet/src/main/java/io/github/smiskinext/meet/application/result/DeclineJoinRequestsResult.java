package io.github.smiskinext.meet.application.result;

import java.util.List;

/**
 * Result of a host decline operation: one {@link JoinDecisionItemResult} per submitted request id,
 * in submission order.
 */
public record DeclineJoinRequestsResult(List<JoinDecisionItemResult> results) {}
