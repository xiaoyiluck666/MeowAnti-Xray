package com.meowantixray.antixray;

import com.meowantixray.MeowAntiXrayMod;
import com.meowantixray.compat.MinecraftCompat;
import com.meowantixray.mixin.ClientboundLevelChunkPacketDataAccessor;
import com.meowantixray.platform.PlatformHelper;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.shorts.ShortIterator;
import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.BitStorage;
import net.minecraft.util.Mth;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.GlobalPalette;
import net.minecraft.world.level.chunk.PalettedContainer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.IntFunction;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class FakeOreService {
    private static final ThreadLocal<SectionVisibilityScratch> SECTION_VISIBILITY_SCRATCH =
        ThreadLocal.withInitial(SectionVisibilityScratch::new);
    private static final ThreadLocal<SectionPaletteReadScratch> SECTION_PALETTE_READ_SCRATCH =
        ThreadLocal.withInitial(SectionPaletteReadScratch::new);
    private static final boolean[] HIDDEN_ONLY_PASSES = {true};
    private static final boolean[] HIDDEN_AND_REPLACEMENT_PASSES = {true, false};
    private FakeOreConfig config = new FakeOreConfig();
    private Palette globalPalette;
    private final Map<String, Palette> dimensionPalettes = new HashMap<>();
    private final Map<UUID, PlayerMask> maskedByPlayer = new HashMap<>();
    private long tickCounter = 0;
    private final LongAdder statObfuscatedChunks = new LongAdder();
    private final LongAdder statObfuscatedSections = new LongAdder();
    private final LongAdder statObfuscatedBlocks = new LongAdder();
    private final LongAdder statRevealPackets = new LongAdder();
    private final LongAdder statRevealBlocks = new LongAdder();
    private final LongAdder statChunkRewriteTasks = new LongAdder();
    private final LongAdder statChunkRewriteNanos = new LongAdder();
    private final AtomicLong statChunkRewriteMaxNanos = new AtomicLong();
    private final LongAdder statRevealTasks = new LongAdder();
    private final LongAdder statRevealNanos = new LongAdder();
    private final AtomicLong statRevealMaxNanos = new AtomicLong();
    private final LongAdder statSyncChunkSends = new LongAdder();
    private long profileLastNano = System.nanoTime();
    private long profileLastChunks = 0L;
    private long profileLastSections = 0L;
    private long profileLastBlocks = 0L;
    private long profileLastRevealPackets = 0L;
    private long profileLastRevealBlocks = 0L;
    private long profileLastChunkRewriteTasks = 0L;
    private long profileLastChunkRewriteNanos = 0L;
    private long profileLastRevealTasks = 0L;
    private long profileLastRevealNanos = 0L;
    private long profileLastSyncChunkSends = 0L;
    private volatile ExecutorService chunkRewriteExecutor;
    private volatile int chunkRewriteExecutorThreads;
    private volatile int chunkRewriteExecutorQueueSize = -1;
    private volatile Semaphore chunkRewritePermits;
    private volatile int chunkRewritePermitLimit = -1;

    private int asyncRewriteCapacity() {
        return Math.max(1, config.asyncWorkerThreads) + Math.max(0, config.asyncQueueSize);
    }
    private static final int[][] PAPER_NEIGHBOR_OFFSETS_RADIUS_1 = {
        {-1, 0, 0},
        {1, 0, 0},
        {0, -1, 0},
        {0, 1, 0},
        {0, 0, -1},
        {0, 0, 1}
    };
    private static final int[][] PAPER_NEIGHBOR_OFFSETS_RADIUS_2 = {
        {-1, 0, 0},
        {-2, 0, 0},
        {-1, -1, 0},
        {-1, 1, 0},
        {-1, 0, -1},
        {-1, 0, 1},
        {1, 0, 0},
        {2, 0, 0},
        {1, -1, 0},
        {1, 1, 0},
        {1, 0, -1},
        {1, 0, 1},
        {0, -1, 0},
        {0, -2, 0},
        {0, -1, -1},
        {0, -1, 1},
        {0, 1, 0},
        {0, 2, 0},
        {0, 1, -1},
        {0, 1, 1},
        {0, 0, -1},
        {0, 0, -2},
        {0, 0, 1},
        {0, 0, 2}
    };

    public void reloadConfig() {
        this.config = FakeOreConfig.loadOrCreate();
        if (config.enforceEngineMode2 && config.engineMode != 2) {
            config.engineMode = 2;
        }
        if (!config.asyncChunkRewrite) {
            shutdownAsyncExecutor();
        }

        this.globalPalette = buildPalette(
            config.enabled,
            config.engineMode,
            config.maxBlockHeight,
            config.onlyObfuscateHiddenBlocks,
            config.mode2ObfuscateReplacementBlocks,
            config.guaranteeHideAllHiddenBlocks,
            config.lavaObscures,
            config.usePermission,
            config.bypassPermission,
            config.updateRadius,
            config.updateIntervalTicks,
            config.maxBlocksPerChunk,
            config.hiddenBlocks,
            config.replacementBlocks
        );

        this.dimensionPalettes.clear();
        for (Map.Entry<String, FakeOreConfig.DimensionSettings> entry : config.dimensionSettings.entrySet()) {
            FakeOreConfig.DimensionSettings ds = entry.getValue();
            int mode = config.enforceEngineMode2 ? 2 : ds.engineMode;
            Palette palette = buildPalette(
                effectiveEnabled(config.enabled, ds.enabled),
                mode,
                ds.maxBlockHeight,
                ds.onlyObfuscateHiddenBlocks,
                ds.mode2ObfuscateReplacementBlocks,
                ds.guaranteeHideAllHiddenBlocks,
                ds.lavaObscures,
                ds.usePermission,
                ds.bypassPermission,
                config.updateRadius,
                config.updateIntervalTicks,
                config.maxBlocksPerChunk,
                ds.hiddenBlocks,
                ds.replacementBlocks
            );
            dimensionPalettes.put(normalizeDimensionKey(entry.getKey()), palette);
        }
    }

    public void onPlayerDisconnect(ServerPlayer player) {
        maskedByPlayer.remove(player.getUUID());
    }

    static boolean shouldBypassWithPermission(boolean usePermission, boolean platformPermission, boolean adminLike) {
        return usePermission && (platformPermission || adminLike);
    }

    private boolean shouldBypassForPlayer(ServerPlayer player, Palette palette) {
        return shouldBypassWithPermission(
            palette.usePermission,
            PlatformHelper.hasPermission(player, palette.bypassPermission),
            MeowAntiXrayMod.isAdminLikePlayer(player)
        );
    }

    public void sendChunkPacket(
        ServerGamePacketListenerImpl connection,
        ServerLevel level,
        LevelChunk chunk,
        ClientboundLevelChunkWithLightPacket packet
    ) {
        ChunkSendContext sendContext = createChunkSendContext(connection, level, chunk, packet);
        if (!sendContext.palette().enabled || sendContext.palette().hiddenBlockIds.isEmpty()) {
            sendPacket(sendContext);
            return;
        }
        if (shouldBypassForPlayer(sendContext.connection().player, sendContext.palette())) {
            maskedByPlayer.remove(sendContext.connection().player.getUUID());
            sendPacket(sendContext);
            return;
        }

        if (!config.asyncChunkRewrite) {
            fallbackSyncChunkSend(sendContext);
            return;
        }
        if (!tryAcquireAsyncRewritePermit()) {
            fallbackSyncChunkSend(sendContext);
            return;
        }

        ChunkRewriteController controller;
        try {
            controller = createChunkRewriteController(sendContext);
        } catch (RuntimeException ex) {
            releaseAsyncRewritePermit();
            throw ex;
        }
        if (!submitAsyncChunkRewrite(controller)) {
            releaseAsyncRewritePermit();
            fallbackSyncChunkSend(sendContext);
        }
    }

    public void shutdownAsyncExecutor() {
        ExecutorService executor = chunkRewriteExecutor;
        if (executor != null) {
            executor.shutdownNow();
            chunkRewriteExecutor = null;
            chunkRewriteExecutorThreads = 0;
            chunkRewriteExecutorQueueSize = -1;
            chunkRewritePermits = null;
            chunkRewritePermitLimit = -1;
        }
    }

    public void obfuscateChunkPacket(
        ServerPlayer player,
        ServerLevel level,
        LevelChunk chunk,
        ClientboundLevelChunkWithLightPacket packet
    ) {
        long startNano = System.nanoTime();
        try {
        Palette palette = paletteFor(level);
        if (!palette.enabled || palette.hiddenBlockIds.isEmpty()) {
            return;
        }
        if (shouldBypassForPlayer(player, palette)) {
            maskedByPlayer.remove(player.getUUID());
            return;
        }

        PlayerMask playerMask = maskedByPlayer.computeIfAbsent(player.getUUID(), key -> new PlayerMask());
        ChunkPacketInfo packetInfo = createChunkPacketInfo(level, chunk, packet, palette);
        LevelChunkSection[] sourceSections = packetInfo.sourceSections();
        boolean[] modifiedSections = new boolean[sourceSections.length];
        byte[][] sectionByteOverrides = new byte[sourceSections.length][];
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        int baseX = packetInfo.baseX();
        int baseZ = packetInfo.baseZ();
        int maxY = packetInfo.maxY();
        int count = 0;
        boolean changed = false;
        int changedSections = 0;
        int changedBlocks = 0;
        LevelChunk westChunk = packetInfo.westChunk();
        LevelChunk eastChunk = packetInfo.eastChunk();
        LevelChunk northChunk = packetInfo.northChunk();
        LevelChunk southChunk = packetInfo.southChunk();
        byte[] originalBuffer = packetInfo.originalBuffer();
        int[] sourceSectionSizes = packetInfo.sourceSectionSizes();

        boolean[] passes = obfuscatesReplacementBlocks(palette)
            ? HIDDEN_AND_REPLACEMENT_PASSES
            : HIDDEN_ONLY_PASSES;

        for (int sectionIndex = 0; sectionIndex < sourceSections.length; sectionIndex++) {
            LevelChunkSection source = sourceSections[sectionIndex];
            if (source == null || source.hasOnlyAir()) {
                continue;
            }
            SectionPaletteReadResult centerRead = snapshotPaletteRead(readSectionPalette(source, palette, null));
            if (!centerRead.matches(true, obfuscatesReplacementBlocks(palette))) {
                continue;
            }

            int sectionY = level.getSectionYFromSectionIndex(sectionIndex);
            int minBlockY = sectionY * 16;
            int maxBlockY = minBlockY + 15;
            if (maxBlockY < MinecraftCompat.minBuildY(level) || minBlockY > maxY) {
                continue;
            }
            LevelChunkSection downSection = sectionIndex > 0 ? sourceSections[sectionIndex - 1] : null;
            LevelChunkSection upSection = sectionIndex + 1 < sourceSections.length ? sourceSections[sectionIndex + 1] : null;
            LevelChunkSection westSection = getSectionByY(level, westChunk, sectionY);
            LevelChunkSection eastSection = getSectionByY(level, eastChunk, sectionY);
            LevelChunkSection northSection = getSectionByY(level, northChunk, sectionY);
            LevelChunkSection southSection = getSectionByY(level, southChunk, sectionY);

            long sectionLong = SectionPos.asLong(MinecraftCompat.chunkX(chunk.getPos()), sectionY, MinecraftCompat.chunkZ(chunk.getPos()));
            playerMask.removeSection(sectionLong);

            VisibilityBuildResult visibilityBuild = buildSectionVisibility(
                level,
                centerRead,
                downSection,
                upSection,
                westSection,
                eastSection,
                northSection,
                southSection
            );
            SectionVisibility visibility = visibilityBuild.visibility();
            SectionPaletteReadResult paletteRead = visibilityBuild.centerRead();

            SectionObfuscationPlan plan = new SectionObfuscationPlan();
            ObfuscationRandom random = createObfuscationRandom(palette);
            int remainingBudget = palette.guaranteeHideAllHiddenBlocks ? Integer.MAX_VALUE : Math.max(0, palette.maxBlocksPerChunk - count);
            boolean budgetReached = obfuscateLiveSectionLayers(
                level,
                source,
                paletteRead,
                palette,
                visibility,
                baseX,
                baseZ,
                minBlockY,
                maxY,
                random,
                passes,
                remainingBudget,
                mutable,
                plan
            );

            if (!plan.isEmpty()) {
                playerMask.putSection(sectionLong, plan.trackedInSection());
                changedSections++;
                changedBlocks += plan.changedBlocks();
                modifiedSections[sectionIndex] = true;
                LevelChunkSection rewritten = MinecraftCompat.copySection(source);
                applyObfuscationPlanToSection(rewritten, plan.changedIndices(), plan.fakeStates());
                sectionByteOverrides[sectionIndex] = serializeSectionBytes(rewritten);
                count += plan.changedBlocks();
                changed = true;
            }
            if (budgetReached || (!palette.guaranteeHideAllHiddenBlocks && count >= palette.maxBlocksPerChunk)) {
                break;
            }
        }

        if (!changed) {
            return;
        }

        statObfuscatedChunks.increment();
        statObfuscatedSections.add(changedSections);
        statObfuscatedBlocks.add(changedBlocks);

        byte[] rewritten = rewriteSections(originalBuffer, sourceSectionSizes, modifiedSections, sectionByteOverrides);
        ((ClientboundLevelChunkPacketDataAccessor) packet.getChunkData()).meowconsole$setBuffer(rewritten);
        } finally {
            recordDuration(statChunkRewriteTasks, statChunkRewriteNanos, statChunkRewriteMaxNanos, System.nanoTime() - startNano);
        }
    }

    public void onBlockBroken(ServerPlayer player, ServerLevel level, BlockPos brokenPos) {
        Palette palette = paletteFor(level);
        if (!palette.enabled) {
            return;
        }
        revealAround(player, level, brokenPos, palette);
        updateNearbyBlocksLikePaper(level, brokenPos, palette);
    }

    public void onBlockChange(ServerLevel level, BlockPos pos, BlockState oldState, BlockState newState) {
        Palette palette = paletteFor(level);
        if (!palette.enabled) {
            return;
        }
        if (oldState == null || newState == null) {
            return;
        }
        if (pos.getY() > palette.maxBlockHeight + palette.updateRadius - 1) {
            return;
        }
        if (!isPaperSolid(level, pos, oldState) || isPaperSolid(level, pos, newState)) {
            return;
        }
        updateNearbyBlocksLikePaper(level, pos, palette);
    }

    public void onBlockBreakStarted(ServerPlayer player, ServerLevel level, BlockPos targetPos) {
        Palette palette = paletteFor(level);
        if (!palette.enabled) {
            return;
        }
        PlayerMask mask = maskedByPlayer.get(player.getUUID());
        if (mask == null || mask.isEmpty()) {
            return;
        }
        revealAround(player, level, targetPos, palette);
        updateNearbyBlocksLikePaper(level, targetPos, palette);
    }

    public void onExplosionBlocksChanged(ServerLevel level, List<BlockPos> explodedPositions) {
        if (explodedPositions == null || explodedPositions.isEmpty()) {
            return;
        }
        Palette palette = paletteFor(level);
        if (!palette.enabled) {
            return;
        }

        List<ServerPlayer> viewers = new ArrayList<>();
        for (ServerPlayer player : playerList()) {
            if (player.level() == level) {
                viewers.add(player);
            }
        }
        if (viewers.isEmpty()) {
            return;
        }

        int centerLimit = Math.min(explodedPositions.size(), 256);
        Long2ObjectOpenHashMap<ShortOpenHashSet> revealBySection = new Long2ObjectOpenHashMap<>();
        int budget = 4096;
        for (int i = 0; i < centerLimit && budget > 0; i++) {
            budget = collectPaperNeighborResync(level, explodedPositions.get(i), palette, revealBySection, budget, true);
        }
        if (revealBySection.isEmpty()) {
            return;
        }

        for (ServerPlayer viewer : viewers) {
            applyRevealCandidatesToPlayer(viewer, level, revealBySection);
        }
    }

    public void onServerTick() {
        if (maskedByPlayer.isEmpty()) {
            return;
        }
        tickCounter++;

        for (ServerPlayer player : playerList()) {
            ServerLevel level = (ServerLevel) player.level();
            Palette palette = paletteFor(level);
            if (!palette.enabled || palette.updateIntervalTicks <= 0) {
                continue;
            }
            if (tickCounter % palette.updateIntervalTicks != 0) {
                continue;
            }

            PlayerMask mask = maskedByPlayer.get(player.getUUID());
            if (mask == null || mask.isEmpty()) {
                continue;
            }

            Long2ObjectOpenHashMap<ShortOpenHashSet> revealBySection = new Long2ObjectOpenHashMap<>();
            BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
            BlockPos playerPos = player.blockPosition();
            int processed = 0;
            int radius = Math.max(1, palette.updateRadius);
            int secRadius = Math.max(1, (radius + 15) >> 4);
            int playerSecX = SectionPos.blockToSectionCoord(playerPos.getX());
            int playerSecY = SectionPos.blockToSectionCoord(playerPos.getY());
            int playerSecZ = SectionPos.blockToSectionCoord(playerPos.getZ());

            for (int sx = playerSecX - secRadius; sx <= playerSecX + secRadius && processed < 4096; sx++) {
                for (int sy = playerSecY - secRadius; sy <= playerSecY + secRadius && processed < 4096; sy++) {
                    for (int sz = playerSecZ - secRadius; sz <= playerSecZ + secRadius && processed < 4096; sz++) {
                        long sectionLong = SectionPos.asLong(sx, sy, sz);
                        ShortOpenHashSet relSet = mask.getSection(sectionLong);
                        if (relSet == null || relSet.isEmpty()) {
                            continue;
                        }
                        LoadedChunkSection loadedSection = resolveLoadedChunkSection(level, sectionLong);
                        if (loadedSection == null) {
                            mask.removeSection(sectionLong);
                            continue;
                        }

                        SectionPos sectionPos = loadedSection.sectionPos();
                        VisibilityBuildResult visibilityBuild = buildSectionVisibilityForLoadedSection(level, loadedSection);
                        ShortIterator relIt = relSet.iterator();
                        while (relIt.hasNext() && processed < 4096) {
                            short rel = relIt.nextShort();
                            mutable.set(
                                sectionPos.relativeToBlockX(rel),
                                sectionPos.relativeToBlockY(rel),
                                sectionPos.relativeToBlockZ(rel)
                            );
                            BlockState now = level.getBlockState(mutable);
                            if (inspectBlockRevealState(level, mutable, palette, now, visibilityBuild).shouldReveal()) {
                                addRevealPosition(revealBySection, sectionLong, rel);
                                relIt.remove();
                                processed++;
                            }
                        }
                        if (relSet.isEmpty()) {
                            mask.removeSection(sectionLong);
                        }
                    }
                }
            }
            sendRevealBatches(player, level, revealBySection);
        }
    }

    public String describeConfig() {
        if (globalPalette == null) {
            return "config not loaded yet";
        }
        Map<String, DimensionSummary> dimensionSummaries = new LinkedHashMap<>();
        for (Map.Entry<String, Palette> entry : dimensionPalettes.entrySet()) {
            Palette palette = entry.getValue();
            dimensionSummaries.put(entry.getKey(), new DimensionSummary(
                palette.enabled,
                palette.engineMode,
                palette.maxBlockHeight
            ));
        }
        return formatConfigSummary(
            globalPalette.engineMode,
            globalPalette.maxBlockHeight,
            config.enforceEngineMode2,
            globalPalette.guaranteeHideAllHiddenBlocks,
            config.asyncChunkRewrite,
            config.asyncWorkerThreads,
            config.asyncQueueSize,
            asyncRewriteCapacity(),
            dimensionSummaries,
            globalPalette.hiddenBlockIds.size(),
            globalPalette.replacementBlockIds.size()
        );
    }

    public List<String> statusDetails() {
        return formatStatusLines(captureStatusSnapshot());
    }

    public boolean isEnabled() {
        return globalPalette != null && globalPalette.enabled;
    }

    public String stressChunkRewrite(ServerLevel level, int centerChunkX, int centerChunkZ, int radius, int passes) {
        ServerLevel safeLevel = Objects.requireNonNull(level, "level");
        Palette palette = paletteFor(safeLevel);
        if (!palette.enabled || palette.hiddenBlockIds.isEmpty()) {
            return "stress skipped: anti-xray disabled or no hidden blocks";
        }

        int safeRadius = Math.max(0, Math.min(16, radius));
        int safePasses = Math.max(1, Math.min(20, passes));
        int chunks = 0;
        int changedChunks = 0;
        int changedSections = 0;
        int changedBlocks = 0;
        long startNano = System.nanoTime();
        for (int pass = 0; pass < safePasses; pass++) {
            for (int chunkX = centerChunkX - safeRadius; chunkX <= centerChunkX + safeRadius; chunkX++) {
                for (int chunkZ = centerChunkZ - safeRadius; chunkZ <= centerChunkZ + safeRadius; chunkZ++) {
                    LevelChunk chunk = safeLevel.getChunk(chunkX, chunkZ);
                    ClientboundLevelChunkWithLightPacket packet = new ClientboundLevelChunkWithLightPacket(chunk, safeLevel.getLightEngine(), null, null);
                    ChunkPacketInfo packetInfo = createChunkPacketInfo(safeLevel, chunk, packet, palette);
                    ChunkRewritePreparation preparation = new ChunkRewritePreparation(
                        new UUID(0L, 0L),
                        safeLevel.getSeed(),
                        replacementProfile(safeLevel),
                        palette,
                        chunkX,
                        chunkZ,
                        packetInfo.minY(),
                        packetInfo.baseX(),
                        packetInfo.baseZ(),
                        packetInfo.maxY(),
                        packetInfo.originalBuffer().clone(),
                        packetInfo.sourceSectionSizes().clone(),
                        snapshotCenterSections(safeLevel, packetInfo),
                        snapshotNeighborSections(safeLevel, packetInfo.westChunk()),
                        snapshotNeighborSections(safeLevel, packetInfo.eastChunk()),
                        snapshotNeighborSections(safeLevel, packetInfo.northChunk()),
                        snapshotNeighborSections(safeLevel, packetInfo.southChunk())
                    );
                    AsyncChunkResult result = processAsyncChunkTask(preparation.toAsyncChunkTask());
                    chunks++;
                    if (result.changed()) {
                        changedChunks++;
                        changedSections += result.changedSections();
                        changedBlocks += result.changedBlocks();
                    }
                }
            }
        }
        long elapsedNano = System.nanoTime() - startNano;
        double elapsedMs = elapsedNano / 1_000_000.0D;
        double avgMs = chunks <= 0 ? 0.0D : elapsedMs / chunks;
        return String.format(
            java.util.Locale.ROOT,
            "stress chunks=%d changedChunks=%d sections=%d blocks=%d elapsedMs=%.3f avgChunkMs=%.3f radius=%d passes=%d",
            chunks,
            changedChunks,
            changedSections,
            changedBlocks,
            elapsedMs,
            avgMs,
            safeRadius,
            safePasses
        );
    }

    static boolean effectiveEnabled(boolean globalEnabled, boolean dimensionEnabled) {
        return globalEnabled && dimensionEnabled;
    }

    public String configLoadSummary() {
        return config.loadSummary();
    }

    public String configPathSummary() {
        return PlatformHelper.configDir().resolve("meowantixray.yml").toString();
    }

    public ReloadReport reloadConfigWithSummary() {
        RuntimeStatusSnapshot before = captureStatusSnapshot();
        boolean hadLoadedConfig = globalPalette != null;
        reloadConfig();
        RuntimeStatusSnapshot after = captureStatusSnapshot();
        return new ReloadReport(
            after.configLoadSummary(),
            formatStatusLines(after),
            describeConfigChanges(hadLoadedConfig ? before : null, after)
        );
    }

    public synchronized String profileSummary() {
        long now = System.nanoTime();
        long chunks = statObfuscatedChunks.sum();
        long sections = statObfuscatedSections.sum();
        long blocks = statObfuscatedBlocks.sum();
        long revealPackets = statRevealPackets.sum();
        long revealBlocks = statRevealBlocks.sum();
        long chunkRewriteTasks = statChunkRewriteTasks.sum();
        long chunkRewriteNanos = statChunkRewriteNanos.sum();
        long revealTasks = statRevealTasks.sum();
        long revealNanos = statRevealNanos.sum();
        long syncChunkSends = statSyncChunkSends.sum();
        int asyncCapacity = asyncRewriteCapacity();
        int asyncAvailable = asyncAvailablePermits(asyncCapacity);
        int asyncInFlight = Math.max(0, asyncCapacity - asyncAvailable);

        double seconds = Math.max(0.001D, (now - profileLastNano) / 1_000_000_000.0D);
        long dChunks = chunks - profileLastChunks;
        long dSections = sections - profileLastSections;
        long dBlocks = blocks - profileLastBlocks;
        long dRevealPackets = revealPackets - profileLastRevealPackets;
        long dRevealBlocks = revealBlocks - profileLastRevealBlocks;
        long dChunkRewriteTasks = chunkRewriteTasks - profileLastChunkRewriteTasks;
        long dChunkRewriteNanos = chunkRewriteNanos - profileLastChunkRewriteNanos;
        long dRevealTasks = revealTasks - profileLastRevealTasks;
        long dRevealNanos = revealNanos - profileLastRevealNanos;
        long dSyncChunkSends = syncChunkSends - profileLastSyncChunkSends;
        long totalChunkSends = chunks + syncChunkSends;
        long deltaChunkSends = dChunks + dSyncChunkSends;
        String pressure = asyncPressure(config.asyncChunkRewrite, asyncInFlight, asyncCapacity, dSyncChunkSends);

        profileLastNano = now;
        profileLastChunks = chunks;
        profileLastSections = sections;
        profileLastBlocks = blocks;
        profileLastRevealPackets = revealPackets;
        profileLastRevealBlocks = revealBlocks;
        profileLastChunkRewriteTasks = chunkRewriteTasks;
        profileLastChunkRewriteNanos = chunkRewriteNanos;
        profileLastRevealTasks = revealTasks;
        profileLastRevealNanos = revealNanos;
        profileLastSyncChunkSends = syncChunkSends;

        return "profile total{chunks=" + chunks
            + ", sections=" + sections
            + ", blocks=" + blocks
            + ", revealPackets=" + revealPackets
            + ", revealBlocks=" + revealBlocks
            + ", rewriteTasks=" + chunkRewriteTasks
            + ", rewriteAvgMs=" + formatAverageMillis(chunkRewriteNanos, chunkRewriteTasks)
            + ", rewriteMaxMs=" + formatMillis(statChunkRewriteMaxNanos.get())
            + ", revealTasks=" + revealTasks
            + ", revealAvgMs=" + formatAverageMillis(revealNanos, revealTasks)
            + ", revealMaxMs=" + formatMillis(statRevealMaxNanos.get())
            + ", syncChunkSends=" + syncChunkSends
            + "} async{enabled=" + config.asyncChunkRewrite
            + ", workerThreads=" + Math.max(1, config.asyncWorkerThreads)
            + ", queueSize=" + Math.max(0, config.asyncQueueSize)
            + ", capacity=" + asyncCapacity
            + ", inFlight=" + asyncInFlight
            + ", availablePermits=" + asyncAvailable
            + ", pressure=" + pressure
            + ", syncFallbackRatio=" + formatRatio(syncChunkSends, totalChunkSends)
            + "} delta(" + String.format("%.1fs", seconds) + "){chunks/s=" + String.format("%.1f", dChunks / seconds)
            + ", sections/s=" + String.format("%.1f", dSections / seconds)
            + ", blocks/s=" + String.format("%.1f", dBlocks / seconds)
            + ", revealPackets/s=" + String.format("%.1f", dRevealPackets / seconds)
            + ", revealBlocks/s=" + String.format("%.1f", dRevealBlocks / seconds)
            + ", rewriteTasks/s=" + String.format("%.1f", dChunkRewriteTasks / seconds)
            + ", rewriteAvgMs=" + formatAverageMillis(dChunkRewriteNanos, dChunkRewriteTasks)
            + ", revealTasks/s=" + String.format("%.1f", dRevealTasks / seconds)
            + ", revealAvgMs=" + formatAverageMillis(dRevealNanos, dRevealTasks)
            + ", syncChunkSends/s=" + String.format("%.1f", dSyncChunkSends / seconds)
            + ", syncFallbackRatio=" + formatRatio(dSyncChunkSends, deltaChunkSends)
            + "}";
    }

    static String asyncPressure(boolean asyncEnabled, int inFlight, int capacity, long syncFallbackDelta) {
        if (!asyncEnabled) {
            return "disabled";
        }
        if (syncFallbackDelta > 0L) {
            return "saturated";
        }
        int safeCapacity = Math.max(1, capacity);
        if (inFlight * 100 >= safeCapacity * 80) {
            return "busy";
        }
        return "normal";
    }

    private static String formatRatio(long numerator, long denominator) {
        if (denominator <= 0L) {
            return "0.0%";
        }
        return String.format(java.util.Locale.ROOT, "%.1f%%", numerator * 100.0D / denominator);
    }

    private int asyncAvailablePermits(int fallbackCapacity) {
        Semaphore permits = chunkRewritePermits;
        if (permits == null) {
            return fallbackCapacity;
        }
        return Math.max(0, permits.availablePermits());
    }

    private static void recordDuration(LongAdder tasks, LongAdder nanos, AtomicLong maxNanos, long elapsedNanos) {
        long safeElapsed = Math.max(0L, elapsedNanos);
        tasks.increment();
        nanos.add(safeElapsed);
        maxNanos.accumulateAndGet(safeElapsed, Math::max);
    }

    private static String formatAverageMillis(long nanos, long tasks) {
        if (tasks <= 0L) {
            return "0.000";
        }
        return formatMillis(nanos / (double) tasks);
    }

    private static String formatMillis(long nanos) {
        return formatMillis((double) nanos);
    }

    private static String formatMillis(double nanos) {
        return String.format(java.util.Locale.ROOT, "%.3f", nanos / 1_000_000.0D);
    }

    public String debugBlock(ServerLevel level, BlockPos pos) {
        return String.join(" | ", inspectBlockDetails(level, pos));
    }

    public List<String> inspectBlockDetails(ServerLevel level, BlockPos pos) {
        Palette palette = paletteFor(level);
        BlockPos safePos = Objects.requireNonNull(pos, "pos");
        BlockRevealContext revealState = inspectBlockRevealState(level, safePos, palette);
        BlockState realState = revealState.realState();
        var realId = BuiltInRegistries.BLOCK.getKey(realState.getBlock());
        ObfuscationRandom debugRandom = createObfuscationRandom(palette);
        debugRandom.enterLayer(safePos.getY() & 15);
        BlockState fakeState = chooseFakeState(level, safePos, realState, palette, debugRandom);
        var fakeId = BuiltInRegistries.BLOCK.getKey(fakeState.getBlock());
        boolean hidden = revealState.hidden();
        boolean replacement = revealState.replacement();
        boolean target = isTargetState(palette, realState);
        boolean exposed = revealState.exposed();
        boolean shouldReveal = revealState.shouldReveal();
        boolean inRange = safePos.getY() >= MinecraftCompat.minBuildY(level)
            && safePos.getY() <= Math.min(palette.maxBlockHeight, MinecraftCompat.maxBuildY(level));
        boolean replacementObfuscates = obfuscatesReplacementBlocks(palette);
        TrackedBlockLocation trackedLocation = trackedBlockLocation(safePos);
        List<String> maskedPlayers = new ArrayList<>();

        for (ServerPlayer player : playerList()) {
            if (player.level() != level) {
                continue;
            }
            PlayerMask mask = maskedByPlayer.get(player.getUUID());
            if (mask != null && mask.contains(trackedLocation.sectionLong(), trackedLocation.rel())) {
                maskedPlayers.add(player.getName().getString());
            }
        }

        String tracked = maskedPlayers.isEmpty() ? "none" : String.join(", ", maskedPlayers);
        String reason = inspectDecisionReason(palette.enabled, inRange, hidden, replacement, exposed);
        List<String> lines = new ArrayList<>();
        lines.add("inspect: world=" + normalizeDimensionKey(level.dimension().toString())
            + ", pos=" + pos.getX() + "," + pos.getY() + "," + pos.getZ()
            + ", real=" + realId
            + ", fake=" + fakeId
            + ", reason=" + reason);
        lines.add("state: enabled=" + palette.enabled
            + ", hidden=" + hidden
            + ", replacement=" + replacement
            + ", target=" + target
            + ", exposed=" + exposed
            + ", shouldReveal=" + shouldReveal
            + ", trackedPlayers=" + maskedPlayers.size()
            + " [" + tracked + "]");
        lines.add("config: mode=" + palette.engineMode
            + ", max-block-height=" + palette.maxBlockHeight
            + ", in-range=" + inRange
            + ", obfuscates-replacement=" + replacementObfuscates
            + ", use-permission=" + palette.usePermission
            + ", bypass-permission=" + palette.bypassPermission
            + ", update-radius=" + palette.updateRadius);
        return lines;
    }

    private List<ServerPlayer> playerList() {
        DedicatedServer server = MeowAntiXrayMod.currentServer();
        if (server != null) {
            return server.getPlayerList().getPlayers();
        }
        return List.of();
    }

    private void revealAround(
        ServerPlayer player,
        ServerLevel level,
        BlockPos center,
        Palette palette
    ) {
        Long2ObjectOpenHashMap<ShortOpenHashSet> revealBySection = new Long2ObjectOpenHashMap<>();
        collectPaperNeighborResync(level, center, palette, revealBySection, 8192, true);
        applyRevealCandidatesToPlayer(player, level, revealBySection);
    }

    private void updateNearbyBlocksLikePaper(ServerLevel level, BlockPos blockPos, Palette palette) {
        if (palette.updateRadius <= 0) {
            return;
        }
        for (BlockPos candidate : paperNeighborUpdatePositions(blockPos, palette.updateRadius)) {
            updateBlockLikePaper(level, candidate, palette);
        }
    }

    private int collectPaperNeighborResync(
        ServerLevel level,
        BlockPos center,
        Palette palette,
        Long2ObjectOpenHashMap<ShortOpenHashSet> revealBySection,
        int budget,
        boolean includeCenter
    ) {
        if (budget <= 0) {
            return 0;
        }
        Long2ObjectOpenHashMap<VisibilityBuildResult> visibilityBySection = new Long2ObjectOpenHashMap<>();
        if (includeCenter) {
            budget = collectResyncCandidate(level, center, center, palette, revealBySection, visibilityBySection, budget);
        }
        for (BlockPos candidate : paperNeighborUpdatePositions(center, palette.updateRadius)) {
            if (budget <= 0) {
                break;
            }
            budget = collectResyncCandidate(level, candidate, center, palette, revealBySection, visibilityBySection, budget);
        }
        return budget;
    }

    private int collectResyncCandidate(
        ServerLevel level,
        BlockPos candidate,
        BlockPos center,
        Palette palette,
        Long2ObjectOpenHashMap<ShortOpenHashSet> revealBySection,
        Long2ObjectOpenHashMap<VisibilityBuildResult> visibilityBySection,
        int budget
    ) {
        BlockPos safeCandidate = Objects.requireNonNull(candidate, "candidate");
        BlockPos safeCenter = Objects.requireNonNull(center, "center");
        if (budget <= 0) {
            return 0;
        }
        TrackedBlockLocation trackedLocation = trackedBlockLocation(safeCandidate);
        LoadedChunkSection loadedSection = resolveLoadedChunkSection(level, trackedLocation.sectionLong());
        if (loadedSection == null) {
            return budget;
        }

        BlockState realState = level.getBlockState(safeCandidate);
        boolean isTargetBlock = safeCandidate.getX() == safeCenter.getX()
            && safeCandidate.getY() == safeCenter.getY()
            && safeCandidate.getZ() == safeCenter.getZ();
        if (isTargetBlock) {
            return addRevealCandidate(revealBySection, budget, trackedLocation, safeCandidate);
        }

        BlockRevealContext revealState = inspectBlockRevealState(
            level,
            safeCandidate,
            palette,
            realState,
            cachedVisibilityBuild(level, loadedSection, visibilityBySection)
        );
        if (!revealState.hidden() && !revealState.replacement()) {
            return budget;
        }
        if (!revealState.shouldReveal()) {
            return budget;
        }

        return addRevealCandidate(revealBySection, budget, trackedLocation, safeCandidate);
    }

    private int addRevealCandidate(
        Long2ObjectOpenHashMap<ShortOpenHashSet> revealBySection,
        int budget,
        TrackedBlockLocation trackedLocation,
        BlockPos candidate
    ) {
        Objects.requireNonNull(candidate, "candidate");
        if (addRevealPosition(revealBySection, trackedLocation.sectionLong(), trackedLocation.rel())) {
            budget--;
        }
        return budget;
    }

    private VisibilityBuildResult cachedVisibilityBuild(
        ServerLevel level,
        LoadedChunkSection loadedSection,
        Long2ObjectOpenHashMap<VisibilityBuildResult> visibilityBySection
    ) {
        long sectionLong = loadedSection.sectionPos().asLong();
        if (!visibilityBySection.containsKey(sectionLong)) {
            visibilityBySection.put(sectionLong, buildSectionVisibilityForLoadedSection(level, loadedSection));
        }
        return visibilityBySection.get(sectionLong);
    }

    private boolean addRevealPosition(
        Long2ObjectOpenHashMap<ShortOpenHashSet> revealBySection,
        long sectionLong,
        short rel
    ) {
        return revealBySection.computeIfAbsent(sectionLong, ignored -> new ShortOpenHashSet()).add(rel);
    }

    static List<BlockPos> paperNeighborUpdatePositions(BlockPos blockPos, int updateRadius) {
        List<BlockPos> candidates = new ArrayList<>(updateRadius >= 2 ? 24 : 6);
        int[][] offsets = updateRadius >= 2 ? PAPER_NEIGHBOR_OFFSETS_RADIUS_2 : PAPER_NEIGHBOR_OFFSETS_RADIUS_1;
        for (int[] offset : offsets) {
            candidates.add(blockPos.offset(offset[0], offset[1], offset[2]));
        }
        return candidates;
    }

    private void sendRevealBatches(
        ServerPlayer player,
        ServerLevel level,
        Long2ObjectOpenHashMap<ShortOpenHashSet> revealBySection
    ) {
        long startNano = System.nanoTime();
        try {
        if (revealBySection.isEmpty()) {
            return;
        }
        for (Long2ObjectMap.Entry<ShortOpenHashSet> entry : revealBySection.long2ObjectEntrySet()) {
            ShortOpenHashSet positions = entry.getValue();
            if (positions == null || positions.isEmpty()) {
                continue;
            }
            LoadedChunkSection loadedSection = resolveLoadedChunkSection(level, entry.getLongKey());
            if (loadedSection == null) {
                continue;
            }
            SectionPos sectionPos = Objects.requireNonNull(loadedSection.sectionPos(), "loadedSection.sectionPos()");
            LevelChunkSection section = Objects.requireNonNull(loadedSection.section(), "loadedSection.section()");
            player.connection.send(new ClientboundSectionBlocksUpdatePacket(
                sectionPos,
                positions,
                section
            ));
            statRevealPackets.increment();
            statRevealBlocks.add(positions.size());
        }
        } finally {
            recordDuration(statRevealTasks, statRevealNanos, statRevealMaxNanos, System.nanoTime() - startNano);
        }
    }

    private ChunkSendContext createChunkSendContext(
        ServerGamePacketListenerImpl connection,
        ServerLevel level,
        LevelChunk chunk,
        ClientboundLevelChunkWithLightPacket packet
    ) {
        ServerGamePacketListenerImpl safeConnection = Objects.requireNonNull(connection, "connection");
        ServerLevel safeLevel = Objects.requireNonNull(level, "level");
        LevelChunk safeChunk = Objects.requireNonNull(chunk, "chunk");
        ClientboundLevelChunkWithLightPacket safePacket = Objects.requireNonNull(packet, "packet");
        return new ChunkSendContext(
            safeConnection,
            safeLevel,
            safeChunk,
            safePacket,
            paletteFor(safeLevel)
        );
    }

    private void sendPacket(ChunkSendContext sendContext) {
        sendContext.connection().send(Objects.requireNonNull(sendContext.packet(), "packet"));
    }

    private void fallbackSyncChunkSend(ChunkSendContext sendContext) {
        statSyncChunkSends.increment();
        obfuscateChunkPacket(
            sendContext.connection().player,
            sendContext.level(),
            sendContext.chunk(),
            sendContext.packet()
        );
        sendPacket(sendContext);
    }

    private ExecutorService ensureChunkRewriteExecutor() {
        ExecutorService current = chunkRewriteExecutor;
        int configuredThreads = Math.max(1, config.asyncWorkerThreads);
        int configuredQueueSize = Math.max(0, config.asyncQueueSize);
        if (
            current != null
                && !current.isShutdown()
                && chunkRewriteExecutorThreads == configuredThreads
                && chunkRewriteExecutorQueueSize == configuredQueueSize
        ) {
            return current;
        }
        synchronized (this) {
            current = chunkRewriteExecutor;
            if (
                current != null
                    && !current.isShutdown()
                    && (chunkRewriteExecutorThreads != configuredThreads || chunkRewriteExecutorQueueSize != configuredQueueSize)
            ) {
                current.shutdownNow();
                current = null;
                chunkRewriteExecutor = null;
            }
            if (current == null || current.isShutdown()) {
                chunkRewriteExecutor = new ThreadPoolExecutor(
                    configuredThreads,
                    configuredThreads,
                    0L,
                    TimeUnit.MILLISECONDS,
                    configuredQueueSize == 0 ? new SynchronousQueue<>() : new ArrayBlockingQueue<>(configuredQueueSize),
                    runnable -> {
                        Thread thread = new Thread(runnable, "meowantixray-rewrite");
                        thread.setDaemon(true);
                        return thread;
                    },
                    new ThreadPoolExecutor.AbortPolicy()
                );
                chunkRewriteExecutorThreads = configuredThreads;
                chunkRewriteExecutorQueueSize = configuredQueueSize;
            }
            return chunkRewriteExecutor;
        }
    }

    private boolean tryAcquireAsyncRewritePermit() {
        Semaphore permits = ensureChunkRewritePermits();
        return permits.tryAcquire();
    }

    private Semaphore ensureChunkRewritePermits() {
        int configuredCapacity = asyncRewriteCapacity();
        Semaphore current = chunkRewritePermits;
        if (current != null && chunkRewritePermitLimit == configuredCapacity) {
            return current;
        }
        synchronized (this) {
            current = chunkRewritePermits;
            if (current == null || chunkRewritePermitLimit != configuredCapacity) {
                current = new Semaphore(configuredCapacity);
                chunkRewritePermits = current;
                chunkRewritePermitLimit = configuredCapacity;
            }
            return current;
        }
    }

    private void releaseAsyncRewritePermit() {
        Semaphore permits = chunkRewritePermits;
        if (permits != null) {
            permits.release();
        }
    }

    private boolean submitAsyncChunkRewrite(ChunkRewriteController controller) {
        AsyncChunkTask task = controller.preparation().toAsyncChunkTask();
        try {
            CompletableFuture
                .supplyAsync(() -> processAsyncChunkTask(task), controller.runtime().workerExecutor())
                .whenComplete((result, throwable) ->
                    scheduleChunkRewriteCompletion(controller, result, throwable)
                );
            return true;
        } catch (RejectedExecutionException | IllegalStateException ex) {
            MeowAntiXrayMod.LOGGER.warn(
                "Meow Anti-Xray async rewrite submit failed, fallback to sync chunk=({}, {})",
                MinecraftCompat.chunkX(controller.sendContext().chunk().getPos()),
                MinecraftCompat.chunkZ(controller.sendContext().chunk().getPos()),
                ex
            );
            return false;
        }
    }

    private ChunkRewriteController createChunkRewriteController(ChunkSendContext sendContext) {
        ChunkRewritePreparation preparation = prepareChunkRewrite(sendContext);
        ChunkRewriteRuntime runtime = createChunkRewriteRuntime(sendContext);
        return new ChunkRewriteController(sendContext, preparation, runtime);
    }

    private ChunkRewriteRuntime createChunkRewriteRuntime(ChunkSendContext sendContext) {
        ServerLevel safeLevel = Objects.requireNonNull(sendContext.level(), "sendContext.level()");
        Executor completionExecutor = command -> {
            Runnable safeCommand = Objects.requireNonNull(command, "command");
            Objects.requireNonNull(safeLevel.getServer(), "sendContext.level().getServer()").execute(safeCommand);
        };
        return new ChunkRewriteRuntime(
            ensureChunkRewriteExecutor(),
            completionExecutor
        );
    }

    private void scheduleChunkRewriteCompletion(
        ChunkRewriteController controller,
        AsyncChunkResult result,
        Throwable throwable
    ) {
        controller.runtime().completionExecutor().execute(() ->
            completeAsyncChunkSend(controller, result, throwable)
        );
    }

    private ChunkRewritePreparation prepareChunkRewrite(ChunkSendContext sendContext) {
        ChunkPacketInfo packetInfo = createChunkPacketInfo(sendContext);
        return new ChunkRewritePreparation(
            sendContext.connection().player.getUUID(),
            sendContext.level().getSeed(),
            replacementProfile(sendContext.level()),
            sendContext.palette(),
            MinecraftCompat.chunkX(sendContext.chunk().getPos()),
            MinecraftCompat.chunkZ(sendContext.chunk().getPos()),
            packetInfo.minY(),
            packetInfo.baseX(),
            packetInfo.baseZ(),
            packetInfo.maxY(),
            packetInfo.originalBuffer(),
            packetInfo.sourceSectionSizes(),
            snapshotCenterSections(sendContext.level(), packetInfo),
            snapshotNeighborSections(sendContext.level(), packetInfo.westChunk()),
            snapshotNeighborSections(sendContext.level(), packetInfo.eastChunk()),
            snapshotNeighborSections(sendContext.level(), packetInfo.northChunk()),
            snapshotNeighborSections(sendContext.level(), packetInfo.southChunk())
        );
    }

    private SectionSnapshot[] snapshotCenterSections(ServerLevel level, ChunkPacketInfo packetInfo) {
        LevelChunkSection[] sourceSections = packetInfo.sourceSections();
        SectionSnapshot[] snapshots = new SectionSnapshot[sourceSections.length];
        for (int i = 0; i < sourceSections.length; i++) {
            LevelChunkSection section = sourceSections[i];
            if (section == null || section.hasOnlyAir()) {
                continue;
            }
            snapshots[i] = snapshotSection(
                level.getSectionYFromSectionIndex(i),
                section,
                packetInfo.sourceSectionOffsets()[i],
                packetInfo.sourceSectionSizes()[i]
            );
        }
        return snapshots;
    }

    private SectionSnapshot[] snapshotNeighborSections(ServerLevel level, LevelChunk chunk) {
        if (chunk == null) {
            return new SectionSnapshot[0];
        }
        LevelChunkSection[] sourceSections = chunk.getSections();
        SectionSnapshot[] snapshots = new SectionSnapshot[sourceSections.length];
        for (int i = 0; i < sourceSections.length; i++) {
            LevelChunkSection section = sourceSections[i];
            if (section == null || section.hasOnlyAir()) {
                continue;
            }
            snapshots[i] = snapshotSection(level.getSectionYFromSectionIndex(i), section, 0, 0);
        }
        return snapshots;
    }

    private SectionSnapshot snapshotSection(int sectionY, LevelChunkSection section, int packetOffset, int serializedSize) {
        SectionStorageAccess storageAccess = new SectionStorageAccess(section.getStates());
        try {
            int[] unpacked = new int[4096];
            storageAccess.storage().unpack(unpacked);
            BlockState[] paletteEntries = snapshotPaletteEntries(storageAccess.palette());
            int paletteSerializedSize = MinecraftCompat.paletteSerializedSize(storageAccess.palette());
            return new SectionSnapshot(
                sectionY,
                packetOffset,
                serializedSize,
                storageAccess.storage().getBits(),
                4 + 1 + paletteSerializedSize,
                storageAccess.storage().getRaw().length * Long.BYTES,
                storageAccess.palette() instanceof GlobalPalette<BlockState>,
                paletteEntries,
                unpacked
            );
        } finally {
            storageAccess.close();
        }
    }

    private BlockState[] snapshotPaletteEntries(net.minecraft.world.level.chunk.Palette<BlockState> palette) {
        if (palette instanceof GlobalPalette<BlockState>) {
            return new BlockState[0];
        }
        BlockState[] entries = new BlockState[palette.getSize()];
        for (int i = 0; i < entries.length; i++) {
            entries[i] = palette.valueFor(i);
        }
        return entries;
    }

    private AsyncChunkResult processAsyncChunkTask(AsyncChunkTask task) {
        long startNano = System.nanoTime();
        try {
        SectionSnapshot[] sourceSections = task.sourceSections();
        boolean[] modifiedSections = new boolean[sourceSections.length];
        byte[][] sectionByteOverrides = new byte[sourceSections.length][];
        Long2ObjectOpenHashMap<short[]> trackedBySection = new Long2ObjectOpenHashMap<>();
        int count = 0;
        int changedSections = 0;
        int changedBlocks = 0;
        boolean changed = false;
        boolean[] passes = obfuscatesReplacementBlocks(task.palette())
            ? HIDDEN_AND_REPLACEMENT_PASSES
            : HIDDEN_ONLY_PASSES;

        for (int sectionIndex = 0; sectionIndex < sourceSections.length; sectionIndex++) {
            SectionSnapshot source = sourceSections[sectionIndex];
            if (source == null) {
                continue;
            }
            SectionPaletteReadResult centerRead = snapshotPaletteRead(readSectionPalette(source, task.palette(), null));
            if (!centerRead.matches(true, obfuscatesReplacementBlocks(task.palette()))) {
                continue;
            }

            int minBlockY = source.sectionY() * 16;
            int maxBlockY = minBlockY + 15;
            if (isSectionOutsideVerticalRange(minBlockY, maxBlockY, task.minY(), task.maxY())) {
                continue;
            }

            SectionSnapshot downSection = sectionIndex > 0 ? sourceSections[sectionIndex - 1] : null;
            SectionSnapshot upSection = sectionIndex + 1 < sourceSections.length ? sourceSections[sectionIndex + 1] : null;
            SectionSnapshot westSection = sectionIndex < task.westSections().length ? task.westSections()[sectionIndex] : null;
            SectionSnapshot eastSection = sectionIndex < task.eastSections().length ? task.eastSections()[sectionIndex] : null;
            SectionSnapshot northSection = sectionIndex < task.northSections().length ? task.northSections()[sectionIndex] : null;
            SectionSnapshot southSection = sectionIndex < task.southSections().length ? task.southSections()[sectionIndex] : null;

            VisibilityBuildResult visibilityBuild = buildSectionVisibility(task.palette(), centerRead, downSection, upSection, westSection, eastSection, northSection, southSection);
            SectionVisibility visibility = visibilityBuild.visibility();
            SectionPaletteReadResult paletteRead = visibilityBuild.centerRead();
            SectionObfuscationPlan plan = new SectionObfuscationPlan();
            ObfuscationRandom random = createObfuscationRandom(task.palette());
            int remainingBudget = task.palette().guaranteeHideAllHiddenBlocks ? Integer.MAX_VALUE : Math.max(0, task.palette().maxBlocksPerChunk - count);
            boolean budgetReached = obfuscateSnapshotSectionLayers(
                task,
                source,
                paletteRead,
                visibility,
                minBlockY,
                random,
                passes,
                remainingBudget,
                plan
            );

            if (!plan.isEmpty()) {
                trackedBySection.put(SectionPos.asLong(task.chunkX(), source.sectionY(), task.chunkZ()), plan.toShortArray());
                changedSections++;
                changedBlocks += plan.changedBlocks();
                modifiedSections[sectionIndex] = true;
                byte[] directPatchedSection = tryPatchSectionBytesInPlace(task.originalBuffer(), source, plan.changedIndices(), plan.fakeStates());
                sectionByteOverrides[sectionIndex] = directPatchedSection != null
                    ? directPatchedSection
                    : repackSectionBytes(task.originalBuffer(), source, plan.changedIndices(), plan.fakeStates());
                count += plan.changedBlocks();
                changed = true;
            }

            if (budgetReached || (!task.palette().guaranteeHideAllHiddenBlocks && count >= task.palette().maxBlocksPerChunk)) {
                break;
            }
        }

        byte[] rewritten = changed
            ? rewriteSections(task.originalBuffer(), task.sectionSizes(), modifiedSections, sectionByteOverrides)
            : null;
        return new AsyncChunkResult(rewritten, trackedBySection, changedSections, changedBlocks, changed);
        } finally {
            recordDuration(statChunkRewriteTasks, statChunkRewriteNanos, statChunkRewriteMaxNanos, System.nanoTime() - startNano);
        }
    }

    private boolean obfuscateLiveSectionLayers(
        ServerLevel level,
        LevelChunkSection source,
        SectionPaletteReadResult paletteRead,
        Palette palette,
        SectionVisibility visibility,
        int baseX,
        int baseZ,
        int minBlockY,
        int maxY,
        ObfuscationRandom random,
        boolean[] passes,
        int remainingBudget,
        BlockPos.MutableBlockPos mutable,
        SectionObfuscationPlan plan
    ) {
        return obfuscateSectionLayers(
            paletteRead,
            visibility,
            maxY,
            random,
            passes,
            remainingBudget,
            plan,
            new LayerObfuscationContext() {
                @Override
                public int blockY(int ly) {
                    return minBlockY + ly;
                }

                @Override
                public BlockState sourceState(int stateIndex, int lx, int ly, int lz) {
                    return source.getBlockState(lx, ly, lz);
                }

                @Override
                public BlockState fakeState(int stateIndex, int lx, int ly, int lz, int y, BlockState sourceState, ObfuscationRandom random) {
                    mutable.set(baseX + lx, y, baseZ + lz);
                    return chooseFakeState(level, mutable, sourceState, palette, random);
                }

                @Override
                public void recordChange(SectionObfuscationPlan plan, int stateIndex, int lx, int ly, int lz, int y, BlockState fake) {
                    plan.recordLive(mutable, stateIndex, Objects.requireNonNull(fake, "fake"));
                }
            }
        );
    }

    private boolean obfuscateSnapshotSectionLayers(
        AsyncChunkTask task,
        SectionSnapshot source,
        SectionPaletteReadResult paletteRead,
        SectionVisibility visibility,
        int minBlockY,
        ObfuscationRandom random,
        boolean[] passes,
        int remainingBudget,
        SectionObfuscationPlan plan
    ) {
        return obfuscateSectionLayers(
            paletteRead,
            visibility,
            task.maxY(),
            random,
            passes,
            remainingBudget,
            plan,
            new LayerObfuscationContext() {
                @Override
                public int blockY(int ly) {
                    return minBlockY + ly;
                }

                @Override
                public BlockState sourceState(int stateIndex, int lx, int ly, int lz) {
                    return source.stateAt(stateIndex);
                }

                @Override
                public BlockState fakeState(int stateIndex, int lx, int ly, int lz, int y, BlockState sourceState, ObfuscationRandom random) {
                    return chooseFakeState(
                        task.replacementProfile(),
                        task.worldSeed(),
                        task.baseX() + lx,
                        y,
                        task.baseZ() + lz,
                        sourceState,
                        task.palette(),
                        random
                    );
                }

                @Override
                public void recordChange(SectionObfuscationPlan plan, int stateIndex, int lx, int ly, int lz, int y, BlockState fake) {
                    plan.recordSnapshot(stateIndex, fake);
                }
            }
        );
    }

    private boolean obfuscateSectionLayers(
        SectionPaletteReadResult paletteRead,
        SectionVisibility visibility,
        int maxY,
        ObfuscationRandom random,
        boolean[] passes,
        int remainingBudget,
        SectionObfuscationPlan plan,
        LayerObfuscationContext context
    ) {
        for (boolean hiddenPass : passes) {
            boolean replacementPass = !hiddenPass;
            if (!paletteRead.matches(hiddenPass, replacementPass)) {
                continue;
            }
            for (int ly = 0; ly < 16; ly++) {
                random.enterLayer(ly);
                if (obfuscateLayer(
                    paletteRead,
                    visibility,
                    maxY,
                    ly,
                    hiddenPass,
                    replacementPass,
                    random,
                    remainingBudget,
                    plan,
                    context
                )) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean obfuscateLayer(
        SectionPaletteReadResult paletteRead,
        SectionVisibility visibility,
        int maxY,
        int ly,
        boolean hiddenPass,
        boolean replacementPass,
        ObfuscationRandom random,
        int remainingBudget,
        SectionObfuscationPlan plan,
        LayerObfuscationContext context
    ) {
        int y = context.blockY(ly);
        if (y > maxY) {
            return false;
        }
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int stateIndex = blockIndex(lx, ly, lz);
                boolean hidden = paletteRead.hiddenAt(stateIndex);
                boolean replacement = paletteRead.replacementAt(stateIndex);
                boolean exposed = visibility.isExposed(lx, ly, lz);
                if (!shouldMaskState(hidden, replacement, hiddenPass, replacementPass) || exposed) {
                    continue;
                }
                BlockState sourceState = context.sourceState(stateIndex, lx, ly, lz);
                BlockState fake = context.fakeState(stateIndex, lx, ly, lz, y, sourceState, random);
                if (fake == sourceState) {
                    continue;
                }
                context.recordChange(plan, stateIndex, lx, ly, lz, y, fake);
                if (plan.reachedBudget(remainingBudget)) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean shouldMaskState(boolean hidden, boolean replacement, boolean hiddenPass, boolean replacementPass) {
        return (hiddenPass && hidden) || (replacementPass && replacement && !hidden);
    }

    static boolean isSectionOutsideVerticalRange(int minBlockY, int maxBlockY, int minY, int maxY) {
        return maxBlockY < minY || minBlockY > maxY;
    }

    private void completeAsyncChunkSend(
        ChunkRewriteController controller,
        AsyncChunkResult result,
        Throwable throwable
    ) {
        try {
            ChunkSendContext sendContext = controller.sendContext();
            ChunkRewritePreparation preparation = controller.preparation();
            if (sendContext.connection().player.level() != sendContext.level()) {
                return;
            }
            if (throwable != null || result == null) {
                MeowAntiXrayMod.LOGGER.warn(
                    "Meow Anti-Xray async rewrite failed, fallback to sync chunk=({}, {})",
                    MinecraftCompat.chunkX(sendContext.chunk().getPos()),
                    MinecraftCompat.chunkZ(sendContext.chunk().getPos()),
                    throwable
                );
                fallbackSyncChunkSend(sendContext);
                return;
            }

            PlayerMask playerMask = preparation.resolvePlayerMask(maskedByPlayer);
            preparation.applyAsyncResult(playerMask, result);

            if (result.changed()) {
                statObfuscatedChunks.increment();
                statObfuscatedSections.add(result.changedSections());
                statObfuscatedBlocks.add(result.changedBlocks());
                ((ClientboundLevelChunkPacketDataAccessor) sendContext.packet().getChunkData()).meowconsole$setBuffer(result.rewrittenBuffer());
            }
            sendPacket(sendContext);
        } finally {
            releaseAsyncRewritePermit();
        }
    }

    private void applyRevealCandidatesToPlayer(
        ServerPlayer player,
        ServerLevel level,
        Long2ObjectOpenHashMap<ShortOpenHashSet> revealBySection
    ) {
        if (revealBySection.isEmpty()) {
            return;
        }
        PlayerMask mask = maskedByPlayer.get(player.getUUID());
        Long2ObjectOpenHashMap<ShortOpenHashSet> playerRevealBySection = new Long2ObjectOpenHashMap<>();
        for (Long2ObjectMap.Entry<ShortOpenHashSet> entry : revealBySection.long2ObjectEntrySet()) {
            long sectionLong = entry.getLongKey();
            ShortOpenHashSet positions = entry.getValue();
            if (positions == null || positions.isEmpty()) {
                continue;
            }

            for (ShortIterator relIt = positions.iterator(); relIt.hasNext(); ) {
                short rel = relIt.nextShort();
                if (mask != null && !mask.isEmpty() && mask.contains(sectionLong, rel)) {
                    mask.remove(sectionLong, rel);
                }
                addRevealPosition(playerRevealBySection, sectionLong, rel);
            }
        }

        sendRevealBatches(player, level, playerRevealBySection);
    }

    private void updateBlockLikePaper(ServerLevel level, BlockPos blockPos, Palette palette) {
        if (level.getChunkSource().getChunkNow(SectionPos.blockToSectionCoord(blockPos.getX()), SectionPos.blockToSectionCoord(blockPos.getZ())) == null) {
            return;
        }
        BlockState blockState = level.getBlockState(blockPos);
        if (!isTargetState(palette, blockState)) {
            return;
        }
        level.getChunkSource().blockChanged(blockPos);
        clearTrackedPosition(blockPos);
    }

    private void clearTrackedPosition(BlockPos blockPos) {
        if (maskedByPlayer.isEmpty()) {
            return;
        }
        BlockPos safePos = Objects.requireNonNull(blockPos, "blockPos");
        TrackedBlockLocation trackedLocation = trackedBlockLocation(safePos);
        for (PlayerMask mask : maskedByPlayer.values()) {
            if (mask != null && !mask.isEmpty()) {
                mask.remove(trackedLocation.sectionLong(), trackedLocation.rel());
            }
        }
    }

    private TrackedBlockLocation trackedBlockLocation(BlockPos pos) {
        BlockPos safePos = Objects.requireNonNull(pos, "pos");
        return new TrackedBlockLocation(
            SectionPos.asLong(safePos),
            SectionPos.sectionRelativePos(safePos)
        );
    }

    private boolean isPaperSolid(ServerLevel level, BlockPos pos, BlockState state) {
        Palette palette = paletteFor(Objects.requireNonNull(level, "level"));
        Objects.requireNonNull(pos, "pos");
        return isSolidState(palette, state);
    }

    private BlockRevealContext inspectBlockRevealState(ServerLevel level, BlockPos pos, Palette palette) {
        BlockPos safePos = Objects.requireNonNull(pos, "pos");
        return inspectBlockRevealState(level, safePos, palette, level.getBlockState(safePos));
    }

    private BlockRevealContext inspectBlockRevealState(ServerLevel level, BlockPos pos, Palette palette, BlockState realState) {
        BlockPos safePos = Objects.requireNonNull(pos, "pos");
        BlockState safeRealState = Objects.requireNonNull(realState, "realState");
        return inspectBlockRevealState(level, safePos, palette, safeRealState, buildSectionVisibilityForBlock(level, safePos));
    }

    private BlockRevealContext inspectBlockRevealState(
        ServerLevel level,
        BlockPos pos,
        Palette palette,
        BlockState realState,
        VisibilityBuildResult visibilityBuild
    ) {
        BlockPos safePos = Objects.requireNonNull(pos, "pos");
        BlockState safeRealState = Objects.requireNonNull(realState, "realState");
        boolean hidden = isHiddenState(palette, safeRealState, visibilityBuild == null ? null : visibilityBuild.centerRead(), safePos);
        boolean replacement = isReplacementState(palette, safeRealState);
        boolean exposed = isExposed(level, safePos, visibilityBuild);
        return new BlockRevealContext(
            safeRealState,
            visibilityBuild,
            hidden,
            replacement,
            exposed,
            shouldRevealState(hidden, exposed)
        );
    }

    static boolean shouldRevealState(boolean hidden, boolean exposed) {
        return !hidden || exposed;
    }

    static boolean debugSyntheticSectionExposedForTest(BlockState[] states, int lx, int ly, int lz, boolean lavaObscures) {
        BlockState[] safeStates = Objects.requireNonNull(states, "states");
        if (safeStates.length != 4096) {
            throw new IllegalArgumentException("states must contain exactly one 16x16x16 section");
        }
        boolean[] hidden = new boolean[4096];
        boolean[] replacement = new boolean[4096];
        boolean[] solid = new boolean[4096];
        for (int i = 0; i < safeStates.length; i++) {
            solid[i] = isPrecomputedSolidState(lavaObscures, safeStates[i]);
        }
        SectionPaletteReadResult centerRead = new SectionPaletteReadResult(false, false, false, hidden, replacement, solid);
        VisibilityBuildResult visibility = new FakeOreService().buildSectionVisibility(
            centerRead,
            new VisibilityNeighborhood(null, null, null, null, null, null)
        );
        return visibility.visibility().isExposed(lx, ly, lz);
    }

    static boolean debugReadSectionPaletteHasTargetForTest(LevelChunkSection section, List<String> hiddenIds, List<String> replacementIds) {
        FakeOreService service = new FakeOreService();
        Palette palette = service.buildPalette(
            true,
            2,
            64,
            true,
            true,
            true,
            false,
            false,
            "paper.antixray.bypass",
            2,
            1,
            8192,
            hiddenIds,
            replacementIds
        );
        return service.readSectionPalette(
            Objects.requireNonNull(section, "section"),
            palette,
            new boolean[4096]
        ).hasTarget();
    }

    private boolean isExposed(ServerLevel level, BlockPos pos, VisibilityBuildResult visibilityBuild) {
        if (visibilityBuild == null) {
            return isExposed(level, pos);
        }
        SectionVisibility visibility = visibilityBuild.visibility();
        return visibility.isExposed(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
    }

    private boolean isHiddenState(Palette palette, BlockState realState, SectionPaletteReadResult centerRead, BlockPos pos) {
        if (centerRead == null) {
            return isHiddenState(palette, realState);
        }
        BlockPos safePos = Objects.requireNonNull(pos, "pos");
        return centerRead.hiddenAt(blockIndex(safePos.getX() & 15, safePos.getY() & 15, safePos.getZ() & 15));
    }

    private boolean isExposed(ServerLevel level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.relative(direction);
            BlockState neighbor = level.getBlockState(neighborPos);
            if (!isPaperSolid(level, neighborPos, neighbor)) {
                return true;
            }
        }
        return false;
    }

    private LevelChunkSection getSectionByY(ServerLevel level, LevelChunk chunk, int sectionY) {
        if (chunk == null) {
            return null;
        }
        int idx = level.getSectionIndexFromSectionY(sectionY);
        LevelChunkSection[] sections = chunk.getSections();
        if (idx < 0 || idx >= sections.length) {
            return null;
        }
        return sections[idx];
    }

    private VisibilityBuildResult buildSectionVisibilityForBlock(ServerLevel level, BlockPos pos) {
        return buildSectionVisibilityForSection(level, SectionPos.asLong(Objects.requireNonNull(pos, "pos")));
    }

    private VisibilityBuildResult buildSectionVisibilityForSection(ServerLevel level, long sectionLong) {
        LoadedChunkSection loadedSection = resolveLoadedChunkSection(level, sectionLong);
        return loadedSection == null ? null : buildSectionVisibilityForLoadedSection(level, loadedSection);
    }

    private VisibilityBuildResult buildSectionVisibilityForLoadedSection(ServerLevel level, LoadedChunkSection loadedSection) {
        if (loadedSection == null) {
            return null;
        }
        SectionPos sectionPos = loadedSection.sectionPos();
        LevelChunk chunk = loadedSection.chunk();
        LevelChunkSection center = loadedSection.section();
        SectionPaletteReadResult centerRead = snapshotPaletteRead(readSectionPalette(center, paletteFor(level), null));
        return buildSectionVisibility(
            level,
            centerRead,
            getSectionByY(level, chunk, sectionPos.y() - 1),
            getSectionByY(level, chunk, sectionPos.y() + 1),
            getSectionByY(level, level.getChunkSource().getChunkNow(sectionPos.x() - 1, sectionPos.z()), sectionPos.y()),
            getSectionByY(level, level.getChunkSource().getChunkNow(sectionPos.x() + 1, sectionPos.z()), sectionPos.y()),
            getSectionByY(level, level.getChunkSource().getChunkNow(sectionPos.x(), sectionPos.z() - 1), sectionPos.y()),
            getSectionByY(level, level.getChunkSource().getChunkNow(sectionPos.x(), sectionPos.z() + 1), sectionPos.y())
        );
    }

    private LoadedChunkSection resolveLoadedChunkSection(ServerLevel level, long sectionLong) {
        SectionPos sectionPos = SectionPos.of(sectionLong);
        LevelChunk chunk = level.getChunkSource().getChunkNow(sectionPos.x(), sectionPos.z());
        if (chunk == null) {
            return null;
        }
        int sectionIndex = level.getSectionIndexFromSectionY(sectionPos.y());
        LevelChunkSection[] sections = chunk.getSections();
        if (sectionIndex < 0 || sectionIndex >= sections.length) {
            return null;
        }
        LevelChunkSection section = sections[sectionIndex];
        if (section == null || section.hasOnlyAir()) {
            return null;
        }
        return new LoadedChunkSection(sectionPos, chunk, section);
    }

    private VisibilityBuildResult buildSectionVisibility(
        ServerLevel level,
        SectionPaletteReadResult centerRead,
        LevelChunkSection down,
        LevelChunkSection up,
        LevelChunkSection west,
        LevelChunkSection east,
        LevelChunkSection north,
        LevelChunkSection south
    ) {
        Palette palette = paletteFor(level);
        return buildSectionVisibility(
            centerRead,
            readVisibilityNeighborhood(palette, down, up, west, east, north, south)
        );
    }

    private VisibilityBuildResult buildSectionVisibility(
        Palette palette,
        SectionPaletteReadResult centerRead,
        SectionSnapshot down,
        SectionSnapshot up,
        SectionSnapshot west,
        SectionSnapshot east,
        SectionSnapshot north,
        SectionSnapshot south
    ) {
        return buildSectionVisibility(
            centerRead,
            readVisibilityNeighborhood(palette, down, up, west, east, north, south)
        );
    }

    private VisibilityBuildResult buildSectionVisibility(
        SectionPaletteReadResult centerRead,
        VisibilityNeighborhood neighborhood
    ) {
        SectionVisibilityScratch scratch = SECTION_VISIBILITY_SCRATCH.get();
        scratch.reset();
        boolean[] solid = scratch.solid;
        boolean[] open = scratch.open;
        boolean[] exposed = scratch.exposed;
        System.arraycopy(centerRead.solidByIndex(), 0, solid, 0, solid.length);

        propagateOpenTransparencyBottomUp(
            neighborhood.down(),
            neighborhood.west(),
            neighborhood.east(),
            neighborhood.north(),
            neighborhood.south(),
            solid,
            open,
            scratch
        );
        propagateOpenTransparencyTopDown(
            neighborhood.up(),
            neighborhood.west(),
            neighborhood.east(),
            neighborhood.north(),
            neighborhood.south(),
            solid,
            open,
            scratch
        );

        for (int ly = 0; ly < 16; ly++) {
            for (int lz = 0; lz < 16; lz++) {
                for (int lx = 0; lx < 16; lx++) {
                    int index = blockIndex(lx, ly, lz);
                    if (!solid[index]) {
                        continue;
                    }
                    if (touchesTransparentBoundary(
                        neighborhood.down(),
                        neighborhood.up(),
                        neighborhood.west(),
                        neighborhood.east(),
                        neighborhood.north(),
                        neighborhood.south(),
                        lx,
                        ly,
                        lz
                    )) {
                        exposed[index] = true;
                        continue;
                    }
                    exposed[index] = isAdjacentToOpenCell(open, lx, ly, lz);
                }
            }
        }

        return new VisibilityBuildResult(new SectionVisibility(exposed), centerRead);
    }

    private void propagateOpenTransparencyBottomUp(
        SectionPaletteReadResult down,
        SectionPaletteReadResult west,
        SectionPaletteReadResult east,
        SectionPaletteReadResult north,
        SectionPaletteReadResult south,
        boolean[] solid,
        boolean[] open,
        SectionVisibilityScratch scratch
    ) {
        scratch.clearLayerWindows();
        boolean[][] current = scratch.current;
        boolean[][] next = scratch.next;
        boolean[][] nextNext = scratch.nextNext;
        seedVerticalIncoming(down, 0, -1, current);
        for (int y = 0; y < 16; y++) {
            clearLayerWindow(next);
            floodLayerOpenTransparency(west, east, north, south, solid, open, current, next, y, scratch.queue);
            boolean[][] temp = current;
            current = next;
            next = nextNext;
            nextNext = temp;
        }
    }

    private void propagateOpenTransparencyTopDown(
        SectionPaletteReadResult up,
        SectionPaletteReadResult west,
        SectionPaletteReadResult east,
        SectionPaletteReadResult north,
        SectionPaletteReadResult south,
        boolean[] solid,
        boolean[] open,
        SectionVisibilityScratch scratch
    ) {
        scratch.clearLayerWindows();
        boolean[][] current = scratch.current;
        boolean[][] next = scratch.next;
        boolean[][] nextNext = scratch.nextNext;
        seedVerticalIncoming(up, 15, 1, current);
        for (int y = 15; y >= 0; y--) {
            clearLayerWindow(next);
            floodLayerOpenTransparency(west, east, north, south, solid, open, current, next, y, scratch.queue);
            boolean[][] temp = current;
            current = next;
            next = nextNext;
            nextNext = temp;
        }
    }

    private void floodLayerOpenTransparency(
        SectionPaletteReadResult west,
        SectionPaletteReadResult east,
        SectionPaletteReadResult north,
        SectionPaletteReadResult south,
        boolean[] solid,
        boolean[] open,
        boolean[][] incoming,
        boolean[][] layerOpen,
        int y,
        int[] queue
    ) {
        int head = 0;
        int tail = 0;
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int index = blockIndex(x, y, z);
                if (solid[index]) {
                    continue;
                }
                if (incoming[z][x] || touchesHorizontalBoundaryTransparent(west, east, north, south, x, y, z)) {
                    open[index] = true;
                    layerOpen[z][x] = true;
                    queue[tail++] = (z << 4) | x;
                }
            }
        }
        while (head < tail) {
            int encoded = queue[head++];
            int x = encoded & 15;
            int z = (encoded >> 4) & 15;
            if (x > 0) {
                tail = enqueueLayerNeighbor(solid, open, layerOpen, queue, tail, x - 1, y, z);
            }
            if (x < 15) {
                tail = enqueueLayerNeighbor(solid, open, layerOpen, queue, tail, x + 1, y, z);
            }
            if (z > 0) {
                tail = enqueueLayerNeighbor(solid, open, layerOpen, queue, tail, x, y, z - 1);
            }
            if (z < 15) {
                tail = enqueueLayerNeighbor(solid, open, layerOpen, queue, tail, x, y, z + 1);
            }
        }
    }

    private int enqueueLayerNeighbor(boolean[] solid, boolean[] open, boolean[][] layerOpen, int[] queue, int tail, int lx, int ly, int lz) {
        int index = blockIndex(lx, ly, lz);
        if (!solid[index] && !layerOpen[lz][lx]) {
            open[index] = true;
            layerOpen[lz][lx] = true;
            queue[tail++] = (lz << 4) | lx;
        }
        return tail;
    }

    private void seedVerticalIncoming(
        SectionPaletteReadResult vertical,
        int y,
        int direction,
        boolean[][] incoming
    ) {
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                incoming[z][x] = isVerticalBoundaryTransparent(vertical, x, y, z, direction);
            }
        }
    }

    private static void clearLayerWindow(boolean[][] window) {
        for (int z = 0; z < 16; z++) {
            Arrays.fill(window[z], false);
        }
    }

    private boolean touchesHorizontalBoundaryTransparent(
        SectionPaletteReadResult west,
        SectionPaletteReadResult east,
        SectionPaletteReadResult north,
        SectionPaletteReadResult south,
        int lx,
        int ly,
        int lz
    ) {
        return (lx == 0 && isTransparentNeighbor(west, 15, ly, lz))
            || (lx == 15 && isTransparentNeighbor(east, 0, ly, lz))
            || (lz == 0 && isTransparentNeighbor(north, lx, ly, 15))
            || (lz == 15 && isTransparentNeighbor(south, lx, ly, 0));
    }

    private boolean isVerticalBoundaryTransparent(
        SectionPaletteReadResult vertical,
        int lx,
        int ly,
        int lz,
        int direction
    ) {
        if (direction < 0) {
            if (ly != 0) {
                return false;
            }
            return isTransparentNeighbor(vertical, lx, 15, lz);
        }
        if (ly != 15) {
            return false;
        }
        return isTransparentNeighbor(vertical, lx, 0, lz);
    }

    private boolean touchesTransparentBoundary(
        SectionPaletteReadResult down,
        SectionPaletteReadResult up,
        SectionPaletteReadResult west,
        SectionPaletteReadResult east,
        SectionPaletteReadResult north,
        SectionPaletteReadResult south,
        int lx,
        int ly,
        int lz
    ) {
        return touchesHorizontalBoundaryTransparent(west, east, north, south, lx, ly, lz)
            || isVerticalBoundaryTransparent(down, lx, ly, lz, -1)
            || isVerticalBoundaryTransparent(up, lx, ly, lz, 1);
    }

    private boolean isAdjacentToOpenCell(boolean[] open, int lx, int ly, int lz) {
        return (lx > 0 && open[blockIndex(lx - 1, ly, lz)])
            || (lx < 15 && open[blockIndex(lx + 1, ly, lz)])
            || (ly > 0 && open[blockIndex(lx, ly - 1, lz)])
            || (ly < 15 && open[blockIndex(lx, ly + 1, lz)])
            || (lz > 0 && open[blockIndex(lx, ly, lz - 1)])
            || (lz < 15 && open[blockIndex(lx, ly, lz + 1)]);
    }

    private int blockIndex(int lx, int ly, int lz) {
        return (ly << 8) | (lz << 4) | lx;
    }

    private static boolean isPaperTransparentOverride(BlockState state) {
        return state.is(Blocks.SPAWNER)
            || state.is(Blocks.BARRIER)
            || state.getBlock() instanceof ShulkerBoxBlock
            || state.is(Blocks.SLIME_BLOCK)
            || state.is(Blocks.MANGROVE_ROOTS);
    }

    static boolean isPrecomputedSolidState(boolean lavaObscures, BlockState state) {
        return state != null
            && ((state.isRedstoneConductor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
            && !isPaperTransparentOverride(state))
            || (lavaObscures && state == Blocks.LAVA.defaultBlockState()));
    }

    private SectionPaletteReadResult snapshotNullableSectionPalette(LevelChunkSection section, Palette palette) {
        return section == null ? null : snapshotPaletteRead(readSectionPalette(section, palette, null));
    }

    private SectionPaletteReadResult snapshotNullableSectionPalette(SectionSnapshot section, Palette palette) {
        return section == null ? null : snapshotPaletteRead(readSectionPalette(section, palette, null));
    }

    private VisibilityNeighborhood readVisibilityNeighborhood(
        Palette palette,
        LevelChunkSection down,
        LevelChunkSection up,
        LevelChunkSection west,
        LevelChunkSection east,
        LevelChunkSection north,
        LevelChunkSection south
    ) {
        return new VisibilityNeighborhood(
            snapshotNullableSectionPalette(down, palette),
            snapshotNullableSectionPalette(up, palette),
            snapshotNullableSectionPalette(west, palette),
            snapshotNullableSectionPalette(east, palette),
            snapshotNullableSectionPalette(north, palette),
            snapshotNullableSectionPalette(south, palette)
        );
    }

    private VisibilityNeighborhood readVisibilityNeighborhood(
        Palette palette,
        SectionSnapshot down,
        SectionSnapshot up,
        SectionSnapshot west,
        SectionSnapshot east,
        SectionSnapshot north,
        SectionSnapshot south
    ) {
        return new VisibilityNeighborhood(
            snapshotNullableSectionPalette(down, palette),
            snapshotNullableSectionPalette(up, palette),
            snapshotNullableSectionPalette(west, palette),
            snapshotNullableSectionPalette(east, palette),
            snapshotNullableSectionPalette(north, palette),
            snapshotNullableSectionPalette(south, palette)
        );
    }

    private SectionPaletteReadResult snapshotPaletteRead(SectionPaletteReadResult result) {
        return new SectionPaletteReadResult(
            result.hasHidden(),
            result.hasReplacement(),
            result.hasTarget(),
            Arrays.copyOf(result.hiddenByIndex(), result.hiddenByIndex().length),
            Arrays.copyOf(result.replacementByIndex(), result.replacementByIndex().length),
            Arrays.copyOf(result.solidByIndex(), result.solidByIndex().length)
        );
    }

    private SectionPaletteReadResult readSectionPalette(LevelChunkSection section, Palette palette, boolean[] solidOut) {
        SectionPaletteReadScratch scratch = SECTION_PALETTE_READ_SCRATCH.get();
        SectionStorageAccess storageAccess = new SectionStorageAccess(section.getStates());
        try {
            int[] unpacked = scratch.unpackedIndices;
            storageAccess.storage().unpack(unpacked);
            return readSectionPalette(
                storageAccess.palette(),
                storageAccess.palette().getSize(),
                index -> Objects.requireNonNull(storageAccess.palette().valueFor(index), "sourcePalette.valueFor(...)"),
                unpacked,
                palette,
                solidOut,
                scratch
            );
        } finally {
            storageAccess.close();
        }
    }

    private SectionPaletteReadResult readSectionPalette(SectionSnapshot section, Palette palette, boolean[] solidOut) {
        SectionPaletteReadScratch scratch = SECTION_PALETTE_READ_SCRATCH.get();
        if (section.globalPalette()) {
            return readGlobalPaletteStates(
                section.stateCount(),
                section::stateAt,
                palette,
                solidOut,
                scratch
            );
        }
        return readLocalPaletteStates(
            section.paletteEntries().length,
            index -> section.paletteEntries()[index],
            section.unpackedIndices(),
            palette,
            solidOut,
            scratch
        );
    }

    private SectionPaletteReadResult readSectionPalette(
        net.minecraft.world.level.chunk.Palette<BlockState> sourcePalette,
        int paletteSize,
        IntFunction<BlockState> paletteEntryResolver,
        int[] unpackedIndices,
        Palette palette,
        boolean[] solidOut,
        SectionPaletteReadScratch scratch
    ) {
        net.minecraft.world.level.chunk.Palette<BlockState> safeSourcePalette =
            Objects.requireNonNull(sourcePalette, "sourcePalette");
        IntFunction<BlockState> safePaletteEntryResolver = Objects.requireNonNull(paletteEntryResolver, "paletteEntryResolver");
        int[] safeUnpackedIndices = Objects.requireNonNull(unpackedIndices, "unpackedIndices");
        Palette safePalette = Objects.requireNonNull(palette, "palette");
        SectionPaletteReadScratch safeScratch = Objects.requireNonNull(scratch, "scratch");
        if (safeSourcePalette instanceof GlobalPalette<BlockState>) {
            return readGlobalPaletteStates(
                safeUnpackedIndices.length,
                index -> Objects.requireNonNull(safeSourcePalette.valueFor(safeUnpackedIndices[index]), "sourcePalette.valueFor(...)"),
                safePalette,
                solidOut,
                safeScratch
            );
        }
        return readLocalPaletteStates(
            paletteSize,
            safePaletteEntryResolver,
            safeUnpackedIndices,
            safePalette,
            solidOut,
            safeScratch
        );
    }

    private SectionPaletteReadResult readGlobalPaletteStates(
        int stateCount,
        IntFunction<BlockState> stateResolver,
        Palette palette,
        boolean[] solidOut,
        SectionPaletteReadScratch scratch
    ) {
        boolean hasHidden = false;
        boolean hasReplacement = false;
        boolean hasTarget = false;
        for (int i = 0; i < stateCount; i++) {
            BlockState state = Objects.requireNonNull(stateResolver.apply(i), "stateResolver.apply(...)");
            boolean hidden = isHiddenState(palette, state);
            boolean replacement = isReplacementState(palette, state);
            scratch.hiddenByIndex[i] = hidden;
            scratch.replacementByIndex[i] = replacement;
            scratch.solidByIndex[i] = isSolidState(palette, state);
            hasHidden |= hidden;
            hasReplacement |= replacement;
            hasTarget |= hidden || replacement;
        }
        if (solidOut != null) {
            System.arraycopy(scratch.solidByIndex, 0, solidOut, 0, scratch.solidByIndex.length);
        }
        return scratch.result(hasHidden, hasReplacement, hasTarget);
    }

    private SectionPaletteReadResult readLocalPaletteStates(
        int paletteSize,
        IntFunction<BlockState> paletteEntryResolver,
        int[] unpackedIndices,
        Palette palette,
        boolean[] solidOut,
        SectionPaletteReadScratch scratch
    ) {
        scratch.ensurePaletteCapacity(paletteSize);
        boolean hasHidden = false;
        boolean hasReplacement = false;
        boolean hasTarget = false;
        for (int i = 0; i < paletteSize; i++) {
            BlockState state = Objects.requireNonNull(paletteEntryResolver.apply(i), "paletteEntryResolver.apply(...)");
            boolean hidden = isHiddenState(palette, state);
            boolean replacement = isReplacementState(palette, state);
            scratch.paletteHiddenFlags[i] = hidden;
            scratch.paletteReplacementFlags[i] = replacement;
            scratch.solidFlags[i] = isSolidState(palette, state);
            hasHidden |= hidden;
            hasReplacement |= replacement;
            hasTarget |= hidden || replacement;
        }
        for (int i = 0; i < unpackedIndices.length; i++) {
            int paletteIndex = unpackedIndices[i];
            scratch.hiddenByIndex[i] = scratch.paletteHiddenFlags[paletteIndex];
            scratch.replacementByIndex[i] = scratch.paletteReplacementFlags[paletteIndex];
            scratch.solidByIndex[i] = scratch.solidFlags[paletteIndex];
        }
        if (solidOut != null) {
            System.arraycopy(scratch.solidByIndex, 0, solidOut, 0, scratch.solidByIndex.length);
        }
        return scratch.result(hasHidden, hasReplacement, hasTarget);
    }

    private boolean isTransparentNeighbor(SectionPaletteReadResult view, int lx, int ly, int lz) {
        return view == null || !view.solidAt(blockIndex(lx, ly, lz));
    }

    private byte[] rewriteSections(
        byte[] originalBuffer,
        int[] sectionSizes,
        boolean[] modifiedSections,
        byte[][] sectionByteOverrides
    ) {
        byte[] safeOriginalBuffer = Objects.requireNonNull(originalBuffer, "originalBuffer");
        int outputSize = 0;
        for (int i = 0; i < sectionSizes.length; i++) {
            int sourceSize = sectionSizes[i];
            if (sourceSize == 0) {
                continue;
            }
            outputSize += modifiedSections[i]
                ? Objects.requireNonNull(sectionByteOverrides[i], "sectionByteOverrides[" + i + "]").length
                : sourceSize;
        }

        FriendlyByteBuf buf = new FriendlyByteBuf(Objects.requireNonNull(Unpooled.buffer(outputSize), "buffer"));
        try {
            int originalOffset = 0;
            for (int i = 0; i < sectionSizes.length; i++) {
                int sourceSize = sectionSizes[i];
                if (sourceSize == 0) {
                    continue;
                }
                if (modifiedSections[i]) {
                    buf.writeBytes(Objects.requireNonNull(sectionByteOverrides[i], "sectionByteOverrides[" + i + "]"));
                } else {
                    buf.writeBytes(safeOriginalBuffer, originalOffset, sourceSize);
                }
                originalOffset += sourceSize;
            }
            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            return data;
        } finally {
            buf.release();
        }
    }

    private void applyObfuscationPlanToSection(
        LevelChunkSection section,
        IntArrayList changedIndices,
        List<BlockState> fakeStates
    ) {
        for (int i = 0; i < changedIndices.size(); i++) {
            int stateIndex = changedIndices.getInt(i);
            int lx = stateIndex & 15;
            int lz = (stateIndex >>> 4) & 15;
            int ly = (stateIndex >>> 8) & 15;
            section.setBlockState(lx, ly, lz, Objects.requireNonNull(fakeStates.get(i), "fakeStates[" + i + "]"), false);
        }
    }

    private byte[] serializeSectionBytes(LevelChunkSection section) {
        FriendlyByteBuf sectionBuf = new FriendlyByteBuf(Objects.requireNonNull(Unpooled.buffer(), "buffer"));
        try {
            section.write(sectionBuf);
            byte[] data = new byte[sectionBuf.readableBytes()];
            sectionBuf.readBytes(data);
            return data;
        } finally {
            sectionBuf.release();
        }
    }

    private byte[] tryPatchSectionBytesInPlace(
        byte[] originalBuffer,
        SectionSnapshot source,
        IntArrayList changedIndices,
        List<BlockState> fakeStates
    ) {
        if (!source.globalPalette() || source.blockStateBits() <= 0 || source.blockStateRawByteLength() <= 0) {
            return null;
        }
        byte[] sectionBytes = Arrays.copyOfRange(originalBuffer, source.packetOffset(), source.packetOffset() + source.serializedSize());
        PacketBitStorageWriter writer = new PacketBitStorageWriter(sectionBytes, source.blockStateDataOffset(), source.blockStateBits());
        for (int i = 0; i < changedIndices.size(); i++) {
            int paletteId = source.findPaletteId(fakeStates.get(i));
            if (paletteId < 0) {
                return null;
            }
            writer.set(changedIndices.getInt(i), paletteId);
        }
        return sectionBytes;
    }

    private byte[] repackSectionBytes(
        byte[] originalBuffer,
        SectionSnapshot source,
        IntArrayList changedIndices,
        List<BlockState> fakeStates
    ) {
        byte[] originalSectionBytes = Arrays.copyOfRange(originalBuffer, source.packetOffset(), source.packetOffset() + source.serializedSize());
        List<BlockState> targetPaletteEntries = buildTargetPaletteEntries(source, fakeStates);
        int targetBits = computeTargetBits(targetPaletteEntries.size(), source.globalPalette());
        int[] repacked = remapIndicesForTargetPalette(source, targetPaletteEntries, targetBits);
        for (int i = 0; i < changedIndices.size(); i++) {
            repacked[changedIndices.getInt(i)] = resolveTargetPaletteId(targetPaletteEntries, targetBits, fakeStates.get(i));
        }
        long[] raw = targetBits == 0
            ? new long[0]
            : new SimpleBitStorage(targetBits, 4096, Objects.requireNonNull(repacked, "repacked")).getRaw();

        FriendlyByteBuf stateBuf = new FriendlyByteBuf(Objects.requireNonNull(Unpooled.buffer(), "buffer"));
        try {
            stateBuf.writeByte(targetBits);
            writeTargetPalette(stateBuf, targetPaletteEntries, targetBits);
            MinecraftCompat.writeLongArray(stateBuf, raw);

            int biomesOffset = source.blockStateDataOffset() + source.blockStateRawByteLength();
            int biomeBytesLength = originalSectionBytes.length - biomesOffset;
            FriendlyByteBuf sectionBuf = new FriendlyByteBuf(Objects.requireNonNull(Unpooled.buffer(), "buffer"));
            try {
                sectionBuf.writeBytes(originalSectionBytes, 0, 4);
                sectionBuf.writeBytes(stateBuf, 0, stateBuf.readableBytes());
                sectionBuf.writeBytes(originalSectionBytes, biomesOffset, biomeBytesLength);
                byte[] data = new byte[sectionBuf.readableBytes()];
                sectionBuf.readBytes(data);
                return data;
            } finally {
                sectionBuf.release();
            }
        } finally {
            stateBuf.release();
        }
    }

    private List<BlockState> buildTargetPaletteEntries(SectionSnapshot source, List<BlockState> fakeStates) {
        if (source.globalPalette()) {
            return List.of();
        }
        LinkedHashSet<BlockState> entries = new LinkedHashSet<>(Arrays.asList(source.paletteEntries()));
        entries.addAll(fakeStates);
        return new ArrayList<>(entries);
    }

    static int computeTargetBits(int paletteSize) {
        return computeTargetBits(paletteSize, false);
    }

    static int computeTargetBits(int paletteSize, boolean forceGlobalPalette) {
        if (forceGlobalPalette || paletteSize > 256) {
            return Mth.ceillog2(Block.BLOCK_STATE_REGISTRY.size());
        }
        if (paletteSize <= 1) {
            return 0;
        }
        if (paletteSize <= 16) {
            return 4;
        }
        if (paletteSize <= 32) {
            return 5;
        }
        if (paletteSize <= 64) {
            return 6;
        }
        if (paletteSize <= 128) {
            return 7;
        }
        return 8;
    }

    private int[] remapIndicesForTargetPalette(SectionSnapshot source, List<BlockState> targetPaletteEntries, int targetBits) {
        if (targetBits > 8) {
            int[] remapped = new int[source.unpackedIndices().length];
            for (int i = 0; i < source.unpackedIndices().length; i++) {
                remapped[i] = Block.BLOCK_STATE_REGISTRY.getId(Objects.requireNonNull(source.stateAt(i), "source.stateAt(...)"));
            }
            return remapped;
        }
        return remapDensePaletteIndices(source::stateAt, source.stateCount(), targetPaletteEntries);
    }

    static int[] remapDensePaletteIndices(IntFunction<BlockState> states, int stateCount, List<? extends BlockState> targetPaletteEntries) {
        IntFunction<BlockState> safeStates = Objects.requireNonNull(states, "states");
        List<? extends BlockState> safeTargetPaletteEntries = Objects.requireNonNull(targetPaletteEntries, "targetPaletteEntries");
        int[] remapped = new int[stateCount];
        for (int i = 0; i < stateCount; i++) {
            remapped[i] = indexOfState(safeTargetPaletteEntries, Objects.requireNonNull(safeStates.apply(i), "states[" + i + "]"));
        }
        return remapped;
    }

    static int[] remapDensePaletteIndices(BlockState[] states, List<? extends BlockState> targetPaletteEntries) {
        BlockState[] safeStates = Objects.requireNonNull(states, "states");
        return remapDensePaletteIndices(index -> safeStates[index], safeStates.length, targetPaletteEntries);
    }

    private int resolveTargetPaletteId(List<BlockState> targetPaletteEntries, int targetBits, BlockState state) {
        if (targetBits > 8) {
            return Block.BLOCK_STATE_REGISTRY.getId(Objects.requireNonNull(state, "state"));
        }
        return indexOfState(targetPaletteEntries, state);
    }

    private static int indexOfState(List<? extends BlockState> states, BlockState target) {
        for (int i = 0; i < states.size(); i++) {
            if (states.get(i) == target) {
                return i;
            }
        }
        throw new IllegalStateException("Missing palette entry for " + target);
    }

    private void writeTargetPalette(FriendlyByteBuf buf, List<BlockState> targetPaletteEntries, int targetBits) {
        if (targetBits > 8) {
            return;
        }
        if (targetBits == 0) {
            BlockState onlyState = Objects.requireNonNull(targetPaletteEntries.getFirst(), "targetPaletteEntries.getFirst()");
            buf.writeVarInt(Block.BLOCK_STATE_REGISTRY.getId(onlyState));
            return;
        }
        buf.writeVarInt(targetPaletteEntries.size());
        for (BlockState state : targetPaletteEntries) {
            buf.writeVarInt(Block.BLOCK_STATE_REGISTRY.getId(Objects.requireNonNull(state, "state")));
        }
    }

    static int readPackedValue(byte[] buffer, int dataOffset, int bits, int index) {
        long mask = (1L << bits) - 1L;
        int bitIndex = index * bits;
        int longIndex = bitIndex >>> 6;
        int bitOffset = bitIndex & 63;
        long current = readPackedLong(buffer, dataOffset, longIndex);
        long value = (current >>> bitOffset) & mask;
        int spill = bitOffset + bits - 64;
        if (spill > 0) {
            long next = readPackedLong(buffer, dataOffset, longIndex + 1);
            value |= (next & ((1L << spill) - 1L)) << (bits - spill);
        }
        return (int) value;
    }

    static void writePackedValue(byte[] buffer, int dataOffset, int bits, int index, int value) {
        long mask = (1L << bits) - 1L;
        int bitIndex = index * bits;
        int longIndex = bitIndex >>> 6;
        int bitOffset = bitIndex & 63;
        long current = readPackedLong(buffer, dataOffset, longIndex);
        long unsignedValue = value & mask;
        current = (current & ~(mask << bitOffset)) | (unsignedValue << bitOffset);
        writePackedLong(buffer, dataOffset, longIndex, current);

        int spill = bitOffset + bits - 64;
        if (spill > 0) {
            long next = readPackedLong(buffer, dataOffset, longIndex + 1);
            next = (next >>> spill) << spill;
            next |= unsignedValue >>> (bits - spill);
            writePackedLong(buffer, dataOffset, longIndex + 1, next);
        }
    }

    private static long readPackedLong(byte[] buffer, int dataOffset, int longIndex) {
        int offset = dataOffset + longIndex * Long.BYTES;
        return ((((long) buffer[offset]) << 56)
            | (((long) buffer[offset + 1] & 0xff) << 48)
            | (((long) buffer[offset + 2] & 0xff) << 40)
            | (((long) buffer[offset + 3] & 0xff) << 32)
            | (((long) buffer[offset + 4] & 0xff) << 24)
            | (((long) buffer[offset + 5] & 0xff) << 16)
            | (((long) buffer[offset + 6] & 0xff) << 8)
            | (((long) buffer[offset + 7] & 0xff)));
    }

    private static void writePackedLong(byte[] buffer, int dataOffset, int longIndex, long value) {
        int offset = dataOffset + longIndex * Long.BYTES;
        buffer[offset] = (byte) (value >>> 56);
        buffer[offset + 1] = (byte) (value >>> 48);
        buffer[offset + 2] = (byte) (value >>> 40);
        buffer[offset + 3] = (byte) (value >>> 32);
        buffer[offset + 4] = (byte) (value >>> 24);
        buffer[offset + 5] = (byte) (value >>> 16);
        buffer[offset + 6] = (byte) (value >>> 8);
        buffer[offset + 7] = (byte) value;
    }

    private BlockState chooseFakeState(
        ReplacementProfile replacementProfile,
        long worldSeed,
        int blockX,
        int blockY,
        int blockZ,
        BlockState real,
        Palette palette,
        ObfuscationRandom random
    ) {
        if (palette.engineMode == 3) {
            int idx = random != null
                ? random.nextDecoyIndex()
                : engineMode3LayerIndex(
                    worldSeed,
                    SectionPos.blockToSectionCoord(blockX),
                    SectionPos.blockToSectionCoord(blockY),
                    SectionPos.blockToSectionCoord(blockZ),
                    blockY & 15,
                    palette.mode2DecoyStates.size()
                );
            BlockState state = palette.mode2DecoyStates.get(idx);
            if (state.getBlock() == real.getBlock() && palette.mode2DecoyStates.size() > 1) {
                state = palette.mode2DecoyStates.get((idx + 1) % palette.mode2DecoyStates.size());
            }
            if (state.getBlock() == real.getBlock()) {
                int fallback = random != null
                    ? random.nextReplacementIndex(palette.replacementStates.size())
                    : boundedXorShiftIndex(mixBlockSeed(worldSeed, blockX, blockY, blockZ, 0x9E3779B9), palette.replacementStates.size());
                return palette.replacementStates.get(fallback);
            }
            return state;
        }
        if (palette.engineMode != 2) {
            return naturalReplacement(replacementProfile, blockY, real);
        }
        int idx = random != null
            ? random.nextDecoyIndex()
            : engineMode2PositionIndex(worldSeed, blockX, blockY, blockZ, palette.mode2DecoyStates.size());
        BlockState state = palette.mode2DecoyStates.get(idx);
        if (state.getBlock() == real.getBlock() && palette.mode2DecoyStates.size() > 1) {
            state = palette.mode2DecoyStates.get((idx + 1) % palette.mode2DecoyStates.size());
        }
        if (state.getBlock() == real.getBlock()) {
            int fallback = random != null
                ? random.nextReplacementIndex(palette.replacementStates.size())
                : boundedXorShiftIndex(mixBlockSeed(worldSeed, blockX, blockY, blockZ, 0x9E3779B9), palette.replacementStates.size());
            state = palette.replacementStates.get(fallback);
        }
        return state;
    }

    private BlockState chooseFakeState(ServerLevel level, BlockPos pos, BlockState real, Palette palette, ObfuscationRandom random) {
        if (palette.engineMode == 3) {
            return chooseLayerFakeState(level, pos, real, palette, random);
        }
        if (palette.engineMode != 2) {
            return naturalReplacement(level, pos, real);
        }
        int idx = random != null
            ? random.nextDecoyIndex()
            : engineMode2PositionIndex(level.getSeed(), pos.getX(), pos.getY(), pos.getZ(), palette.mode2DecoyStates.size());
        BlockState state = palette.mode2DecoyStates.get(idx);
        if (state.getBlock() == real.getBlock() && palette.mode2DecoyStates.size() > 1) {
            state = palette.mode2DecoyStates.get((idx + 1) % palette.mode2DecoyStates.size());
        }
        if (state.getBlock() == real.getBlock()) {
            int fallback = random != null
                ? random.nextReplacementIndex(palette.replacementStates.size())
                : boundedXorShiftIndex(mixBlockSeed(level.getSeed(), pos.getX(), pos.getY(), pos.getZ(), 0x9E3779B9), palette.replacementStates.size());
            state = palette.replacementStates.get(fallback);
        }
        return state;
    }

    private BlockState chooseLayerFakeState(ServerLevel level, BlockPos pos, BlockState real, Palette palette, ObfuscationRandom random) {
        int idx = random != null
            ? random.nextDecoyIndex()
            : engineMode3LayerIndex(
                level.getSeed(),
                SectionPos.blockToSectionCoord(pos.getX()),
                SectionPos.blockToSectionCoord(pos.getY()),
                SectionPos.blockToSectionCoord(pos.getZ()),
                pos.getY() & 15,
                palette.mode2DecoyStates.size()
            );
        BlockState state = palette.mode2DecoyStates.get(idx);
        if (state.getBlock() == real.getBlock() && palette.mode2DecoyStates.size() > 1) {
            state = palette.mode2DecoyStates.get((idx + 1) % palette.mode2DecoyStates.size());
        }
        if (state.getBlock() == real.getBlock()) {
            int fallback = random != null
                ? random.nextReplacementIndex(palette.replacementStates.size())
                : boundedXorShiftIndex(mixBlockSeed(level.getSeed(), pos.getX(), pos.getY(), pos.getZ(), 0x9E3779B9), palette.replacementStates.size());
            return palette.replacementStates.get(fallback);
        }
        return state;
    }

    static int engineMode3LayerIndex(long worldSeed, int sectionX, int sectionY, int sectionZ, int localY, int bound) {
        if (bound <= 1) {
            return 0;
        }
        int state = mixSectionSeed(worldSeed, sectionX, sectionY, sectionZ, 0x6D2B79F5);
        int layer = Math.max(0, localY);
        int next = 0;
        for (int i = 0; i <= layer; i++) {
            state = xorshift32(state);
            next = scaleUnsignedBound(state, bound);
        }
        return next;
    }

    static int engineMode2PositionIndex(long worldSeed, int blockX, int blockY, int blockZ, int bound) {
        if (bound <= 1) {
            return 0;
        }
        return boundedXorShiftIndex(mixBlockSeed(worldSeed, blockX, blockY, blockZ, 0x6D2B79F5), bound);
    }

    static int boundedXorShiftIndex(int seed, int bound) {
        if (bound <= 1) {
            return 0;
        }
        int state = xorshift32(seed);
        return scaleUnsignedBound(state, bound);
    }

    private static int mixBlockSeed(long worldSeed, int blockX, int blockY, int blockZ, int salt) {
        long mixed = worldSeed;
        mixed ^= ((long) blockX * 0x9E3779B97F4A7C15L);
        mixed ^= ((long) blockY * 0xC2B2AE3D27D4EB4FL);
        mixed ^= ((long) blockZ * 0x165667B19E3779F9L);
        mixed ^= Integer.toUnsignedLong(salt);
        return normalizeNonZeroSeed((int) (mixed ^ (mixed >>> 32)), salt);
    }

    private static int mixSectionSeed(long worldSeed, int sectionX, int sectionY, int sectionZ, int salt) {
        long mixed = worldSeed;
        mixed ^= ((long) sectionX * 0x9E3779B97F4A7C15L);
        mixed ^= ((long) sectionY * 0xC2B2AE3D27D4EB4FL);
        mixed ^= ((long) sectionZ * 0x165667B19E3779F9L);
        mixed ^= Integer.toUnsignedLong(salt);
        return normalizeNonZeroSeed((int) (mixed ^ (mixed >>> 32)), salt);
    }

    private static int normalizeNonZeroSeed(int seed, int fallback) {
        return seed == 0 ? fallback : seed;
    }

    static int paperRuntimeRandomSeed() {
        int seed;
        while ((seed = ThreadLocalRandom.current().nextInt()) == 0) {
            // Paper avoids zero because xorshift would stay at zero.
        }
        return seed;
    }

    private static int xorshift32(int state) {
        int next = normalizeNonZeroSeed(state, 0x6D2B79F5);
        next ^= next << 13;
        next ^= next >>> 17;
        next ^= next << 5;
        return next;
    }

    private static int scaleUnsignedBound(int state, int bound) {
        return (int) ((Integer.toUnsignedLong(state) * bound) >>> 32);
    }

    private ObfuscationRandom createObfuscationRandom(Palette palette) {
        return new ObfuscationRandom(palette.engineMode, palette.mode2DecoyStates.size());
    }

    private ChunkPacketInfo createChunkPacketInfo(
        ServerLevel level,
        LevelChunk chunk,
        ClientboundLevelChunkWithLightPacket packet,
        Palette palette
    ) {
        LevelChunkSection[] sourceSections = chunk.getSections();
        int[] sourceSectionSizes = new int[sourceSections.length];
        int[] sourceSectionOffsets = new int[sourceSections.length];
        int sourceCursor = 0;
        for (int i = 0; i < sourceSections.length; i++) {
            LevelChunkSection section = sourceSections[i];
            if (section == null) {
                continue;
            }
            sourceSectionOffsets[i] = sourceCursor;
            sourceSectionSizes[i] = section.getSerializedSize();
            sourceCursor += sourceSectionSizes[i];
        }
        return new ChunkPacketInfo(
            sourceSections,
            level.getChunkSource().getChunkNow(MinecraftCompat.chunkX(chunk.getPos()) - 1, MinecraftCompat.chunkZ(chunk.getPos())),
            level.getChunkSource().getChunkNow(MinecraftCompat.chunkX(chunk.getPos()) + 1, MinecraftCompat.chunkZ(chunk.getPos())),
            level.getChunkSource().getChunkNow(MinecraftCompat.chunkX(chunk.getPos()), MinecraftCompat.chunkZ(chunk.getPos()) - 1),
            level.getChunkSource().getChunkNow(MinecraftCompat.chunkX(chunk.getPos()), MinecraftCompat.chunkZ(chunk.getPos()) + 1),
            ((ClientboundLevelChunkPacketDataAccessor) packet.getChunkData()).meowconsole$getBuffer(),
            sourceSectionSizes,
            sourceSectionOffsets,
            MinecraftCompat.minBuildY(level),
            chunk.getPos().getMinBlockX(),
            chunk.getPos().getMinBlockZ(),
            Math.min(palette.maxBlockHeight, MinecraftCompat.maxBuildY(level))
        );
    }

    private ChunkPacketInfo createChunkPacketInfo(ChunkSendContext sendContext) {
        return createChunkPacketInfo(
            sendContext.level(),
            sendContext.chunk(),
            sendContext.packet(),
            sendContext.palette()
        );
    }

    private boolean isHiddenState(Palette palette, BlockState state) {
        return state != null && palette.hiddenStateLookup[registryStateId(state)];
    }

    private boolean isReplacementState(Palette palette, BlockState state) {
        return state != null && palette.replacementStateLookup[registryStateId(state)];
    }

    private boolean isTargetState(Palette palette, BlockState state) {
        return state != null && palette.targetStateLookup[registryStateId(state)];
    }

    private boolean isSolidState(Palette palette, BlockState state) {
        return state != null && palette.solidStateLookup[registryStateId(state)];
    }

    private static int registryStateId(BlockState state) {
        return Block.BLOCK_STATE_REGISTRY.getId(Objects.requireNonNull(state, "state"));
    }

    static boolean[] buildStateLookup(Set<Block> blocks) {
        boolean[] lookup = new boolean[Block.BLOCK_STATE_REGISTRY.size()];
        for (int i = 0; i < lookup.length; i++) {
            BlockState state = Block.BLOCK_STATE_REGISTRY.byId(i);
            if (state != null && blocks.contains(state.getBlock())) {
                lookup[i] = true;
            }
        }
        return lookup;
    }

    static boolean[] buildSolidStateLookup(boolean lavaObscures) {
        boolean[] lookup = new boolean[Block.BLOCK_STATE_REGISTRY.size()];
        for (int i = 0; i < lookup.length; i++) {
            BlockState state = Block.BLOCK_STATE_REGISTRY.byId(i);
            if (state != null) {
                lookup[i] = isPrecomputedSolidState(lavaObscures, state);
            }
        }
        return lookup;
    }

    private BlockState naturalReplacement(ServerLevel level, BlockPos pos, BlockState real) {
        return naturalReplacement(replacementProfile(level), pos.getY(), real);
    }

    private BlockState naturalReplacement(ReplacementProfile replacementProfile, int y, BlockState real) {
        if (replacementProfile == ReplacementProfile.NETHER) {
            return Blocks.NETHERRACK.defaultBlockState();
        }
        if (replacementProfile == ReplacementProfile.END) {
            return Blocks.END_STONE.defaultBlockState();
        }
        if (y < 0) {
            return Blocks.DEEPSLATE.defaultBlockState();
        }
        return Blocks.STONE.defaultBlockState();
    }

    static BlockState debugNaturalReplacementForTest(String replacementProfile, int y, BlockState real) {
        return new FakeOreService().naturalReplacement(
            ReplacementProfile.valueOf(Objects.requireNonNull(replacementProfile, "replacementProfile")),
            y,
            Objects.requireNonNull(real, "real")
        );
    }

    private ReplacementProfile replacementProfile(ServerLevel level) {
        if (level.dimension() == Level.NETHER) {
            return ReplacementProfile.NETHER;
        }
        if (level.dimension() == Level.END) {
            return ReplacementProfile.END;
        }
        return ReplacementProfile.OVERWORLD;
    }

    private Palette paletteFor(ServerLevel level) {
        String key = normalizeDimensionKey(level.dimension() == Level.NETHER ? "minecraft:the_nether" : level.dimension() == Level.END ? "minecraft:the_end" : "minecraft:overworld");
        return dimensionPalettes.getOrDefault(key, globalPalette);
    }

    private String normalizeDimensionKey(String key) {
        String candidate = Objects.requireNonNullElse(key, "").trim();
        if (candidate.startsWith("ResourceKey[")) {
            int slashIndex = candidate.indexOf('/');
            int endIndex = candidate.lastIndexOf(']');
            if (slashIndex >= 0 && endIndex > slashIndex) {
                candidate = candidate.substring(slashIndex + 1, endIndex).trim();
            }
        }
        return switch (candidate) {
            case "overworld", "minecraft:overworld" -> "minecraft:overworld";
            case "nether", "the_nether", "minecraft:the_nether" -> "minecraft:the_nether";
            case "end", "the_end", "minecraft:the_end" -> "minecraft:the_end";
            default -> candidate;
        };
    }

    private Palette buildPalette(
        boolean enabled,
        int engineMode,
        int maxBlockHeight,
        boolean onlyObfuscateHiddenBlocks,
        boolean mode2ObfuscateReplacementBlocks,
        boolean guaranteeHideAllHiddenBlocks,
        boolean lavaObscures,
        boolean usePermission,
        String bypassPermission,
        int updateRadius,
        int updateIntervalTicks,
        int maxBlocksPerChunk,
        List<String> hiddenIds,
        List<String> replacementIds
    ) {
        List<BlockState> replacementStates = new ArrayList<>();
        Set<Block> replacementSet = new LinkedHashSet<>();
        Set<Block> hiddenSet = new LinkedHashSet<>();

        for (String id : hiddenIds) {
            if (id == null || id.isBlank()) {
                continue;
            }
            MinecraftCompat.findBlockById(id).ifPresent(hiddenSet::add);
        }
        for (String id : replacementIds) {
            if (id == null || id.isBlank()) {
                continue;
            }
            MinecraftCompat.findBlockById(id).ifPresent(block -> {
                replacementSet.add(block);
                replacementStates.add(block.defaultBlockState());
            });
        }
        if (replacementStates.isEmpty()) {
            replacementStates.add(Blocks.STONE.defaultBlockState());
            replacementStates.add(Blocks.DEEPSLATE.defaultBlockState());
            replacementStates.add(Blocks.NETHERRACK.defaultBlockState());
        }

        LinkedHashSet<BlockState> decoy = new LinkedHashSet<>();
        for (Block block : hiddenSet) {
            if (!isEntityBlock(block)) {
                decoy.add(block.defaultBlockState());
            }
        }
        if (decoy.isEmpty()) {
            decoy.addAll(replacementStates);
        }
        boolean[] hiddenStateLookup = buildStateLookup(hiddenSet);
        boolean[] replacementStateLookup = buildStateLookup(replacementSet);
        boolean[] targetStateLookup = buildTargetStateLookup(hiddenStateLookup, replacementStateLookup, engineMode, mode2ObfuscateReplacementBlocks);
        boolean[] solidStateLookup = buildSolidStateLookup(lavaObscures);

        return new Palette(
            enabled,
            engineMode,
            FakeOreConfig.normalizeMaxBlockHeight(maxBlockHeight),
            onlyObfuscateHiddenBlocks,
            mode2ObfuscateReplacementBlocks,
            guaranteeHideAllHiddenBlocks,
            lavaObscures,
            usePermission,
            normalizePermissionNode(bypassPermission),
            FakeOreConfig.normalizeUpdateRadius(updateRadius),
            Math.max(1, updateIntervalTicks),
            Math.max(64, maxBlocksPerChunk),
            new ArrayList<>(hiddenIds),
            new ArrayList<>(replacementIds),
            hiddenSet,
            replacementSet,
            replacementStates,
            new ArrayList<>(decoy),
            hiddenStateLookup,
            replacementStateLookup,
            targetStateLookup,
            solidStateLookup
        );
    }

    static List<String> debugMode2DecoyBlockIdsForTest(List<String> hiddenIds, List<String> replacementIds) {
        Palette palette = new FakeOreService().buildPalette(
            true,
            2,
            64,
            false,
            true,
            true,
            false,
            false,
            "paper.antixray.bypass",
            2,
            10,
            32768,
            hiddenIds,
            replacementIds
        );
        List<String> ids = new ArrayList<>();
        for (BlockState state : palette.mode2DecoyStates) {
            ids.add(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        }
        return ids;
    }

    static boolean debugTargetStateForTest(int engineMode, boolean mode2ObfuscateReplacementBlocks, BlockState state) {
        Palette palette = new FakeOreService().buildPalette(
            true,
            engineMode,
            64,
            false,
            mode2ObfuscateReplacementBlocks,
            true,
            false,
            false,
            "paper.antixray.bypass",
            2,
            10,
            32768,
            List.of("minecraft:diamond_ore"),
            List.of("minecraft:stone")
        );
        return palette.targetStateLookup()[registryStateId(Objects.requireNonNull(state, "state"))];
    }

    private static boolean[] buildTargetStateLookup(
        boolean[] hiddenStateLookup,
        boolean[] replacementStateLookup,
        int engineMode,
        boolean mode2ObfuscateReplacementBlocks
    ) {
        boolean[] targetStateLookup = Arrays.copyOf(hiddenStateLookup, hiddenStateLookup.length);
        if (engineMode != 1 && (engineMode != 2 || mode2ObfuscateReplacementBlocks)) {
            for (int i = 0; i < targetStateLookup.length; i++) {
                targetStateLookup[i] = targetStateLookup[i] || replacementStateLookup[i];
            }
        }
        return targetStateLookup;
    }

    private static boolean obfuscatesReplacementBlocks(Palette palette) {
        return palette.engineMode != 1 && (palette.engineMode != 2 || palette.mode2ObfuscateReplacementBlocks);
    }

    private record Palette(
        boolean enabled,
        int engineMode,
        int maxBlockHeight,
        boolean onlyObfuscateHiddenBlocks,
        boolean mode2ObfuscateReplacementBlocks,
        boolean guaranteeHideAllHiddenBlocks,
        boolean lavaObscures,
        boolean usePermission,
        String bypassPermission,
        int updateRadius,
        int updateIntervalTicks,
        int maxBlocksPerChunk,
        List<String> hiddenBlockIds,
        List<String> replacementBlockIds,
        Set<Block> hiddenSet,
        Set<Block> replacementSet,
        List<BlockState> replacementStates,
        List<BlockState> mode2DecoyStates,
        boolean[] hiddenStateLookup,
        boolean[] replacementStateLookup,
        boolean[] targetStateLookup,
        boolean[] solidStateLookup
    ) {
    }

    private static boolean isEntityBlock(Block block) {
        return block instanceof net.minecraft.world.level.block.EntityBlock;
    }

    private static String normalizePermissionNode(String permissionNode) {
        String normalized = permissionNode == null ? "" : permissionNode.trim();
        return normalized.isEmpty() ? "paper.antixray.bypass" : normalized;
    }

    static String formatConfigSummary(
        int defaultMode,
        int defaultMaxHeight,
        boolean enforceEngineMode2,
        boolean guaranteeHideAllHiddenBlocks,
        boolean asyncChunkRewrite,
        int asyncWorkerThreads,
        int asyncQueueSize,
        int asyncCapacity,
        Map<String, DimensionSummary> dimensionSummaries,
        int globalHiddenCount,
        int globalReplacementCount
    ) {
        StringJoiner dimensionJoiner = new StringJoiner(", ", "[", "]");
        for (Map.Entry<String, DimensionSummary> entry : dimensionSummaries.entrySet()) {
            DimensionSummary summary = entry.getValue();
            dimensionJoiner.add(entry.getKey()
                + "(enabled=" + summary.enabled()
                + ",mode=" + summary.mode()
                + ",max=" + summary.maxBlockHeight()
                + ")");
        }
        return "default-mode=" + defaultMode
            + ", default-max-height=" + defaultMaxHeight
            + ", enforce-mode2=" + enforceEngineMode2
            + ", guarantee-hide-all-hidden-blocks=" + guaranteeHideAllHiddenBlocks
            + ", async-chunk-rewrite=" + asyncChunkRewrite
            + ", async-worker-threads=" + asyncWorkerThreads
            + ", async-queue-size=" + asyncQueueSize
            + ", async-capacity=" + asyncCapacity
            + ", dimensions=" + dimensionJoiner
            + ", global-hidden=" + globalHiddenCount
            + ", global-replacement=" + globalReplacementCount;
    }

    private RuntimeStatusSnapshot captureStatusSnapshot() {
        if (globalPalette == null) {
            return new RuntimeStatusSnapshot(
                false,
                "config not loaded yet",
                configPathSummary(),
                false,
                0,
                0,
                false,
                false,
                0,
                0,
                0,
                List.of()
            );
        }
        List<DimensionStatus> dimensions = new ArrayList<>();
        List<String> keys = new ArrayList<>(dimensionPalettes.keySet());
        keys.sort(String::compareToIgnoreCase);
        for (String key : keys) {
            Palette palette = dimensionPalettes.get(key);
            if (palette == null) {
                continue;
            }
            dimensions.add(new DimensionStatus(
                key,
                palette.enabled,
                palette.engineMode,
                palette.maxBlockHeight,
                palette.hiddenBlockIds.size(),
                palette.replacementBlockIds.size()
            ));
        }
        return new RuntimeStatusSnapshot(
            true,
            config.loadSummary(),
            configPathSummary(),
            globalPalette.enabled,
            globalPalette.engineMode,
            globalPalette.maxBlockHeight,
            config.enforceEngineMode2,
            config.asyncChunkRewrite,
            Math.max(1, config.asyncWorkerThreads),
            Math.max(0, config.asyncQueueSize),
            asyncRewriteCapacity(),
            List.copyOf(dimensions)
        );
    }

    private List<String> formatStatusLines(RuntimeStatusSnapshot snapshot) {
        List<String> lines = new ArrayList<>();
        if (!snapshot.loaded()) {
            lines.add("status: config not loaded yet");
            lines.add("config-path: " + snapshot.configPath());
            return lines;
        }
        lines.add("status: enabled=" + snapshot.enabled()
            + ", mode=" + snapshot.engineMode()
            + ", max-block-height=" + snapshot.maxBlockHeight()
            + ", enforce-mode2=" + snapshot.enforceEngineMode2());
        lines.add("async: enabled=" + snapshot.asyncChunkRewrite()
            + ", workerThreads=" + snapshot.asyncWorkerThreads()
            + ", queueSize=" + snapshot.asyncQueueSize()
            + ", capacity=" + snapshot.asyncCapacity());
        lines.add("config-path: " + snapshot.configPath());
        if (snapshot.dimensions().isEmpty()) {
            lines.add("dimensions: none");
            return lines;
        }
        for (DimensionStatus dimension : snapshot.dimensions()) {
            lines.add("dimension " + dimension.dimensionKey()
                + ": enabled=" + dimension.enabled()
                + ", mode=" + dimension.engineMode()
                + ", max-block-height=" + dimension.maxBlockHeight()
                + ", hidden=" + dimension.hiddenCount()
                + ", replacement=" + dimension.replacementCount());
        }
        return lines;
    }

    private List<String> describeConfigChanges(RuntimeStatusSnapshot before, RuntimeStatusSnapshot after) {
        if (before == null || !before.loaded()) {
            return List.of("changes: initialized config state");
        }
        List<String> changes = new ArrayList<>();
        appendChange(changes, "enabled", before.enabled(), after.enabled());
        appendChange(changes, "engine-mode", before.engineMode(), after.engineMode());
        appendChange(changes, "max-block-height", before.maxBlockHeight(), after.maxBlockHeight());
        appendChange(changes, "enforce-mode2", before.enforceEngineMode2(), after.enforceEngineMode2());
        appendChange(changes, "async-chunk-rewrite", before.asyncChunkRewrite(), after.asyncChunkRewrite());
        appendChange(changes, "async-worker-threads", before.asyncWorkerThreads(), after.asyncWorkerThreads());
        appendChange(changes, "async-queue-size", before.asyncQueueSize(), after.asyncQueueSize());
        appendChange(changes, "async-capacity", before.asyncCapacity(), after.asyncCapacity());

        Map<String, DimensionStatus> beforeByKey = new LinkedHashMap<>();
        for (DimensionStatus dimension : before.dimensions()) {
            beforeByKey.put(dimension.dimensionKey(), dimension);
        }
        Map<String, DimensionStatus> afterByKey = new LinkedHashMap<>();
        for (DimensionStatus dimension : after.dimensions()) {
            afterByKey.put(dimension.dimensionKey(), dimension);
        }
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        keys.addAll(beforeByKey.keySet());
        keys.addAll(afterByKey.keySet());
        for (String key : keys) {
            DimensionStatus previous = beforeByKey.get(key);
            DimensionStatus current = afterByKey.get(key);
            if (previous == null && current != null) {
                changes.add("dimension " + key + " added");
                continue;
            }
            if (previous != null && current == null) {
                changes.add("dimension " + key + " removed");
                continue;
            }
            if (previous == null) {
                continue;
            }
            if (current == null) {
                continue;
            }
            appendDimensionChange(changes, key, "enabled", previous.enabled(), current.enabled());
            appendDimensionChange(changes, key, "mode", previous.engineMode(), current.engineMode());
            appendDimensionChange(changes, key, "max-block-height", previous.maxBlockHeight(), current.maxBlockHeight());
            appendDimensionChange(changes, key, "hidden", previous.hiddenCount(), current.hiddenCount());
            appendDimensionChange(changes, key, "replacement", previous.replacementCount(), current.replacementCount());
        }
        if (changes.isEmpty()) {
            return List.of("changes: no effective runtime changes");
        }
        changes.add(0, "changes:");
        return changes;
    }

    private static void appendChange(List<String> changes, String key, Object before, Object after) {
        if (!Objects.equals(before, after)) {
            changes.add(key + ": " + before + " -> " + after);
        }
    }

    private static void appendDimensionChange(List<String> changes, String dimensionKey, String key, Object before, Object after) {
        if (!Objects.equals(before, after)) {
            changes.add("dimension " + dimensionKey + " " + key + ": " + before + " -> " + after);
        }
    }

    static String inspectDecisionReason(boolean enabled, boolean inRange, boolean hidden, boolean replacement, boolean exposed) {
        if (!enabled) {
            return "disabled";
        }
        if (!inRange) {
            return "out-of-range";
        }
        if (!hidden && !replacement) {
            return "not-targeted";
        }
        if (exposed) {
            return "exposed";
        }
        return hidden ? "hidden-block-obfuscated" : "replacement-block-obfuscated";
    }

    record DimensionSummary(
        boolean enabled,
        int mode,
        int maxBlockHeight
    ) {
    }

    public record ReloadReport(
        String configLoadSummary,
        List<String> statusLines,
        List<String> changeLines
    ) {
    }

    private record RuntimeStatusSnapshot(
        boolean loaded,
        String configLoadSummary,
        String configPath,
        boolean enabled,
        int engineMode,
        int maxBlockHeight,
        boolean enforceEngineMode2,
        boolean asyncChunkRewrite,
        int asyncWorkerThreads,
        int asyncQueueSize,
        int asyncCapacity,
        List<DimensionStatus> dimensions
    ) {
    }

    private record DimensionStatus(
        String dimensionKey,
        boolean enabled,
        int engineMode,
        int maxBlockHeight,
        int hiddenCount,
        int replacementCount
    ) {
    }

    private static final class PlayerMask {
        private final Long2ObjectOpenHashMap<ShortOpenHashSet> bySection = new Long2ObjectOpenHashMap<>();

        boolean isEmpty() {
            return bySection.isEmpty();
        }

        void putSection(long sectionLong, ShortOpenHashSet rels) {
            bySection.put(sectionLong, rels);
        }

        void removeSection(long sectionLong) {
            bySection.remove(sectionLong);
        }

        boolean contains(long sectionLong, short rel) {
            ShortOpenHashSet set = bySection.get(sectionLong);
            return set != null && set.contains(rel);
        }

        void remove(long sectionLong, short rel) {
            ShortOpenHashSet set = bySection.get(sectionLong);
            if (set == null) {
                return;
            }
            set.remove(rel);
            if (set.isEmpty()) {
                bySection.remove(sectionLong);
            }
        }

        ShortOpenHashSet getSection(long sectionLong) {
            return bySection.get(sectionLong);
        }
    }

    private static final class SectionVisibility {
        private final boolean[] exposed;

        private SectionVisibility(boolean[] exposed) {
            this.exposed = Arrays.copyOf(exposed, exposed.length);
        }

        private boolean isExposed(int lx, int ly, int lz) {
            if (lx < 0 || lx > 15 || ly < 0 || ly > 15 || lz < 0 || lz > 15) {
                return false;
            }
            int index = (ly << 8) | (lz << 4) | lx;
            return exposed[index];
        }
    }

    private static final class SectionStorageAccess {
        private static final Field DATA_FIELD = initDataField();
        private static final Method DATA_STORAGE_METHOD = initDataMethod("storage");
        private static final Method DATA_PALETTE_METHOD = initDataMethod("palette");
        private final PalettedContainer<BlockState> states;
        private BitStorage storage;
        private net.minecraft.world.level.chunk.Palette<BlockState> palette;

        private SectionStorageAccess(PalettedContainer<BlockState> states) {
            this.states = states;
            this.states.acquire();
            refresh();
        }

        private void refresh() {
            Object data = readData(states);
            this.storage = readStorage(data);
            this.palette = readPalette(data);
        }

        private BitStorage storage() {
            return storage;
        }

        private net.minecraft.world.level.chunk.Palette<BlockState> palette() {
            return palette;
        }

        private void close() {
            states.release();
        }

        private static Field initDataField() {
            try {
                Field field = PalettedContainer.class.getDeclaredField("data");
                field.setAccessible(true);
                return field;
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        private static Method initDataMethod(String name) {
            try {
                Method method = DATA_FIELD.getType().getDeclaredMethod(name);
                method.setAccessible(true);
                return method;
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        private static Object readData(PalettedContainer<BlockState> states) {
            try {
                return Objects.requireNonNull(DATA_FIELD.get(states), "states.data");
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Cannot access PalettedContainer.data", e);
            }
        }

        private static BitStorage readStorage(Object data) {
            try {
                return (BitStorage) Objects.requireNonNull(DATA_STORAGE_METHOD.invoke(data), "data.storage()");
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Cannot invoke PalettedContainer$Data.storage()", e);
            }
        }

        @SuppressWarnings("unchecked")
        private static net.minecraft.world.level.chunk.Palette<BlockState> readPalette(Object data) {
            try {
                return (net.minecraft.world.level.chunk.Palette<BlockState>) Objects.requireNonNull(DATA_PALETTE_METHOD.invoke(data), "data.palette()");
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Cannot invoke PalettedContainer$Data.palette()", e);
            }
        }
    }

    private record ChunkPacketInfo(
        LevelChunkSection[] sourceSections,
        LevelChunk westChunk,
        LevelChunk eastChunk,
        LevelChunk northChunk,
        LevelChunk southChunk,
        byte[] originalBuffer,
        int[] sourceSectionSizes,
        int[] sourceSectionOffsets,
        int minY,
        int baseX,
        int baseZ,
        int maxY
    ) {
    }

    private record ChunkSendContext(
        ServerGamePacketListenerImpl connection,
        ServerLevel level,
        LevelChunk chunk,
        ClientboundLevelChunkWithLightPacket packet,
        Palette palette
    ) {
    }

    private record ChunkRewritePreparation(
        UUID playerId,
        long worldSeed,
        ReplacementProfile replacementProfile,
        Palette palette,
        int chunkX,
        int chunkZ,
        int minY,
        int baseX,
        int baseZ,
        int maxY,
        byte[] originalBuffer,
        int[] sectionSizes,
        SectionSnapshot[] centerSections,
        SectionSnapshot[] westSections,
        SectionSnapshot[] eastSections,
        SectionSnapshot[] northSections,
        SectionSnapshot[] southSections
    ) {
        private AsyncChunkTask toAsyncChunkTask() {
            return new AsyncChunkTask(
                playerId,
                worldSeed,
                replacementProfile,
                chunkX,
                chunkZ,
                minY,
                baseX,
                baseZ,
                maxY,
                palette,
                originalBuffer.clone(),
                sectionSizes.clone(),
                centerSections,
                westSections,
                eastSections,
                northSections,
                southSections
            );
        }

        private void clearTrackedSections(PlayerMask playerMask) {
            for (SectionSnapshot section : centerSections) {
                if (section != null) {
                    playerMask.removeSection(SectionPos.asLong(chunkX, section.sectionY(), chunkZ));
                }
            }
        }

        private void applyTrackedSections(PlayerMask playerMask, Long2ObjectOpenHashMap<short[]> trackedBySection) {
            for (Long2ObjectMap.Entry<short[]> entry : trackedBySection.long2ObjectEntrySet()) {
                ShortOpenHashSet rels = new ShortOpenHashSet(entry.getValue());
                if (!rels.isEmpty()) {
                    playerMask.putSection(entry.getLongKey(), rels);
                }
            }
        }

        private PlayerMask resolvePlayerMask(Map<UUID, PlayerMask> maskedByPlayer) {
            return maskedByPlayer.computeIfAbsent(playerId, ignored -> new PlayerMask());
        }

        private void applyAsyncResult(PlayerMask playerMask, AsyncChunkResult result) {
            clearTrackedSections(playerMask);
            applyTrackedSections(playerMask, result.trackedBySection());
        }
    }

    private record ChunkRewriteRuntime(
        ExecutorService workerExecutor,
        Executor completionExecutor
    ) {
    }

    private record ChunkRewriteController(
        ChunkSendContext sendContext,
        ChunkRewritePreparation preparation,
        ChunkRewriteRuntime runtime
    ) {
    }

    private record AsyncChunkTask(
        UUID playerId,
        long worldSeed,
        ReplacementProfile replacementProfile,
        int chunkX,
        int chunkZ,
        int minY,
        int baseX,
        int baseZ,
        int maxY,
        Palette palette,
        byte[] originalBuffer,
        int[] sectionSizes,
        SectionSnapshot[] sourceSections,
        SectionSnapshot[] westSections,
        SectionSnapshot[] eastSections,
        SectionSnapshot[] northSections,
        SectionSnapshot[] southSections
    ) {
    }

    private record AsyncChunkResult(
        byte[] rewrittenBuffer,
        Long2ObjectOpenHashMap<short[]> trackedBySection,
        int changedSections,
        int changedBlocks,
        boolean changed
    ) {
    }

    private static final class PacketBitStorageWriter {
        private final byte[] buffer;
        private final int dataOffset;
        private final int bits;
        private final long mask;

        private PacketBitStorageWriter(byte[] buffer, int dataOffset, int bits) {
            this.buffer = buffer;
            this.dataOffset = dataOffset;
            this.bits = bits;
            this.mask = (1L << bits) - 1L;
        }

        private void set(int index, int value) {
            writePackedValue(buffer, dataOffset, bits, index, (int) (value & mask));
        }
    }

    private static final class SectionVisibilityScratch {
        private final boolean[] solid = new boolean[4096];
        private final boolean[] open = new boolean[4096];
        private final boolean[] exposed = new boolean[4096];
        private final boolean[][] current = new boolean[16][16];
        private final boolean[][] next = new boolean[16][16];
        private final boolean[][] nextNext = new boolean[16][16];
        private final int[] queue = new int[256];

        private void reset() {
            Arrays.fill(solid, false);
            Arrays.fill(open, false);
            Arrays.fill(exposed, false);
            clearLayerWindows();
        }

        private void clearLayerWindows() {
            clearLayerWindow(current);
            clearLayerWindow(next);
            clearLayerWindow(nextNext);
        }
    }

    private record SectionPaletteReadResult(
        boolean hasHidden,
        boolean hasReplacement,
        boolean hasTarget,
        boolean[] hiddenByIndex,
        boolean[] replacementByIndex,
        boolean[] solidByIndex
    ) {
        private boolean matches(boolean hiddenPass, boolean replacementPass) {
            if (hiddenPass && replacementPass) {
                return hasTarget;
            }
            if (hiddenPass) {
                return hasHidden;
            }
            if (replacementPass) {
                return hasReplacement;
            }
            return false;
        }

        private boolean hiddenAt(int index) {
            return hiddenByIndex[index];
        }

        private boolean replacementAt(int index) {
            return replacementByIndex[index];
        }

        private boolean solidAt(int index) {
            return solidByIndex[index];
        }
    }

    private record VisibilityNeighborhood(
        SectionPaletteReadResult down,
        SectionPaletteReadResult up,
        SectionPaletteReadResult west,
        SectionPaletteReadResult east,
        SectionPaletteReadResult north,
        SectionPaletteReadResult south
    ) {
    }

    private record VisibilityBuildResult(
        SectionVisibility visibility,
        SectionPaletteReadResult centerRead
    ) {
    }

    private record LoadedChunkSection(
        SectionPos sectionPos,
        LevelChunk chunk,
        LevelChunkSection section
    ) {
    }

    private record TrackedBlockLocation(
        long sectionLong,
        short rel
    ) {
    }

    private record BlockRevealContext(
        BlockState realState,
        VisibilityBuildResult visibilityBuild,
        boolean hidden,
        boolean replacement,
        boolean exposed,
        boolean shouldReveal
    ) {
    }

    private static final class SectionPaletteReadScratch {
        private final int[] unpackedIndices = new int[4096];
        private final boolean[] hiddenByIndex = new boolean[4096];
        private final boolean[] replacementByIndex = new boolean[4096];
        private final boolean[] solidByIndex = new boolean[4096];
        private boolean[] paletteHiddenFlags = new boolean[16];
        private boolean[] paletteReplacementFlags = new boolean[16];
        private boolean[] solidFlags = new boolean[16];

        private void ensurePaletteCapacity(int size) {
            if (solidFlags.length >= size) {
                return;
            }
            int nextSize = Math.max(size, solidFlags.length << 1);
            paletteHiddenFlags = Arrays.copyOf(paletteHiddenFlags, nextSize);
            paletteReplacementFlags = Arrays.copyOf(paletteReplacementFlags, nextSize);
            solidFlags = Arrays.copyOf(solidFlags, nextSize);
        }

        private SectionPaletteReadResult result(boolean hasHidden, boolean hasReplacement, boolean hasTarget) {
            return new SectionPaletteReadResult(
                hasHidden,
                hasReplacement,
                hasTarget,
                hiddenByIndex,
                replacementByIndex,
                solidByIndex
            );
        }
    }

    private static final class SectionObfuscationPlan {
        private final ShortOpenHashSet trackedInSection = new ShortOpenHashSet();
        private final IntArrayList changedIndices = new IntArrayList();
        private final List<BlockState> fakeStates = new ArrayList<>();

        private void recordLive(BlockPos pos, int stateIndex, BlockState fake) {
            BlockPos safePos = Objects.requireNonNull(pos, "pos");
            trackedInSection.add(SectionPos.sectionRelativePos(safePos));
            changedIndices.add(stateIndex);
            fakeStates.add(fake);
        }

        private void recordSnapshot(int stateIndex, BlockState fake) {
            trackedInSection.add((short) stateIndex);
            changedIndices.add(stateIndex);
            fakeStates.add(fake);
        }

        private boolean isEmpty() {
            return trackedInSection.isEmpty();
        }

        private boolean reachedBudget(int remainingBudget) {
            return remainingBudget != Integer.MAX_VALUE && changedIndices.size() >= remainingBudget;
        }

        private int changedBlocks() {
            return changedIndices.size();
        }

        private ShortOpenHashSet trackedInSection() {
            return trackedInSection;
        }

        private IntArrayList changedIndices() {
            return changedIndices;
        }

        private List<BlockState> fakeStates() {
            return fakeStates;
        }

        private short[] toShortArray() {
            return trackedInSection().toShortArray();
        }
    }

    private interface LayerObfuscationContext {
        int blockY(int ly);

        BlockState sourceState(int stateIndex, int lx, int ly, int lz);

        BlockState fakeState(int stateIndex, int lx, int ly, int lz, int y, BlockState sourceState, ObfuscationRandom random);

        void recordChange(SectionObfuscationPlan plan, int stateIndex, int lx, int ly, int lz, int y, BlockState fake);
    }

    private enum ReplacementProfile {
        OVERWORLD,
        NETHER,
        END
    }

    private record SectionSnapshot(
        int sectionY,
        int packetOffset,
        int serializedSize,
        int blockStateBits,
        int blockStateDataOffset,
        int blockStateRawByteLength,
        boolean globalPalette,
        BlockState[] paletteEntries,
        int[] unpackedIndices
    ) {
        private int stateCount() {
            return unpackedIndices.length;
        }

        private BlockState stateAt(int index) {
            if (globalPalette) {
                return Objects.requireNonNull(Block.BLOCK_STATE_REGISTRY.byId(unpackedIndices[index]), "states[" + index + "]");
            }
            return Objects.requireNonNull(paletteEntries[unpackedIndices[index]], "states[" + index + "]");
        }

        private int findPaletteId(BlockState state) {
            BlockState safeState = Objects.requireNonNull(state, "state");
            if (globalPalette) {
                return Block.BLOCK_STATE_REGISTRY.getId(safeState);
            }
            BlockState[] safePaletteEntries = Objects.requireNonNull(paletteEntries, "paletteEntries");
            for (int i = 0; i < safePaletteEntries.length; i++) {
                if (safePaletteEntries[i] == safeState) {
                    return i;
                }
            }
            return -1;
        }
    }

    private static final class ObfuscationRandom {
        private final int engineMode;
        private final int decoyBound;
        private int state;
        private int currentLayer;
        private int currentLayerDecoy;

        private ObfuscationRandom(int engineMode, int decoyBound) {
            this.engineMode = engineMode;
            this.decoyBound = decoyBound;
            this.state = paperRuntimeRandomSeed();
            this.currentLayer = -1;
            this.currentLayerDecoy = 0;
        }

        private void enterLayer(int localY) {
            if (engineMode != 3) {
                this.currentLayer = localY;
                return;
            }
            int targetLayer = Math.max(0, localY);
            while (currentLayer < targetLayer) {
                state = xorshift32(state);
                currentLayerDecoy = scaleUnsignedBound(state, decoyBound);
                currentLayer++;
            }
        }

        private int nextDecoyIndex() {
            if (decoyBound <= 1) {
                return 0;
            }
            if (engineMode == 3) {
                return currentLayerDecoy;
            }
            state = xorshift32(state);
            return scaleUnsignedBound(state, decoyBound);
        }

        private int nextReplacementIndex(int bound) {
            if (bound <= 1) {
                return 0;
            }
            if (engineMode == 3) {
                return boundedXorShiftIndex(state ^ 0x9E3779B9, bound);
            }
            state = xorshift32(state);
            return scaleUnsignedBound(state, bound);
        }
    }
}
