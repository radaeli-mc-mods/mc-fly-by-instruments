package com.radaeli.aeronauticsflybyinstruments.client;

import java.util.Map;

/**
 * Geometry of the Attitude Indicator display, pure and without game state (so it is testable and the same code
 * draws the lens, the preview PNG and the unit tests). Flat convex polygons in lens units: the lens is 12 x 12
 * (both models), x right, y up, origin at the lens centre. Everything is a polygon, text included (a 5 x 7
 * pixel font), so the renderer draws one flat unlit pass that never fights the lens for depth.
 *
 * <p>v2 layout (2026-11-07) on a grid of display pixels ({@link #DP}, 48 across the lens): every stroke and font
 * pixel is a whole number of them. Sky and ground with a horizon line, a pitch ladder of unnumbered lines every
 * 10 degrees, a bank index with marks at 30 and 60 degrees and a pointer that turns with the roll, a brass
 * aircraft symbol, and a band along the bottom with the speed and height readings in large figures. A band that
 * is off gives its height back to the horizon, which stays centred in what is left.
 *
 * <p>Day and night: by day a light sky from Create's schematic blues, a lighter ground and lead-grey marks; at
 * night (the {@code night} factor of {@link #build(Canvas, AttitudeDisplayState, float)}, 0 day to 1 night)
 * the slate sky of the item's lens, a darker ground and cream marks, so the unlit display does not glare at night
 * under shaders. Sky and ground blend through dusk and dawn; the marks switch from dark to light halfway.
 */
final class AttitudeDisplayLayout {
    static final float HALF = 6f;
    /** Display pixel: the grid every size is a multiple of (48 across the 12-unit lens). */
    static final float DP = 0.25f;
    /** Height of the bottom band (speed and AGL readings). */
    static final float BAND_HEIGHT = 10 * DP;
    /** Font pixel of the band's figures and captions, and of the units after the figures (3 x 5 glyphs). */
    static final float NUMBER_PIXEL = 0.75f * DP, UNIT_PIXEL = 0.5f * DP;
    /** Pitch ladder scale: lens units per degree (one display pixel). */
    static final float PITCH_UNITS_PER_DEGREE = DP;
    static final float BANK_RADIUS = 15 * DP;

    /** Day palette (schematic blue sky, schematic-table brown ground, lead-grey marks) and its night ends. */
    static final int SKY = 0x9FB0E8, GROUND = 0x876438, MARK = 0x2B2D33,
            SKY_NIGHT = 0x464C5F, GROUND_NIGHT = 0x3B2814, MARK_NIGHT = 0xF6EDD8;
    /** Fixed colours: band figures, brass symbol and its outline, band panel, captions, flags. */
    static final int CREAM = 0xF6EDD8, BRASS = 0xE4B763, OUTLINE = 0x2B2D33, PANEL = 0x333846, MUTED = 0x8A90A6,
            AMBER = 0xF7CB6C, RED = 0xE02020;

    /** Receives flat convex polygons; {@code xy} holds {@code count} points and is only valid during the call. */
    interface Canvas {
        void polygon(int color, float[] xy, int count);
    }

    private AttitudeDisplayLayout() {}

    /** Daylight colours. */
    static void build(Canvas out, AttitudeDisplayState state) {
        build(out, state, 0);
    }

    /** @param night 0 in full daylight, 1 at night; colours blend in between */
    static void build(Canvas out, AttitudeDisplayState state, float night) {
        rect(out, PANEL, -HALF, -HALF, HALF, HALF);
        if (state.status() == AttitudeDisplayState.Status.NO_DATA) {
            text(out, "ATT", 0, 0, 2 * DP, RED, 0, 0, 0);
            return;
        }
        float t = Math.max(0, Math.min(1, night));
        // Sky and ground blend; the marks switch halfway, since a blend of dark and light marks is a grey that
        // loses the sky behind it.
        int sky = mix(SKY, SKY_NIGHT, t), ground = mix(GROUND, GROUND_NIGHT, t), mark = t < 0.5f ? MARK : MARK_NIGHT;
        float bottom = -HALF + Math.max(state.altitudeTape(), state.speedTape()) * BAND_HEIGHT;
        float centre = (bottom + HALF) / 2;

        var view = new Clip(out, -HALF, bottom, HALF, HALF);
        horizon(view, state, centre, bottom, sky, ground, mark);
        bankScale(view, state.rollDegrees(), centre, mark);
        aircraft(view, centre);
        band(out, state);
        if (state.status() == AttitudeDisplayState.Status.DEGRADED) {
            float y = bottom + 5.5f * DP;
            rect(view, PANEL, -10 * DP, y - 4.5f * DP, 10 * DP, y + 4.5f * DP);
            text(view, "ATT", 0, y, DP, AMBER, 0, 0, 0);
        }
    }

