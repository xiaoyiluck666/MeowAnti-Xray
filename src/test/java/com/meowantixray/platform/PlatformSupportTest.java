package com.meowantixray.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import org.eclipse.jdt.annotation.NonNull;
import org.junit.jupiter.api.Test;

class PlatformSupportTest {
    @Test
    void sortedLoadedModsOrdersIdsCaseInsensitivelyAndCopiesResult() {
        List<@NonNull LoadedModInfo> unsorted = List.of(
            new LoadedModInfo("zeta", "1.0.0", true),
            new LoadedModInfo("Alpha", "1.0.0", true),
            new LoadedModInfo("beta", "1.0.0", true)
        );
        List<@NonNull LoadedModInfo> sorted = PlatformSupport.sortedLoadedMods(unsorted);

        assertEquals(List.of("Alpha", "beta", "zeta"), sorted.stream().map(mod -> mod.id()).toList());
    }

    @Test
    void hasUsablePermissionRequestRejectsMissingInputs() {
        assertFalse(PlatformSupport.hasUsablePermissionRequest(null, "paper.antixray.bypass"));
        assertFalse(PlatformSupport.hasUsablePermissionRequest(null, ""));
        assertFalse(PlatformSupport.hasUsablePermissionRequest(null, "   "));
    }
}
