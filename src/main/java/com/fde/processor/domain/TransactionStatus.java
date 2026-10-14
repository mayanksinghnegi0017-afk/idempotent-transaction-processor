package com.fde.processor.domain;

/** Observable lifecycle for a transaction request/business transaction. */
public enum TransactionStatus { RECEIVED, PENDING, PROCESSING, RETRY_PENDING, PROCESSED, DUPLICATE, FAILED }
