package com.radaeli.aeronauticsflybyinstruments.panel;

import com.radaeli.aeronauticsflybyinstruments.attitude.AttitudeReading;
import com.radaeli.aeronauticsflybyinstruments.panel.AttitudeIndicatorFeed.AltimeterSource;
import com.radaeli.aeronauticsflybyinstruments.panel.AttitudeIndicatorFeed.AttitudeSource;
import com.radaeli.aeronauticsflybyinstruments.panel.AttitudeIndicatorFeed.Link;
import com.radaeli.aeronauticsflybyinstruments.panel.AttitudeIndicatorFeed.Status;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.*;

class AttitudeIndicatorFeedTest {
    private static AttitudeReading reading(AttitudeReading.Status status, double pitch, double roll) {
        return new AttitudeReading(status, pitch, roll, 90, 0, 0, 0, 100);
    }

    private static AttitudeSource unit(long distance, boolean sameVessel, boolean fresh, double pitch, double roll) {
        return new AttitudeSource(distance, sameVessel, fresh, reading(AttitudeReading.Status.VALID, pitch, roll));
    }

    private static AltimeterSource radar(boolean sameVessel, boolean fresh, boolean healthy, Double height) {
        return new AltimeterSource(sameVessel, fresh, healthy,
                height == null ? OptionalDouble.empty() : OptionalDouble.of(height));
    }

    @Test
    void anIncompleteNetworkShowsTheAttFlag() {
        var sample = AttitudeIndicatorFeed.resolve(false, List.of(unit(1, true, true, 5, 6)), List.of(), true);
        assertEquals(Status.NO_DATA, sample.status());
        assertEquals(Link.NO_NETWORK, sample.attitude());
        assertEquals(Link.NO_NETWORK, sample.altimeter());
        assertFalse(sample.altitudeTape());
    }

    @Test
    void theNearestUsableUnitWins() {
        var sample = AttitudeIndicatorFeed.resolve(true, List.of(unit(9, true, true, 1, 1), unit(4, true, true, 7, -8),
                unit(1, false, true, 50, 50), unit(2, true, false, 60, 60)), List.of(), true);
        assertEquals(Status.OK, sample.status());
        assertEquals(Link.OK, sample.attitude());
        assertEquals(7, sample.pitch());
        assertEquals(-8, sample.roll());
    }

    @Test
    void whyAUnitIsNotUsedDependsOnWhatTheNetworkHolds() {
        assertEquals(Link.NONE, AttitudeIndicatorFeed.resolve(true, List.of(), List.of(), true).attitude());
        assertEquals(Link.OTHER_VESSEL, AttitudeIndicatorFeed.resolve(true,
                List.of(unit(1, false, true, 0, 0)), List.of(), true).attitude());
        assertEquals(Link.STALE, AttitudeIndicatorFeed.resolve(true,
                List.of(unit(1, true, false, 0, 0), unit(2, false, true, 0, 0)), List.of(), true).attitude());
        var stale = AttitudeIndicatorFeed.resolve(true, List.of(unit(1, true, false, 3, 3)), List.of(), true);
        assertEquals(Status.NO_DATA, stale.status());
        assertEquals(0, stale.pitch(), "A lost unit leaves no old attitude behind");
    }

    @Test
    void aUnitWithoutValuesIsNotUsable() {
        var silent = new AttitudeSource(1, true, true, AttitudeReading.unavailable(100));
        assertEquals(Link.STALE, AttitudeIndicatorFeed.resolve(true, List.of(silent), List.of(), true).attitude());
    }

    @Test
    void groundAndDegradedReadingsMapToTheDisplayStatus() {
        var ground = new AttitudeSource(1, true, true, reading(AttitudeReading.Status.GROUND, 0, 0));
        assertEquals(Status.OK, AttitudeIndicatorFeed.resolve(true, List.of(ground), List.of(), true).status());
        var degraded = new AttitudeSource(1, true, true, reading(AttitudeReading.Status.DEGRADED, 88, 12));
        var sample = AttitudeIndicatorFeed.resolve(true, List.of(degraded), List.of(), true);
        assertEquals(Status.DEGRADED, sample.status());
        assertEquals(12, sample.roll());
    }

