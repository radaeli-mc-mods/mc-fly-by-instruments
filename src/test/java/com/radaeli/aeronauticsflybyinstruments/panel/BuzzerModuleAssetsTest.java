package com.radaeli.aeronauticsflybyinstruments.panel;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Buzzer module 1x1 v2.1: square housing with a stepped grille cap (guide §9.2 and §9.4). */
class BuzzerModuleAssetsTest {
    private static final String ROOT = "/assets/aeronautics_fly_by_instruments/";
    private static final Map<String, int[]> FACE_AXES = Map.of(
            "north", new int[]{0, 1}, "south", new int[]{0, 1}, "east", new int[]{2, 1},
            "west", new int[]{2, 1}, "up", new int[]{0, 2}, "down", new int[]{0, 2});

    private JsonObject model() throws Exception {
        try (var input = getClass().getResourceAsStream(ROOT + "models/block/buzzer_module_1x1.json")) {
            assertNotNull(input);
            return JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test
    void geometryIsAnUnrotatedFourByFourHousingOnIntegerCoordinates() throws Exception {
        var elements = model().getAsJsonArray("elements");
        assertTrue(elements.size() >= 2 && elements.size() <= 5, "1x1 modules use 2-5 cubes");
        int minX = 16, maxX = 0, minZ = 16, maxZ = 0, minY = 16, maxY = 0;
        for (var element : elements) {
            var cube = element.getAsJsonObject();
            assertFalse(cube.has("rotation"), "No rotated facets in v2.1");
            var from = cube.getAsJsonArray("from");
            var to = cube.getAsJsonArray("to");
            for (int axis = 0; axis < 3; axis++) {
                for (var value : new double[]{from.get(axis).getAsDouble(), to.get(axis).getAsDouble()}) {
                    assertEquals(Math.rint(value), value, 1e-9, "Integer coordinates");
                }
                // Even widths keep every piece centred on X/Z 8.
                if (axis != 1) assertEquals(16, from.get(axis).getAsInt() + to.get(axis).getAsInt());
            }
            minX = Math.min(minX, from.get(0).getAsInt()); maxX = Math.max(maxX, to.get(0).getAsInt());
            minY = Math.min(minY, from.get(1).getAsInt()); maxY = Math.max(maxY, to.get(1).getAsInt());
            minZ = Math.min(minZ, from.get(2).getAsInt()); maxZ = Math.max(maxZ, to.get(2).getAsInt());
            for (var face : cube.getAsJsonObject("faces").entrySet()) {
                var f = face.getValue().getAsJsonObject();
                assertFalse(f.has("cullface"), "Module faces must not cull against the table");
                var uv = f.getAsJsonArray("uv");
                var axes = FACE_AXES.get(face.getKey());
                // 16 x 16 atlas: model UV units are texels.
                assertEquals(to.get(axes[0]).getAsDouble() - from.get(axes[0]).getAsDouble(),
                        Math.abs(uv.get(2).getAsDouble() - uv.get(0).getAsDouble()), 1e-9, "1 texel per unit");
                assertEquals(to.get(axes[1]).getAsDouble() - from.get(axes[1]).getAsDouble(),
                        Math.abs(uv.get(3).getAsDouble() - uv.get(1).getAsDouble()), 1e-9, "1 texel per unit");
            }
        }
        assertArrayEquals(new int[]{6, 10, 6, 10}, new int[]{minX, maxX, minZ, maxZ}, "4 x 4 housing, as the 1x1 lamp");
        assertEquals(0, minY, "Mounting face must start at Y=0");
        assertEquals(3, maxY, "Top of the grille cap (PanelSpatial emitter height)");
    }

    @Test
    void atlasIsOpaqueSixteenSquareWithAPaintedGrille() throws Exception {
        assertEquals("aeronautics_fly_by_instruments:block/buzzer_module",
                model().getAsJsonObject("textures").get("0").getAsString());
        try (var input = getClass().getResourceAsStream(ROOT + "textures/block/buzzer_module.png")) {
            assertNotNull(input);
            var atlas = ImageIO.read(input);
            assertEquals(16, atlas.getWidth());
            assertEquals(16, atlas.getHeight());
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) assertEquals(255, atlas.getRGB(x, y) >>> 24, "Opaque at " + x + "," + y);
            }
            // Cap top at (2,4)-(4,6): alternating holes, so neighbouring pixels differ.
            assertNotEquals(atlas.getRGB(2, 4), atlas.getRGB(3, 4), "Grille must alternate");
            assertNotEquals(atlas.getRGB(2, 4), atlas.getRGB(2, 5), "Grille must alternate");
        }
    }
}
