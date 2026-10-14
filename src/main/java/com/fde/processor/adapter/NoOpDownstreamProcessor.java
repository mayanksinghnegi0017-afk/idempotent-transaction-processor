package com.fde.processor.adapter;

import com.fde.processor.domain.TransactionRequest;
import com.fde.processor.port.DownstreamProcessor;

/** Default downstream adapter; tests can inject a failure simulator. */
public final class NoOpDownstreamProcessor implements DownstreamProcessor {
    @Override public void beforeApply(TransactionRequest request) { /* No external system in this local assessment. */ }
}
