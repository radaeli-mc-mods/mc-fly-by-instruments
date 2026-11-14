package com.radaeli.aeronauticsflybyinstruments.sensor;

import com.radaeli.aeronauticsflybyinstruments.attitude.AttitudeReferenceUnitBlockEntity;
import com.radaeli.aeronauticsflybyinstruments.avionics.*;
import com.radaeli.aeronauticsflybyinstruments.block.ModBlocks;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SensorNetworkAvailabilityTest {
    @Test
    void altimeterRetainsLatestValidReadingAcrossMissingReturnsAndFailures() {
        var radar = new RadarAltimeterBlockEntity(BlockPos.ZERO, ModBlocks.RADAR_ALTIMETER.get().defaultBlockState());
        radar.acceptReading(SensorReading.valid(125.0, "blocks", 5));
        radar.acceptReading(SensorReading.unavailable("blocks", 10));
        assertEquals(125.0, radar.lastValidReading().value().orElseThrow());
        assertTrue(radar.sensorAvailability().isOperating(10, 10));
        assertTrue(available(radar));
        radar.acceptReading(SensorReading.failed("blocks", 15));
        assertFalse(radar.sensorAvailability().isOperating(15, 10));
        assertFalse(available(radar));
        radar.acceptReading(SensorReading.valid(32.0, "blocks", 20));
        radar.acceptReading(SensorReading.unavailable("blocks", 25));
        assertEquals(32.0, radar.lastValidReading().value().orElseThrow());
        assertTrue(available(radar));
        radar.setRemoved();
        assertFalse(available(radar));
    }

    @Test
    void bothScannerVariantsReportOperatingWithoutAnySampledRays() {
        for (var block : List.of(ModBlocks.TERRAIN_SCANNER.get(), ModBlocks.TERRAIN_SCANNER_ADVANCED.get())) {
            var scanner = new TerrainScannerBlockEntity(BlockPos.ZERO, block.defaultBlockState());
            scanner.acceptReading(TerrainScanSnapshot.unavailable(9, 5));
            assertTrue(available(scanner));
            scanner.acceptReading(TerrainScanSnapshot.failed(9, 10));
            assertFalse(available(scanner));
            scanner.acceptReading(TerrainScanSnapshot.unavailable(9, 15));
            assertTrue(available(scanner));
            scanner.onChunkUnloaded();
            assertFalse(available(scanner));
        }
    }

    @Test
    void attitudeUnitAlsoPublishesAnIndependentAvailabilityChannel() {
        var aru = new AttitudeReferenceUnitBlockEntity(BlockPos.ZERO,
                ModBlocks.ATTITUDE_REFERENCE_UNIT.get().defaultBlockState());
        var channels = AvionicsEndpoints.signalsOf(null, aru);
        assertTrue(channels.stream().anyMatch(channel -> channel.name().equals("attitude.pitch")));
        assertFalse(available(aru));
    }

    private static boolean available(net.minecraft.world.level.block.entity.BlockEntity sensor) {
        var channel = AvionicsEndpoints.signalsOf(null, sensor).stream()
                .filter(signal -> signal.name().equals(SensorAvailability.CHANNEL)).findFirst().orElseThrow();
        assertEquals(ReadingStatus.VALID, channel.reading().status());
        return (Boolean) channel.reading().value().orElseThrow();
    }
}
