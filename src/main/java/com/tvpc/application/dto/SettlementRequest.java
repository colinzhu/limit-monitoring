package com.tvpc.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Settlement Request DTO - Input for settlement ingestion
 * Application layer DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettlementRequest {
    private String settlementId;
    private Long settlementVersion;
    private String pts;
    private String processingEntity;
    private String counterpartyId;
    private String valueDate;
    private String currency;
    private Double amount;
    private String businessStatus;
    private String direction;
    private String settlementType;
}
