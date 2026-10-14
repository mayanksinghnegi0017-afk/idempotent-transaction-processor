package com.fde.processor.adapter;

import com.fde.processor.domain.TransactionRequest;
import com.fde.processor.port.TransactionSource;
import java.util.List;

/** Simple replaceable source for local demonstrations and tests. */
public record ListTransactionSource(List<TransactionRequest> requests) implements TransactionSource {
    public ListTransactionSource { requests = List.copyOf(requests); }
    @Override public List<TransactionRequest> load() { return requests; }
}
