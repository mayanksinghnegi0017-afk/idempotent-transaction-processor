package com.fde.processor.domain;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable snapshot for console output, operations, and test assertions. */
public record ProcessingSummary(Map<TransactionStatus, Long> counts,
        Map<String, BigDecimal> balances, List<RequestResult> auditTrail, List<String> sequenceGaps) {
    public ProcessingSummary {
        counts = Map.copyOf(counts); balances = Map.copyOf(balances);
        auditTrail = List.copyOf(auditTrail); sequenceGaps = List.copyOf(sequenceGaps);
    }
    public long count(TransactionStatus status) { return counts.getOrDefault(status, 0L); }
    public static Map<TransactionStatus, Long> countStatuses(List<RequestResult> results) {
        Map<TransactionStatus, Long> counts = new EnumMap<>(TransactionStatus.class);
        for (TransactionStatus status : TransactionStatus.values()) counts.put(status, 0L);
        results.forEach(r -> counts.merge(r.status(), 1L, Long::sum));
        return counts;
    }
}
