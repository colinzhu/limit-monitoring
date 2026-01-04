package com.tvpc.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for enums
 */
class EnumTest {

    @Test
    void testSettlementDirection() {
        assertEquals(SettlementDirection.PAY, SettlementDirection.fromValue("PAY"));
        assertEquals(SettlementDirection.RECEIVE, SettlementDirection.fromValue("RECEIVE"));
        assertEquals(SettlementDirection.PAY, SettlementDirection.fromValue("pay"));
        assertEquals(SettlementDirection.RECEIVE, SettlementDirection.fromValue("receive"));

        assertTrue(SettlementDirection.isValid("PAY"));
        assertTrue(SettlementDirection.isValid("RECEIVE"));
        assertFalse(SettlementDirection.isValid("INVALID"));

        assertEquals("PAY", SettlementDirection.PAY.getValue());
        assertEquals("RECEIVE", SettlementDirection.RECEIVE.getValue());

        assertThrows(IllegalArgumentException.class, () -> SettlementDirection.fromValue("INVALID"));
    }

    @Test
    void testSettlementType() {
        assertEquals(SettlementType.GROSS, SettlementType.fromValue("GROSS"));
        assertEquals(SettlementType.NET, SettlementType.fromValue("NET"));
        assertEquals(SettlementType.GROSS, SettlementType.fromValue("gross"));
        assertEquals(SettlementType.NET, SettlementType.fromValue("net"));

        assertTrue(SettlementType.isValid("GROSS"));
        assertTrue(SettlementType.isValid("NET"));
        assertFalse(SettlementType.isValid("INVALID"));

        assertEquals("GROSS", SettlementType.GROSS.getValue());
        assertEquals("NET", SettlementType.NET.getValue());

        assertThrows(IllegalArgumentException.class, () -> SettlementType.fromValue("INVALID"));
    }

    @Test
    void testBusinessStatus() {
        assertEquals(BusinessStatus.PENDING, BusinessStatus.fromValue("PENDING"));
        assertEquals(BusinessStatus.INVALID, BusinessStatus.fromValue("INVALID"));
        assertEquals(BusinessStatus.VERIFIED, BusinessStatus.fromValue("VERIFIED"));
        assertEquals(BusinessStatus.CANCELLED, BusinessStatus.fromValue("CANCELLED"));
        assertEquals(BusinessStatus.PENDING, BusinessStatus.fromValue("pending"));
        assertEquals(BusinessStatus.VERIFIED, BusinessStatus.fromValue("verified"));

        assertTrue(BusinessStatus.isValid("PENDING"));
        assertTrue(BusinessStatus.isValid("INVALID"));
        assertTrue(BusinessStatus.isValid("VERIFIED"));
        assertTrue(BusinessStatus.isValid("CANCELLED"));
        assertFalse(BusinessStatus.isValid("UNKNOWN"));

        assertEquals("PENDING", BusinessStatus.PENDING.getValue());
        assertEquals("VERIFIED", BusinessStatus.VERIFIED.getValue());

        assertThrows(IllegalArgumentException.class, () -> BusinessStatus.fromValue("UNKNOWN"));
    }
}
