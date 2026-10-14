package com.fde.processor.adapter;

import com.fde.processor.domain.TransactionRequest;
import com.fde.processor.port.DownstreamProcessor;
import com.fde.processor.service.TransientProcessingException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Deterministic demo adapter: fail a configured number of times per transaction, then succeed. */
public final class FailFirstAttemptProcessor implements DownstreamProcessor {
    private final Map<String, AtomicInteger> failuresRemaining = new ConcurrentHashMap<>();
    public FailFirstAttemptProcessor failTimes(String transactionId, int count) {
        failuresRemaining.put(transactionId, new AtomicInteger(Math.max(0, count))); return this;
    }
    @Override public void beforeApply(TransactionRequest request) {
        AtomicInteger remaining = failuresRemaining.get(request.transactionId());
        if (remaining != null && remaining.getAndUpdate(v -> Math.max(0, v - 1)) > 0)
            throw new TransientProcessingException("Simulated temporary downstream failure");
    }
}
