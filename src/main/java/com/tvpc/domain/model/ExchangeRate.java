package com.tvpc.domain.model;

import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Exchange Rate - Currency conversion rate to USD
 * Entity - Has identity
 */
@Value
public class ExchangeRate {
    private final String currency;  // ISO 4217 code
    private final BigDecimal rateToUsd;
    private final LocalDateTime updateTime;
}
