package com.meowantixray.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ModrinthUpdateCheckerTest {
    @Test
    void compareVersionsIgnoresLoaderBuildMetadata() {
        assertEquals(0, ModrinthUpdateChecker.compareVersions("1.0.0+fabric", "1.0.0"));
        assertEquals(0, ModrinthUpdateChecker.compareVersions("1.0.0+neoforge", "1.0.0"));
    }

    @Test
    void compareVersionsStillDetectsNewerBaseVersion() {
        assertTrue(ModrinthUpdateChecker.compareVersions("1.0.1+fabric", "1.0.0") > 0);
        assertTrue(ModrinthUpdateChecker.compareVersions("1.0.0", "1.0.1+neoforge") < 0);
    }
}
