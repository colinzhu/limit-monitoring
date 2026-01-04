package com.tvpc.application.service;

import com.tvpc.application.port.in.SettlementIngestionUseCase.SettlementIngestionCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SettlementValidator
 */
class SettlementValidatorTest {

    private SettlementValidator validator;

    @BeforeEach
    void setUp() {
        validator = new SettlementValidator();
    }

    @Test
    void testValidSettlementRequest() {
        SettlementIngestionCommand command = new SettlementIngestionCommand(
                "SETT-12345",
                1735689600000L,
                "PTS-A",
                "PE-001",
                "CP-ABC",
                "2025-12-31",
                "EUR",
                new BigDecimal("1000000.00"),
                "VERIFIED",
                "PAY",
                "GROSS"
        );

        ValidationResult result = validator.validate(command);
        assertTrue(result.isValid(), "Valid settlement should pass validation");
        assertTrue(result.errors().isEmpty(), "No errors expected for valid settlement");
    }

    @Test
    void testMissingRequiredFields() {
        SettlementIngestionCommand command = new SettlementIngestionCommand(
                null, null, null, null, null, null, null, null, null, null, null
        );

        ValidationResult result = validator.validate(command);
        assertFalse(result.isValid(), "Should fail with missing fields");
        assertEquals(11, result.errors().size(), "Should have 11 errors for missing fields");
    }

    @Test
    void testInvalidCurrency() {
        SettlementIngestionCommand command = new SettlementIngestionCommand(
                "SETT-12345",
                1735689600000L,
                "PTS-A",
                "PE-001",
                "CP-ABC",
                "2025-12-31",
                "EURR",
                new BigDecimal("1000000.00"),
                "VERIFIED",
                "PAY",
                "GROSS"
        );

        ValidationResult result = validator.validate(command);
        assertFalse(result.isValid(), "Should fail with invalid currency");
        assertTrue(result.errors().toString().contains("currency"), "Error should mention currency");
    }

    @Test
    void testNegativeAmount() {
        SettlementIngestionCommand command = new SettlementIngestionCommand(
                "SETT-12345",
                1735689600000L,
                "PTS-A",
                "PE-001",
                "CP-ABC",
                "2025-12-31",
                "EUR",
                new BigDecimal("-1000.00"),
                "VERIFIED",
                "PAY",
                "GROSS"
        );

        ValidationResult result = validator.validate(command);
        assertFalse(result.isValid(), "Should fail with negative amount");
    }

    @Test
    void testInvalidDate() {
        SettlementIngestionCommand command = new SettlementIngestionCommand(
                "SETT-12345",
                1735689600000L,
                "PTS-A",
                "PE-001",
                "CP-ABC",
                "2025-13-45",
                "EUR",
                new BigDecimal("1000000.00"),
                "VERIFIED",
                "PAY",
                "GROSS"
        );

        ValidationResult result = validator.validate(command);
        assertFalse(result.isValid(), "Should fail with invalid date format");
    }

    @Test
    void testInvalidDirection() {
        SettlementIngestionCommand command = new SettlementIngestionCommand(
                "SETT-12345",
                1735689600000L,
                "PTS-A",
                "PE-001",
                "CP-ABC",
                "2025-12-31",
                "EUR",
                new BigDecimal("1000000.00"),
                "VERIFIED",
                "INVALID",
                "GROSS"
        );

        ValidationResult result = validator.validate(command);
        assertFalse(result.isValid(), "Should fail with invalid direction");
    }

    @Test
    void testInvalidBusinessStatus() {
        SettlementIngestionCommand command = new SettlementIngestionCommand(
                "SETT-12345",
                1735689600000L,
                "PTS-A",
                "PE-001",
                "CP-ABC",
                "2025-12-31",
                "EUR",
                new BigDecimal("1000000.00"),
                "INVALID_STATUS",
                "PAY",
                "GROSS"
        );

        ValidationResult result = validator.validate(command);
        assertFalse(result.isValid(), "Should fail with invalid business status");
    }

    @Test
    void testInvalidSettlementType() {
        SettlementIngestionCommand command = new SettlementIngestionCommand(
                "SETT-12345",
                1735689600000L,
                "PTS-A",
                "PE-001",
                "CP-ABC",
                "2025-12-31",
                "EUR",
                new BigDecimal("1000000.00"),
                "VERIFIED",
                "PAY",
                "INVALID_TYPE"
        );

        ValidationResult result = validator.validate(command);
        assertFalse(result.isValid(), "Should fail with invalid settlement type");
    }

    @Test
    void testPastDate() {
        SettlementIngestionCommand command = new SettlementIngestionCommand(
                "SETT-12345",
                1735689600000L,
                "PTS-A",
                "PE-001",
                "CP-ABC",
                "2000-01-01",
                "EUR",
                new BigDecimal("1000000.00"),
                "VERIFIED",
                "PAY",
                "GROSS"
        );

        ValidationResult result = validator.validate(command);
        assertTrue(result.isValid(), "Past dates are acceptable for settlements");
    }

    @Test
    void testExcessiveAmount() {
        SettlementIngestionCommand command = new SettlementIngestionCommand(
                "SETT-12345",
                1735689600000L,
                "PTS-A",
                "PE-001",
                "CP-ABC",
                "2025-12-31",
                "EUR",
                new BigDecimal("9999999999999.99"),
                "VERIFIED",
                "PAY",
                "GROSS"
        );

        ValidationResult result = validator.validate(command);
        assertFalse(result.isValid(), "Should fail with excessive amount");
    }

    @Test
    void testInvalidVersion() {
        SettlementIngestionCommand command = new SettlementIngestionCommand(
                "SETT-12345",
                -1L,
                "PTS-A",
                "PE-001",
                "CP-ABC",
                "2025-12-31",
                "EUR",
                new BigDecimal("1000000.00"),
                "VERIFIED",
                "PAY",
                "GROSS"
        );

        ValidationResult result = validator.validate(command);
        assertFalse(result.isValid(), "Should fail with invalid version");
    }
}
