package com.fde.processor;

import com.fde.processor.adapter.*;
import com.fde.processor.domain.*;
import com.fde.processor.port.*;
import com.fde.processor.service.BackgroundTransactionProcessor;
import com.fde.processor.util.PiiMasker;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import static org.junit.jupiter.api.Assertions.*;

class BackgroundTransactionProcessorTest {
    private TransactionRequest tx(String req, String txn, String account, TransactionType type, String amount, long seq) {
        return new TransactionRequest(req, txn, account, type, new BigDecimal(amount), "USD", seq, Instant.parse("2026-10-14T10:00:00Z"));
    }
    private BackgroundTransactionProcessor processor(InMemoryStateStore store, InMemoryAccountLedger ledger, com.fde.processor.port.DownstreamProcessor downstream) {
        return new BackgroundTransactionProcessor(store, ledger, downstream, 2);
    }

    @Test void sampleAssessmentDatasetHasExpectedFinalBalancesAndStatuses() {
        var store = new InMemoryStateStore(); var ledger = new InMemoryAccountLedger();
        var downstream = new FailFirstAttemptProcessor().failTimes("TXN-2005", 1);
        var p = processor(store, ledger, downstream);
        var summary = p.processBatch(List.of(
            tx("REQ-2", "TXN-2002", "ACC-201", TransactionType.DEBIT, "40", 2),
            tx("REQ-1", "TXN-2001", "ACC-201", TransactionType.CREDIT, "100", 1),
            tx("REQ-2-DUP", "TXN-2002", "ACC-201", TransactionType.DEBIT, "40", 2),
            tx("REQ-3", "TXN-2003", "ACC-201", TransactionType.CREDIT, "25", 3),
            tx("REQ-4", "TXN-2004", "ACC-202", TransactionType.DEBIT, "-10", 4),
            tx("REQ-5", "TXN-2005", "ACC-203", TransactionType.CREDIT, "500", 5)));
        assertEquals(new BigDecimal("85"), summary.balances().get("ACC-201"));
        assertEquals(new BigDecimal("500"), summary.balances().get("ACC-203"));
        assertEquals(new BigDecimal("0"), ledger.balance("ACC-202"));
        assertEquals(TransactionStatus.PROCESSED, result(summary, "REQ-2").status());
        assertEquals(TransactionStatus.DUPLICATE, result(summary, "REQ-2-DUP").status());
        assertEquals(TransactionStatus.FAILED, result(summary, "REQ-4").status());
        assertEquals(2, result(summary, "REQ-5").attempts());
        assertEquals(TransactionStatus.PROCESSED, result(summary, "REQ-5").status());
    }

    @Test void repeatedRequestIdIsRejectedAndBusinessReplayDoesNotChangeBalance() {
        var store = new InMemoryStateStore(); var ledger = new InMemoryAccountLedger();
        var p = processor(store, ledger, new NoOpDownstreamProcessor());
        var first = tx("R1", "T1", "A1", TransactionType.CREDIT, "10", 1);
        var summary = p.processBatch(List.of(first, tx("R1", "T2", "A1", TransactionType.CREDIT, "99", 2), tx("R2", "T1", "A1", TransactionType.CREDIT, "10", 2)));
        assertEquals(new BigDecimal("10"), ledger.balance("A1"));
        assertEquals(TransactionStatus.DUPLICATE, result(summary, "R1").status());
        assertEquals(TransactionStatus.DUPLICATE, result(summary, "R2").status());
    }

    @Test void missingSequenceIsReportedWithoutApplyingFutureEvent() {
        var store = new InMemoryStateStore(); var ledger = new InMemoryAccountLedger();
        var summary = processor(store, ledger, new NoOpDownstreamProcessor()).processBatch(List.of(
            tx("R1", "T1", "A1", TransactionType.CREDIT, "10", 1),
            tx("R3", "T3", "A1", TransactionType.CREDIT, "30", 3)));
        assertEquals(new BigDecimal("10"), ledger.balance("A1"));
        assertEquals(TransactionStatus.PENDING, result(summary, "R3").status());
        assertFalse(summary.sequenceGaps().isEmpty());
    }

    @Test void exhaustedRetriesFailWithoutChangingBalance() {
        var store = new InMemoryStateStore(); var ledger = new InMemoryAccountLedger();
        var downstream = new FailFirstAttemptProcessor().failTimes("T1", 5);
        var summary = processor(store, ledger, downstream).processBatch(List.of(tx("R1", "T1", "A1", TransactionType.CREDIT, "10", 1)));
        assertEquals(TransactionStatus.FAILED, result(summary, "R1").status());
        assertEquals(3, result(summary, "R1").attempts());
        assertEquals(BigDecimal.ZERO, ledger.balance("A1"));
    }

    @Test void invalidRecordDoesNotStopBatchAndMaskerHidesAccountPrefix() {
        var store = new InMemoryStateStore(); var ledger = new InMemoryAccountLedger();
        var summary = processor(store, ledger, new NoOpDownstreamProcessor()).processBatch(List.of(
            tx("BAD", "BAD-T", "A1", TransactionType.DEBIT, "-1", 1),
            tx("GOOD", "GOOD-T", "A2", TransactionType.CREDIT, "5", 1)));
        assertEquals(TransactionStatus.FAILED, result(summary, "BAD").status());
        assertEquals(TransactionStatus.PROCESSED, result(summary, "GOOD").status());
        assertEquals("ACC-***01", PiiMasker.maskAccountId("ACC-201"));
    }

    @Test void concurrentSameTransactionIsAppliedOnlyOnce() throws Exception {
        var store = new InMemoryStateStore(); var ledger = new InMemoryAccountLedger();
        var p = processor(store, ledger, new NoOpDownstreamProcessor());
        int workers = 12; var ready = new CountDownLatch(workers); var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(workers)) {
            for (int i = 0; i < workers; i++) {
                final int n = i;
                pool.submit(() -> { ready.countDown(); try { start.await(); p.processBatch(List.of(tx("R" + n, "SAME-TXN", "A1", TransactionType.CREDIT, "10", 1))); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } });
            }
            ready.await(); start.countDown(); pool.shutdown();
            while (!pool.isTerminated()) Thread.sleep(10);
        }
        assertEquals(new BigDecimal("10"), ledger.balance("A1"));
    }

    private RequestResult result(ProcessingSummary summary, String requestId) {
        return summary.auditTrail().stream().filter(r -> r.requestId().equals(requestId)).findFirst().orElseThrow();
    }
}
