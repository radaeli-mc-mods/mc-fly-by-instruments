package com.radaeli.aeronauticsflybyinstruments.block;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.radaeli.aeronauticsflybyinstruments.attitude.AttitudeOrientation;
import com.radaeli.aeronauticsflybyinstruments.attitude.AttitudeReading;
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
 * Attitude Reference Unit: the exported glass-cube models, the blockstate that points the model airplane's nose
 * and tail fin at the Nose and Up faces, and the socket status light match the Blockbench source rules (guide
 * section 8.7) and the block's collision shape.
 */
class AttitudeReferenceUnitAssetsTest {
    private static final String NS = "aeronautics_fly_by_instruments";
    private static final String NAME = "attitude_reference_unit";
    private static final List<String> STATIC_MODELS = List.of("frame_ok", "glass");
    private static final List<String> AIRPLANE_MODELS = List.of("airplane", "airplane_rolled");
    private static final List<String> STATUSES = List.of("ok", "degraded", "inop");

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
        var frame = model(NAME + "_frame_ok").getAsJsonArray("elements");
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
        for (String part : List.of("frame_ok", "frame_degraded", "frame_inop", "glass", "airplane", "airplane_rolled",
                "propeller", "item")) {
            for (JsonElement entry : model(NAME + "_" + part).getAsJsonArray("elements")) {
                var cube = entry.getAsJsonObject();
                assertFalse(cube.has("rotation"), part);
                for (double v : vector(cube.getAsJsonArray("from"))) assertEquals(Math.rint(v), v, part);
                for (double v : vector(cube.getAsJsonArray("to"))) assertEquals(Math.rint(v), v, part);
            }
        }
    }

    private static JsonObject cube(JsonObject model, String name) {
        for (JsonElement entry : model.getAsJsonArray("elements")) {
            if (entry.getAsJsonObject().get("name").getAsString().equals(name)) return entry.getAsJsonObject();
        }
        return fail(name);
    }

    private static double[] centre(JsonObject cube) {
        var from = vector(cube.getAsJsonArray("from"));
        var to = vector(cube.getAsJsonArray("to"));
        return new double[]{(from[0] + to[0]) / 2, (from[1] + to[1]) / 2, (from[2] + to[2]) / 2};
    }

    @Test
    void theAirplanePointsItsNoseConeAtTheNoseFaceAndItsFinAtTheUpFaceInEveryValidFrame() throws Exception {
        var multipart = json("blockstates/" + NAME + ".json").getAsJsonArray("multipart");
        for (Direction noseFace : Direction.values()) {
            for (Direction upFace : Direction.values()) {
                if (!AttitudeOrientation.valid(noseFace, upFace)) continue;
                for (String status : STATUSES) {
                    var state = Map.of("nose", noseFace.getSerializedName(), "up", upFace.getSerializedName(),
                            "status", status);
                    String label = noseFace + "/" + upFace + "/" + status;
                    int airplanes = 0, frames = 0, glass = 0;
                    for (JsonElement entry : multipart) {
                        var part = entry.getAsJsonObject();
                        if (part.has("when") && !matches(part.getAsJsonObject("when"), state)) continue;
                        var apply = part.getAsJsonObject("apply");
                        String model = apply.get("model").getAsString().substring((NS + ":block/").length());
                        int x = apply.has("x") ? apply.get("x").getAsInt() : 0;
                        int y = apply.has("y") ? apply.get("y").getAsInt() : 0;
                        if (model.equals(NAME + "_frame_" + status)) {
                            frames++;
                        } else if (model.equals(NAME + "_glass")) {
                            glass++;
                        } else {
                            assertTrue(AIRPLANE_MODELS.contains(model.substring(NAME.length() + 1)), label + ": " + model);
                            airplanes++;
                            // Measured on the geometry: the nose cone is ahead of the fuselage, the fin above it
                            // (the part along the fuselage, where the fin sits at the tail, does not count).
                            var json = model(model);
                            var body = centre(cube(json, "fuselage"));
                            var cone = centre(cube(json, "nose_cone"));
                            var fin = centre(cube(json, "fin"));
                            double[] ahead = {cone[0] - body[0], cone[1] - body[1], cone[2] - body[2]};
                            double[] above = {fin[0] - body[0], fin[1] - body[1], 0};
                            assertEquals(0, ahead[0], label);
                            assertEquals(0, ahead[1], label);
                            assertEquals(noseFace, nearest(turn(ahead, x, y)), label + ": nose cone");
                            assertEquals(upFace, nearest(turn(above, x, y)), label + ": tail fin");
                            // The stand is on the face opposite to Up.
                            var stand = centre(cube(json, "stand_base"));
                            double[] below = {stand[0] - body[0], stand[1] - body[1], 0};
                            assertEquals(upFace.getOpposite(), nearest(turn(below, x, y)), label + ": stand");
                        }
                    }
                    assertEquals(1, airplanes, label);
                    assertEquals(1, frames, label);
                    assertEquals(1, glass, label);
                }
            }
        }
    }

    @Test
    void theRolledAirplaneIsTheSameAirplaneTurnedAboutTheNoseAxis() throws Exception {
        var plain = model(NAME + "_airplane").getAsJsonArray("elements");
        var rolled = model(NAME + "_airplane_rolled").getAsJsonArray("elements");
        assertEquals(plain.size(), rolled.size());
        assertEquals(model(NAME + "_airplane").get("textures"), model(NAME + "_airplane_rolled").get("textures"));
        for (int i = 0; i < plain.size(); i++) {
            var a = plain.get(i).getAsJsonObject();
            var b = rolled.get(i).getAsJsonObject();
            String name = a.get("name").getAsString();
            assertEquals(name, b.get("name").getAsString());
            var from = vector(a.getAsJsonArray("from"));
            var to = vector(a.getAsJsonArray("to"));
            // (x, y, z) -> (y, 16 - x, z): UP goes to EAST, the nose stays on NORTH.
            assertArrayEquals(new double[]{from[1], 16 - to[0], from[2]}, vector(b.getAsJsonArray("from")), name);
            assertArrayEquals(new double[]{to[1], 16 - from[0], to[2]}, vector(b.getAsJsonArray("to")), name);
            assertEquals(a.getAsJsonObject("faces").size(), b.getAsJsonObject("faces").size(), name);
        }
    }

    @Test
    void theAirplaneStaysInsideTheGlassAndClearOfTheSockets() throws Exception {
        for (String part : List.of("airplane", "airplane_rolled", "propeller")) {
            for (JsonElement entry : model(NAME + "_" + part).getAsJsonArray("elements")) {
                var cube = entry.getAsJsonObject();
                for (double v : vector(cube.getAsJsonArray("from"))) assertTrue(v >= 2, part);
                for (double v : vector(cube.getAsJsonArray("to"))) assertTrue(v <= 14, part);
            }
        }
    }

    @Test
    void theStatusFramesOnlySwapTheSocketTexture() throws Exception {
        var ok = model(NAME + "_frame_ok");
        for (String status : STATUSES) {
            var frame = model(NAME + "_frame_" + status);
            assertEquals(ok.get("elements"), frame.get("elements"), status);
            var textures = frame.getAsJsonObject("textures");
            assertEquals(ok.getAsJsonObject("textures").keySet(), textures.keySet(), status);
            int swapped = 0;
            for (String key : textures.keySet()) {
                String id = textures.get(key).getAsString();
                if (id.equals(ok.getAsJsonObject("textures").get(key).getAsString())) continue;
                swapped++;
                assertEquals(NS + ":block/" + NAME + "_socket_" + status, id);
            }
            assertEquals(status.equals("ok") ? 0 : 1, swapped, status);
            assertTrue(textures.entrySet().stream().anyMatch(e ->
                    e.getValue().getAsString().equals(NS + ":block/" + NAME + "_socket_" + status)), status);
        }
    }

    @Test
    void thePropellerModelExistsAndNeverAppearsInTheBlockstate() throws Exception {
        assertEquals(1, model(NAME + "_propeller").getAsJsonArray("elements").size());
        for (JsonElement entry : json("blockstates/" + NAME + ".json").getAsJsonArray("multipart")) {
            assertFalse(entry.getAsJsonObject().getAsJsonObject("apply").get("model").getAsString().contains("propeller"));
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
        assertEquals(AttitudeReferenceUnitBlock.Status.OK, state.getValue(AttitudeReferenceUnitBlock.STATUS));
        assertEquals(6 * 6 * 3, ModBlocks.ATTITUDE_REFERENCE_UNIT.get().getStateDefinition().getPossibleStates().size());
        assertNull(ModBlocks.ATTITUDE_REFERENCE_UNIT.get().getStateDefinition().getProperty("powered"));
    }

    @Test
    void theStatusLightFollowsTheReadingStatus() {
        assertEquals(AttitudeReferenceUnitBlock.Status.OK, AttitudeReferenceUnitBlock.Status.of(AttitudeReading.Status.VALID));
        assertEquals(AttitudeReferenceUnitBlock.Status.OK, AttitudeReferenceUnitBlock.Status.of(AttitudeReading.Status.GROUND));
        assertEquals(AttitudeReferenceUnitBlock.Status.DEGRADED,
                AttitudeReferenceUnitBlock.Status.of(AttitudeReading.Status.DEGRADED));
        assertEquals(AttitudeReferenceUnitBlock.Status.INOP,
                AttitudeReferenceUnitBlock.Status.of(AttitudeReading.Status.UNAVAILABLE));
    }

    @Test
    void flashingStatusTexturesAreStripsOfSquareFramesWithAnAnimationFile() throws Exception {
        for (String status : List.of("degraded", "inop")) {
            String path = "assets/" + NS + "/textures/block/" + NAME + "_socket_" + status + ".png";
            try (var input = getClass().getClassLoader().getResourceAsStream(path)) {
                assertNotNull(input, path);
                BufferedImage image = ImageIO.read(input);
                assertEquals(2 * image.getWidth(), image.getHeight(), status);
                for (int x = 0; x < image.getWidth(); x++) {
                    for (int y = 0; y < image.getHeight(); y++) assertEquals(255, image.getRGB(x, y) >>> 24, status);
                }
            }
            try (var input = getClass().getClassLoader().getResourceAsStream(path + ".mcmeta")) {
                assertNotNull(input, path + ".mcmeta");
                var meta = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
                assertTrue(meta.getAsJsonObject("animation").get("frametime").getAsInt() > 1, status);
            }
        }
    }

    @Test
    void texturesAreSquareAndOnlyTheGlassIsTranslucent() throws Exception {
        for (String name : List.of(NAME + "_parts", NAME + "_glass", NAME + "_socket_ok")) {
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
