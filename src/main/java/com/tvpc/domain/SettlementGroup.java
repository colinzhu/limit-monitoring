package com.tvpc.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Settlement group - represents aggregated information for a group.
 */
public class SettlementGroup {
    private String pts;
    private String processingEntity;
    private String counterpartyId;
    private LocalDate valueDate;
    private BigDecimal runningTotal;
    private BigDecimal limit;
    private int settlementCount;
    private SettlementStatus status;

    public SettlementGroup(
            String pts,
            String processingEntity,
            String counterpartyId,
            LocalDate valueDate,
            BigDecimal runningTotal,
            BigDecimal limit,
            int settlementCount,
            SettlementStatus status
    ) {
        this.pts = pts;
        this.processingEntity = processingEntity;
        this.counterpartyId = counterpartyId;
        this.valueDate = valueDate;
        this.runningTotal = runningTotal;
        this.limit = limit;
        this.settlementCount = settlementCount;
        this.status = status;
    }

    // Getters
    public String getPts() { return pts; }
    public String getProcessingEntity() { return processingEntity; }
    public String getCounterpartyId() { return counterpartyId; }
    public LocalDate getValueDate() { return valueDate; }
    public BigDecimal getRunningTotal() { return runningTotal; }
    public BigDecimal getLimit() { return limit; }
    public int getSettlementCount() { return settlementCount; }
    public SettlementStatus getStatus() { return status; }

    public BigDecimal getPercentUsed() {
        if (limit == null || limit.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return runningTotal.multiply(new BigDecimal("100")).divide(limit, 2, java.math.RoundingMode.HALF_UP);
    }

    @Override
    public String toString() {
        return "SettlementGroup{" +
                "pts='" + pts + '\'' +
                ", processingEntity='" + processingEntity + '\'' +
                ", counterpartyId='" + counterpartyId + '\'' +
                ", valueDate=" + valueDate +
                ", runningTotal=" + runningTotal +
                ", limit=" + limit +
                ", settlementCount=" + settlementCount +
                ", status=" + status +
                '}';
    }
}