    /** Straight RGB blend from {@code a} (t 0) to {@code b} (t 1). */
    static int mix(int a, int b, float t) {
        if (t <= 0) return a;
        if (t >= 1) return b;
        int r = Math.round((a >> 16 & 255) + ((b >> 16 & 255) - (a >> 16 & 255)) * t);
        int g = Math.round((a >> 8 & 255) + ((b >> 8 & 255) - (a >> 8 & 255)) * t);
        int bl = Math.round((a & 255) + ((b & 255) - (a & 255)) * t);
        return r << 16 | g << 8 | bl;
    }

    // --- Horizon, ladder, bank, symbol ---------------------------------------------------------

    private static void horizon(Clip view, AttitudeDisplayState state, float centre, float bottom,
                                int sky, int ground, int mark) {
        double roll = Math.toRadians(state.rollDegrees());
        float dx = (float) Math.cos(roll), dy = (float) Math.sin(roll);   // along the horizon
        float nx = -dy, ny = dx;                                           // towards the sky
        // Pitch up moves the horizon down, along the sky direction.
        float offset = (float) (-state.pitchDegrees() * PITCH_UNITS_PER_DEGREE);
        float px = nx * offset, py = centre + ny * offset;
        float far = 30;

        rect(view, sky, -HALF, bottom, HALF, HALF);
        quad(view, ground, px - dx * far, py - dy * far, px + dx * far, py + dy * far,
                px + dx * far - nx * far, py + dy * far - ny * far, px - dx * far - nx * far, py - dy * far - ny * far);
        line(view, mark, px - dx * far, py - dy * far, px + dx * far, py + dy * far, DP);

        // Pitch ladder: a line every 10 degrees, longer at 20, 40..., no figures; clipped to a central window
        // that stays clear of the bank pointer.
        var window = new Clip(view, -7 * DP, centre - 11 * DP, 7 * DP, centre + 11 * DP);
        int first = (int) Math.max(-90, Math.floor((state.pitchDegrees() - 15) / 10) * 10);
        int last = (int) Math.min(90, Math.ceil((state.pitchDegrees() + 15) / 10) * 10);
        for (int degrees = first; degrees <= last; degrees += 10) {
            if (degrees == 0) continue;
            float cx = px + nx * degrees * PITCH_UNITS_PER_DEGREE, cy = py + ny * degrees * PITCH_UNITS_PER_DEGREE;
            float half = (degrees % 20 == 0 ? 6 : 4) * DP;
            line(window, mark, cx - dx * half, cy - dy * half, cx + dx * half, cy + dy * half, DP);
        }
    }

    private static final int[] BANK_MARKS = {30, 60};

    /** Fixed marks and index on the aircraft, and a pointer showing where the sky is (turns with the roll). */
    private static void bankScale(Clip view, double rollDegrees, float centre, int mark) {
        for (int degrees : BANK_MARKS) {
            for (int side = -1; side <= 1; side += 2) {
                double angle = Math.toRadians(side * degrees);
                float sin = (float) Math.sin(angle), cos = (float) Math.cos(angle);
                float inner = BANK_RADIUS - 2 * DP;
                line(view, mark, sin * inner, centre + cos * inner, sin * BANK_RADIUS, centre + cos * BANK_RADIUS, DP);
            }
        }
        // Index: a triangle on the scale, pointing down at 0 degrees.
        tri(view, mark, 0, centre + BANK_RADIUS, -1.5f * DP, centre + BANK_RADIUS + 2 * DP,
                1.5f * DP, centre + BANK_RADIUS + 2 * DP);
        // Pointer: the sky direction, so it moves against the bank: right wing down swings it to the left.
        double angle = -Math.toRadians(rollDegrees);
        float ex = (float) Math.sin(angle), ey = (float) Math.cos(angle), tx = ey, ty = -ex;
        float apex = BANK_RADIUS - DP, base = BANK_RADIUS - 3 * DP, wide = 1.5f * DP;
        tri(view, mark, ex * apex, centre + ey * apex,
                ex * base - tx * wide, centre + ey * base - ty * wide,
                ex * base + tx * wide, centre + ey * base + ty * wide);
    }

