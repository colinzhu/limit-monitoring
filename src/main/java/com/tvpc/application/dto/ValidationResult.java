package com.tvpc.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Validation Result DTO - Output for validation operations
 * Application layer DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationResult {
    private boolean valid;
    private List<String> errors;

    public static ValidationResult valid() {
        return ValidationResult.builder()
                .valid(true)
                .errors(List.of())
                .build();
    }

    public static ValidationResult invalid(List<String> errors) {
        return ValidationResult.builder()
                .valid(false)
                .errors(errors)
                .build();
    }

    public static ValidationResult invalid(String error) {
        return ValidationResult.builder()
                .valid(false)
                .errors(List.of(error))
                .build();
    }
}
