package com.fde.processor.port;

import com.fde.processor.domain.TransactionRequest;
import java.math.BigDecimal;
import java.util.Map;

/** Atomic account balance and exactly-once ledger-effect boundary. */
public interface AccountLedger {
    void applyOnce(TransactionRequest request);
    BigDecimal balance(String accountId);
    Map<String, BigDecimal> balances();
}
