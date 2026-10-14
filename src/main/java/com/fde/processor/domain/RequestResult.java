package com.fde.processor.domain;

/** Final/current result for an individual delivery attempt, including duplicate attempts. */
public record RequestResult(String requestId, String transactionId, TransactionStatus status, String message, int attempts) { }
