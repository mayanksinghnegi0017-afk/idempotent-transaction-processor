package com.fde.processor.service;

import com.fde.processor.domain.TransactionRequest;
import java.util.Currency;

/** Keeps malformed input out of business processing and isolates bad records from the batch. */
public final class TransactionValidator {
    public String validate(TransactionRequest r) {
        if (r == null) return "Request is null";
        if (blank(r.requestId())) return "requestId is required";
        if (blank(r.transactionId())) return "transactionId is required";
        if (blank(r.accountId())) return "accountId is required";
        if (r.transactionType() == null) return "transactionType is required";
        if (r.amount() == null || r.amount().signum() <= 0) return "amount must be greater than zero";
        if (r.sequenceNumber() < 1) return "sequenceNumber must be at least 1";
        if (blank(r.currency())) return "currency is required";
        try { Currency.getInstance(r.currency().trim().toUpperCase()); }
        catch (IllegalArgumentException ex) { return "currency must be a valid ISO currency code"; }
        if (r.eventTime() == null) return "eventTime is required";
        return null;
    }
    private boolean blank(String value) { return value == null || value.isBlank(); }
}
