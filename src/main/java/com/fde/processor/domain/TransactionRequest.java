package com.fde.processor.domain;

import java.math.BigDecimal;
import java.time.Instant;

/** Immutable input event. requestId identifies a delivery attempt; transactionId identifies the business effect. */
public record TransactionRequest(String requestId, String transactionId, String accountId,
        TransactionType transactionType, BigDecimal amount, String currency,
        long sequenceNumber, Instant eventTime) { }
