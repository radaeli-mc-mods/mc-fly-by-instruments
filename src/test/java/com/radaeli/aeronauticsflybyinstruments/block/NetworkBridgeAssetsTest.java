package com.radaeli.aeronauticsflybyinstruments.block;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import static com.radaeli.aeronauticsflybyinstruments.sensor.SensorModelChecks.*;
import static org.junit.jupiter.api.Assertions.*;

/** Network Bridge v2.1: plain metal box, a grille plate on top and grille panels on two opposite sides. */
class NetworkBridgeAssetsTest {
    private static final String ROOT = "/assets/aeronautics_fly_by_instruments/";
    private static final String NAME = "network_bridge";
    private static final String NS = "aeronautics_fly_by_instruments";

    @Test void followsTheGuideRulesWithOneTexture() throws Exception {
        assertGuideRules(NAME);
        var model = model(NAME);
        var names = new HashSet<String>();
        for (var value : model.getAsJsonArray("elements")) names.add(value.getAsJsonObject().get("name").getAsString());
        assertEquals(Set.of("box", "plate"), names);
        assertEquals(Set.of(NS + ":block/network_bridge"), textureIds(model));
    }

    @Test void thePlateIsCentredOnTopAndFlushWithTheBlock() throws Exception {
        var plate = element(model(NAME), "plate");
        var from = vector(plate.getAsJsonArray("from"));
        var to = vector(plate.getAsJsonArray("to"));
        assertEquals(16, to[1], "The plate ends flush with the top, where a Wire Connector seats");
        assertEquals(8, (from[0] + to[0]) / 2);
        assertEquals(8, (from[2] + to[2]) / 2);
        double share = (to[0] - from[0]) * (to[2] - from[2]) / 256;
        assertTrue(share > 0.5 && share < 0.65, "The plate covers about 60% of the top: " + share);
    }

    @Test void everyFaceIsWithinAWireConnectorStemOfTheBlockEdge() throws Exception {
        var box = element(model(NAME), "box");
        var from = vector(box.getAsJsonArray("from"));
        var to = vector(box.getAsJsonArray("to"));
        for (int axis = 0; axis < 3; axis++) {
            assertTrue(from[axis] <= 2 && to[axis] >= 14, "A connector's 2 px stem reaches the box on axis " + axis);
        }
    }

    @Test void outlineAndCollisionFollowTheModel() throws Exception {
        var volume = Shapes.empty();
        for (var entry : model(NAME).getAsJsonArray("elements")) {
            var from = vector(entry.getAsJsonObject().getAsJsonArray("from"));
            var to = vector(entry.getAsJsonObject().getAsJsonArray("to"));
            volume = Shapes.or(volume, Shapes.box(from[0] / 16, from[1] / 16, from[2] / 16, to[0] / 16, to[1] / 16, to[2] / 16));
        }
        for (var axis : new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Z}) {
            // The box is square, so the y 90 turn of axis=x keeps the same volume.
            var state = ModBlocks.NETWORK_BRIDGE.get().defaultBlockState().setValue(NetworkBridgeBlock.AXIS, axis);
            assertFalse(Shapes.joinIsNotEmpty(volume, state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO), BooleanOp.NOT_SAME));
            assertFalse(Shapes.joinIsNotEmpty(volume, state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO,
                    CollisionContext.empty()), BooleanOp.NOT_SAME));
        }
    }

    @Test void theDetailedSidesFaceAlongTheBlockstateAxis() throws Exception {
        var faces = element(model(NAME), "box").getAsJsonObject("faces");
        // North and south carry the panel; east and west are the plain side.
        assertEquals(faces.getAsJsonObject("north").get("uv"), faces.getAsJsonObject("south").get("uv"));
        assertEquals(faces.getAsJsonObject("east").get("uv"), faces.getAsJsonObject("west").get("uv"));
        assertNotEquals(faces.getAsJsonObject("north").get("uv"), faces.getAsJsonObject("east").get("uv"));
        assertEquals(Direction.Axis.Z, ModBlocks.NETWORK_BRIDGE.get().defaultBlockState().getValue(NetworkBridgeBlock.AXIS));
    }

    @Test void itemAndBlockStatesReferenceTheModel() throws Exception {
        assertEquals(NS + ":block/network_bridge", json("models/item/network_bridge.json").get("parent").getAsString());
        var variants = json("blockstates/network_bridge.json").getAsJsonObject("variants");
        assertEquals(2, variants.size());
        var z = variants.getAsJsonObject("axis=z");
        var x = variants.getAsJsonObject("axis=x");
        assertEquals(NS + ":block/network_bridge", z.get("model").getAsString());
        assertEquals(NS + ":block/network_bridge", x.get("model").getAsString());
        assertFalse(z.has("y"));
        assertEquals(90, x.get("y").getAsInt(), "The detailed sides turn to east and west");
    }

    private static JsonObject json(String path) throws Exception {
        try (var stream = NetworkBridgeAssetsTest.class.getResourceAsStream(ROOT + path)) {
            assertNotNull(stream, path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
