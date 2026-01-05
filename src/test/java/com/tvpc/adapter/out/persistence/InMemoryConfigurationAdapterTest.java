package com.tvpc.adapter.out.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryConfigurationAdapterTest {

    private InMemoryConfigurationAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new InMemoryConfigurationAdapter();
    }

    @Test
    void testGetExposureLimit_returnsMvpLimit() {
        var limit = adapter.getExposureLimit("CP-ABC");

        assertNotNull(limit);
        assertEquals("500000000.00", limit.toString());
    }

    @Test
    void testIsMvpMode_returnsTrue() {
        assertTrue(adapter.isMvpMode());
    }
}
