package com.meowantixray.antixray;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FakeOreConfigTest {
    @Test
    void loadOrCreatePreservesUserValuesAndDoesNotRewriteFile(@TempDir Path tempDir) throws Exception {
        Path path = tempDir.resolve("meowantixray.yml");
        String original = """
            # custom comment
            anti-xray:
              enabled: false
              engine-mode: 1
              enforce-engine-mode-2: false
              max-block-height: 128
              only-obfuscate-hidden-blocks: true
              mode2-obfuscate-replacement-blocks: false
              guarantee-hide-all-hidden-blocks: false
              lava-obscures: true
              use-permission: true
              bypass-permission: custom.antixray.bypass
              async-chunk-rewrite: false
              async-worker-threads: 3
              async-queue-size: 12
              update-radius: 4
              update-interval-ticks: 20
              max-blocks-per-chunk: 4096
              hidden-blocks:
                - minecraft:diamond_ore
              replacement-blocks:
                - minecraft:stone
              dimension-settings:
                minecraft:the_nether:
                  enabled: false
                  engine-mode: 1
                  max-block-height: 96
                  only-obfuscate-hidden-blocks: true
                  mode2-obfuscate-replacement-blocks: false
                  guarantee-hide-all-hidden-blocks: false
                  lava-obscures: true
                  use-permission: true
                  bypass-permission: custom.nether.bypass
                  hidden-blocks:
                    - minecraft:ancient_debris
                  replacement-blocks:
                    - minecraft:netherrack
            """;
        Files.writeString(path, original, StandardCharsets.UTF_8);

        FakeOreConfig config = FakeOreConfig.loadOrCreate(path);

        assertFalse(config.enabled);
        assertEquals(1, config.engineMode);
        assertFalse(config.enforceEngineMode2);
        assertEquals(128, config.maxBlockHeight);
        assertTrue(config.onlyObfuscateHiddenBlocks);
        assertFalse(config.mode2ObfuscateReplacementBlocks);
        assertFalse(config.guaranteeHideAllHiddenBlocks);
        assertTrue(config.lavaObscures);
        assertTrue(config.usePermission);
        assertEquals("custom.antixray.bypass", config.bypassPermission);
        assertFalse(config.asyncChunkRewrite);
        assertEquals(3, config.asyncWorkerThreads);
        assertEquals(12, config.asyncQueueSize);
        assertEquals(2, config.updateRadius);
        assertEquals(20, config.updateIntervalTicks);
        assertEquals(4096, config.maxBlocksPerChunk);
        assertEquals(List.of("minecraft:diamond_ore"), config.hiddenBlocks);
        assertEquals(List.of("minecraft:stone"), config.replacementBlocks);

        FakeOreConfig.DimensionSettings nether = config.dimensionSettings.get("minecraft:the_nether");
        assertNotNull(nether);
        assertFalse(nether.enabled);
        assertEquals(1, nether.engineMode);
        assertEquals(96, nether.maxBlockHeight);
        assertTrue(nether.onlyObfuscateHiddenBlocks);
        assertFalse(nether.mode2ObfuscateReplacementBlocks);
        assertFalse(nether.guaranteeHideAllHiddenBlocks);
        assertTrue(nether.lavaObscures);
        assertTrue(nether.usePermission);
        assertEquals("custom.nether.bypass", nether.bypassPermission);
        assertEquals(List.of("minecraft:ancient_debris"), nether.hiddenBlocks);
        assertEquals(List.of("minecraft:netherrack"), nether.replacementBlocks);

        assertEquals(original, Files.readString(path, StandardCharsets.UTF_8));
    }

    @Test
    void loadOrCreateSupplementsMissingTopLevelKeysWithoutOverwritingExistingContent(@TempDir Path tempDir) throws Exception {
        Path path = tempDir.resolve("meowantixray.yml");
        String original = """
            # keep this comment
            anti-xray:
              enabled: false
              max-block-height: 100
              hidden-blocks:
                - minecraft:diamond_ore
            """;
        Files.writeString(path, original, StandardCharsets.UTF_8);

        FakeOreConfig config = FakeOreConfig.loadOrCreate(path);
        String supplemented = Files.readString(path, StandardCharsets.UTF_8);

        assertFalse(config.enabled);
        assertEquals(96, config.maxBlockHeight);
        assertTrue(config.loadSummary().contains("supplemented missing keys"));
        assertTrue(supplemented.contains("# keep this comment"));
        assertTrue(supplemented.contains("  enabled: false"));
        assertTrue(supplemented.contains("  engine-mode: 2"));
        assertTrue(supplemented.contains("  lava-obscures: false"));
        assertTrue(supplemented.contains("  use-permission: false"));
        assertTrue(supplemented.contains("  bypass-permission: paper.antixray.bypass"));
        assertTrue(supplemented.contains("  async-chunk-rewrite: true"));
        assertTrue(supplemented.contains("  async-worker-threads: 1"));
        assertTrue(supplemented.contains("  async-queue-size: 16"));
        assertTrue(supplemented.contains("  replacement-blocks:\n    - minecraft:stone"));
        assertTrue(supplemented.contains("  dimension-settings:\n    minecraft:overworld:"));

        assertEquals(supplemented, Files.readString(path, StandardCharsets.UTF_8));

        FakeOreConfig.loadOrCreate(path);
        assertEquals(supplemented, Files.readString(path, StandardCharsets.UTF_8));
    }

    @Test
    void loadOrCreateSupplementsExistingDimensionBlockWithoutAddingOtherDimensions(@TempDir Path tempDir) throws Exception {
        Path path = tempDir.resolve("meowantixray.yml");
        String original = """
            anti-xray:
              enabled: false
              hidden-blocks:
                - minecraft:diamond_ore
              replacement-blocks:
                - minecraft:stone
              dimension-settings:
                minecraft:the_nether:
                  enabled: false
            """;
        Files.writeString(path, original, StandardCharsets.UTF_8);

        FakeOreConfig config = FakeOreConfig.loadOrCreate(path);
        String supplemented = Files.readString(path, StandardCharsets.UTF_8);

        FakeOreConfig.DimensionSettings nether = config.dimensionSettings.get("minecraft:the_nether");
        assertNotNull(nether);
        assertFalse(nether.enabled);
        assertEquals(2, nether.engineMode);
        assertTrue(supplemented.contains("    minecraft:the_nether:\n      enabled: false\n      engine-mode: 2"));
        assertTrue(supplemented.contains("      lava-obscures: false"));
        assertTrue(supplemented.contains("      use-permission: false"));
        assertTrue(supplemented.contains("      bypass-permission: paper.antixray.bypass"));
        assertTrue(supplemented.contains("      replacement-blocks:\n        - minecraft:netherrack"));
        assertFalse(supplemented.contains("    minecraft:overworld:"));
        assertFalse(supplemented.contains("    minecraft:the_end:"));

        FakeOreConfig.loadOrCreate(path);
        assertEquals(supplemented, Files.readString(path, StandardCharsets.UTF_8));
    }

    @Test
    void normalizesPaperStyleScalarSemantics() {
        assertEquals(96, FakeOreConfig.normalizeMaxBlockHeight(100));
        assertEquals(128, FakeOreConfig.normalizeMaxBlockHeight(128));
        assertEquals(-16, FakeOreConfig.normalizeMaxBlockHeight(-1));
        assertEquals(0, FakeOreConfig.normalizeUpdateRadius(-5));
        assertEquals(2, FakeOreConfig.normalizeUpdateRadius(9));
        assertEquals(1, FakeOreConfig.normalizeUpdateRadius(1));
    }

    @Test
    void generatedDefaultsIncludePaperStyleBlocksProjectAdditionsAndPermissionNode(@TempDir Path tempDir) throws Exception {
        Path path = tempDir.resolve("meowantixray.yml");

        FakeOreConfig config = FakeOreConfig.loadOrCreate(path);
        String generated = Files.readString(path, StandardCharsets.UTF_8);

        assertEquals("paper.antixray.bypass", config.bypassPermission);
        assertTrue(generated.contains("  bypass-permission: paper.antixray.bypass"));
        assertTrue(generated.contains("    - minecraft:raw_copper_block"));
        assertTrue(generated.contains("    - minecraft:raw_iron_block"));
        assertTrue(generated.contains("    - minecraft:mossy_cobblestone"));
        assertTrue(generated.contains("    - minecraft:obsidian"));
        assertTrue(generated.contains("    - minecraft:chest"));
        assertTrue(generated.contains("    - minecraft:ender_chest"));
        assertTrue(generated.contains("    - minecraft:clay"));
        assertTrue(generated.contains("    - minecraft:oak_planks"));
    }

    @Test
    void loadOrCreateAcceptsEngineModeThree(@TempDir Path tempDir) throws Exception {
        Path path = tempDir.resolve("meowantixray.yml");
        String original = """
            anti-xray:
              enabled: true
              engine-mode: 3
              enforce-engine-mode-2: false
              hidden-blocks:
                - minecraft:diamond_ore
              replacement-blocks:
                - minecraft:stone
              dimension-settings:
                minecraft:the_nether:
                  enabled: true
                  engine-mode: 3
                  hidden-blocks:
                    - minecraft:ancient_debris
                  replacement-blocks:
                    - minecraft:netherrack
            """;
        Files.writeString(path, original, StandardCharsets.UTF_8);

        FakeOreConfig config = FakeOreConfig.loadOrCreate(path);

        assertEquals(3, config.engineMode);
        assertFalse(config.enforceEngineMode2);
        assertEquals(3, config.dimensionSettings.get("minecraft:the_nether").engineMode);
    }

    @Test
    void loadOrCreateReadsAsyncChunkRewriteSettings(@TempDir Path tempDir) throws Exception {
        Path path = tempDir.resolve("meowantixray.yml");
        String original = """
            anti-xray:
              enabled: true
              async-chunk-rewrite: false
              async-worker-threads: 4
              async-queue-size: 0
              hidden-blocks:
                - minecraft:diamond_ore
              replacement-blocks:
                - minecraft:stone
            """;
        Files.writeString(path, original, StandardCharsets.UTF_8);

        FakeOreConfig config = FakeOreConfig.loadOrCreate(path);

        assertFalse(config.asyncChunkRewrite);
        assertEquals(4, config.asyncWorkerThreads);
        assertEquals(0, config.asyncQueueSize);
    }
}

