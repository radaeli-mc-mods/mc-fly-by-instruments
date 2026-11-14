package com.radaeli.aeronauticsflybyinstruments.avionics;

import com.radaeli.aeronauticsflybyinstruments.sensor.TerrainScanGeometry;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.*;

class GpwsTerrainPathTest {
    @ParameterizedTest
    @ValueSource(ints = {30, 35, 45, 90, 180})
    void flatFloorReturnsDoNotRaiseTerrainOrPullUpInLevelFlight(int aperture) {
        // Intersect the real scanner ray distribution with a plane five blocks below the lens.
        var contacts = new ArrayList<GpwsSample.TerrainContact>();
        for (var ray : TerrainScanGeometry.advancedDirections(aperture)) {
            if (ray.y() >= 0) continue;
            double distance = 5 / -ray.y();
            if (distance > 128) continue;
            var direction = new Vec3(ray.x(), ray.y(), ray.z());
            contacts.add(contact(direction.scale(distance), new Vec3(0, 0, 10)));
        }
        assertFalse(contacts.isEmpty());
        assertTrue(contacts.stream().noneMatch(GpwsSample.TerrainContact::threatensFlightPath));
        var result = evaluate(contacts);
        assertEquals(AlertState.INACTIVE, result.alerts().get(AlertChannel.TERRAIN).state());
        assertEquals(AlertState.INACTIVE, result.alerts().get(AlertChannel.PULL_UP).state());
    }

    @Test
    void closeFloorDoesNotHideAWallBehindTheNearestReturn() {
        var floor = contact(new Vec3(0, -2, 5), new Vec3(0, 0, 10));
        var wall = contact(new Vec3(0, 0, 20), new Vec3(0, 0, 10));
        assertFalse(floor.threatensFlightPath());
        assertTrue(wall.threatensFlightPath());
        var result = evaluate(List.of(floor, wall));
        assertEquals(AlertState.ACTIVE, result.alerts().get(AlertChannel.TERRAIN).state());
        assertEquals(AlertSeverity.CAUTION, result.alerts().get(AlertChannel.TERRAIN).severity());
        assertEquals(AlertState.INACTIVE, result.alerts().get(AlertChannel.PULL_UP).state());
    }

    @Test
    void floorInsideOldPullUpConeIsQuietButCloseWallStillWarnsImmediately() {
        var velocity = new Vec3(0, 0, 10);
        var floor = contact(new Vec3(0, -2, 6), velocity);
        assertTrue(floor.angleFromForwardDegrees() < 22.5);
        assertTrue(floor.distance() < 8);
        var clear = evaluate(List.of(floor));
        assertEquals(AlertState.INACTIVE, clear.alerts().get(AlertChannel.PULL_UP).state());
        var engine = new GpwsEngine();
        var wall = contact(new Vec3(0, 0, 6), velocity);
        var warning = engine.evaluate(sample(0, List.of(floor, wall)), new GpwsConfiguration(), false, 5);
        assertEquals(AlertState.ACTIVE, warning.alerts().get(AlertChannel.PULL_UP).state());
    }

    @Test
    void descentIntoGroundAndRisingTerrainRemainThreats() {
        var descendingGround = contact(new Vec3(0, -5, 25), new Vec3(0, -2, 10));
        assertTrue(descendingGround.threatensFlightPath());
        assertEquals(AlertState.ACTIVE,
                evaluate(List.of(descendingGround)).alerts().get(AlertChannel.TERRAIN).state());
        var hill = contact(new Vec3(0, 2, 6), new Vec3(0, 0, 10));
        assertTrue(hill.threatensFlightPath());
        assertEquals(AlertState.ACTIVE, evaluate(List.of(hill)).alerts().get(AlertChannel.PULL_UP).state());
        assertTrue(GpwsTerrainPath.threatens(new Vec3(0, -5, 0), new Vec3(0, -2, 0)));
    }

    @Test
    void actualMotionRatherThanNosePitchDeterminesTheProjectedHeight() {
        var downwardHit = new Vec3(0, -5, 15);
        assertFalse(GpwsTerrainPath.threatens(downwardHit, new Vec3(0, 0, 10)));
        assertFalse(GpwsTerrainPath.threatens(downwardHit, new Vec3(0, 2, 10)));
        assertTrue(GpwsTerrainPath.threatens(downwardHit, new Vec3(0, -4, 10)));
        assertFalse(GpwsTerrainPath.threatens(new Vec3(0, 0, -10), new Vec3(0, 0, 10)));
        assertTrue(GpwsTerrainPath.threatens(new Vec3(10, 0, 0), new Vec3(10, 0, 0)));
        assertTrue(GpwsTerrainPath.threatens(new Vec3(-10, -2, 0), new Vec3(-10, -2, 0)));
    }

    @Test
    void stationaryFallbackAndUnknownMotionDoNotHideCloseObstacles() {
        assertFalse(GpwsTerrainPath.threatens(new Vec3(0, -5, 10), Vec3.ZERO));
        assertTrue(GpwsTerrainPath.threatens(new Vec3(0, -1, 10), Vec3.ZERO));
        assertTrue(GpwsTerrainPath.threatens(new Vec3(0, 0, 10), Vec3.ZERO));
        assertTrue(GpwsTerrainPath.threatens(new Vec3(0, -5, 10), new Vec3(Double.NaN, 0, 0)));
    }

    @Test
    void filteringGroundDoesNotTurnIncompleteCoverageIntoAClearScan() {
        var engine = new GpwsEngine();
        var floor = contact(new Vec3(0, -5, 10), new Vec3(0, 0, 10));
        var input = new GpwsSample(0, NetworkStatus.CONNECTED, 2, 1, false, OptionalDouble.of(5), 0,
                1, false, false, false, List.of(floor), OptionalDouble.of(0));
        var result = engine.evaluate(input, new GpwsConfiguration(), false, 5);
        assertEquals(GpwsEngine.REASON_PARTIAL_COVERAGE, result.reasons().get(AlertChannel.TERRAIN));
    }

    private static GpwsSample.TerrainContact contact(Vec3 offset, Vec3 velocity) {
        double angle = Math.toDegrees(Math.acos(Math.clamp(offset.normalize().z, -1, 1)));
        return new GpwsSample.TerrainContact(offset.length(), angle, GpwsTerrainPath.threatens(offset, velocity));
    }

    private static GpwsEngine.Evaluation evaluate(List<GpwsSample.TerrainContact> contacts) {
        var engine = new GpwsEngine();
        engine.phaseOverride = FlightPhase.CRUISE;
        var config = new GpwsConfiguration();
        engine.evaluate(sample(0, contacts), config, false, 5);
        return engine.evaluate(sample(5, contacts), config, false, 5);
    }

    private static GpwsSample sample(long now, List<GpwsSample.TerrainContact> contacts) {
        return new GpwsSample(now, NetworkStatus.CONNECTED, 2, 1, false, OptionalDouble.of(5), now,
                1, false, true, true, contacts, OptionalDouble.of(0));
    }
}
