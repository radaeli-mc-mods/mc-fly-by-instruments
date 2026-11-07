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
        return new AttitudeDisplayState(Status.OK, pitch, roll, agl, Double.NaN, altitude, 0f);
    }

    private static AttitudeDisplayState readings(double agl, double speed, float altitude, float speedTape) {
        return new AttitudeDisplayState(Status.OK, 0, 0, agl, speed, altitude, speedTape);
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
        double half = (12 - AttitudeDisplayLayout.BAND_HEIGHT) / 2;
        assertEquals(12 * half, area(draw(state(0, 0, 100, 1)), AttitudeDisplayLayout.GROUND), 1e-3);
        // Nose up 10 degrees: the horizon sinks by 10 x the ladder scale.
        assertEquals(12 * (half - 10 * AttitudeDisplayLayout.PITCH_UNITS_PER_DEGREE),
                area(draw(state(10, 0, 100, 1)), AttitudeDisplayLayout.GROUND), 1e-3);
        assertEquals(12 * (half + 10 * AttitudeDisplayLayout.PITCH_UNITS_PER_DEGREE),
                area(draw(state(-10, 0, 100, 1)), AttitudeDisplayLayout.GROUND), 1e-3);
        // A vertical bank splits the view in half; the horizon passes through the middle of it.
        assertEquals(12 * half, area(draw(state(0, 90, 100, 1)), AttitudeDisplayLayout.GROUND), 1e-3);
        // Inverted: sky below, ground above.
        assertEquals(12 * half, area(draw(state(0, 180, 100, 1)), AttitudeDisplayLayout.GROUND), 1e-3);
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
    void altitudeBandReflowGivesTheSpaceBackToTheHorizon() {
        double band = AttitudeDisplayLayout.BAND_HEIGHT;
        var off = draw(state(0, 0, 100, 0));
        assertEquals(144, area(off, AttitudeDisplayLayout.SKY), 1e-3);   // the sky is laid first over the whole view
        assertEquals(0, area(off, AttitudeDisplayLayout.MUTED), 1e-9);

        var on = draw(state(0, 0, 100, 1));
        assertEquals((12 - band) * 12, area(on, AttitudeDisplayLayout.SKY), 1e-3);
        assertTrue(area(on, AttitudeDisplayLayout.MUTED) > 0, "AGL caption");
        // The horizon never reaches into the band.
        for (var polygon : on) {
            if (polygon.color() != AttitudeDisplayLayout.SKY && polygon.color() != AttitudeDisplayLayout.GROUND) continue;
            for (int i = 1; i < polygon.xy().length; i += 2) assertTrue(polygon.xy()[i] >= -6 + band - 1e-4);
        }
        // Mid-transition: the band is half shown.
        assertEquals((12 - band / 2) * 12, area(draw(state(0, 0, 100, 0.5f)), AttitudeDisplayLayout.SKY), 1e-3);
    }

    @Test
    void speedAloneTakesTheBandWithItsCaption() {
        var speed = draw(readings(100, 24, 0f, 1f));
        assertEquals((12 - AttitudeDisplayLayout.BAND_HEIGHT) * 12, area(speed, AttitudeDisplayLayout.SKY), 1e-3);
        assertTrue(area(speed, AttitudeDisplayLayout.MUTED) > 0, "SPD caption");
    }

    @Test
    void bothReadingsShareTheBandWithoutCaptions() {
        double dp = AttitudeDisplayLayout.DP, figure = AttitudeDisplayLayout.NUMBER_PIXEL, unit = AttitudeDisplayLayout.UNIT_PIXEL;
        var both = draw(readings(Double.NaN, Double.NaN, 1f, 1f));
        // Muted: the split rule and the units "b/s" and "b" (10 + 5 + 11 + 10 unit pixels), no captions.
        assertEquals(dp * 5 * dp + 36 * unit * unit, area(both, AttitudeDisplayLayout.MUTED), 1e-6);
        // Three dashes for each reading, each 5 x 1 figure pixels.
        assertEquals(6 * 5 * figure * figure, area(both, AttitudeDisplayLayout.CREAM), 1e-6);
        assertEquals((12 - AttitudeDisplayLayout.BAND_HEIGHT) * 12, area(both, AttitudeDisplayLayout.SKY), 1e-3);
    }

    @Test
    void nightDarkensTheSkyAndGroundAndLightsTheMarks() {
        var night = new ArrayList<Poly>();
        AttitudeDisplayLayout.build((color, xy, count) ->
                night.add(new Poly(color, java.util.Arrays.copyOf(xy, count * 2))), state(0, 0, 100, 0), 1);
        assertEquals(0, area(night, AttitudeDisplayLayout.SKY), 1e-9);
        assertEquals(144, area(night, AttitudeDisplayLayout.SKY_NIGHT), 1e-3);   // laid first over the whole view
        assertEquals(72, area(night, AttitudeDisplayLayout.GROUND_NIGHT), 1e-3);
        assertTrue(area(night, AttitudeDisplayLayout.MARK_NIGHT) > 0);
        // Day marks are the dark lead grey; dusk is a blend of both ends.
        assertTrue(area(draw(state(0, 0, 100, 0)), AttitudeDisplayLayout.MARK) > 0);
        assertEquals(0x725E4D, AttitudeDisplayLayout.mix(0x000000, 0xE4BC9A, 0.5f));
    }

    @Test
    void radarWithoutReadingShowsThreeDashes() {
        double figure = AttitudeDisplayLayout.NUMBER_PIXEL;
        double bandTop = -6 + AttitudeDisplayLayout.BAND_HEIGHT;
        double cream = draw(state(0, 0, Double.NaN, 1)).stream()
                .filter(p -> p.color() == AttitudeDisplayLayout.CREAM && maxY(p) <= bandTop + 1e-4)
                .mapToDouble(Poly::area).sum();
        assertEquals(3 * 5 * figure * figure, cream, 1e-6);   // three dashes, each one 5 x 1 figure pixels
    }

    @Test
    void readingsStopAtThreeDigitsAndKeepTheirUnitInside() {
        // 4321 blocks reads 999 (three digits); the unit after it still ends inside the band's margin.
        var polygons = draw(readings(4321, 1234, 1f, 1f));
        double bandTop = -6 + AttitudeDisplayLayout.BAND_HEIGHT;
        double right = Double.NEGATIVE_INFINITY;
        for (var polygon : polygons) {
            if (maxY(polygon) > bandTop + 1e-4 || polygon.color() == AttitudeDisplayLayout.PANEL) continue;
            for (int i = 0; i < polygon.xy().length; i += 2) right = Math.max(right, polygon.xy()[i]);
        }
        assertEquals(6 - 2 * AttitudeDisplayLayout.DP, right, 1e-4);
        // The unit glyphs are much smaller than the figures.
        assertTrue(AttitudeDisplayLayout.UNIT_PIXEL * AttitudeDisplayLayout.Glyphs.UNIT_HEIGHT
                < 0.6 * AttitudeDisplayLayout.NUMBER_PIXEL * AttitudeDisplayLayout.Glyphs.HEIGHT);
    }

    private static double maxY(Poly poly) {
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 1; i < poly.xy().length; i += 2) max = Math.max(max, poly.xy()[i]);
        return max;
    }

    @Test
    void marksAreAtLeastOneDisplayPixelWide() {
        // On the horizon every mark is at least one display pixel; the band's unit glyphs go down to half of one.
        double bandTop = -6 + AttitudeDisplayLayout.BAND_HEIGHT;
        for (var polygon : draw(readings(128, 26, 1f, 1f))) {
            double dp = maxY(polygon) <= bandTop + 1e-4 ? AttitudeDisplayLayout.UNIT_PIXEL : AttitudeDisplayLayout.DP;
            int c = polygon.color();
            if (c == AttitudeDisplayLayout.SKY || c == AttitudeDisplayLayout.GROUND || c == AttitudeDisplayLayout.PANEL) continue;
            double x0 = Double.POSITIVE_INFINITY, x1 = Double.NEGATIVE_INFINITY, y0 = x0, y1 = x1;
            for (int i = 0; i < polygon.xy().length; i += 2) {
                x0 = Math.min(x0, polygon.xy()[i]); x1 = Math.max(x1, polygon.xy()[i]);
                y0 = Math.min(y0, polygon.xy()[i + 1]); y1 = Math.max(y1, polygon.xy()[i + 1]);
            }
            assertTrue(Math.min(x1 - x0, y1 - y0) >= dp - 1e-4, Integer.toHexString(c));
        }
    }

    @Test
    void flagsFollowTheStatus() {
        assertEquals(0, area(draw(state(0, 0, 100, 1)), AttitudeDisplayLayout.AMBER), 1e-9);
        assertTrue(area(draw(state(0, 0, 100, 1).withStatus(Status.DEGRADED)), AttitudeDisplayLayout.AMBER) > 0);
        var none = draw(state(0, 0, 100, 1).withStatus(Status.NO_DATA));
        assertTrue(area(none, AttitudeDisplayLayout.RED) > 0);
        assertEquals(0, area(none, AttitudeDisplayLayout.SKY), 1e-9);
        assertEquals(144, area(none, AttitudeDisplayLayout.PANEL), 1e-3);
    }

    @Test
    void theAircraftSymbolIsBrassAndCentredOnTheHorizonView() {
        var polygons = draw(state(0, 0, 100, 1));
        double low = Double.POSITIVE_INFINITY, high = Double.NEGATIVE_INFINITY, top = Double.NEGATIVE_INFINITY;
        for (var polygon : polygons) {
            if (polygon.color() != AttitudeDisplayLayout.BRASS) continue;
            for (int i = 0; i < polygon.xy().length; i += 2) {
                low = Math.min(low, polygon.xy()[i]); high = Math.max(high, polygon.xy()[i]);
                top = Math.max(top, polygon.xy()[i + 1]);
            }
        }
        assertEquals(0, (low + high) / 2, 1e-4);
        // The wing bars sit on the centre of the view above the band.
        assertEquals(AttitudeDisplayLayout.BAND_HEIGHT / 2 + AttitudeDisplayLayout.DP, top, 1e-4);
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
        for (char c : "bs/".toCharArray()) {
            var rows = AttitudeDisplayLayout.Glyphs.rows(c);
            assertNotNull(rows, "" + c);
            assertEquals(AttitudeDisplayLayout.Glyphs.UNIT_HEIGHT, rows.length);
            for (var row : rows) assertEquals(3, row.length());
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
        // Top row by day, middle row at dusk and at night with the same flight, bottom row the flags.
        var flying = new AttitudeDisplayState(Status.OK, 10, 30, 128, 26, 1f, 1f);
        var states = new AttitudeDisplayState[]{
                state(10, 30, 128, 0), flying, state(10, 30, 128, 1),
                flying, flying, state(10, 30, 128, 0),
                state(-4, -12, Double.NaN, 1).withStatus(Status.DEGRADED),
                new AttitudeDisplayState(Status.OK, -4, -12, 64, Double.NaN, 0f, 1f).withStatus(Status.DEGRADED),
                state(0, 0, 0, 1).withStatus(Status.NO_DATA)};
        float[] nights = {0, 0, 0, 0.5f, 1, 1, 0, 1, 0};
        int scale = 32, side = 12 * scale, gap = 24, columns = 3, rows = 3;
        var image = new BufferedImage(side * columns + gap * (columns + 1), side * rows + gap * (rows + 1),
                BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        g.setColor(new Color(0x303030));
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        for (int i = 0; i < states.length; i++) {
            int ox = gap + (i % columns) * (side + gap), oy = gap + (i / columns) * (side + gap);
            var graphics = g;
            AttitudeDisplayLayout.build((color, xy, count) -> {
                int[] px = new int[count], py = new int[count];
                for (int k = 0; k < count; k++) {
                    px[k] = ox + Math.round((xy[k * 2] + 6) * scale);
                    py[k] = oy + Math.round((6 - xy[k * 2 + 1]) * scale);
                }
                graphics.setColor(new Color(color));
                graphics.fillPolygon(px, py, count);
            }, states[i], nights[i]);
        }
        g.dispose();
        var out = new File(target);
        assertTrue(ImageIO.write(image, "png", out), out.getAbsolutePath());
    }
}
