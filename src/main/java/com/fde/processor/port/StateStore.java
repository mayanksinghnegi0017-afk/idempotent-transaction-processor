package com.fde.processor.port;

import com.fde.processor.domain.*;
import java.util.List;

/** Persistence boundary for request deduplication, business idempotency, and audit results. */
public interface StateStore {
    boolean registerRequestId(String requestId);
    boolean claimTransaction(String transactionId, TransactionRecord record);
    TransactionRecord findTransaction(String transactionId);
    void saveResult(RequestResult result);
    List<RequestResult> results();
    List<TransactionRecord> transactions();
}
