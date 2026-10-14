package com.fde.processor.service;

import com.fde.processor.domain.*;
import com.fde.processor.port.*;
import com.fde.processor.util.PiiMasker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * Batch processor implementing sequence-based buffering, business idempotency, retry-safe application,
 * and an audit result for each delivery attempt. A single coordinator lock serializes batch submissions;
 * ledger locks still protect account mutation if adapters are used from other callers.
 */
public final class BackgroundTransactionProcessor {
    private static final Logger log = LoggerFactory.getLogger(BackgroundTransactionProcessor.class);
    private final StateStore stateStore;
    private final AccountLedger ledger;
    private final DownstreamProcessor downstream;
    private final TransactionValidator validator;
    private final int maxRetries;
    private final Map<String, Long> nextSequence = new HashMap<>();
    private final Map<String, PriorityQueue<TransactionRecord>> pending = new HashMap<>();
    private final List<String> sequenceGaps = new ArrayList<>();

    public BackgroundTransactionProcessor(StateStore stateStore, AccountLedger ledger,
            DownstreamProcessor downstream, int maxRetries) {
        this(stateStore, ledger, downstream, new TransactionValidator(), maxRetries);
    }
    public BackgroundTransactionProcessor(StateStore stateStore, AccountLedger ledger,
            DownstreamProcessor downstream, TransactionValidator validator, int maxRetries) {
        this.stateStore = Objects.requireNonNull(stateStore); this.ledger = Objects.requireNonNull(ledger);
        this.downstream = Objects.requireNonNull(downstream); this.validator = Objects.requireNonNull(validator);
        if (maxRetries < 0) throw new IllegalArgumentException("maxRetries cannot be negative");
        this.maxRetries = maxRetries;
    }

    /** Synchronized coordinator prevents two callers from racing on sequence state in this in-memory implementation. */
    public synchronized ProcessingSummary processBatch(List<TransactionRequest> requests) {
        for (TransactionRequest request : requests) accept(request);
        // Only accounts with no processed sequence are allowed to establish a batch baseline.
        // This supports a standalone ACC-203 seq=5 record without letting ACC-201 seq=2 jump seq=1.
        for (var entry : pending.entrySet()) {
            String account = entry.getKey();
            if (nextSequence.getOrDefault(account, 1L) == 1L && !entry.getValue().isEmpty()) {
                long baseline = entry.getValue().peek().request().sequenceNumber();
                nextSequence.put(account, baseline);
                drain(account);
            }
        }
        reportGaps();
        Map<TransactionStatus, Long> counts = ProcessingSummary.countStatuses(stateStore.results());
        return new ProcessingSummary(counts, ledger.balances(), stateStore.results(), sequenceGaps);
    }

