package com.meowantixray.antixray;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FakeOrePacketBitStorageTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void writesAndReadsValueWithinSingleLong() {
        byte[] buffer = new byte[32];

        FakeOreService.writePackedValue(buffer, 0, 4, 3, 11);

        assertEquals(11, FakeOreService.readPackedValue(buffer, 0, 4, 3));
    }

    @Test
    void writesAndReadsValueAcrossLongBoundary() {
        byte[] buffer = new byte[32];

        FakeOreService.writePackedValue(buffer, 0, 9, 7, 341);

        assertEquals(341, FakeOreService.readPackedValue(buffer, 0, 9, 7));
    }

    @Test
    void preservesNeighborValuesWhenCrossBoundaryWriteOccurs() {
        byte[] buffer = new byte[64];

        FakeOreService.writePackedValue(buffer, 0, 13, 3, 1234);
        FakeOreService.writePackedValue(buffer, 0, 13, 4, 5678);
        FakeOreService.writePackedValue(buffer, 0, 13, 5, 2468);

        assertEquals(1234, FakeOreService.readPackedValue(buffer, 0, 13, 3));
        assertEquals(5678, FakeOreService.readPackedValue(buffer, 0, 13, 4));
        assertEquals(2468, FakeOreService.readPackedValue(buffer, 0, 13, 5));
    }

    @Test
    void computeTargetBitsMatchesPaperThresholds() {
        assertEquals(0, FakeOreService.computeTargetBits(1));
        assertEquals(4, FakeOreService.computeTargetBits(2));
        assertEquals(4, FakeOreService.computeTargetBits(16));
        assertEquals(5, FakeOreService.computeTargetBits(17));
        assertEquals(6, FakeOreService.computeTargetBits(33));
        assertEquals(7, FakeOreService.computeTargetBits(65));
        assertEquals(8, FakeOreService.computeTargetBits(129));
        assertEquals(8, FakeOreService.computeTargetBits(256));
    }

    @Test
    void computeTargetBitsSwitchesToGlobalPaletteWhenNeeded() {
        int globalBits = Mth.ceillog2(Block.BLOCK_STATE_REGISTRY.size());

        assertEquals(globalBits, FakeOreService.computeTargetBits(257));
        assertEquals(globalBits, FakeOreService.computeTargetBits(0, true));
    }

    @Test
    void remapDensePaletteIndicesRebuildsDenseIdsFromStates() {
        BlockState[] states = {
            Blocks.NETHERRACK.defaultBlockState(),
            Blocks.ANCIENT_DEBRIS.defaultBlockState(),
            Blocks.NETHERRACK.defaultBlockState(),
            Blocks.NETHER_QUARTZ_ORE.defaultBlockState()
        };

        int[] remapped = FakeOreService.remapDensePaletteIndices(
            states,
            List.of(
                Blocks.NETHERRACK.defaultBlockState(),
                Blocks.ANCIENT_DEBRIS.defaultBlockState(),
                Blocks.NETHER_QUARTZ_ORE.defaultBlockState()
            )
        );

        assertEquals(0, remapped[0]);
        assertEquals(1, remapped[1]);
        assertEquals(0, remapped[2]);
        assertEquals(2, remapped[3]);
    }
}
