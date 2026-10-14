package com.fde.processor;

import com.fde.processor.adapter.*;
import com.fde.processor.domain.*;
import com.fde.processor.port.*;
import com.fde.processor.service.BackgroundTransactionProcessor;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Runnable CLI demo using the exact six business scenarios from the assessment. */
public final class ApplicationRunner {
    private ApplicationRunner() { }
    public static void main(String[] args) {
        List<TransactionRequest> sample = List.of(
            tx("REQ-2", "TXN-2002", "ACC-201", TransactionType.DEBIT, "40", 2),
            tx("REQ-1", "TXN-2001", "ACC-201", TransactionType.CREDIT, "100", 1),
            tx("REQ-2-DUP", "TXN-2002", "ACC-201", TransactionType.DEBIT, "40", 2),
            tx("REQ-3", "TXN-2003", "ACC-201", TransactionType.CREDIT, "25", 3),
            tx("REQ-4", "TXN-2004", "ACC-202", TransactionType.DEBIT, "-10", 4),
            tx("REQ-5", "TXN-2005", "ACC-203", TransactionType.CREDIT, "500", 5));
        StateStore store = new InMemoryStateStore();
        AccountLedger ledger = new InMemoryAccountLedger();
        FailFirstAttemptProcessor downstream = new FailFirstAttemptProcessor().failTimes("TXN-2005", 1);
        var summary = new BackgroundTransactionProcessor(store, ledger, downstream, 2).processBatch(sample);
        System.out.println("\n========== TRANSACTION PROCESSING SUMMARY ==========");
        System.out.printf("%-14s %-14s %-14s %-12s %-8s%n", "REQUEST", "TRANSACTION", "STATUS", "ATTEMPTS", "MESSAGE");
        summary.auditTrail().forEach(r -> System.out.printf("%-14s %-14s %-14s %-12d %s%n", r.requestId(), r.transactionId(), r.status(), r.attempts(), r.message()));
        System.out.println("\nStatus counts:");
        summary.counts().forEach((status, count) -> System.out.printf("  %-14s %d%n", status, count));
        System.out.println("Balances: " + summary.balances());
        System.out.println("Sequence gaps: " + summary.sequenceGaps());
        System.out.println("====================================================\n");
    }
    private static TransactionRequest tx(String req, String txn, String account, TransactionType type, String amount, long seq) {
        return new TransactionRequest(req, txn, account, type, new BigDecimal(amount), "USD", seq, Instant.parse("2026-10-14T10:00:00Z"));
    }
}
