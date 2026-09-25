package com.kushagra.riskguard.dto;

import java.util.List;

public class RiskResponse {

    private final Long transactionId;
    private final int riskScore;
    private final boolean flagged;
    private final List<String> reasons;

    public RiskResponse(Long transactionId, int riskScore, boolean flagged, List<String> reasons) {
        this.transactionId = transactionId;
        this.riskScore = riskScore;
        this.flagged = flagged;
        this.reasons = List.copyOf(reasons);
    }

    public Long getTransactionId() {
        return transactionId;
    }

    public int getRiskScore() {
        return riskScore;
    }

    public boolean isFlagged() {
        return flagged;
    }

    public List<String> getReasons() {
        return reasons;
    }
}
