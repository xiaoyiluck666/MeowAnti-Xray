package com.meowantixray.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import org.junit.jupiter.api.Test;

class PlatformSupportTest {
    @Test
    void sortedLoadedModsOrdersIdsCaseInsensitivelyAndCopiesResult() {
        List<LoadedModInfo> sorted = PlatformSupport.sortedLoadedMods(List.of(
            new LoadedModInfo("zeta", "1.0.0", null, true),
            new LoadedModInfo("Alpha", "1.0.0", null, true),
            new LoadedModInfo("beta", "1.0.0", null, true)
        ));

        assertEquals(List.of("Alpha", "beta", "zeta"), sorted.stream().map(LoadedModInfo::id).toList());
    }

    @Test
    void hasUsablePermissionRequestRejectsMissingInputs() {
        assertFalse(PlatformSupport.hasUsablePermissionRequest(null, "paper.antixray.bypass"));
        assertFalse(PlatformSupport.hasUsablePermissionRequest(null, ""));
        assertFalse(PlatformSupport.hasUsablePermissionRequest(null, "   "));
    }
}