    private void accept(TransactionRequest request) {
        if (request == null) {
            stateStore.saveResult(new RequestResult("<null-" + (stateStore.results().size()+1) + ">", "<unknown>", TransactionStatus.FAILED, "Request is null", 0));
            return;
        }
        String validationError = validator.validate(request);
        if (validationError != null) {
            // Use a synthetic audit key when the supplied request ID itself is missing; null map keys are unsafe.
            String auditId = request.requestId() == null || request.requestId().isBlank()
                    ? "<invalid-" + (stateStore.results().size() + 1) + ">" : request.requestId();
            stateStore.saveResult(new RequestResult(auditId, request.transactionId() == null ? "<unknown>" : request.transactionId(), TransactionStatus.FAILED, validationError, 0));
            log.warn("request={} status=FAILED reason={}", auditId, validationError); return;
        }
        if (!stateStore.registerRequestId(request.requestId())) {
            stateStore.saveResult(new RequestResult(request.requestId(), request.transactionId(), TransactionStatus.DUPLICATE, "Duplicate requestId", 0));
            log.info("request={} status=DUPLICATE reason=duplicate_request_id", request.requestId()); return;
        }
        TransactionRecord record = new TransactionRecord(request);
        if (!stateStore.claimTransaction(request.transactionId(), record)) {
            TransactionRecord existing = stateStore.findTransaction(request.transactionId());
            if (existing != null) existing.addRequest(request.requestId());
            stateStore.saveResult(new RequestResult(request.requestId(), request.transactionId(), TransactionStatus.DUPLICATE, "Business transaction already registered", 0));
            log.info("request={} txn={} status=DUPLICATE", request.requestId(), request.transactionId()); return;
        }
        String account = request.accountId();
        long expected = nextSequence.getOrDefault(account, 1L);
        if (request.sequenceNumber() < expected) {
            record.transition(TransactionStatus.DUPLICATE, "Stale sequence number");
            stateStore.saveResult(new RequestResult(request.requestId(), request.transactionId(), TransactionStatus.DUPLICATE, "Stale sequence number", 0)); return;
        }
        if (request.sequenceNumber() > expected) {
            record.transition(TransactionStatus.PENDING, "Waiting for sequence " + expected);
            pending.computeIfAbsent(account, ignored -> new PriorityQueue<>(Comparator.comparingLong(r -> r.request().sequenceNumber()))).add(record);
            stateStore.saveResult(new RequestResult(request.requestId(), request.transactionId(), TransactionStatus.PENDING, "Waiting for sequence " + expected, 0));
            log.info("request={} account={} status=PENDING", request.requestId(), PiiMasker.maskAccountId(account)); return;
        }
        processRecord(record);
        nextSequence.put(account, expected + 1);
        drain(account);
    }

    private void drain(String account) {
        PriorityQueue<TransactionRecord> queue = pending.get(account);
        if (queue == null) return;
        while (!queue.isEmpty()) {
            long expected = nextSequence.getOrDefault(account, 1L);
            TransactionRecord next = queue.peek();
            if (next.request().sequenceNumber() != expected) break;
            queue.poll();
            processRecord(next);
            nextSequence.put(account, expected + 1);
        }
    }

    private void processRecord(TransactionRecord record) {
        TransactionRequest request = record.request();
        record.transition(TransactionStatus.PROCESSING, null);
        int retries = 0;
        while (true) {
            record.incrementAttempts();
            try {
                downstream.beforeApply(request); // Failures happen before ledger mutation, so retry is safe.
                ledger.applyOnce(request);
                record.transition(TransactionStatus.PROCESSED, null);
                stateStore.saveResult(new RequestResult(request.requestId(), request.transactionId(), TransactionStatus.PROCESSED, "Processed", record.attempts()));
                log.info("request={} txn={} account={} status=PROCESSED attempts={}", request.requestId(), request.transactionId(), PiiMasker.maskAccountId(request.accountId()), record.attempts());
                return;
            } catch (TransientProcessingException ex) {
                retries++;
                if (retries <= maxRetries) {
                    record.transition(TransactionStatus.RETRY_PENDING, ex.getMessage());
                    log.warn("request={} status=RETRY_PENDING retry={}", request.requestId(), retries);
                } else {
                    record.transition(TransactionStatus.FAILED, "Retry limit reached: " + ex.getMessage());
                    stateStore.saveResult(new RequestResult(request.requestId(), request.transactionId(), TransactionStatus.FAILED, record.errorMessage(), record.attempts()));
                    return;
                }
            } catch (RuntimeException ex) {
                record.transition(TransactionStatus.FAILED, ex.getMessage());
                stateStore.saveResult(new RequestResult(request.requestId(), request.transactionId(), TransactionStatus.FAILED, ex.getMessage(), record.attempts()));
                log.warn("request={} status=FAILED reason={}", request.requestId(), ex.getMessage());
                return;
            }
        }
    }

    private void reportGaps() {
        for (var entry : pending.entrySet()) {
            PriorityQueue<TransactionRecord> queue = entry.getValue();
            if (!queue.isEmpty()) {
                long expected = nextSequence.getOrDefault(entry.getKey(), 1L);
                String gap = PiiMasker.maskAccountId(entry.getKey()) + ": expected sequence " + expected + ", next available " + queue.peek().request().sequenceNumber();
                if (!sequenceGaps.contains(gap)) sequenceGaps.add(gap);
            }
        }
    }
}