    @Test
    void theLowestUsableAltimeterGivesTheHeight() {
        var sample = AttitudeIndicatorFeed.resolve(true, List.of(unit(1, true, true, 0, 0)),
                List.of(radar(true, true, true, 40.0), radar(true, true, true, 12.5), radar(true, false, true, 3.0),
                        radar(false, true, true, 1.0), radar(true, true, false, 2.0)), true);
        assertEquals(Link.OK, sample.altimeter());
        assertTrue(sample.altitudeTape());
        assertEquals(12.5, sample.agl());
    }

    @Test
    void anAltimeterWithNoGroundInRangeKeepsTheTapeWithDashes() {
        var sample = AttitudeIndicatorFeed.resolve(true, List.of(unit(1, true, true, 0, 0)),
                List.of(radar(true, true, true, null)), true);
        assertEquals(Link.OK, sample.altimeter());
        assertTrue(sample.altitudeTape());
        assertTrue(Double.isNaN(sample.agl()));
    }

    @Test
    void theTapeNeedsBothTheSettingAndAnAltimeterOnTheNetwork() {
        var units = List.of(unit(1, true, true, 0, 0));
        assertFalse(AttitudeIndicatorFeed.resolve(true, units, List.of(), true).altitudeTape(), "no altimeter");
        assertEquals(Link.NONE, AttitudeIndicatorFeed.resolve(true, units, List.of(), true).altimeter());
        var off = AttitudeIndicatorFeed.resolve(true, units, List.of(radar(true, true, true, 20.0)), false);
        assertFalse(off.altitudeTape(), "switched off");
        assertTrue(Double.isNaN(off.agl()));
        assertEquals(Link.OK, off.altimeter(), "the screen still reports the link");
        var broken = AttitudeIndicatorFeed.resolve(true, units, List.of(radar(true, true, false, null)), true);
        assertTrue(broken.altitudeTape(), "a failing altimeter still has its tape, with dashes");
        assertEquals(Link.STALE, broken.altimeter());
        assertEquals(Link.OTHER_VESSEL, AttitudeIndicatorFeed.resolve(true, units,
                List.of(radar(false, true, true, 5.0)), true).altimeter());
    }

    @Test
    void anAltimeterFaultNeverBlanksTheHorizon() {
        var sample = AttitudeIndicatorFeed.resolve(true, List.of(unit(1, true, true, 4, 9)),
                List.of(radar(true, false, true, 30.0)), true);
        assertEquals(Status.OK, sample.status());
        assertEquals(4, sample.pitch());
    }

    @Test
    void onlyRealChangesAreWorthAPacket() {
        var base = new AttitudeIndicatorFeed.Sample(Status.OK, Link.OK, Link.OK, 10, 20, 100, true);
        assertFalse(base.differsFrom(new AttitudeIndicatorFeed.Sample(Status.OK, Link.OK, Link.OK, 10.1, 20.1, 100.2, true)));
        assertTrue(base.differsFrom(new AttitudeIndicatorFeed.Sample(Status.OK, Link.OK, Link.OK, 10.3, 20, 100, true)));
        assertTrue(base.differsFrom(new AttitudeIndicatorFeed.Sample(Status.OK, Link.OK, Link.OK, 10, 20, 100.6, true)));
        assertTrue(base.differsFrom(new AttitudeIndicatorFeed.Sample(Status.OK, Link.OK, Link.OK, 10, 20, Double.NaN, true)));
        assertTrue(base.differsFrom(new AttitudeIndicatorFeed.Sample(Status.DEGRADED, Link.OK, Link.OK, 10, 20, 100, true)));
        assertTrue(base.differsFrom(new AttitudeIndicatorFeed.Sample(Status.OK, Link.OK, Link.OK, 10, 20, 100, false)));
        assertTrue(base.differsFrom(null));
        var inverted = new AttitudeIndicatorFeed.Sample(Status.OK, Link.OK, Link.OK, 0, 179.9, Double.NaN, false);
        assertFalse(inverted.differsFrom(new AttitudeIndicatorFeed.Sample(Status.OK, Link.OK, Link.OK, 0, -179.9, Double.NaN, false)),
                "Roll wraps at 180: a small step across it is small");
    }

    @Test
    void wireOrderIsStable() {
        assertEquals(List.of(Status.OK, Status.DEGRADED, Status.NO_DATA), List.of(Status.values()));
        assertEquals(Link.NO_NETWORK, Link.of(99));
    }
}
