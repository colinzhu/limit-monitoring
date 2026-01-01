package com.tvpc.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Running total entity - represents aggregated exposure per group.
 */
public class RunningTotal {
    private Long id;
    private String pts;
    private String processingEntity;
    private String counterpartyId;
    private LocalDate valueDate;
    private BigDecimal runningTotal;
    private Long refId;  // Settlement sequence ID used for calculation
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public RunningTotal() {}

    public RunningTotal(
            String pts,
            String processingEntity,
            String counterpartyId,
            LocalDate valueDate,
            BigDecimal runningTotal,
            Long refId
    ) {
        this.pts = pts;
        this.processingEntity = processingEntity;
        this.counterpartyId = counterpartyId;
        this.valueDate = valueDate;
        this.runningTotal = runningTotal;
        this.refId = refId;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPts() { return pts; }
    public void setPts(String pts) { this.pts = pts; }

    public String getProcessingEntity() { return processingEntity; }
    public void setProcessingEntity(String processingEntity) { this.processingEntity = processingEntity; }

    public String getCounterpartyId() { return counterpartyId; }
    public void setCounterpartyId(String counterpartyId) { this.counterpartyId = counterpartyId; }

    public LocalDate getValueDate() { return valueDate; }
    public void setValueDate(LocalDate valueDate) { this.valueDate = valueDate; }

    public BigDecimal getRunningTotal() { return runningTotal; }
    public void setRunningTotal(BigDecimal runningTotal) { this.runningTotal = runningTotal; }

    public Long getRefId() { return refId; }
    public void setRefId(Long refId) { this.refId = refId; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RunningTotal that = (RunningTotal) o;
        return Objects.equals(pts, that.pts) &&
               Objects.equals(processingEntity, that.processingEntity) &&
               Objects.equals(counterpartyId, that.counterpartyId) &&
               Objects.equals(valueDate, that.valueDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pts, processingEntity, counterpartyId, valueDate);
    }

    @Override
    public String toString() {
        return "RunningTotal{" +
                "pts='" + pts + '\'' +
                ", processingEntity='" + processingEntity + '\'' +
                ", counterpartyId='" + counterpartyId + '\'' +
                ", valueDate=" + valueDate +
                ", runningTotal=" + runningTotal +
                ", refId=" + refId +
                '}';
    }
}
