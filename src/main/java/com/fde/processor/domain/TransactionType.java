package com.fde.processor.domain;

/** Supported account transaction operations. REVERSAL is modeled as a credit in this demo;
 * production reversals should reference and validate the original transaction. */
public enum TransactionType { CREDIT, DEBIT, REVERSAL }
