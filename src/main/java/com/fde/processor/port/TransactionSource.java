package com.fde.processor.port;

import com.fde.processor.domain.TransactionRequest;
import java.util.List;

/** Replaceable input boundary: a file, queue, API, or scheduler can implement this port. */
public interface TransactionSource { List<TransactionRequest> load(); }
