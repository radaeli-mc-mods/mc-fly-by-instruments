package com.radaeli.aeronauticsflybyinstruments.client;

import com.radaeli.aeronauticsflybyinstruments.client.AttitudeDisplayState.Status;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AttitudeDisplayFeedTest {
    @Test
    void withoutASampleTheDisplayShowsTheAttFlag() {
        var feed = new AttitudeDisplayFeed();
        assertEquals(Status.NO_DATA, feed.advance(0.016).status());
    }

    @Test
    void theFirstSampleIsTakenAsItIs() {
        var feed = new AttitudeDisplayFeed();
        feed.accept(Status.OK, 12, -30, 80, true, 24, true);
        var state = feed.advance(0);
        assertEquals(Status.OK, state.status());
        assertEquals(12, state.pitchDegrees(), 1e-9);
        assertEquals(-30, state.rollDegrees(), 1e-9);
        assertEquals(80, state.aglBlocks(), 1e-9);
        assertEquals(1f, state.altitudeTape());
        assertEquals(24, state.speed(), 1e-9);
        assertEquals(1f, state.speedTape());
    }

    @Test
    void laterSamplesAreApproachedNotJumpedTo() {
        var feed = new AttitudeDisplayFeed();
        feed.accept(Status.OK, 0, 0, 50, true, Double.NaN, false);
        feed.advance(0);
        feed.accept(Status.OK, 10, 20, 60, true, Double.NaN, false);
        var early = feed.advance(0.016);
        assertTrue(early.pitchDegrees() > 0 && early.pitchDegrees() < 10, "" + early.pitchDegrees());
        assertTrue(early.rollDegrees() > 0 && early.rollDegrees() < 20);
        assertTrue(early.aglBlocks() > 50 && early.aglBlocks() < 60);
        var late = early;
        for (int i = 0; i < 60; i++) late = feed.advance(0.016);
        assertEquals(10, late.pitchDegrees(), 0.01);
        assertEquals(20, late.rollDegrees(), 0.01);
        assertEquals(60, late.aglBlocks(), 0.01);
    }

    @Test
    void rollTakesTheShortWayAcrossPlusOrMinus180() {
        var feed = new AttitudeDisplayFeed();
        feed.accept(Status.OK, 0, 175, Double.NaN, false, Double.NaN, false);
        feed.advance(0);
        feed.accept(Status.OK, 0, -175, Double.NaN, false, Double.NaN, false);
        var state = feed.advance(0.03);
        assertTrue(Math.abs(state.rollDegrees()) > 175, "It must not sweep through level: " + state.rollDegrees());
        assertTrue(Math.abs(state.rollDegrees()) <= 180);
    }

    @Test
    void aMissingHeightStaysDashesAndAnewOneAppearsAtOnce() {
        var feed = new AttitudeDisplayFeed();
        feed.accept(Status.OK, 0, 0, Double.NaN, true, Double.NaN, false);
        assertTrue(Double.isNaN(feed.advance(0.016).aglBlocks()));
        feed.accept(Status.OK, 0, 0, 33, true, Double.NaN, false);
        assertEquals(33, feed.advance(0.016).aglBlocks(), 1e-9, "No smoothing from dashes");
        feed.accept(Status.OK, 0, 0, Double.NaN, true, Double.NaN, false);
        assertTrue(Double.isNaN(feed.advance(0.016).aglBlocks()));
    }

    @Test
    void theTapeSlidesInAndOutInAQuarterSecond() {
        var feed = new AttitudeDisplayFeed();
        feed.accept(Status.OK, 0, 0, 10, true, Double.NaN, false);
        feed.advance(0);
        feed.accept(Status.OK, 0, 0, 10, false, Double.NaN, false);
        var half = feed.advance(0.125);
        assertEquals(0.5f, half.altitudeTape(), 1e-5);
        assertEquals(0f, feed.advance(0.2).altitudeTape());
        feed.accept(Status.OK, 0, 0, 10, true, Double.NaN, false);
        assertEquals(0.4f, feed.advance(0.1).altitudeTape(), 1e-5);
    }

    @Test
    void aFeedNobodyRepeatsGoesStaleAndComesBackWithoutSweeping() {
        var feed = new AttitudeDisplayFeed();
        feed.accept(Status.OK, 20, 40, 10, true, Double.NaN, false);
        feed.advance(1);
        assertEquals(Status.OK, feed.advance(1.5).status());
        assertEquals(Status.NO_DATA, feed.advance(1).status(), "More than three seconds in all");
        feed.accept(Status.OK, -5, 3, 10, true, Double.NaN, false);
        var back = feed.advance(0);
        assertEquals(-5, back.pitchDegrees(), 1e-9);
        assertEquals(3, back.rollDegrees(), 1e-9);
    }

    @Test
    void theServersNoDataAndDegradedFlagsPassThrough() {
        var feed = new AttitudeDisplayFeed();
        feed.accept(Status.DEGRADED, 89, 10, Double.NaN, false, Double.NaN, false);
        assertEquals(Status.DEGRADED, feed.advance(0.016).status());
        feed.accept(Status.NO_DATA, 0, 0, Double.NaN, false, Double.NaN, false);
        assertEquals(Status.NO_DATA, feed.advance(0.016).status());
    }

    @Test
    void theSpeedFromTheServerIsDrawnAndItsTapeFollowsTheSetting() {
        var feed = new AttitudeDisplayFeed();
        feed.accept(Status.OK, 0, 0, Double.NaN, false, Double.NaN, false);
        assertTrue(Double.isNaN(feed.advance(0).speed()), "no velocity sensor: dashes");
        assertEquals(0f, feed.advance(0).speedTape());

        feed.accept(Status.OK, 0, 0, Double.NaN, false, 30, true);
        var later = feed.advance(0.25);
        assertTrue(later.speed() > 0 && later.speed() <= 30, "the speed moves toward the sample");
        assertEquals(1f, later.speedTape(), 1e-6);

        feed.accept(Status.OK, 0, 0, Double.NaN, false, 30, false);
        assertEquals(0f, feed.advance(0.25).speedTape(), 1e-6, "the tape slides out when switched off");
    }
}
