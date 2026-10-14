package com.fde.processor.util;

/** Basic log masking utility. Production systems should use a policy tailored to data classification. */
public final class PiiMasker {
    private PiiMasker() { }
    public static String maskAccountId(String accountId) {
        if (accountId == null || accountId.isBlank()) return "***";
        if (accountId.length() <= 2) return "*".repeat(accountId.length());
        return "ACC-***" + accountId.substring(accountId.length() - 2);
    }
}
