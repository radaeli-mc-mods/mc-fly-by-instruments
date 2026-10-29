package com.radaeli.aeronauticsflybyinstruments.fms;

import com.google.gson.JsonParser;
import com.radaeli.aeronauticsflybyinstruments.sensor.SensorModelChecks;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class FmsAssetsTest {
    @Test
    void moduleUsesCenteredModel() throws Exception {
        var module = readJson("/assets/aeronautics_fly_by_instruments/models/item/fms_module.json");
        assertEquals("aeronautics_fly_by_instruments:block/fms_module_2x3", module.get("parent").getAsString());
        // Tilted face-on in the GUI: side lighting would shade the screen almost black.
        assertEquals("front", module.get("gui_light").getAsString());
        var model = readJson("/assets/aeronautics_fly_by_instruments/models/block/fms_module_2x3.json");
        double[] low = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
        double[] high = {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
        for (var element : model.getAsJsonArray("elements")) {
            var cube = element.getAsJsonObject();
            for (int axis = 0; axis < 3; axis++) {
                low[axis] = Math.min(low[axis], cube.getAsJsonArray("from").get(axis).getAsDouble());
                high[axis] = Math.max(high[axis], cube.getAsJsonArray("to").get(axis).getAsDouble());
            }
        }
        assertEquals(8, (low[0] + high[0]) / 2);
        assertEquals(8, (low[2] + high[2]) / 2);
        assertEquals(0, low[1]);
        assertTrue(high[0] - low[0] < 16 * 2.0 / 3);
        assertTrue(high[2] - low[2] < 16);
    }

    /** FMS Compass v2.1: a round pocket compass (stepped disc) with the FMS face: iron rim, brass bezel, lens. */
    @Test
    void navigatorIsARoundCompassInTheFmsLanguage() throws Exception {
        var item = readJson("/assets/aeronautics_fly_by_instruments/models/item/fms_navigator.json");
        assertEquals("aeronautics_fly_by_instruments:block/fms_navigator", item.get("parent").getAsString());
        for (String context : new String[]{"gui", "ground", "fixed", "thirdperson_righthand", "thirdperson_lefthand",
                "firstperson_righthand", "firstperson_lefthand"}) {
            assertTrue(item.getAsJsonObject("display").has(context), context);
        }
        SensorModelChecks.assertGuideRules("fms_navigator");
        var model = SensorModelChecks.model("fms_navigator");
        assertEquals(java.util.Set.of("aeronautics_fly_by_instruments:block/fms_navigator",
                "create:block/industrial_iron_block"), SensorModelChecks.textureIds(model));
        assertEquals("create:block/industrial_iron_block", model.getAsJsonObject("textures").get("particle").getAsString());
        assertTrue(model.getAsJsonArray("elements").size() <= 8, "Small item: a handful of cubes");
        // The disc is centred on (8, 8) and as wide as it is deep: it reads round.
        double[] low = {16, 16}, high = {0, 0};
        for (var element : model.getAsJsonArray("elements")) {
            var cube = element.getAsJsonObject();
            var from = SensorModelChecks.vector(cube.getAsJsonArray("from"));
            var to = SensorModelChecks.vector(cube.getAsJsonArray("to"));
            assertEquals(8, (from[0] + to[0]) / 2, cube.get("name") + " is centred on X");
            if (!cube.get("name").getAsString().startsWith("disc")) continue;
            low[0] = Math.min(low[0], from[0]); high[0] = Math.max(high[0], to[0]);
            low[1] = Math.min(low[1], from[2]); high[1] = Math.max(high[1], to[2]);
        }
        assertEquals(8, (low[1] + high[1]) / 2, "The disc is centred on Z");
        assertEquals(high[0] - low[0], high[1] - low[1], "The disc is as wide as it is deep");
    }

    /** v2.1 (guide §9): 10 x 14 carcass, integer grid, 1 texel per unit, industrial iron housing. */
    @Test
    void moduleFollowsTheTableModuleGuide() throws Exception {
        var model = readJson("/assets/aeronautics_fly_by_instruments/models/block/fms_module_2x3.json");
        var textures = model.getAsJsonObject("textures");
        assertEquals("create:block/industrial_iron_block", textures.get("particle").getAsString());
        var elements = model.getAsJsonArray("elements");
        assertTrue(elements.size() <= 15, "Screen module budget is ~15 cubes");
        var names = new java.util.HashSet<String>();
        double[] low = {16, 16, 16};
        double[] high = {0, 0, 0};
        for (var element : elements) {
            var cube = element.getAsJsonObject();
            names.add(cube.get("name").getAsString());
            assertFalse(cube.has("rotation"), cube.get("name") + ": unrotated");
            double[] size = new double[3];
            for (int axis = 0; axis < 3; axis++) {
                double from = cube.getAsJsonArray("from").get(axis).getAsDouble();
                double to = cube.getAsJsonArray("to").get(axis).getAsDouble();
                assertEquals(Math.rint(from), from, "integer coordinates");
                assertEquals(Math.rint(to), to, "integer coordinates");
                low[axis] = Math.min(low[axis], from);
                high[axis] = Math.max(high[axis], to);
                size[axis] = to - from;
            }
            for (var face : cube.getAsJsonObject("faces").entrySet()) {
                var json = face.getValue().getAsJsonObject();
                assertFalse(json.has("cullface"), "Modules never cull against neighbouring blocks");
                var id = textures.get(json.get("texture").getAsString().substring(1)).getAsString();
                int width = id.startsWith("create:") ? 16 : readImage(id).getWidth();
                int[] axes = switch (face.getKey()) {
                    case "north", "south" -> new int[]{0, 1};
                    case "east", "west" -> new int[]{2, 1};
                    default -> new int[]{0, 2};
                };
                var uv = json.getAsJsonArray("uv");
                assertEquals(size[axes[0]], Math.abs(uv.get(2).getAsDouble() - uv.get(0).getAsDouble()) * width / 16,
                        cube.get("name") + "." + face.getKey() + ": 1 texel per unit (u)");
                assertEquals(size[axes[1]], Math.abs(uv.get(3).getAsDouble() - uv.get(1).getAsDouble()) * width / 16,
                        cube.get("name") + "." + face.getKey() + ": 1 texel per unit (v)");
            }
        }
        assertArrayEquals(new double[]{3, 0, 1}, low);
        assertArrayEquals(new double[]{13, 3, 15}, high);
        assertTrue(names.containsAll(java.util.List.of("housing", "screen", "knob_left", "knob_right")), names.toString());
        assertEquals(32, readImage("aeronautics_fly_by_instruments:block/fms_module").getWidth());
    }

    /** Linked state (guide §8.5 pattern): same geometry, a second atlas differing only in a few face pixels. */
    @Test
    void linkedNavigatorOnlySwapsTheAtlas() throws Exception {
        var item = readJson("/assets/aeronautics_fly_by_instruments/models/item/fms_navigator.json");
        var override = item.getAsJsonArray("overrides").get(0).getAsJsonObject();
        assertEquals(1, override.getAsJsonObject("predicate").get("aeronautics_fly_by_instruments:linked").getAsInt());
        assertEquals("aeronautics_fly_by_instruments:item/fms_navigator_linked", override.get("model").getAsString());
        var linkedItem = readJson("/assets/aeronautics_fly_by_instruments/models/item/fms_navigator_linked.json");
        assertEquals("aeronautics_fly_by_instruments:block/fms_navigator_linked", linkedItem.get("parent").getAsString());
        assertEquals(item.get("display"), linkedItem.get("display"), "Linked state keeps the display transforms");
        // Laid flat and seen from above in the GUI: side lighting would shade the face almost black.
        assertEquals("front", item.get("gui_light").getAsString());
        assertEquals("front", linkedItem.get("gui_light").getAsString());

        SensorModelChecks.assertGuideRules("fms_navigator_linked");
        var base = SensorModelChecks.model("fms_navigator");
        var linked = SensorModelChecks.model("fms_navigator_linked");
        assertEquals(base.get("elements"), linked.get("elements"), "Linked state must not change the geometry");
        var baseTextures = base.getAsJsonObject("textures");
        var linkedTextures = linked.getAsJsonObject("textures");
        assertEquals(baseTextures.keySet(), linkedTextures.keySet());
        for (var key : baseTextures.keySet()) {
            var from = baseTextures.get(key).getAsString();
            var to = linkedTextures.get(key).getAsString();
            assertEquals(from.equals("aeronautics_fly_by_instruments:block/fms_navigator")
                    ? "aeronautics_fly_by_instruments:block/fms_navigator_linked" : from, to, key);
        }

        // Subtle: a handful of pixels, all inside the 12 x 12 face, never on the iron rim outline.
        var off = readImage("aeronautics_fly_by_instruments:block/fms_navigator");
        var on = readImage("aeronautics_fly_by_instruments:block/fms_navigator_linked");
        assertEquals(off.getWidth(), on.getWidth());
        assertEquals(off.getHeight(), on.getHeight());
        int changed = 0;
        for (int y = 0; y < off.getHeight(); y++) {
            for (int x = 0; x < off.getWidth(); x++) {
                if (off.getRGB(x, y) == on.getRGB(x, y)) continue;
                changed++;
                assertTrue(x >= 2 && x < 10 && y >= 2 && y < 10, "Changed pixel (" + x + ", " + y + ") outside the lens");
            }
        }
        assertTrue(changed > 0 && changed <= 6, "Linked state changes " + changed + " pixels");
    }

    private static java.awt.image.BufferedImage readImage(String id) throws Exception {
        String path = "/assets/aeronautics_fly_by_instruments/textures/block/"
                + id.substring("aeronautics_fly_by_instruments:block/".length()) + ".png";
        try (var stream = FmsAssetsTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, "Missing resource " + path);
            return javax.imageio.ImageIO.read(stream);
        }
    }

    @Test
    void portugueseAndEnglishCoverEveryFmsVisibleString() throws Exception {
        // Keys FmsScreen and FmsWaypointScreen use, including the ones they build at runtime.
        var required = new String[]{"title", "waypoint_count.one", "waypoint_count.other", "no_destination",
                "direct_to", "destination", "distance", "bearing", "position", "other_dimension", "waypoints",
                "empty", "elsewhere", "waypoint_tooltip", "compass", "compass_status", "compass.missing",
                "compass.linked", "compass.unlinked", "compass.other", "compass_tooltip", "link_compass",
                "relink_compass", "new_waypoint", "clear_target", "create_waypoint", "edit_waypoint", "name",
                "name_hint", "coordinate_tooltip", "current_position", "add", "back", "done", "delete",
                "confirm_delete", "invalid.name", "invalid.coordinates"};
        var messages = new String[]{"invalid_waypoint", "confirm_rebind"};
        var english = readJson("/assets/aeronautics_fly_by_instruments/lang/en_us.json");
        var portuguese = readJson("/assets/aeronautics_fly_by_instruments/lang/pt_br.json");
        var keys = new java.util.ArrayList<String>();
        for (String key : required) keys.add("gui.aeronautics_fly_by_instruments.fms." + key);
        for (String key : messages) keys.add("message.aeronautics_fly_by_instruments.fms." + key);
        for (String fullKey : keys) {
            assertTrue(english.has(fullKey), "Missing en_us key " + fullKey);
            assertTrue(portuguese.has(fullKey), "Missing pt_br key " + fullKey);
        }
    }

    private static com.google.gson.JsonObject readJson(String path) throws Exception {
        try (var stream = FmsAssetsTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, "Missing resource " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
