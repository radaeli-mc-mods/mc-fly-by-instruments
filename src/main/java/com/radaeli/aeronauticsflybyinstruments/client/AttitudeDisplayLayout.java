package com.radaeli.aeronauticsflybyinstruments.client;

import java.util.Map;

/**
 * Geometry of the Attitude Indicator display, pure and without game state (so it is testable and the same code
 * draws the lens, the preview PNG and the unit tests). Flat convex polygons in lens units: the lens is 12 x 12
 * (both models), x right, y up, origin at the lens centre. Everything is a polygon, text included (a 5 x 7
 * pixel font), so the renderer draws one flat unlit pass that never fights the lens for depth.
 *
 * <p>Layout (guide §9.4, display content): sky and ground with a white horizon line, a pitch ladder (long
 * line and number every 10 degrees, short every 5, within a central window), a fixed bank scale with a pointer
 * that turns with the roll, a brass aircraft symbol, and an optional altitude tape on the right. A tape that
 * is off gives its space back to the horizon, which stays centred in what is left. The speed tape is reserved
 * for a future sensor: it only takes (and gives back) its space.
 */
final class AttitudeDisplayLayout {
    static final float HALF = 6f;
    static final float TAPE_WIDTH = 2.2f;
    /** Pitch ladder scale: lens units per degree (20 degrees is 5 units). */
    static final float PITCH_UNITS_PER_DEGREE = 0.25f;
    /** Altitude tape scale: lens units per block of height. */
    static final float TAPE_UNITS_PER_BLOCK = 0.12f;
    static final float BANK_RADIUS = 4.9f;

    static final int SKY = 0x2F6DB5, GROUND = 0x7B4A22, GROUND_DARK = 0x4E2F14, WHITE = 0xF2F2EA, BRASS = 0xE4B763,
            BLACK = 0x000000, AMBER = 0xF7CB6C, RED = 0xE02020, TAPE = 0x1E2127, TAPE_HEADER = 0x14161A, GREY = 0xB9BCB5;

    /** Receives flat convex polygons; {@code xy} holds {@code count} points and is only valid during the call. */
    interface Canvas {
        void polygon(int color, float[] xy, int count);
    }

    private AttitudeDisplayLayout() {}

    static void build(Canvas out, AttitudeDisplayState state) {
        rect(out, BLACK, -HALF, -HALF, HALF, HALF);
        if (state.status() == AttitudeDisplayState.Status.NO_DATA) {
            text(out, "ATT", 0, 0, 0.3f, RED, 0, 0, 0);
            return;
        }
        float left = -HALF + state.speedTape() * TAPE_WIDTH;
        float right = HALF - state.altitudeTape() * TAPE_WIDTH;
        float centre = (left + right) / 2;

        var view = new Clip(out, left, -HALF, right, HALF);
        horizon(view, state, centre, left, right);
        bankScale(view, state.rollDegrees(), centre);
        aircraft(view, centre);
        if (state.altitudeTape() > 0) altitudeTape(out, state.aglBlocks(), state.altitudeTape());
        if (state.status() == AttitudeDisplayState.Status.DEGRADED) {
            text(view, "ATT", centre, -5.1f, 0.09f, AMBER, 0, 0, 0.03f);
        }
    }

    // --- Horizon, ladder, bank, symbol ---------------------------------------------------------

