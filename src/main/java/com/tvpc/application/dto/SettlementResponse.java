package com.tvpc.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * DTO for settlement processing response
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Value
@Builder
public class SettlementResponse {
    private final String status;  // "success" or "error"
    private final String message;
    private final Long sequenceId;
    private final List<String> errors;

    // Static factory methods
    public static SettlementResponse success(String message, Long sequenceId) {
        return SettlementResponse.builder()
                .status("success")
                .message(message)
                .sequenceId(sequenceId)
                .build();
    }

    public static SettlementResponse error(String message, List<String> errors) {
        return SettlementResponse.builder()
                .status("error")
                .message(message)
                .errors(errors)
                .build();
    }

    public static SettlementResponse error(String message) {
        return SettlementResponse.builder()
                .status("error")
                .message(message)
                .build();
    }
}
