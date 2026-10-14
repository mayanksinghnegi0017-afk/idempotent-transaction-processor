package com.fde.processor.adapter;

import com.fde.processor.domain.TransactionRequest;
import com.fde.processor.domain.TransactionType;
import com.fde.processor.port.AccountLedger;
import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/** In-memory ledger with a lock per account: unrelated accounts can proceed independently. */
public final class InMemoryAccountLedger implements AccountLedger {
    private final Map<String, BigDecimal> balances = new ConcurrentHashMap<>();
    private final Map<String, ReentrantLock> accountLocks = new ConcurrentHashMap<>();
    private final Map<String, Boolean> appliedTransactionIds = new ConcurrentHashMap<>();

    @Override public void applyOnce(TransactionRequest request) {
        ReentrantLock lock = accountLocks.computeIfAbsent(request.accountId(), ignored -> new ReentrantLock());
        lock.lock();
        try {
            // The transaction-id guard is checked while holding the account lock so a replay cannot mutate balance twice.
            if (appliedTransactionIds.containsKey(request.transactionId())) return;
            BigDecimal current = balances.getOrDefault(request.accountId(), BigDecimal.ZERO);
            BigDecimal next;
            if (request.transactionType() == TransactionType.DEBIT) {
                next = current.subtract(request.amount());
                if (next.signum() < 0) throw new IllegalStateException("Insufficient funds");
            } else {
                next = current.add(request.amount());
            }
            balances.put(request.accountId(), next);
            appliedTransactionIds.put(request.transactionId(), Boolean.TRUE);
        } finally { lock.unlock(); }
    }
    @Override public BigDecimal balance(String accountId) { return balances.getOrDefault(accountId, BigDecimal.ZERO); }
    @Override public Map<String, BigDecimal> balances() { return Map.copyOf(balances); }
}
