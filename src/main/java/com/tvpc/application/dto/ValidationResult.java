package com.tvpc.application.dto;

import lombok.Builder;
import lombok.Value;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * DTO for validation results
 */
@Value
@Builder
public class ValidationResult {
    private final boolean valid;
    @Builder.Default
    private final List<String> errors = Collections.unmodifiableList(new ArrayList<>());

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    // Static factory methods
    public static ValidationResult valid() {
        return ValidationResult.builder().valid(true).build();
    }

    public static ValidationResult invalid(String error) {
        return ValidationResult.builder().valid(false).errors(Collections.singletonList(error)).build();
    }

    public static ValidationResult invalid(List<String> errors) {
        return ValidationResult.builder().valid(false).errors(Collections.unmodifiableList(new ArrayList<>(errors))).build();
    }
}
