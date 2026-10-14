package com.fde.processor.adapter;

import com.fde.processor.domain.*;
import com.fde.processor.port.StateStore;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe demo store. ConcurrentHashMap putIfAbsent makes claims atomic within this JVM. */
public final class InMemoryStateStore implements StateStore {
    private final Set<String> requestIds = ConcurrentHashMap.newKeySet();
    private final Map<String, TransactionRecord> transactions = new ConcurrentHashMap<>();
    private final Map<String, RequestResult> results = new ConcurrentHashMap<>();
    @Override public boolean registerRequestId(String id) { return requestIds.add(id); }
    @Override public boolean claimTransaction(String id, TransactionRecord record) { return transactions.putIfAbsent(id, record) == null; }
    @Override public TransactionRecord findTransaction(String id) { return transactions.get(id); }
    @Override public void saveResult(RequestResult result) { results.put(result.requestId(), result); }
    @Override public List<RequestResult> results() { return results.values().stream().sorted(Comparator.comparing(RequestResult::requestId)).toList(); }
    @Override public List<TransactionRecord> transactions() { return List.copyOf(transactions.values()); }
}
