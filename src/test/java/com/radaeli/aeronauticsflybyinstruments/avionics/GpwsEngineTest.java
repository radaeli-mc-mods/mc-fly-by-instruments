package com.radaeli.aeronauticsflybyinstruments.avionics;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.*;

class GpwsEngineTest {
    private static final int INTERVAL = 5;

    @Test
    void estimatesVerticalSpeedAndStabilizesAutomaticFlightPhase() {
        GpwsEngine engine = new GpwsEngine();
        GpwsConfiguration configuration = new GpwsConfiguration();

        var first = engine.evaluate(sample(0, 40, 0), configuration, true, INTERVAL);
        assertTrue(first.verticalSpeed().isEmpty());
        assertEquals(FlightPhase.UNKNOWN, first.phase());

        var second = engine.evaluate(sample(5, 40, 5), configuration, true, INTERVAL);
        assertEquals(0, second.verticalSpeed().orElseThrow(), 1.0E-9);
        assertEquals(FlightPhase.UNKNOWN, second.phase());

        var third = engine.evaluate(sample(10, 40, 10), configuration, true, INTERVAL);
        assertEquals(FlightPhase.CRUISE, third.phase());
    }

    @Test
    void groundPhaseRequiresOneContinuousSecond() {
        GpwsEngine engine = new GpwsEngine();
        GpwsConfiguration configuration = new GpwsConfiguration();
        engine.evaluate(sample(0, 1, 0), configuration, true, INTERVAL);
        assertEquals(FlightPhase.UNKNOWN,
                engine.evaluate(sample(5, 1, 5), configuration, true, INTERVAL).phase());
        engine.evaluate(sample(10, 1, 10), configuration, true, INTERVAL);
        engine.evaluate(sample(15, 1, 15), configuration, true, INTERVAL);
        assertEquals(FlightPhase.UNKNOWN,
                engine.evaluate(sample(20, 1, 20), configuration, true, INTERVAL).phase());
        assertEquals(FlightPhase.GROUND,
                engine.evaluate(sample(25, 1, 25), configuration, true, INTERVAL).phase());
    }

    @Test
    void groundQualificationDoesNotEmitTransientLowClearanceWarning() {
        GpwsEngine engine = new GpwsEngine();
        GpwsConfiguration configuration = new GpwsConfiguration();
        engine.evaluate(sample(0, 1, 0), configuration, false, INTERVAL);
        var settling = engine.evaluate(sample(5, 1, 5), configuration, false, INTERVAL);
        assertNotEquals(AlertState.ACTIVE, settling.alerts().get(AlertChannel.TOO_LOW).state());
        assertNotEquals(AlertState.ACTIVE, settling.alerts().get(AlertChannel.PULL_UP).state());
    }

    @Test
    void unknownAltitudeImmediatelyMakesAltitudeDependentChannelsUnavailable() {
        GpwsEngine engine = new GpwsEngine();
        var input = sample(10, null, 10);
        var result = engine.evaluate(input, new GpwsConfiguration(), false, INTERVAL);
        assertEquals(FlightPhase.UNKNOWN, result.phase());
        assertEquals(AlertState.UNAVAILABLE, result.alerts().get(AlertChannel.TOO_LOW).state());
        assertEquals(AlertState.UNAVAILABLE, result.alerts().get(AlertChannel.SINK_RATE).state());
    }

    @Test
    void partialTerrainCoverageCannotClaimClearButKnownContactCanAlert() {
        GpwsEngine emptyPartial = new GpwsEngine();
        var partial = sample(0, 40, 0, 1, false, false, List.of());
        var partialResult = emptyPartial.evaluate(partial, new GpwsConfiguration(), false, INTERVAL);
        assertEquals(AlertState.UNAVAILABLE, partialResult.alerts().get(AlertChannel.TERRAIN).state());

        GpwsEngine hitPartial = new GpwsEngine();
        var hit = sample(0, 40, 0, 1, false, false, List.of(new GpwsSample.TerrainContact(20, 10)));
        hitPartial.evaluate(hit, new GpwsConfiguration(), false, INTERVAL);
        var alerted = hitPartial.evaluate(sample(5, 40, 5, 1, false, false,
                List.of(new GpwsSample.TerrainContact(20, 10))), new GpwsConfiguration(), false, INTERVAL);
        assertEquals(AlertState.ACTIVE, alerted.alerts().get(AlertChannel.TERRAIN).state());
        assertEquals(AlertSeverity.CAUTION, alerted.alerts().get(AlertChannel.TERRAIN).severity());
    }

    @Test
    void pullUpUsesKnownContactImmediatelyAndRequiresCoverageToReportClear() {
        GpwsEngine detected = new GpwsEngine();
        var knownHit = sample(0, null, 0, 1, false, false,
                List.of(new GpwsSample.TerrainContact(7, 12)));
        var detectedResult = detected.evaluate(knownHit, new GpwsConfiguration(), false, INTERVAL);
        assertEquals(AlertState.ACTIVE, detectedResult.alerts().get(AlertChannel.PULL_UP).state());
        assertEquals(AlertSeverity.WARNING, detectedResult.alerts().get(AlertChannel.PULL_UP).severity());

        GpwsEngine partial = new GpwsEngine();
        var first = partial.evaluate(sample(0, 40, 0, 1, false, false, List.of()),
                new GpwsConfiguration(), false, INTERVAL);
        assertEquals(AlertState.UNAVAILABLE, first.alerts().get(AlertChannel.PULL_UP).state());
    }

