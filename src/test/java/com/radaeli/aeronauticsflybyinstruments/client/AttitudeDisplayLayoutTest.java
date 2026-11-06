package com.radaeli.aeronauticsflybyinstruments.client;

import com.radaeli.aeronauticsflybyinstruments.client.AttitudeDisplayState.Status;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AttitudeDisplayLayoutTest {
    private record Poly(int color, float[] xy) {
        double area() {
            double sum = 0;
            for (int i = 0; i < xy.length / 2; i++) {
                int j = (i + 1) % (xy.length / 2);
                sum += xy[i * 2] * xy[j * 2 + 1] - xy[j * 2] * xy[i * 2 + 1];
            }
            return Math.abs(sum) / 2;
        }
    }

    private static List<Poly> draw(AttitudeDisplayState state) {
        var polygons = new ArrayList<Poly>();
        AttitudeDisplayLayout.build((color, xy, count) ->
                polygons.add(new Poly(color, java.util.Arrays.copyOf(xy, count * 2))), state);
        return polygons;
    }

    private static AttitudeDisplayState state(double pitch, double roll, double agl, float altitude) {
        return new AttitudeDisplayState(Status.OK, pitch, roll, agl, altitude, 0f);
    }

    private static double area(List<Poly> polygons, int color) {
        return polygons.stream().filter(p -> p.color() == color).mapToDouble(Poly::area).sum();
    }

    @Test
    void everythingStaysInsideTheTwelveByTwelveLens() {
        for (var state : new AttitudeDisplayState[]{state(0, 0, 100, 1), state(35, -170, 100, 1), state(-60, 90, Double.NaN, 1),
                state(10, 30, 0, 0), state(0, 0, 5000, 0.5f), state(0, 0, 100, 1).withStatus(Status.DEGRADED),
                state(0, 0, 100, 1).withStatus(Status.NO_DATA)}) {
            for (var polygon : draw(state)) {
                assertTrue(polygon.xy().length >= 6 && polygon.xy().length % 2 == 0);
                for (float v : polygon.xy()) assertTrue(Math.abs(v) <= AttitudeDisplayLayout.HALF + 1e-4, "" + v);
            }
        }
    }

    @Test
    void groundAreaFollowsPitchAndRoll() {
        double width = 12 - AttitudeDisplayLayout.TAPE_WIDTH;
        assertEquals(width * 6, area(draw(state(0, 0, 100, 1)), AttitudeDisplayLayout.GROUND), 1e-3);
        // Nose up 10 degrees: the horizon sinks by 10 x the ladder scale.
        assertEquals(width * (6 - 10 * AttitudeDisplayLayout.PITCH_UNITS_PER_DEGREE),
                area(draw(state(10, 0, 100, 1)), AttitudeDisplayLayout.GROUND), 1e-3);
        assertEquals(width * (6 + 10 * AttitudeDisplayLayout.PITCH_UNITS_PER_DEGREE),
                area(draw(state(-10, 0, 100, 1)), AttitudeDisplayLayout.GROUND), 1e-3);
        // A vertical bank splits the view in half; the horizon passes through the middle of it.
        assertEquals(width * 6, area(draw(state(0, 90, 100, 1)), AttitudeDisplayLayout.GROUND), 1e-3);
        // Inverted: sky below, ground above.
        assertEquals(width * 6, area(draw(state(0, 180, 100, 1)), AttitudeDisplayLayout.GROUND), 1e-3);
    }

    @Test
    void rightBankLiftsTheRightSideOfTheHorizon() {
        // Right wing down: the horizon turns counter-clockwise and its right side rises.
        var banked = draw(state(0, 20, 100, 0));
        double right = banked.stream().filter(p -> p.color() == AttitudeDisplayLayout.GROUND)
                .mapToDouble(p -> clippedArea(p, 0, 6)).sum();
        double left = banked.stream().filter(p -> p.color() == AttitudeDisplayLayout.GROUND)
                .mapToDouble(p -> clippedArea(p, -6, 0)).sum();
        assertTrue(right > left, "The horizon rises on the right, so more ground shows there");
    }

    /** Area of a convex polygon inside the vertical strip x0..x1, by sampling: good enough for a direction check. */
    private static double clippedArea(Poly poly, double x0, double x1) {
        double total = 0;
        int n = poly.xy().length / 2;
        for (int sx = 0; sx < 120; sx++) {
            double x = x0 + (x1 - x0) * (sx + 0.5) / 120;
            double low = Double.POSITIVE_INFINITY, high = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                double ax = poly.xy()[i * 2], ay = poly.xy()[i * 2 + 1], bx = poly.xy()[j * 2], by = poly.xy()[j * 2 + 1];
                if ((ax - x) * (bx - x) > 0 || ax == bx) continue;
                double y = ay + (by - ay) * (x - ax) / (bx - ax);
                low = Math.min(low, y); high = Math.max(high, y);
            }
            if (high > low) total += (high - low) * (x1 - x0) / 120;
        }
        return total;
    }

    @Test
    void altitudeTapeReflowGivesTheSpaceBackToTheHorizon() {
        var off = draw(state(0, 0, 100, 0));
        assertEquals(0, area(off, AttitudeDisplayLayout.TAPE), 1e-9);
        assertEquals(144, area(off, AttitudeDisplayLayout.SKY), 1e-3);   // the sky is laid first over the whole view

        var on = draw(state(0, 0, 100, 1));
        assertEquals(AttitudeDisplayLayout.TAPE_WIDTH * 12, area(on, AttitudeDisplayLayout.TAPE), 1e-3);
        assertEquals((12 - AttitudeDisplayLayout.TAPE_WIDTH) * 12, area(on, AttitudeDisplayLayout.SKY), 1e-3);
        // The horizon never reaches under the tape.
        for (var polygon : on) {
            if (polygon.color() != AttitudeDisplayLayout.SKY && polygon.color() != AttitudeDisplayLayout.GROUND) continue;
            for (int i = 0; i < polygon.xy().length; i += 2) {
                assertTrue(polygon.xy()[i] <= 6 - AttitudeDisplayLayout.TAPE_WIDTH + 1e-4);
            }
        }
        // Mid-transition: the tape is half shown.
        assertEquals(AttitudeDisplayLayout.TAPE_WIDTH * 6, area(draw(state(0, 0, 100, 0.5f)), AttitudeDisplayLayout.TAPE), 1e-3);
    }

    @Test
    void reservedSpeedTapeOnlyTakesItsSpace() {
        var reserved = draw(new AttitudeDisplayState(Status.OK, 0, 0, 100, 0f, 1f));
        assertEquals((12 - AttitudeDisplayLayout.TAPE_WIDTH) * 12, area(reserved, AttitudeDisplayLayout.SKY), 1e-3);
        assertEquals(0, area(reserved, AttitudeDisplayLayout.TAPE), 1e-9);
    }

    @Test
    void radarWithoutReadingShowsDashesAndNoGroundBand() {
        var unknown = draw(state(0, 0, Double.NaN, 1));
        assertEquals(0, area(unknown, AttitudeDisplayLayout.GROUND_DARK), 1e-9);
        // Standing on the ground the band fills the lower half of the tape.
        assertEquals(AttitudeDisplayLayout.TAPE_WIDTH * 6, area(draw(state(0, 0, 0, 1)), AttitudeDisplayLayout.GROUND_DARK), 1e-3);
    }

    @Test
    void flagsFollowTheStatus() {
        assertEquals(0, area(draw(state(0, 0, 100, 1)), AttitudeDisplayLayout.AMBER), 1e-9);
        assertTrue(area(draw(state(0, 0, 100, 1).withStatus(Status.DEGRADED)), AttitudeDisplayLayout.AMBER) > 0);
        var none = draw(state(0, 0, 100, 1).withStatus(Status.NO_DATA));
        assertTrue(area(none, AttitudeDisplayLayout.RED) > 0);
        assertEquals(0, area(none, AttitudeDisplayLayout.SKY), 1e-9);
        assertEquals(144, area(none, AttitudeDisplayLayout.BLACK), 1e-3);
    }

    @Test
    void theAircraftSymbolIsBrassAndCentredOnTheHorizonView() {
        double centre = (-6 + 6 - AttitudeDisplayLayout.TAPE_WIDTH) / 2;
        var polygons = draw(state(0, 0, 100, 1));
        double low = Double.POSITIVE_INFINITY, high = Double.NEGATIVE_INFINITY;
        for (var polygon : polygons) {
            if (polygon.color() != AttitudeDisplayLayout.BRASS) continue;
            for (int i = 0; i < polygon.xy().length; i += 2) {
                low = Math.min(low, polygon.xy()[i]); high = Math.max(high, polygon.xy()[i]);
            }
        }
        assertEquals(centre, (low + high) / 2, 1e-4);
    }

    @Test
    void clipDropsWhatIsOutsideAndTrimsWhatCrosses() {
        var kept = new ArrayList<Poly>();
        var clip = new AttitudeDisplayLayout.Clip((c, xy, n) -> kept.add(new Poly(c, java.util.Arrays.copyOf(xy, n * 2))),
                0, 0, 1, 1);
        AttitudeDisplayLayout.rect(clip, 1, 5, 5, 6, 6);
        assertTrue(kept.isEmpty());
        AttitudeDisplayLayout.rect(clip, 2, -1, -1, 0.5f, 0.5f);
        assertEquals(1, kept.size());
        assertEquals(0.25, kept.getFirst().area(), 1e-6);
        AttitudeDisplayLayout.tri(clip, 3, -10, -10, 10, -10, 0, 10);
        assertEquals(2, kept.size());
        assertTrue(kept.getLast().area() > 0 && kept.getLast().area() <= 1 + 1e-6);
    }

    @Test
    void fontCoversEveryCharacterTheDisplayDraws() {
        for (char c : "0123456789AGLT-".toCharArray()) {
            var rows = AttitudeDisplayLayout.Glyphs.rows(c);
            assertNotNull(rows, "" + c);
            assertEquals(AttitudeDisplayLayout.Glyphs.HEIGHT, rows.length);
            for (var row : rows) assertEquals(AttitudeDisplayLayout.Glyphs.WIDTH, row.length());
        }
    }

    @Test
    void mockMovesAndStaysInRange() {
        var first = AttitudeDisplayMock.sample(0);
        var later = AttitudeDisplayMock.sample(5);
        assertNotEquals(first.pitchDegrees(), later.pitchDegrees());
        for (double t = 0; t < 120; t += 0.5) {
            var s = AttitudeDisplayMock.sample(t);
            assertEquals(Status.OK, s.status());
            assertTrue(Math.abs(s.pitchDegrees()) < 20 && Math.abs(s.rollDegrees()) < 30 && s.aglBlocks() > 50);
        }
    }

    /** Review image for blockbench/attitude-indicator-module: set FBI_WRITE_PREVIEW to the PNG path to write it. */
    @Test
    void writesThePreviewWhenAsked() throws Exception {
        String target = System.getenv("FBI_WRITE_PREVIEW");
        if (target == null) return;
        var states = new AttitudeDisplayState[]{
                state(10, 30, 128, 0), state(10, 30, 128, 1),
                state(-4, -12, Double.NaN, 1).withStatus(Status.DEGRADED), state(0, 0, 0, 1).withStatus(Status.NO_DATA)};
        int scale = 32, side = 12 * scale, gap = 24;
        var image = new BufferedImage(side * 2 + gap * 3, side * 2 + gap * 3, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        g.setColor(new Color(0x303030));
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        for (int i = 0; i < states.length; i++) {
            int ox = gap + (i % 2) * (side + gap), oy = gap + (i / 2) * (side + gap);
            var graphics = g;
            AttitudeDisplayLayout.build((color, xy, count) -> {
                int[] px = new int[count], py = new int[count];
                for (int k = 0; k < count; k++) {
                    px[k] = ox + Math.round((xy[k * 2] + 6) * scale);
                    py[k] = oy + Math.round((6 - xy[k * 2 + 1]) * scale);
                }
                graphics.setColor(new Color(color));
                graphics.fillPolygon(px, py, count);
            }, states[i]);
        }
        g.dispose();
        var out = new File(target);
        assertTrue(ImageIO.write(image, "png", out), out.getAbsolutePath());
    }
}