    /**
     * The marker that matters, in brass: wing bars with a drop at the inner end and a centre dot, outlined by one
     * display pixel of dark lead so it holds against the light day sky (the outline melts into the night sky).
     */
    private static void aircraft(Clip view, float centre) {
        symbol(view, centre, DP, OUTLINE);
        symbol(view, centre, 0, BRASS);
    }

    private static void symbol(Clip view, float centre, float grow, int color) {
        for (int side = -1; side <= 1; side += 2) {
            float outer = side * 11 * DP, inner = side * 4 * DP, drop = side * 6 * DP;
            rect(view, color, Math.min(outer, inner) - grow, centre - DP - grow, Math.max(outer, inner) + grow, centre + DP + grow);
            rect(view, color, Math.min(drop, inner) - grow, centre - 3 * DP - grow, Math.max(drop, inner) + grow, centre + DP + grow);
        }
        rect(view, color, -DP - grow, centre - DP - grow, DP + grow, centre + DP + grow);
    }

    // --- Bottom band ---------------------------------------------------------------------------------

    /**
     * One reading alone has its caption on the left and the figures on the right. With both, speed sits on the
     * left and height on the right (the order of a flight display) without captions, split by a short rule.
     * Figures carry their unit after them in a much smaller font: {@code b/s} for speed, {@code b} (blocks) for
     * height; both read up to three digits. Each reading slides in with its own amount; dashes stand for a
     * missing reading.
     */
    private static void band(Canvas out, AttitudeDisplayState state) {
        float speed = state.speedTape(), altitude = state.altitudeTape();
        String spd = speedFigures(state.speed()), agl = figures(state.aglBlocks());
        if (speed > 0 && altitude > 0) {
            reading(slot(out, speed), spd, "b/s", -HALF + 2 * DP, slotY(speed), -1);
            reading(slot(out, altitude), agl, "b", HALF - 2 * DP, slotY(altitude), 1);
            float both = Math.min(speed, altitude);
            rect(slot(out, both), MUTED, -DP / 2, slotY(both) - 2.5f * DP, DP / 2, slotY(both) + 2.5f * DP);
        } else if (altitude > 0) {
            labelled(out, "AGL", agl, "b", altitude);
        } else if (speed > 0) {
            labelled(out, "SPD", spd, "b/s", speed);
        }
    }

    /** Up to three digits, dashes without a reading. */
    private static String figures(double value) {
        return Double.isNaN(value) ? "---" : Integer.toString((int) Math.max(0, Math.min(999, Math.round(value))));
    }

    /**
     * Speed along the sensor's face, signed: a minus sign when the aircraft moves against the way the sensor faces.
     * The Velocity Sensor's own readout shows the size only, so this is the one place the direction is shown.
     */
    static String speedFigures(double value) {
        return Double.isNaN(value) ? "---" : Integer.toString((int) Math.max(-999, Math.min(999, Math.round(value))));
    }

    private static void labelled(Canvas out, String caption, String figures, String unit, float amount) {
        var band = slot(out, amount);
        text(band, caption, -HALF + 2 * DP, slotY(amount), NUMBER_PIXEL, MUTED, 0, -1, 0);
        reading(band, figures, unit, HALF - 2 * DP, slotY(amount), 1);
    }

    /** Figures and their unit, bottoms aligned; {@code anchor} -1 starts the pair at x, 1 ends it at x. */
    private static void reading(Canvas c, String figures, String unit, float x, float y, int anchor) {
        float gap = DP, wide = textWidth(figures, NUMBER_PIXEL);
        float start = anchor < 0 ? x : x - wide - gap - textWidth(unit, UNIT_PIXEL);
        text(c, figures, start, y, NUMBER_PIXEL, CREAM, 0, -1, 0);
        float bottom = y - Glyphs.HEIGHT / 2f * NUMBER_PIXEL;
        text(c, unit, start + wide + gap, bottom + Glyphs.UNIT_HEIGHT / 2f * UNIT_PIXEL, UNIT_PIXEL, MUTED, 0, -1, 0);
    }

    /** The visible part of the band for a reading shown by {@code amount}. */
    private static Clip slot(Canvas out, float amount) {
        return new Clip(out, -HALF, -HALF, HALF, -HALF + amount * BAND_HEIGHT);
    }

    /** Centre line of a reading: it slides in with the band's top edge. */
    private static float slotY(float amount) {
        return -HALF + amount * BAND_HEIGHT - BAND_HEIGHT / 2;
    }

