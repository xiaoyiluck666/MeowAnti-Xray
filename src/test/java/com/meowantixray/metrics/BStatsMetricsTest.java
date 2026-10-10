package com.meowantixray.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class BStatsMetricsTest {
    @Test
    void invalidServiceIdsDisableSubmission() throws Exception {
        Method parse = BStatsMetrics.class.getDeclaredMethod("parseServiceId", String.class);
        parse.setAccessible(true);
        assertEquals(0, parse.invoke(null, "0"));
        assertEquals(0, parse.invoke(null, "not-a-number"));
        assertEquals(123, parse.invoke(null, "123"));
    }
}
