package com.tvpc.domain.model;

import lombok.Value;

import java.math.BigDecimal;

/**
 * Running Total - Aggregated exposure for a settlement group
 * Entity - Has identity
 */
@Value
public class RunningTotal {
    private final Long id;  // Primary key
    private final String pts;
    private final String processingEntity;
    private final String counterpartyId;
    private final String valueDate;  // Stored as string for simplicity
    private final BigDecimal total;
    private final Long refId;  // Sequence ID used for calculation
}
