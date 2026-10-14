package com.fde.processor.service;

/** Signals a temporary problem that is safe to retry before applying the ledger effect. */
public class TransientProcessingException extends RuntimeException {
    public TransientProcessingException(String message) { super(message); }
}
