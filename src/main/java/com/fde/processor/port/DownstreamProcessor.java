package com.fde.processor.port;

import com.fde.processor.domain.TransactionRequest;

/** Hook for downstream checks/integrations; implementations may throw a retryable exception. */
public interface DownstreamProcessor { void beforeApply(TransactionRequest request); }
