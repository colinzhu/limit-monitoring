package com.tvpc.presentation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * API Settlement Response DTO - HTTP response
 * Presentation layer DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiSettlementResponse {
    private String status;
    private String message;
    private Long sequenceId;

    // Factory methods
    public static ApiSettlementResponse success(String message, Long sequenceId) {
        return ApiSettlementResponse.builder()
                .status("success")
                .message(message)
                .sequenceId(sequenceId)
                .build();
    }

    public static ApiSettlementResponse error(String message) {
        return ApiSettlementResponse.builder()
                .status("error")
                .message(message)
                .build();
    }
}