    @Test
    void pullUpUsesEstimatedTimeToGroundWhenNoTerrainScannerIsInstalled() {
        GpwsEngine engine = new GpwsEngine();
        GpwsConfiguration configuration = new GpwsConfiguration();
        engine.evaluate(sample(0, 20, 0), configuration, false, INTERVAL);
        engine.evaluate(sample(5, 18, 5), configuration, false, INTERVAL);
        var warning = engine.evaluate(sample(10, 16, 10), configuration, false, INTERVAL);
        assertEquals(AlertState.ACTIVE, warning.alerts().get(AlertChannel.PULL_UP).state());
        assertEquals(AlertSeverity.WARNING, warning.alerts().get(AlertChannel.PULL_UP).severity());
    }

    @Test
    void dontSinkWarnsAfterFourBlocksOfRecentTakeoffOrClimbLoss() {
        GpwsEngine engine = new GpwsEngine();
        GpwsConfiguration configuration = new GpwsConfiguration();
        engine.phaseOverride = FlightPhase.TAKEOFF;
        engine.evaluate(sample(0, 3, 0), configuration, false, INTERVAL);
        engine.evaluate(sample(5, 3.5, 5), configuration, false, INTERVAL);
        engine.evaluate(sample(10, 4.5, 10), configuration, false, INTERVAL);
        engine.evaluate(sample(15, 3.5, 15), configuration, false, INTERVAL);
        engine.phaseOverride = FlightPhase.APPROACH;
        var first = engine.evaluate(sample(20, 0.5, 20), configuration, false, INTERVAL);
        assertNotEquals(AlertState.ACTIVE, first.alerts().get(AlertChannel.DONT_SINK).state());
        var warning = engine.evaluate(sample(25, 0.4, 25), configuration, false, INTERVAL);
        assertEquals(AlertState.ACTIVE, warning.alerts().get(AlertChannel.DONT_SINK).state());
        assertEquals(AlertSeverity.WARNING, warning.alerts().get(AlertChannel.DONT_SINK).severity());
    }

    @Test
    void radarOnlyWorksWithoutTreatingTheOptionalScannerAsFailure() {
        GpwsEngine engine = new GpwsEngine();
        var first = engine.evaluate(sample(0, 40, 0), new GpwsConfiguration(), false, INTERVAL);
        var second = engine.evaluate(sample(5, 40, 5), new GpwsConfiguration(), false, INTERVAL);
        var third = engine.evaluate(sample(10, 40, 10), new GpwsConfiguration(), false, INTERVAL);
        assertEquals(AlertState.INACTIVE, second.alerts().get(AlertChannel.SENSOR_FAIL).state());
        assertEquals(AlertState.UNAVAILABLE, first.alerts().get(AlertChannel.TERRAIN).state());
        assertEquals(AlertState.INACTIVE, third.alerts().get(AlertChannel.PULL_UP).state());
    }

    @Test
    void inopFunctionsExplainWhatIsMissing() {
        GpwsEngine engine = new GpwsEngine();
        var noAltimeter = engine.evaluate(sample(0, null, 0), new GpwsConfiguration(), false, INTERVAL);
        assertEquals(GpwsEngine.REASON_NO_ALTIMETER, noAltimeter.reasons().get(AlertChannel.TOO_LOW));
        assertEquals(GpwsEngine.REASON_NO_SCANNER, noAltimeter.reasons().get(AlertChannel.TERRAIN));

        GpwsEngine settling = new GpwsEngine();
        var first = settling.evaluate(sample(0, 40, 0), new GpwsConfiguration(), false, INTERVAL);
        assertEquals(GpwsEngine.REASON_SETTLING, first.reasons().get(AlertChannel.SINK_RATE));

        GpwsEngine sideScanner = new GpwsEngine();
        sideScanner.evaluate(sample(0, 40, 0, 1, true, false, List.of()), new GpwsConfiguration(), false, INTERVAL);
        sideScanner.evaluate(sample(5, 40, 5, 1, true, false, List.of()), new GpwsConfiguration(), false, INTERVAL);
        var clear = sideScanner.evaluate(sample(10, 40, 10, 1, true, false, List.of()), new GpwsConfiguration(),
                false, INTERVAL);
        assertEquals(AlertState.INACTIVE, clear.alerts().get(AlertChannel.TERRAIN).state());
        assertFalse(clear.reasons().containsKey(AlertChannel.TERRAIN));
    }