    // --- Primitives --------------------------------------------------------------------------------

    private static final float[] Q = new float[8];

    static void quad(Canvas c, int color, float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3) {
        Q[0] = x0; Q[1] = y0; Q[2] = x1; Q[3] = y1; Q[4] = x2; Q[5] = y2; Q[6] = x3; Q[7] = y3;
        c.polygon(color, Q, 4);
    }

    static void tri(Canvas c, int color, float x0, float y0, float x1, float y1, float x2, float y2) {
        Q[0] = x0; Q[1] = y0; Q[2] = x1; Q[3] = y1; Q[4] = x2; Q[5] = y2;
        c.polygon(color, Q, 3);
    }

    static void rect(Canvas c, int color, float x0, float y0, float x1, float y1) {
        quad(c, color, x0, y0, x1, y0, x1, y1, x0, y1);
    }

    /** A straight stroke of the given width; ends are cut square on the end points. */
    static void line(Canvas c, int color, float x0, float y0, float x1, float y1, float width) {
        float dx = x1 - x0, dy = y1 - y0, length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 1e-5f) return;
        float nx = -dy / length * width / 2, ny = dx / length * width / 2;
        quad(c, color, x0 + nx, y0 + ny, x0 - nx, y0 - ny, x1 - nx, y1 - ny, x1 + nx, y1 + ny);
    }

    /** Width of {@code text} at {@code pixel}: glyph widths plus one pixel between glyphs. */
    static float textWidth(String text, float pixel) {
        int columns = -1;
        for (int i = 0; i < text.length(); i++) {
            String[] rows = Glyphs.rows(text.charAt(i));
            columns += (rows == null ? Glyphs.WIDTH : rows[0].length()) + 1;
        }
        return Math.max(0, columns) * pixel;
    }

    /**
     * Text in the pixel font (5 x 7, and 3 x 5 for the unit glyphs), upright when {@code angle} is 0 or turned
     * about (x, y) by {@code angle} radians (counter-clockwise). {@code anchor}: -1 starts at x, 0 centres on it,
     * 1 ends at it; each glyph is centred vertically on y. {@code outline} > 0 first draws every pixel run grown
     * by that much in the panel colour.
     */
    static void text(Canvas c, String text, float x, float y, float pixel, int color, double angle, int anchor,
                     float outline) {
        float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle);
        float width = textWidth(text, pixel);
        float start = anchor < 0 ? 0 : anchor == 0 ? -width / 2 : -width;
        for (int pass = outline > 0 ? 0 : 1; pass < 2; pass++) {
            int fill = pass == 0 ? PANEL : color;
            float grow = pass == 0 ? outline : 0;
            float u0 = start;
            for (int i = 0; i < text.length(); i++) {
                String[] rows = Glyphs.rows(text.charAt(i));
                if (rows == null) { u0 += (Glyphs.WIDTH + 1) * pixel; continue; }
                int columns = rows[0].length(), height = rows.length;
                for (int row = 0; row < height; row++) {
                    int column = 0;
                    while (column < columns) {
                        if (rows[row].charAt(column) != 'X') { column++; continue; }
                        int end = column;
                        while (end + 1 < columns && rows[row].charAt(end + 1) == 'X') end++;
                        float ua = u0 + column * pixel - grow, ub = u0 + (end + 1) * pixel + grow;
                        float vt = (height / 2f - row) * pixel + grow, vb = (height / 2f - row - 1) * pixel - grow;
                        quad(c, fill, x + ua * cos - vt * sin, y + ua * sin + vt * cos,
                                x + ub * cos - vt * sin, y + ub * sin + vt * cos,
                                x + ub * cos - vb * sin, y + ub * sin + vb * cos,
                                x + ua * cos - vb * sin, y + ua * sin + vb * cos);
                        column = end + 1;
                    }
                }
                u0 += (columns + 1) * pixel;
            }
        }
    }

    /** Axis-aligned clip of every polygon sent through it (Sutherland-Hodgman), forwarding what is left. */
    static final class Clip implements Canvas {
        private final Canvas target;
        private final float x0, y0, x1, y1;
        private float[] input = new float[32], output = new float[32];

        Clip(Canvas target, float x0, float y0, float x1, float y1) {
            this.target = target;
            this.x0 = x0; this.y0 = y0; this.x1 = x1; this.y1 = y1;
        }

        @Override
        public void polygon(int color, float[] xy, int count) {
            if (x1 <= x0 || y1 <= y0) return;
            System.arraycopy(xy, 0, input, 0, count * 2);
            for (int edge = 0; edge < 4 && count > 0; edge++) {
                int n = 0;
                for (int i = 0; i < count; i++) {
                    int j = (i + 1) % count;
                    float ax = input[i * 2], ay = input[i * 2 + 1], bx = input[j * 2], by = input[j * 2 + 1];
                    float da = distance(edge, ax, ay), db = distance(edge, bx, by);
                    if (da >= 0) { output[n * 2] = ax; output[n * 2 + 1] = ay; n++; }
                    if (da >= 0 != db >= 0) {
                        float t = da / (da - db);
                        output[n * 2] = ax + (bx - ax) * t; output[n * 2 + 1] = ay + (by - ay) * t; n++;
                    }
                }
                var swap = input; input = output; output = swap;
                count = n;
            }
            if (count >= 3) target.polygon(color, input, count);
        }

        /** Signed distance inside the edge (>= 0 is kept). */
        private float distance(int edge, float x, float y) {
            return switch (edge) {
                case 0 -> x - x0;
                case 1 -> x1 - x;
                case 2 -> y - y0;
                default -> y1 - y;
            };
        }
    }

    /** The 5 x 7 pixel font (digits and the few letters the display needs, same style as the FMS compass letters) and the 3 x 5 unit glyphs. */
    static final class Glyphs {
        static final int WIDTH = 5, HEIGHT = 7;
        /** Height of the small unit glyphs (lower case and the slash, 3 x 5). */
        static final int UNIT_HEIGHT = 5;
        private static final Map<Character, String[]> ROWS = Map.ofEntries(
                Map.entry('0', new String[]{".XXX.", "X...X", "X..XX", "X.X.X", "XX..X", "X...X", ".XXX."}),
                Map.entry('1', new String[]{"..X..", ".XX..", "..X..", "..X..", "..X..", "..X..", ".XXX."}),
                Map.entry('2', new String[]{".XXX.", "X...X", "....X", "...X.", "..X..", ".X...", "XXXXX"}),
                Map.entry('3', new String[]{".XXX.", "X...X", "....X", "..XX.", "....X", "X...X", ".XXX."}),
                Map.entry('4', new String[]{"...X.", "..XX.", ".X.X.", "X..X.", "XXXXX", "...X.", "...X."}),
                Map.entry('5', new String[]{"XXXXX", "X....", "XXXX.", "....X", "....X", "X...X", ".XXX."}),
                Map.entry('6', new String[]{".XXX.", "X....", "X....", "XXXX.", "X...X", "X...X", ".XXX."}),
                Map.entry('7', new String[]{"XXXXX", "....X", "...X.", "..X..", ".X...", ".X...", ".X..."}),
                Map.entry('8', new String[]{".XXX.", "X...X", "X...X", ".XXX.", "X...X", "X...X", ".XXX."}),
                Map.entry('9', new String[]{".XXX.", "X...X", "X...X", ".XXXX", "....X", "...X.", ".XX.."}),
                Map.entry('A', new String[]{".XXX.", "X...X", "X...X", "XXXXX", "X...X", "X...X", "X...X"}),
                Map.entry('D', new String[]{"XXXX.", "X...X", "X...X", "X...X", "X...X", "X...X", "XXXX."}),
                Map.entry('P', new String[]{"XXXX.", "X...X", "X...X", "XXXX.", "X....", "X....", "X...."}),
                Map.entry('S', new String[]{".XXXX", "X....", "X....", ".XXX.", "....X", "....X", "XXXX."}),
                Map.entry('b', new String[]{"X..", "X..", "XXX", "X.X", "XXX"}),
                Map.entry('s', new String[]{"XXX", "X..", "XXX", "..X", "XXX"}),
                Map.entry('/', new String[]{"..X", "..X", ".X.", "X..", "X.."}),
                Map.entry('G', new String[]{".XXX.", "X...X", "X....", "X.XXX", "X...X", "X...X", ".XXX."}),
                Map.entry('L', new String[]{"X....", "X....", "X....", "X....", "X....", "X....", "XXXXX"}),
                Map.entry('T', new String[]{"XXXXX", "..X..", "..X..", "..X..", "..X..", "..X..", "..X.."}),
                Map.entry('-', new String[]{".....", ".....", ".....", "XXXXX", ".....", ".....", "....."}));

        private Glyphs() {}

        static String[] rows(char c) {
            return ROWS.get(c);
        }
    }
}
