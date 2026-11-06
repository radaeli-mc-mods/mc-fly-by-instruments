package com.radaeli.aeronauticsflybyinstruments.block;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.radaeli.aeronauticsflybyinstruments.attitude.AttitudeOrientation;
import com.radaeli.aeronauticsflybyinstruments.attitude.AttitudeReferenceUnitBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Attitude Reference Unit: the exported glass-cube models and the blockstate that points the Nose and Up
 * reference arms match the Blockbench source rules (guide section 8.7) and the block's collision shape.
 */
class AttitudeReferenceUnitAssetsTest {
    private static final String NS = "aeronautics_fly_by_instruments";
    private static final String NAME = "attitude_reference_unit";
    private static final List<String> STATIC_MODELS = List.of("frame", "glass");
    private static final List<String> GIMBAL_MODELS = List.of("gimbal_ring", "gimbal_rotor");

    private static JsonObject json(String relativePath) throws Exception {
        try (var input = AttitudeReferenceUnitAssetsTest.class.getResourceAsStream("/assets/" + NS + "/" + relativePath)) {
            assertNotNull(input, relativePath);
            return JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static JsonObject model(String name) throws Exception {
        return json("models/block/" + name + ".json");
    }

    private static double[] vector(JsonArray array) {
        return new double[]{array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble()};
    }

    /** Every element of a model as a box in block units. */
    private static VoxelShape volume(JsonObject model) {
        VoxelShape shape = Shapes.empty();
        for (JsonElement entry : model.getAsJsonArray("elements")) {
            var cube = entry.getAsJsonObject();
            var from = vector(cube.getAsJsonArray("from"));
            var to = vector(cube.getAsJsonArray("to"));
            shape = Shapes.or(shape, Block.box(from[0], from[1], from[2], to[0], to[1], to[2]));
        }
        return shape;
    }

    /** Whether a multipart "when" entry matches a state; values may list alternatives with "|". */
    private static boolean matches(JsonObject when, Map<String, String> state) {
        for (String property : when.keySet()) {
            String actual = state.get(property);
            boolean any = false;
            for (String alternative : when.get(property).getAsString().split("\\|")) {
                any |= alternative.equals(actual);
            }
            if (!any) return false;
        }
        return true;
    }

    /** Minecraft blockstate rotation: x first, then y; x=90 turns NORTH into DOWN and UP into NORTH. */
    private static double[] turn(double[] v, int x, int y) {
        double rx = Math.toRadians(x), ry = Math.toRadians(y);
        double[] a = {v[0], v[1] * Math.cos(rx) + v[2] * Math.sin(rx), -v[1] * Math.sin(rx) + v[2] * Math.cos(rx)};
        return new double[]{a[0] * Math.cos(ry) - a[2] * Math.sin(ry), a[1], a[0] * Math.sin(ry) + a[2] * Math.cos(ry)};
    }

    private static Direction nearest(double[] v) {
        return Direction.getNearest(v[0], v[1], v[2]);
    }

    @Test
    void glassAndFrameCoverTheVolumeOfTheCollisionShape() throws Exception {
        var volume = Shapes.empty();
        for (String part : STATIC_MODELS) volume = Shapes.or(volume, volume(model(NAME + "_" + part)));
        var state = ModBlocks.ATTITUDE_REFERENCE_UNIT.get().defaultBlockState();
        var outline = state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        var collision = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty());
        assertFalse(Shapes.joinIsNotEmpty(volume, outline, BooleanOp.NOT_SAME));
        assertFalse(Shapes.joinIsNotEmpty(volume, collision, BooleanOp.NOT_SAME));
        assertFalse(state.canOcclude());
        assertFalse(Shapes.block().equals(outline));
    }

    @Test
    void theFrameHasFourSlimPostsAndSixSocketsAndTheGlassIsOneTranslucentCube() throws Exception {
        var frame = model(NAME + "_frame").getAsJsonArray("elements");
        int posts = 0, sockets = 0;
        for (JsonElement entry : frame) {
            var cube = entry.getAsJsonObject();
            String name = cube.get("name").getAsString();
            var from = vector(cube.getAsJsonArray("from"));
            var to = vector(cube.getAsJsonArray("to"));
            if (name.startsWith("post_")) {
                posts++;
                assertArrayEquals(new double[]{2, 16, 2}, new double[]{to[0] - from[0], to[1] - from[1], to[2] - from[2]}, name);
            } else if (name.startsWith("socket_")) {
                sockets++;
                // 6x6x2: the connector stem (4x4, 2 deep) stays buried in it instead of crossing the glass.
                double[] size = {to[0] - from[0], to[1] - from[1], to[2] - from[2]};
                java.util.Arrays.sort(size);
                assertArrayEquals(new double[]{2, 6, 6}, size, name);
            }
        }
        assertEquals(4, posts);
        assertEquals(6, sockets);
        var glass = model(NAME + "_glass");
        assertEquals("minecraft:translucent", glass.get("render_type").getAsString());
        assertEquals(1, glass.getAsJsonArray("elements").size());
        var cube = glass.getAsJsonArray("elements").get(0).getAsJsonObject();
        assertArrayEquals(new double[]{1, 1, 1}, vector(cube.getAsJsonArray("from")));
        assertArrayEquals(new double[]{15, 15, 15}, vector(cube.getAsJsonArray("to")));
    }

    @Test
    void theCableSocketIsOnTheFacePlaneOfEveryFace() {
        var block = (AttitudeReferenceUnitBlock) ModBlocks.ATTITUDE_REFERENCE_UNIT.get();
        var state = block.defaultBlockState();
        for (Direction face : Direction.values()) {
            var socket = block.cableSocket(state, face);
            assertEquals(.5 + .5 * face.getStepX(), socket.x, 1e-9, face.toString());
            assertEquals(.5 + .5 * face.getStepY(), socket.y, 1e-9, face.toString());
            assertEquals(.5 + .5 * face.getStepZ(), socket.z, 1e-9, face.toString());
        }
    }

    @Test
    void everyModelHasIntegerCoordinatesAndNoRotation() throws Exception {
        for (String part : List.of("frame", "glass", "arm_nose", "arm_up", "gimbal_ring", "gimbal_rotor", "item")) {
            for (JsonElement entry : model(NAME + "_" + part).getAsJsonArray("elements")) {
                var cube = entry.getAsJsonObject();
                assertFalse(cube.has("rotation"), part);
                for (double v : vector(cube.getAsJsonArray("from"))) assertEquals(Math.rint(v), v, part);
                for (double v : vector(cube.getAsJsonArray("to"))) assertEquals(Math.rint(v), v, part);
            }
        }
    }

    @Test
    void theNoseAndUpArmsHaveDistinctEndsAndPointTheRightWayInEveryValidFrame() throws Exception {
        // Source arms: the Nose arm's copper bulb is at the NORTH end (z low), the Up arm's brass bulb at the top.
        var nose = model(NAME + "_arm_nose").getAsJsonArray("elements");
        var up = model(NAME + "_arm_up").getAsJsonArray("elements");
        assertEquals("nose_bulb", nose.get(0).getAsJsonObject().get("name").getAsString());
        assertEquals(1.0, vector(nose.get(0).getAsJsonObject().getAsJsonArray("from"))[2]);
        assertEquals("up_bulb", up.get(0).getAsJsonObject().get("name").getAsString());
        assertEquals(15.0, vector(up.get(0).getAsJsonObject().getAsJsonArray("to"))[1]);

        var multipart = json("blockstates/" + NAME + ".json").getAsJsonArray("multipart");
        for (Direction noseFace : Direction.values()) {
            for (Direction upFace : Direction.values()) {
                if (!AttitudeOrientation.valid(noseFace, upFace)) continue;
                var state = Map.of("nose", noseFace.getSerializedName(), "up", upFace.getSerializedName());
                Direction noseTip = null, upTip = null;
                int arms = 0, always = 0;
                for (JsonElement entry : multipart) {
                    var part = entry.getAsJsonObject();
                    if (part.has("when") && !matches(part.getAsJsonObject("when"), state)) continue;
                    var apply = part.getAsJsonObject("apply");
                    String model = apply.get("model").getAsString();
                    int x = apply.has("x") ? apply.get("x").getAsInt() : 0;
                    int y = apply.has("y") ? apply.get("y").getAsInt() : 0;
                    if (model.endsWith("_arm_nose")) {
                        arms++;
                        noseTip = nearest(turn(new double[]{0, 0, -1}, x, y));
                    } else if (model.endsWith("_arm_up")) {
                        arms++;
                        upTip = nearest(turn(new double[]{0, 1, 0}, x, y));
                    } else {
                        always++;
                    }
                }
                String label = noseFace + "/" + upFace;
                assertEquals(2, arms, label);
                assertEquals(STATIC_MODELS.size(), always, label);
                assertEquals(noseFace, noseTip, label + ": Nose arm tip");
                assertEquals(upFace, upTip, label + ": Up arm tip");
            }
        }
    }

    @Test
    void theGimbalModelsExistAndNeverAppearInTheBlockstate() throws Exception {
        for (String part : GIMBAL_MODELS) assertNotNull(model(NAME + "_" + part), part);
        for (JsonElement entry : json("blockstates/" + NAME + ".json").getAsJsonArray("multipart")) {
            assertFalse(entry.getAsJsonObject().getAsJsonObject("apply").get("model").getAsString().contains("gimbal"));
        }
    }

    @Test
    void noseAndUpFacesAreAlwaysPerpendicularInEveryValidFrame() {
        int frames = 0;
        for (Direction nose : Direction.values()) {
            for (Direction up : Direction.values()) {
                if (!AttitudeOrientation.valid(nose, up)) continue;
                frames++;
                assertNotEquals(nose.getAxis(), up.getAxis());
            }
        }
        assertEquals(24, frames);
    }

    @Test
    void blockstateKeepsNoseAndUpAndDefaultsToTheStandardFrame() {
        BlockState state = ModBlocks.ATTITUDE_REFERENCE_UNIT.get().defaultBlockState();
        assertEquals(Direction.NORTH, state.getValue(AttitudeReferenceUnitBlock.NOSE));
        assertEquals(Direction.UP, state.getValue(AttitudeReferenceUnitBlock.UP));
        assertEquals(6 * 6, ModBlocks.ATTITUDE_REFERENCE_UNIT.get().getStateDefinition().getPossibleStates().size());
        assertNull(ModBlocks.ATTITUDE_REFERENCE_UNIT.get().getStateDefinition().getProperty("powered"));
    }

    @Test
    void texturesAreSquareAndOnlyTheGlassIsTranslucent() throws Exception {
        for (String name : List.of(NAME + "_parts", NAME + "_glass")) {
            try (var input = getClass().getClassLoader()
                    .getResourceAsStream("assets/" + NS + "/textures/block/" + name + ".png")) {
                assertNotNull(input, name);
                BufferedImage image = ImageIO.read(input);
                assertEquals(image.getWidth(), image.getHeight(), name);
                boolean glass = name.endsWith("_glass");
                for (int x = 0; x < image.getWidth(); x++) {
                    for (int y = 0; y < image.getHeight(); y++) {
                        int alpha = image.getRGB(x, y) >>> 24;
                        if (glass) assertTrue(alpha > 0 && alpha < 255, name + " translucent at " + x + "," + y);
                        else assertEquals(255, alpha, name + " opaque at " + x + "," + y);
                    }
                }
            }
        }
    }
}
