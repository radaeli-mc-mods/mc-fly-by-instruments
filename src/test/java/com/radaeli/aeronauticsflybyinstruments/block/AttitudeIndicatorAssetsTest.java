package com.radaeli.aeronauticsflybyinstruments.block;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.radaeli.aeronauticsflybyinstruments.panel.PanelCoordinates;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Attitude Indicator v2 (visual preview, 2x2): wedge for floor tables, plate for the rest (guide §9). */
class AttitudeIndicatorAssetsTest {
    private static final String NS = "aeronautics_fly_by_instruments";
    private static final Map<String, int[]> AXES = Map.of("north", new int[]{0, 1}, "south", new int[]{0, 1},
            "east", new int[]{2, 1}, "west", new int[]{2, 1}, "up", new int[]{0, 2}, "down", new int[]{0, 2});

    private static JsonObject json(String path) throws Exception {
        try (var input = AttitudeIndicatorAssetsTest.class.getResourceAsStream("/assets/" + NS + "/" + path)) {
            assertNotNull(input, path);
            return JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static double[] vec(JsonArray array) {
        return new double[]{array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble()};
    }

    private static JsonObject element(JsonObject model, String name) {
        for (var entry : model.getAsJsonArray("elements")) {
            if (entry.getAsJsonObject().get("name").getAsString().equals(name)) return entry.getAsJsonObject();
        }
        throw new AssertionError("Missing element " + name);
    }

    @Test
    void itemUsesTheWedgeAndSwitchesToThePlateByCustomModelData() throws Exception {
        var item = json("models/item/attitude_indicator_module.json");
        assertEquals(NS + ":block/attitude_indicator_desk", item.get("parent").getAsString());
        assertEquals("front", item.get("gui_light").getAsString());
        for (String context : new String[]{"gui", "ground", "fixed", "thirdperson_righthand", "thirdperson_lefthand",
                "firstperson_righthand", "firstperson_lefthand"}) {
            assertTrue(item.getAsJsonObject("display").has(context), context);
        }
        var override = item.getAsJsonArray("overrides").get(0).getAsJsonObject();
        assertEquals(1, override.getAsJsonObject("predicate").get("custom_model_data").getAsInt());
        assertEquals(NS + ":block/attitude_indicator_wall", override.get("model").getAsString());
    }

    @Test
    void bothModelsFollowTheTableModuleGuide() throws Exception {
        for (String name : new String[]{"attitude_indicator_desk", "attitude_indicator_wall"}) {
            var model = json("models/block/" + name + ".json");
            assertEquals("create:block/industrial_iron_block", model.getAsJsonObject("textures").get("particle").getAsString());
            var elements = model.getAsJsonArray("elements");
            assertTrue(elements.size() <= 15, name + ": screen module budget is ~15 cubes");
            double[] low = {16, 16, 16}, high = {0, 0, 0};
            var names = new HashSet<String>();
            for (var entry : elements) {
                var cube = entry.getAsJsonObject();
                names.add(cube.get("name").getAsString());
                var from = vec(cube.getAsJsonArray("from")); var to = vec(cube.getAsJsonArray("to"));
                for (int axis = 0; axis < 3; axis++) {
                    assertEquals(Math.rint(from[axis]), from[axis], cube.get("name") + ": integer coordinates");
                    assertEquals(Math.rint(to[axis]), to[axis], cube.get("name") + ": integer coordinates");
                    low[axis] = Math.min(low[axis], from[axis]); high[axis] = Math.max(high[axis], to[axis]);
                }
                // 1 texel per unit on every face: 32 px atlas = 2 per uv unit, Create's 16 px texture = 1.
                for (var face : cube.getAsJsonObject("faces").entrySet()) {
                    var f = face.getValue().getAsJsonObject();
                    var uv = f.getAsJsonArray("uv");
                    double texels = f.get("texture").getAsString().equals("#0") ? 2 : 1;
                    int[] axes = AXES.get(face.getKey());
                    assertEquals(to[axes[0]] - from[axes[0]], Math.abs(uv.get(2).getAsDouble() - uv.get(0).getAsDouble()) * texels,
                            1e-9, name + "/" + cube.get("name") + "." + face.getKey() + " u");
                    assertEquals(to[axes[1]] - from[axes[1]], Math.abs(uv.get(3).getAsDouble() - uv.get(1).getAsDouble()) * texels,
                            1e-9, name + "/" + cube.get("name") + "." + face.getKey() + " v");
                }
            }
            // 10 x 10 carcass (2-cell axis, guide §9.2), centred on X/Z 8, resting on Y 0.
            assertEquals(3, low[0]); assertEquals(13, high[0]);
            assertEquals(3, low[2]);
            assertEquals(0, low[1]);
            if (name.endsWith("desk")) {
                // The visor stands one unit past the screen front (z 13).
                assertEquals(14, high[2]);
                assertEquals(12, high[1], "The wedge's top meets the block top over the 4 px table");
                assertEquals(PanelCoordinates.DISPLAY_HEIGHT * 16, high[1]);
                assertEquals(16, high[1] + PanelCoordinates.SURFACE * 16);
                // Only the lid turns, and by 22.5 degrees about X (the Minecraft element limit; guide §3).
                for (var entry : elements) {
                    var cube = entry.getAsJsonObject();
                    if (cube.get("name").getAsString().equals("housing_slope")) {
                        var rotation = cube.getAsJsonObject("rotation");
                        assertEquals("x", rotation.get("axis").getAsString());
                        assertEquals(22.5, Math.abs(rotation.get("angle").getAsDouble()));
                    } else {
                        assertFalse(cube.has("rotation"), cube.get("name") + ": unrotated");
                    }
                }
                assertTrue(names.containsAll(java.util.List.of("housing", "housing_foot", "housing_slope", "screen",
                        "visor")));
                assertFalse(names.contains("knob"), "No controls on this module");
                // The screen is one solid cube, so the display cannot show through a gap behind it; the renderer
                // draws on its front (z 13), lens X 4..12 / Y 3..11.
                var screen = element(model, "screen");
                assertArrayEquals(new double[]{3, 2, 11}, vec(screen.getAsJsonArray("from")));
                assertArrayEquals(new double[]{13, 12, 13}, vec(screen.getAsJsonArray("to")));
                for (String side : new String[]{"north", "south", "east", "west", "up"}) {
                    assertTrue(screen.getAsJsonObject("faces").has(side), "screen." + side);
                }
                // The lid is inset from the screen sides (no coplanar faces) and its front end is buried in the screen.
                var slope = element(model, "housing_slope");
                assertEquals(4, vec(slope.getAsJsonArray("from"))[0]);
                assertEquals(12, vec(slope.getAsJsonArray("to"))[0]);
                assertFalse(slope.getAsJsonObject("faces").has("south"));
                // Sun visor over the top row of the frame, one unit proud.
                var visor = element(model, "visor");
                assertArrayEquals(new double[]{3, 11, 13}, vec(visor.getAsJsonArray("from")));
                assertArrayEquals(new double[]{13, 12, 14}, vec(visor.getAsJsonArray("to")));
            } else {
                assertEquals(13, high[2]);
                assertEquals(3, high[1], "The plate is flat: housing 2 + screen 1");
                for (var entry : elements) assertFalse(entry.getAsJsonObject().has("rotation"));
                var screen = element(model, "screen");
                assertArrayEquals(new double[]{3, 2, 3}, vec(screen.getAsJsonArray("from")));
                assertArrayEquals(new double[]{13, 3, 13}, vec(screen.getAsJsonArray("to")));
                assertTrue(screen.getAsJsonObject("faces").has("up"));
            }
        }
    }

    @Test
    void atlasIs32SquareAndOpaque() throws Exception {
        try (var input = AttitudeIndicatorAssetsTest.class.getResourceAsStream(
                "/assets/" + NS + "/textures/block/attitude_indicator_module.png")) {
            var image = ImageIO.read(input);
            assertEquals(32, image.getWidth());
            assertEquals(32, image.getHeight());
            for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) {
                assertEquals(255, image.getRGB(x, y) >>> 24, "opaque at " + x + "," + y);
            }
        }
    }

    @Test
    void selectionVolumeStopsAtTheBlockTopForTheTallWedge() {
        assertEquals(1.0, PanelCoordinates.moduleTop(PanelCoordinates.ATTITUDE_INDICATOR));
        assertEquals(PanelCoordinates.MODULE_TOP, PanelCoordinates.moduleTop("fms"));
        assertEquals(PanelCoordinates.MODULE_TOP, PanelCoordinates.moduleTop("lamp"));
    }

    @Test
    void langHasTheNameInBothLanguages() throws Exception {
        for (String lang : new String[]{"en_us", "pt_br"}) {
            var strings = json("lang/" + lang + ".json");
            assertTrue(strings.has("item." + NS + ".attitude_indicator_module"), lang);
            assertTrue(strings.has("item." + NS + ".attitude_indicator_module.tooltip.summary"), lang);
        }
    }
}
