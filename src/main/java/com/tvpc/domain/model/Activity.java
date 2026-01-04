package com.tvpc.domain.model;

import lombok.Value;

import java.time.LocalDateTime;

/**
 * Activity - Audit record for approval workflow
 * Entity - Has identity
 */
@Value
public class Activity {
    private final Long id;
    private final String pts;
    private final String processingEntity;
    private final String settlementId;
    private final Long settlementVersion;
    private final String userId;
    private final String userName;
    private final String actionType;  // REQUEST_RELEASE, AUTHORISE, etc.
    private final String actionComment;
    private final LocalDateTime createTime;
}
