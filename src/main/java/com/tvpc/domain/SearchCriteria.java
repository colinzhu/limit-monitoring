package com.tvpc.domain;

import java.time.LocalDate;

/**
 * Search criteria for settlement queries.
 */
public class SearchCriteria {
    private String pts;
    private String processingEntity;
    private String counterpartyId;
    private LocalDate valueDateFrom;
    private LocalDate valueDateTo;
    private String settlementDirection;
    private String settlementType;
    private String businessStatus;
    private SettlementStatus settlementStatus; // CREATED, BLOCKED, PENDING_AUTHORISE, AUTHORISED

    // Getters and Setters
    public String getPts() { return pts; }
    public void setPts(String pts) { this.pts = pts; }

    public String getProcessingEntity() { return processingEntity; }
    public void setProcessingEntity(String processingEntity) { this.processingEntity = processingEntity; }

    public String getCounterpartyId() { return counterpartyId; }
    public void setCounterpartyId(String counterpartyId) { this.counterpartyId = counterpartyId; }

    public LocalDate getValueDateFrom() { return valueDateFrom; }
    public void setValueDateFrom(LocalDate valueDateFrom) { this.valueDateFrom = valueDateFrom; }

    public LocalDate getValueDateTo() { return valueDateTo; }
    public void setValueDateTo(LocalDate valueDateTo) { this.valueDateTo = valueDateTo; }

    public String getSettlementDirection() { return settlementDirection; }
    public void setSettlementDirection(String settlementDirection) { this.settlementDirection = settlementDirection; }

    public String getSettlementType() { return settlementType; }
    public void setSettlementType(String settlementType) { this.settlementType = settlementType; }

    public String getBusinessStatus() { return businessStatus; }
    public void setBusinessStatus(String businessStatus) { this.businessStatus = businessStatus; }

    public SettlementStatus getSettlementStatus() { return settlementStatus; }
    public void setSettlementStatus(SettlementStatus settlementStatus) { this.settlementStatus = settlementStatus; }
}