    private static void horizon(Clip view, AttitudeDisplayState state, float centre, float left, float right) {
        double roll = Math.toRadians(state.rollDegrees());
        float dx = (float) Math.cos(roll), dy = (float) Math.sin(roll);   // along the horizon
        float nx = -dy, ny = dx;                                           // towards the sky
        // Pitch up moves the horizon down, along the sky direction.
        float offset = (float) (-state.pitchDegrees() * PITCH_UNITS_PER_DEGREE);
        float px = centre + nx * offset, py = ny * offset;
        float far = 30;

        rect(view, SKY, left, -HALF, right, HALF);
        quad(view, GROUND, px - dx * far, py - dy * far, px + dx * far, py + dy * far,
                px + dx * far - nx * far, py + dy * far - ny * far, px - dx * far - nx * far, py - dy * far - ny * far);
        line(view, WHITE, px - dx * far, py - dy * far, px + dx * far, py + dy * far, 0.1f);

        // Pitch ladder, clipped to a central window so it never crowds the edges or the tape.
        var window = new Clip(view, centre - 2.9f, -3.9f, centre + 2.9f, 3.9f);
        int first = (int) Math.max(-90, Math.floor((state.pitchDegrees() - 25) / 5) * 5);
        int last = (int) Math.min(90, Math.ceil((state.pitchDegrees() + 25) / 5) * 5);
        for (int degrees = first; degrees <= last; degrees += 5) {
            if (degrees == 0) continue;
            float cx = px + nx * degrees * PITCH_UNITS_PER_DEGREE, cy = py + ny * degrees * PITCH_UNITS_PER_DEGREE;
            boolean major = degrees % 10 == 0;
            float half = major ? 1.1f : 0.55f;
            line(window, WHITE, cx - dx * half, cy - dy * half, cx + dx * half, cy + dy * half, 0.08f);
            if (!major) continue;
            String label = Integer.toString(Math.abs(degrees));
            float reach = half + 0.55f;
            text(window, label, cx - dx * reach, cy - dy * reach, 0.075f, WHITE, roll, 0, 0.03f);
            text(window, label, cx + dx * reach, cy + dy * reach, 0.075f, WHITE, roll, 0, 0.03f);
        }
    }

    private static final int[] BANK_MARKS = {10, 20, 30, 45, 60};
    private static final float[] BANK_LENGTHS = {0.3f, 0.3f, 0.55f, 0.35f, 0.55f};

    /** Fixed scale on the aircraft, a fixed index at 0 and a pointer showing where the sky is (turns with the roll). */
    private static void bankScale(Clip view, double rollDegrees, float centre) {
        for (int i = 0; i < BANK_MARKS.length; i++) {
            for (int side = -1; side <= 1; side += 2) {
                double angle = Math.toRadians(side * BANK_MARKS[i]);
                float sin = (float) Math.sin(angle), cos = (float) Math.cos(angle);
                float inner = BANK_RADIUS - BANK_LENGTHS[i];
                line(view, WHITE, centre + sin * inner, cos * inner, centre + sin * BANK_RADIUS, cos * BANK_RADIUS, 0.08f);
            }
        }
        // Index: a triangle above the scale, pointing down at 0 degrees.
        tri(view, WHITE, centre, BANK_RADIUS + 0.05f, centre - 0.28f, BANK_RADIUS + 0.6f, centre + 0.28f, BANK_RADIUS + 0.6f);
        // Pointer: the sky direction, so it moves against the bank: right wing down swings it to the left.
        double angle = -Math.toRadians(rollDegrees);
        float ex = (float) Math.sin(angle), ey = (float) Math.cos(angle), tx = ey, ty = -ex;
        float apex = BANK_RADIUS - 0.62f, base = BANK_RADIUS - 1.1f;
        tri(view, WHITE, centre + ex * apex, ey * apex,
                centre + ex * base - tx * 0.22f, ey * base - ty * 0.22f,
                centre + ex * base + tx * 0.22f, ey * base + ty * 0.22f);
    }

    /** The marker that matters (brass, outlined in black): wing bars with a drop at the inner end and a centre dot. */
    private static void aircraft(Clip view, float centre) {
        symbol(view, centre, 0.08f, BLACK);
        symbol(view, centre, 0, BRASS);
    }

    private static void symbol(Clip view, float centre, float grow, int color) {
        for (int side = -1; side <= 1; side += 2) {
            float outer = centre + side * 3.0f, inner = centre + side * 1.1f, drop = centre + side * 1.32f;
            rect(view, color, Math.min(outer, inner) - grow, -0.11f - grow, Math.max(outer, inner) + grow, 0.11f + grow);
            rect(view, color, Math.min(drop, inner) - grow, -0.55f - grow, Math.max(drop, inner) + grow, 0.11f + grow);
        }
        rect(view, color, centre - 0.13f - grow, -0.13f - grow, centre + 0.13f + grow, 0.13f + grow);
    }

    // --- Altitude tape ---------------------------------------------------------------------------

