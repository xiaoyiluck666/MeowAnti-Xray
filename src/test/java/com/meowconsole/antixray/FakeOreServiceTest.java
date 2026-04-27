package com.meowconsole.antixray;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet;
import net.minecraft.server.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
            dimensions,
            19,
            6
        );

        assertTrue(summary.contains("default-max-height=64"));
        assertTrue(summary.contains("async-chunk-rewrite=true"));
        assertTrue(summary.contains("async-worker-threads=2"));
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
        assertFalse(FakeOreService.shouldBypassWithPermission(false, false));
        assertFalse(FakeOreService.shouldBypassWithPermission(false, true));
        assertFalse(FakeOreService.shouldBypassWithPermission(true, false));
        assertTrue(FakeOreService.shouldBypassWithPermission(true, true));
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
    void shouldRevealStateMatchesHiddenAndExposureSemantics() {
        assertTrue(FakeOreService.shouldRevealState(false, false));
        assertTrue(FakeOreService.shouldRevealState(true, true));
        assertFalse(FakeOreService.shouldRevealState(true, false));
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
}
