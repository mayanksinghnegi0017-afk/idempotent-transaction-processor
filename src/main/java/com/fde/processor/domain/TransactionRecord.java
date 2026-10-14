package com.fde.processor.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Mutable state for one business transaction. State changes are coordinated by the processor. */
public final class TransactionRecord {
    private final TransactionRequest request;
    private volatile TransactionStatus status = TransactionStatus.RECEIVED;
    private volatile int attempts;
    private volatile String errorMessage;
    private volatile Instant updatedAt = Instant.now();
    private final List<String> requestHistory = new ArrayList<>();

    public TransactionRecord(TransactionRequest request) { this.request = request; requestHistory.add(request.requestId()); }
    public TransactionRequest request() { return request; }
    public TransactionStatus status() { return status; }
    public int attempts() { return attempts; }
    public String errorMessage() { return errorMessage; }
    public Instant updatedAt() { return updatedAt; }
    public synchronized List<String> requestHistory() { return List.copyOf(requestHistory); }
    public synchronized void addRequest(String requestId) { requestHistory.add(requestId); }
    public synchronized void transition(TransactionStatus next, String error) {
        this.status = next; this.errorMessage = error; this.updatedAt = Instant.now();
    }
    public synchronized void incrementAttempts() { attempts++; updatedAt = Instant.now(); }
}