    private static void altitudeTape(Canvas out, double agl, float amount) {
        float x0 = HALF - TAPE_WIDTH;
        var tape = new Clip(out, HALF - amount * TAPE_WIDTH, -HALF, HALF, HALF);
        rect(tape, TAPE, x0, -HALF, HALF, HALF);
        boolean valid = !Double.isNaN(agl);
        if (valid) {
            float zero = (float) (-agl * TAPE_UNITS_PER_BLOCK);
            if (zero > -HALF) rect(tape, GROUND_DARK, x0, -HALF, HALF, zero);   // below the ground
            double span = HALF / TAPE_UNITS_PER_BLOCK;
            int from = (int) Math.max(0, Math.floor((agl - span) / 10) * 10);
            int to = (int) Math.ceil((agl + span) / 10) * 10;
            for (int value = from; value <= to; value += 10) {
                float y = (float) ((value - agl) * TAPE_UNITS_PER_BLOCK);
                boolean major = value % 20 == 0;
                line(tape, WHITE, x0, y, x0 + (major ? 0.55f : 0.3f), y, 0.07f);
                if (major) text(tape, Integer.toString(value), x0 + 0.75f, y, 0.075f, WHITE, 0, -1, 0);
            }
        }
        line(tape, GREY, x0, -HALF, x0, HALF, 0.05f);
        // Caption and reading box (the box always shows the figure, or dashes without a radar reading).
        rect(tape, TAPE_HEADER, x0, 5.0f, HALF, HALF);
        text(tape, "AGL", x0 + 0.15f, 5.5f, 0.06f, GREY, 0, -1, 0);
        rect(tape, WHITE, x0 + 0.05f, -0.55f, HALF - 0.05f, 0.55f);
        rect(tape, BLACK, x0 + 0.1f, -0.5f, HALF - 0.1f, 0.5f);
        String reading = valid ? Integer.toString((int) Math.max(0, Math.min(9999, Math.round(agl)))) : "----";
        text(tape, reading, HALF - 0.17f, 0, 0.08f, WHITE, 0, 1, 0);
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

    /**
     * Text in the 5 x 7 pixel font, upright when {@code angle} is 0 or turned about (x, y) by {@code angle}
     * radians (counter-clockwise). {@code anchor}: -1 starts at x, 0 centres on it, 1 ends at it; the text is
     * centred vertically on y. {@code outline} > 0 first draws every pixel run grown by that much in black.
     */
    static void text(Canvas c, String text, float x, float y, float pixel, int color, double angle, int anchor,
                     float outline) {
        float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle);
        float width = (text.length() * (Glyphs.WIDTH + 1) - 1) * pixel;
        float start = anchor < 0 ? 0 : anchor == 0 ? -width / 2 : -width;
        for (int pass = outline > 0 ? 0 : 1; pass < 2; pass++) {
            int fill = pass == 0 ? BLACK : color;
            float grow = pass == 0 ? outline : 0;
            for (int i = 0; i < text.length(); i++) {
                String[] rows = Glyphs.rows(text.charAt(i));
                if (rows == null) continue;
                float u0 = start + i * (Glyphs.WIDTH + 1) * pixel;
                for (int row = 0; row < Glyphs.HEIGHT; row++) {
                    int column = 0;
                    while (column < Glyphs.WIDTH) {
                        if (rows[row].charAt(column) != 'X') { column++; continue; }
                        int end = column;
                        while (end + 1 < Glyphs.WIDTH && rows[row].charAt(end + 1) == 'X') end++;
                        float ua = u0 + column * pixel - grow, ub = u0 + (end + 1) * pixel + grow;
                        float vt = (Glyphs.HEIGHT / 2f - row) * pixel + grow, vb = (Glyphs.HEIGHT / 2f - row - 1) * pixel - grow;
                        quad(c, fill, x + ua * cos - vt * sin, y + ua * sin + vt * cos,
                                x + ub * cos - vt * sin, y + ub * sin + vt * cos,
                                x + ub * cos - vb * sin, y + ub * sin + vb * cos,
                                x + ua * cos - vb * sin, y + ua * sin + vb * cos);
                        column = end + 1;
                    }
                }
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

    /** The 5 x 7 pixel font: digits and the few letters the display needs. Same style as the FMS compass letters. */
    static final class Glyphs {
        static final int WIDTH = 5, HEIGHT = 7;
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
