package com.tvpc.presentation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * API Settlement Request DTO - HTTP request body
 * Presentation layer DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiSettlementRequest {
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