    @Test
    void inhibitedFunctionsStayQuietAndReportInhibited() {
        GpwsConfiguration configuration = new GpwsConfiguration();
        configuration.setInhibited(AlertChannel.SINK_RATE, true);
        GpwsEngine engine = new GpwsEngine();
        engine.evaluate(sample(0, 40, 0), configuration, false, INTERVAL);
        engine.evaluate(sample(5, 37, 5), configuration, false, INTERVAL);
        var result = engine.evaluate(sample(10, 34, 10), configuration, false, INTERVAL);
        assertEquals(AlertState.INHIBITED, result.alerts().get(AlertChannel.SINK_RATE).state());
    }

    @Test
    void sinkRateUsesBlocksPerSecondAndConfirmationSamples() {
        GpwsEngine engine = new GpwsEngine();
        GpwsConfiguration configuration = new GpwsConfiguration();
        engine.evaluate(sample(0, 40, 0), configuration, false, INTERVAL);
        var second = engine.evaluate(sample(5, 37, 5), configuration, false, INTERVAL);
        assertEquals(-12, second.verticalSpeed().orElseThrow(), 1.0E-9);
        assertNotEquals(AlertState.ACTIVE, second.alerts().get(AlertChannel.SINK_RATE).state());
        var third = engine.evaluate(sample(10, 34, 10), configuration, false, INTERVAL);
        assertEquals(AlertState.ACTIVE, third.alerts().get(AlertChannel.SINK_RATE).state());
        assertEquals(AlertSeverity.WARNING, third.alerts().get(AlertChannel.SINK_RATE).severity());
    }

    @Test
    void approachClearanceUsesSensitivityAndHysteresisBeforeClearing() {
        GpwsConfiguration configuration = new GpwsConfiguration();
        GpwsEngine engine = new GpwsEngine();
        engine.phaseOverride = FlightPhase.APPROACH;
        engine.evaluate(sample(0, 2, 0), configuration, false, INTERVAL);
        var active = engine.evaluate(sample(5, 2, 5), configuration, false, INTERVAL);
        assertEquals(AlertSeverity.CAUTION, active.alerts().get(AlertChannel.TOO_LOW).severity());
        engine.evaluate(sample(10, 3.2, 10), configuration, false, INTERVAL);
        var withinHysteresis = engine.evaluate(sample(15, 3.2, 15), configuration, false, INTERVAL);
        assertEquals(AlertState.ACTIVE, withinHysteresis.alerts().get(AlertChannel.TOO_LOW).state());
        engine.evaluate(sample(20, 3.4, 20), configuration, false, INTERVAL);
        var clearing = engine.evaluate(sample(25, 3.4, 25), configuration, false, INTERVAL);
        assertEquals(AlertState.INACTIVE, clearing.alerts().get(AlertChannel.TOO_LOW).state());

        GpwsConfiguration high = new GpwsConfiguration();
        high.setSensitivity(GpwsSensitivity.HIGH);
        GpwsEngine highEngine = new GpwsEngine();
        highEngine.phaseOverride = FlightPhase.APPROACH;
        highEngine.evaluate(sample(0, 3.5, 0), high, false, INTERVAL);
        var sensitive = highEngine.evaluate(sample(5, 3.5, 5), high, false, INTERVAL);
        assertEquals(AlertState.ACTIVE, sensitive.alerts().get(AlertChannel.TOO_LOW).state());
    }

    @Test
    void missingSensorsRaiseInopAndTestActivatesEveryChannel() {
        GpwsEngine engine = new GpwsEngine();
        GpwsConfiguration configuration = new GpwsConfiguration();
        var missingSensors = sample(0, null, 0, 0, false, false, List.of());
        engine.evaluate(missingSensors, configuration, false, INTERVAL);
        var inop = engine.evaluate(sample(5, null, 5, 0, false, false, List.of()), configuration, false, INTERVAL);
        assertEquals(AlertState.ACTIVE, inop.alerts().get(AlertChannel.SENSOR_FAIL).state());
        assertEquals(AlertSeverity.CAUTION, inop.alerts().get(AlertChannel.SENSOR_FAIL).severity());
        assertEquals(AlertState.UNAVAILABLE, inop.alerts().get(AlertChannel.TERRAIN).state());

        var test = engine.evaluate(missingSensors, configuration, true, INTERVAL);
        assertTrue(test.alerts().values().stream().allMatch(alert -> alert.state() == AlertState.ACTIVE
                && alert.severity() == AlertSeverity.WARNING));
    }

    private static GpwsSample sample(long now, Number clearance, long measurementTime) {
        return sample(now, clearance, measurementTime, 0, false, false, List.of());
    }

    private static GpwsSample sample(long now, Number clearance, long measurementTime, int scannerCount,
                                     boolean complete, boolean pullUpComplete,
                                     List<GpwsSample.TerrainContact> contacts) {
        return new GpwsSample(now, NetworkStatus.CONNECTED, 2, clearance == null ? 0 : 1,
                false, clearance == null ? OptionalDouble.empty() : OptionalDouble.of(clearance.doubleValue()), measurementTime,
                scannerCount, false, complete, pullUpComplete, contacts, OptionalDouble.of(0));
    }
}
