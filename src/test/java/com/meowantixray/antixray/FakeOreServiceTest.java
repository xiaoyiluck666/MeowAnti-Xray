package com.meowantixray.antixray;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.Strategy;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FakeOreServiceTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void formatConfigSummaryShowsPerDimensionEffectiveMaxHeight() {
        Map<String, FakeOreService.DimensionSummary> dimensions = new LinkedHashMap<>();
        dimensions.put("minecraft:overworld", new FakeOreService.DimensionSummary(true, 2, 64));
        dimensions.put("minecraft:the_nether", new FakeOreService.DimensionSummary(true, 2, 40));
        dimensions.put("minecraft:the_end", new FakeOreService.DimensionSummary(true, 2, 64));

        String summary = FakeOreService.formatConfigSummary(
            2,
            64,
            true,
            true,
            true,
            2,
            64,
            66,
            dimensions,
            19,
            6
        );

        assertTrue(summary.contains("default-max-height=64"));
        assertTrue(summary.contains("async-chunk-rewrite=true"));
        assertTrue(summary.contains("async-worker-threads=2"));
        assertTrue(summary.contains("async-queue-size=64"));
        assertTrue(summary.contains("async-capacity=66"));
        assertTrue(summary.contains("minecraft:the_nether(enabled=true,mode=2,max=40)"));
        assertTrue(summary.contains("minecraft:overworld(enabled=true,mode=2,max=64)"));
    }

    @Test
    void globalDisableWinsOverDimensionEnable() {
        assertFalse(FakeOreService.effectiveEnabled(false, true));
        assertFalse(FakeOreService.effectiveEnabled(false, false));
        assertTrue(FakeOreService.effectiveEnabled(true, true));
    }

    @Test
    void usePermissionBypassesOnlyForAdminLikePlayers() {
        assertFalse(FakeOreService.shouldBypassWithPermission(false, false, false));
        assertFalse(FakeOreService.shouldBypassWithPermission(false, true, true));
        assertFalse(FakeOreService.shouldBypassWithPermission(true, false, false));
        assertTrue(FakeOreService.shouldBypassWithPermission(true, true, false));
        assertTrue(FakeOreService.shouldBypassWithPermission(true, false, true));
    }

    @Test
    void profileSummaryIncludesDurationMetrics() {
        String summary = new FakeOreService().profileSummary();

        assertTrue(summary.contains("rewriteTasks="));
        assertTrue(summary.contains("rewriteAvgMs="));
        assertTrue(summary.contains("rewriteMaxMs="));
        assertTrue(summary.contains("revealTasks="));
        assertTrue(summary.contains("revealAvgMs="));
        assertTrue(summary.contains("revealMaxMs="));
        assertTrue(summary.contains("syncChunkSends="));
        assertTrue(summary.contains("async{enabled="));
        assertTrue(summary.contains("availablePermits="));
        assertTrue(summary.contains("pressure="));
        assertTrue(summary.contains("syncFallbackRatio="));
        assertTrue(summary.contains("syncChunkSends/s="));
    }

    @Test
    void statusDetailsExposeKeyRuntimeFields() throws Exception {
        FakeOreService service = new FakeOreService();
        setFakeConfigPath(createTempConfig("anti-xray:\n  hidden-blocks:\n    - minecraft:diamond_ore\n  replacement-blocks:\n    - minecraft:stone\n"));
        setField(service, "globalPalette", createPalette(true, 2, 64, 3, 2, true, 2));
        @SuppressWarnings("unchecked")
        Map<String, Object> dimensionPalettes = (Map<String, Object>) getField(service, "dimensionPalettes");
        dimensionPalettes.put("minecraft:overworld", createPalette(true, 2, 64, 3, 2, true, 2));
        dimensionPalettes.put("minecraft:the_nether", createPalette(false, 1, 32, 1, 1, false, 2));
        setConfig(service, createConfig(true, 2, 8));

        List<String> lines = service.statusDetails();

        assertTrue(lines.stream().anyMatch(line -> line.contains("status: enabled=true, mode=2, max-block-height=64")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("async: enabled=true, workerThreads=2, queueSize=8, capacity=10")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("dimension minecraft:overworld: enabled=true, mode=2, max-block-height=64, hidden=3, replacement=2")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("dimension minecraft:the_nether: enabled=false, mode=1, max-block-height=32, hidden=1, replacement=1")));
    }

    @Test
    void reloadReportListsChangedRuntimeFields() throws Exception {
        FakeOreService service = new FakeOreService();
        setField(service, "globalPalette", createPalette(true, 2, 64, 3, 2, true, 2));
        @SuppressWarnings("unchecked")
        Map<String, Object> dimensionPalettes = (Map<String, Object>) getField(service, "dimensionPalettes");
        dimensionPalettes.put("minecraft:overworld", createPalette(true, 2, 64, 3, 2, true, 2));
        setConfig(service, createConfig(true, 2, 8));

        setFakeConfigPath(createTempConfig("""
            anti-xray:
              enabled: false
              engine-mode: 1
              enforce-engine-mode-2: false
              async-worker-threads: 3
              async-queue-size: 1
              hidden-blocks:
                - minecraft:diamond_ore
              replacement-blocks:
                - minecraft:stone
              dimension-settings:
                minecraft:overworld:
                  enabled: false
                  engine-mode: 1
                  hidden-blocks:
                    - minecraft:diamond_ore
                  replacement-blocks:
                    - minecraft:stone
            """));

        FakeOreService.ReloadReport report = service.reloadConfigWithSummary();

        assertTrue(report.statusLines().stream().anyMatch(line -> line.contains("status: enabled=false, mode=1")));
        assertTrue(report.changeLines().stream().anyMatch(line -> line.contains("enabled: true -> false")));
        assertTrue(report.changeLines().stream().anyMatch(line -> line.contains("engine-mode: 2 -> 1")));
        assertTrue(report.changeLines().stream().anyMatch(line -> line.contains("async-worker-threads: 2 -> 3")));
        assertTrue(report.changeLines().stream().anyMatch(line -> line.contains("dimension minecraft:overworld enabled: true -> false")));
    }

    @Test
    void inspectDecisionReasonExplainsCommonCases() {
        assertEquals("disabled", FakeOreService.inspectDecisionReason(false, true, true, false, false));
        assertEquals("out-of-range", FakeOreService.inspectDecisionReason(true, false, true, false, false));
        assertEquals("not-targeted", FakeOreService.inspectDecisionReason(true, true, false, false, false));
        assertEquals("exposed", FakeOreService.inspectDecisionReason(true, true, true, false, true));
        assertEquals("hidden-block-obfuscated", FakeOreService.inspectDecisionReason(true, true, true, false, false));
        assertEquals("replacement-block-obfuscated", FakeOreService.inspectDecisionReason(true, true, false, true, false));
    }

    @Test
    void normalizeDimensionKeyStripsResourceKeyWrapper() throws Exception {
        FakeOreService service = new FakeOreService();
        Method method = FakeOreService.class.getDeclaredMethod("normalizeDimensionKey", String.class);
        method.setAccessible(true);

        assertEquals("minecraft:overworld", method.invoke(service, "ResourceKey[minecraft:dimension / minecraft:overworld]"));
        assertEquals("minecraft:the_nether", method.invoke(service, "ResourceKey[minecraft:dimension / minecraft:the_nether]"));
    }

    @Test
    void asyncPressureSummarizesBackpressureState() {
        assertEquals("disabled", FakeOreService.asyncPressure(false, 0, 17, 0));
        assertEquals("normal", FakeOreService.asyncPressure(true, 2, 17, 0));
        assertEquals("busy", FakeOreService.asyncPressure(true, 14, 17, 0));
        assertEquals("saturated", FakeOreService.asyncPressure(true, 0, 17, 1));
    }

    @Test
    void stateLookupMarksAllStatesOfConfiguredBlocks() {
        Set<net.minecraft.world.level.block.Block> blocks = new LinkedHashSet<>();
        blocks.add(Blocks.DIAMOND_ORE);
        boolean[] lookup = FakeOreService.buildStateLookup(blocks);

        int oreId = net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY.getId(Blocks.DIAMOND_ORE.defaultBlockState());
        int stoneId = net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY.getId(Blocks.STONE.defaultBlockState());

        assertTrue(lookup[oreId]);
        assertFalse(lookup[stoneId]);
    }

    @Test
    void paperNeighborUpdatePositionsMatchPaperRadiusShapes() {
        BlockPos center = new BlockPos(10, 20, 30);

        assertEquals(Set.of(
            new BlockPos(9, 20, 30),
            new BlockPos(11, 20, 30),
            new BlockPos(10, 19, 30),
            new BlockPos(10, 21, 30),
            new BlockPos(10, 20, 29),
            new BlockPos(10, 20, 31)
        ), Set.copyOf(FakeOreService.paperNeighborUpdatePositions(center, 1)));

        assertEquals(Set.of(
            new BlockPos(9, 20, 30),
            new BlockPos(8, 20, 30),
            new BlockPos(9, 19, 30),
            new BlockPos(9, 21, 30),
            new BlockPos(9, 20, 29),
            new BlockPos(9, 20, 31),
            new BlockPos(11, 20, 30),
            new BlockPos(12, 20, 30),
            new BlockPos(11, 19, 30),
            new BlockPos(11, 21, 30),
            new BlockPos(11, 20, 29),
            new BlockPos(11, 20, 31),
            new BlockPos(10, 19, 30),
            new BlockPos(10, 18, 30),
            new BlockPos(10, 19, 29),
            new BlockPos(10, 19, 31),
            new BlockPos(10, 21, 30),
            new BlockPos(10, 22, 30),
            new BlockPos(10, 21, 29),
            new BlockPos(10, 21, 31),
            new BlockPos(10, 20, 29),
            new BlockPos(10, 20, 28),
            new BlockPos(10, 20, 31),
            new BlockPos(10, 20, 32)
        ), Set.copyOf(FakeOreService.paperNeighborUpdatePositions(center, 2)));
    }

    @Test
    void modeTwoDecoysExcludeEntityBlocksLikePaper() {
        List<String> decoys = FakeOreService.debugMode2DecoyBlockIdsForTest(
            List.of("minecraft:chest", "minecraft:diamond_ore"),
            List.of("minecraft:stone")
        );

        assertFalse(decoys.contains("minecraft:chest"));
        assertTrue(decoys.contains("minecraft:diamond_ore"));
    }

    @Test
    void engineModeOneNaturalReplacementMatchesPaperDimensionAndHeightRules() {
        assertEquals(
            Blocks.STONE.defaultBlockState(),
            FakeOreService.debugNaturalReplacementForTest("OVERWORLD", 4, Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState())
        );
        assertEquals(
            Blocks.DEEPSLATE.defaultBlockState(),
            FakeOreService.debugNaturalReplacementForTest("OVERWORLD", -1, Blocks.DIAMOND_ORE.defaultBlockState())
        );
        assertEquals(
            Blocks.NETHERRACK.defaultBlockState(),
            FakeOreService.debugNaturalReplacementForTest("NETHER", 32, Blocks.ANCIENT_DEBRIS.defaultBlockState())
        );
        assertEquals(
            Blocks.END_STONE.defaultBlockState(),
            FakeOreService.debugNaturalReplacementForTest("END", 32, Blocks.DIAMOND_ORE.defaultBlockState())
        );
    }

    @Test
    void solidStateLookupRespectsLavaObscures() {
        boolean[] withoutLava = FakeOreService.buildSolidStateLookup(false);
        boolean[] withLava = FakeOreService.buildSolidStateLookup(true);
        int lavaId = net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY.getId(Blocks.LAVA.defaultBlockState());
        int stoneId = net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY.getId(Blocks.STONE.defaultBlockState());

        assertFalse(withoutLava[lavaId]);
        assertTrue(withLava[lavaId]);
        assertEquals(withoutLava[stoneId], withLava[stoneId]);
        assertTrue(withLava[stoneId]);
    }

    @Test
    void shouldMaskStateMatchesHiddenAndReplacementPassSemantics() {
        assertTrue(FakeOreService.shouldMaskState(true, true, true, false));
        assertFalse(FakeOreService.shouldMaskState(true, true, false, true));
        assertTrue(FakeOreService.shouldMaskState(false, true, false, true));
        assertFalse(FakeOreService.shouldMaskState(false, false, true, false));
    }

    @Test
    void updateTargetsMatchActualObfuscationTargetsByEngineMode() {
        assertTrue(FakeOreService.debugTargetStateForTest(1, true, Blocks.DIAMOND_ORE.defaultBlockState()));
        assertFalse(FakeOreService.debugTargetStateForTest(1, true, Blocks.STONE.defaultBlockState()));

        assertTrue(FakeOreService.debugTargetStateForTest(2, true, Blocks.DIAMOND_ORE.defaultBlockState()));
        assertTrue(FakeOreService.debugTargetStateForTest(2, true, Blocks.STONE.defaultBlockState()));
        assertTrue(FakeOreService.debugTargetStateForTest(2, false, Blocks.DIAMOND_ORE.defaultBlockState()));
        assertFalse(FakeOreService.debugTargetStateForTest(2, false, Blocks.STONE.defaultBlockState()));

        assertTrue(FakeOreService.debugTargetStateForTest(3, false, Blocks.DIAMOND_ORE.defaultBlockState()));
        assertTrue(FakeOreService.debugTargetStateForTest(3, false, Blocks.STONE.defaultBlockState()));
    }

    @Test
    void shouldRevealStateMatchesHiddenAndExposureSemantics() {
        assertTrue(FakeOreService.shouldRevealState(false, false));
        assertTrue(FakeOreService.shouldRevealState(true, true));
        assertFalse(FakeOreService.shouldRevealState(true, false));
    }

    @Test
    void caveAdjacentHiddenOreStaysExposedInsteadOfBackfilled() {
        BlockState[] states = filledSection(Blocks.STONE.defaultBlockState());
        states[blockIndex(8, 8, 8)] = Blocks.DIAMOND_ORE.defaultBlockState();
        for (int lx = 9; lx < 16; lx++) {
            states[blockIndex(lx, 8, 8)] = Blocks.AIR.defaultBlockState();
        }

        assertTrue(FakeOreService.debugSyntheticSectionExposedForTest(states, 8, 8, 8, false));
    }

    @Test
    void fullyEnclosedHiddenOreIsNotExposedAndCanBeBackfilled() {
        BlockState[] states = filledSection(Blocks.STONE.defaultBlockState());
        states[blockIndex(8, 8, 8)] = Blocks.DIAMOND_ORE.defaultBlockState();

        assertFalse(FakeOreService.debugSyntheticSectionExposedForTest(states, 8, 8, 8, false));
    }

    @Test
    void liveLocalPaletteReadUsesPaletteSizeInsteadOfBlockCount() {
        PalettedContainer<BlockState> states = new PalettedContainer<>(
            Blocks.STONE.defaultBlockState(),
            Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY)
        );
        LevelChunkSection section = new LevelChunkSection(states, null);
        BlockState[] paletteStates = {
            Blocks.STONE.defaultBlockState(),
            Blocks.DIRT.defaultBlockState(),
            Blocks.GRANITE.defaultBlockState(),
            Blocks.DIORITE.defaultBlockState(),
            Blocks.ANDESITE.defaultBlockState(),
            Blocks.TUFF.defaultBlockState(),
            Blocks.COPPER_ORE.defaultBlockState(),
            Blocks.COAL_ORE.defaultBlockState(),
            Blocks.REDSTONE_ORE.defaultBlockState(),
            Blocks.DIAMOND_ORE.defaultBlockState()
        };
        for (int i = 0; i < paletteStates.length; i++) {
            section.setBlockState(i, 0, 0, paletteStates[i], false);
        }

        assertTrue(FakeOreService.debugReadSectionPaletteHasTargetForTest(
            section,
            List.of("minecraft:diamond_ore"),
            List.of("minecraft:stone")
        ));
    }

    @Test
    void exposedBlocksAreNotMasked() {
        assertTrue(FakeOreService.shouldMaskState(true, false, true, false));
        assertTrue(FakeOreService.shouldMaskState(false, true, false, true));
    }

    @Test
    void deepNegativeSectionStillFallsWithinConfiguredRange() {
        assertFalse(FakeOreService.isSectionOutsideVerticalRange(-64, -49, -64, 64));
        assertFalse(FakeOreService.isSectionOutsideVerticalRange(-48, -33, -64, 64));
        assertTrue(FakeOreService.isSectionOutsideVerticalRange(80, 95, -64, 64));
    }

    @Test
    void addRevealPositionDeduplicatesWithinSameSection() throws Exception {
        FakeOreService service = new FakeOreService();
        Long2ObjectOpenHashMap<ShortOpenHashSet> revealBySection = new Long2ObjectOpenHashMap<>();
        Method addRevealPosition = FakeOreService.class.getDeclaredMethod(
            "addRevealPosition",
            Long2ObjectOpenHashMap.class,
            long.class,
            short.class
        );
        addRevealPosition.setAccessible(true);

        long sectionLong = 123L;
        short rel = 7;
        boolean first = (boolean) addRevealPosition.invoke(service, revealBySection, sectionLong, rel);
        boolean second = (boolean) addRevealPosition.invoke(service, revealBySection, sectionLong, rel);

        assertTrue(first);
        assertFalse(second);
        assertNotNull(revealBySection.get(sectionLong));
        assertEquals(1, revealBySection.get(sectionLong).size());
        assertTrue(revealBySection.get(sectionLong).contains(rel));
    }

    @Test
    void engineMode3LayerIndexIsStablePerLayer() {
        int sameA = FakeOreService.engineMode3LayerIndex(12345L, 2, 4, 6, 7, 8);
        int sameB = FakeOreService.engineMode3LayerIndex(12345L, 2, 4, 6, 7, 8);
        int differentLayer = FakeOreService.engineMode3LayerIndex(12345L, 2, 4, 6, 8, 8);

        assertEquals(sameA, sameB);
        assertTrue(sameA >= 0 && sameA < 8);
        assertTrue(differentLayer >= 0 && differentLayer < 8);
    }

    @Test
    void engineMode2PositionIndexIsStablePerBlock() {
        int sameA = FakeOreService.engineMode2PositionIndex(12345L, 10, 20, 30, 8);
        int sameB = FakeOreService.engineMode2PositionIndex(12345L, 10, 20, 30, 8);
        int differentBlock = FakeOreService.engineMode2PositionIndex(12345L, 11, 20, 30, 8);

        assertEquals(sameA, sameB);
        assertTrue(sameA >= 0 && sameA < 8);
        assertTrue(differentBlock >= 0 && differentBlock < 8);
    }

    @Test
    void paperRuntimeRandomSeedNeverReturnsZero() {
        for (int i = 0; i < 256; i++) {
            assertTrue(FakeOreService.paperRuntimeRandomSeed() != 0);
        }
    }

    @Test
    void shutdownAsyncExecutorReleasesAndRecreatesRewriteExecutor() throws Exception {
        FakeOreService service = new FakeOreService();
        ExecutorService first = ensureChunkRewriteExecutor(service);

        assertTrue(first != null && !first.isShutdown());

        service.shutdownAsyncExecutor();
        assertTrue(first.isShutdown());
        assertNull(chunkRewriteExecutorField().get(service));

        ExecutorService second = ensureChunkRewriteExecutor(service);
        assertTrue(second != null && !second.isShutdown());
        assertNotSame(first, second);

        service.shutdownAsyncExecutor();
        assertTrue(second.isShutdown());
    }

    private static ExecutorService ensureChunkRewriteExecutor(FakeOreService service) throws Exception {
        Method method = FakeOreService.class.getDeclaredMethod("ensureChunkRewriteExecutor");
        method.setAccessible(true);
        return (ExecutorService) method.invoke(service);
    }

    private static Field chunkRewriteExecutorField() throws Exception {
        Field field = FakeOreService.class.getDeclaredField("chunkRewriteExecutor");
        field.setAccessible(true);
        return field;
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = FakeOreService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object getField(Object target, String fieldName) throws Exception {
        Field field = FakeOreService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void setConfig(FakeOreService service, FakeOreConfig config) throws Exception {
        setField(service, "config", config);
    }

    private static FakeOreConfig createConfig(boolean asyncChunkRewrite, int asyncWorkerThreads, int asyncQueueSize) {
        FakeOreConfig config = new FakeOreConfig();
        config.asyncChunkRewrite = asyncChunkRewrite;
        config.asyncWorkerThreads = asyncWorkerThreads;
        config.asyncQueueSize = asyncQueueSize;
        return config;
    }

    private static Object createPalette(
        boolean enabled,
        int engineMode,
        int maxBlockHeight,
        int hiddenCount,
        int replacementCount,
        boolean usePermission,
        int updateRadius
    ) throws Exception {
        Class<?> paletteClass = Class.forName("com.meowantixray.antixray.FakeOreService$Palette");
        var constructor = paletteClass.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        List<String> hidden = new java.util.ArrayList<>();
        for (int i = 0; i < hiddenCount; i++) {
            hidden.add("minecraft:hidden_" + i);
        }
        List<String> replacement = new java.util.ArrayList<>();
        for (int i = 0; i < replacementCount; i++) {
            replacement.add("minecraft:replacement_" + i);
        }
        return constructor.newInstance(
            enabled,
            engineMode,
            maxBlockHeight,
            false,
            true,
            true,
            false,
            usePermission,
            "paper.antixray.bypass",
            updateRadius,
            10,
            32768,
            hidden,
            replacement,
            Set.of(),
            Set.of(),
            List.of(),
            List.of(),
            new boolean[Block.BLOCK_STATE_REGISTRY.size()],
            new boolean[Block.BLOCK_STATE_REGISTRY.size()],
            new boolean[Block.BLOCK_STATE_REGISTRY.size()],
            new boolean[Block.BLOCK_STATE_REGISTRY.size()]
        );
    }

    private static Path createTempConfig(String content) throws Exception {
        Path dir = Files.createTempDirectory("meowantixray-test-config");
        Path path = dir.resolve("meowantixray.yml");
        Files.writeString(path, content);
        return path;
    }

    private static void setFakeConfigPath(Path path) throws Exception {
        Class<?> helperClass = Class.forName("com.meowantixray.platform.PlatformHelper");
        Class<?> platformClass = Class.forName("com.meowantixray.platform.ModPlatform");
        Class<?> loaderTypeClass = Class.forName("com.meowantixray.platform.LoaderType");
        Object proxy = java.lang.reflect.Proxy.newProxyInstance(
            platformClass.getClassLoader(),
            new Class<?>[]{platformClass},
            (ignored, method, args) -> switch (method.getName()) {
                case "loaderType" -> loaderTypeClass.getField("FABRIC").get(null);
                case "configDir", "gameDir" -> path.getParent();
                case "isModLoaded" -> false;
                case "modVersion" -> java.util.Optional.empty();
                case "loadedMods" -> List.of();
                case "hasPermission" -> false;
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
        Method bind = helperClass.getDeclaredMethod("bind", platformClass);
        bind.invoke(null, proxy);
    }

    private static BlockState[] filledSection(BlockState state) {
        BlockState[] states = new BlockState[4096];
        java.util.Arrays.fill(states, state);
        return states;
    }

    private static int blockIndex(int lx, int ly, int lz) {
        return (ly << 8) | (lz << 4) | lx;
    }
}
