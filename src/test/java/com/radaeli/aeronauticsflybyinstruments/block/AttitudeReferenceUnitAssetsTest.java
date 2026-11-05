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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Attitude Reference Unit: the exported model and blockstate match the Blockbench source rules
 * (guide v2.1 section 8.7) and the block's collision shape.
 */
class AttitudeReferenceUnitAssetsTest {
    private static final String NS = "aeronautics_fly_by_instruments";
    private static final String NAME = "attitude_reference_unit";
    private static final List<String> SIDES = List.of("north", "east", "south", "west", "up", "down");

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

    @Test
    void bodyFacesAndRimCoverTheVolumeOfTheCollisionShape() throws Exception {
        var northNose = model(NAME + "_north_nose");
        var rim = model(NAME + "_rim");
        var volume = Shapes.empty();
        for (String side : SIDES) {
            volume = Shapes.or(volume, volume(model(NAME + "_" + side + "_plain")));
        }
        volume = Shapes.or(volume, volume(rim));
        var state = ModBlocks.ATTITUDE_REFERENCE_UNIT.get().defaultBlockState();
        var outline = state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        var collision = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty());
        assertFalse(Shapes.joinIsNotEmpty(volume, outline, BooleanOp.NOT_SAME));
        assertFalse(Shapes.joinIsNotEmpty(volume, collision, BooleanOp.NOT_SAME));
        assertFalse(state.canOcclude());
        assertFalse(Shapes.block().equals(outline));
        assertNotNull(northNose);
    }

    @Test
    void everyBodyPartDrawsExactlyItsOwnFaceInTheRightRegion() throws Exception {
        for (String side : SIDES) {
            for (String role : List.of("plain", "nose", "up")) {
                for (String suffix : List.of("", "_powered")) {
                    var part = model(NAME + "_" + side + "_" + role + suffix);
                    var elements = part.getAsJsonArray("elements");
                    assertEquals(1, elements.size(), side + " " + role);
                    var cube = elements.get(0).getAsJsonObject();
                    assertEquals("face_" + side, cube.get("name").getAsString());
                    var faces = cube.getAsJsonObject("faces");
                    assertEquals(1, faces.size(), side + " " + role + ": only the side's own face is drawn");
                    assertTrue(faces.has(side), side);
                    var uv = faces.getAsJsonObject(side).getAsJsonArray("uv");
                    double[] expected = switch (role) {
                        case "nose" -> new double[]{8, 0, 15, 7};
                        case "up" -> new double[]{0, 8, 7, 15};
                        default -> new double[]{0, 0, 7, 7};
                    };
                    for (int i = 0; i < 4; i++) assertEquals(expected[i], uv.get(i).getAsDouble(), 1.0E-9, side + " " + role);
                    assertEquals("#0", faces.getAsJsonObject(side).get("texture").getAsString());
                    var from = vector(cube.getAsJsonArray("from"));
                    var to = vector(cube.getAsJsonArray("to"));
                    assertArrayEquals(new double[]{1, 1, 1}, from);
                    assertArrayEquals(new double[]{15, 15, 15}, to);
                }
            }
        }
    }

    @Test
    void theRimHasEightBarsAndNoSideFaceOverlapsTheBody() throws Exception {
        var rim = model(NAME + "_rim");
        assertEquals(8, rim.getAsJsonArray("elements").size());
        for (JsonElement entry : rim.getAsJsonArray("elements")) {
            var cube = entry.getAsJsonObject();
            for (var face : cube.getAsJsonObject("faces").entrySet()) {
                assertEquals("#0", face.getValue().getAsJsonObject().get("texture").getAsString());
            }
            for (double v : vector(cube.getAsJsonArray("from"))) assertEquals(Math.rint(v), v);
            for (double v : vector(cube.getAsJsonArray("to"))) assertEquals(Math.rint(v), v);
        }
    }

    @Test
    void everyValidFrameAndPowerStateResolvesToOneModelPerFace() throws Exception {
        var multipart = json("blockstates/" + NAME + ".json").getAsJsonArray("multipart");
        for (Direction nose : Direction.values()) {
            for (Direction up : Direction.values()) {
                if (!AttitudeOrientation.valid(nose, up)) continue;
                for (boolean powered : new boolean[]{false, true}) {
                    var state = Map.of("nose", nose.getSerializedName(), "up", up.getSerializedName(),
                            "powered", Boolean.toString(powered));
                    List<String> applied = new ArrayList<>();
                    for (JsonElement entry : multipart) {
                        var part = entry.getAsJsonObject();
                        if (part.has("when") && !matches(part.getAsJsonObject("when"), state)) continue;
                        applied.add(part.getAsJsonObject("apply").get("model").getAsString());
                    }
                    assertEquals(1 + SIDES.size(), applied.size(), nose + "/" + up + "/" + powered);
                    for (String side : SIDES) {
                        String role = nose.getSerializedName().equals(side) ? "nose"
                                : up.getSerializedName().equals(side) ? "up" : "plain";
                        String suffix = powered ? "_powered" : "";
                        String expected = NS + ":block/" + NAME + "_" + side + "_" + role + suffix;
                        assertTrue(applied.contains(expected), nose + "/" + up + "/" + powered + ": " + expected);
                    }
                }
            }
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
    void blockstateKeepsNoseUpAndPoweredAndDefaultsToTheStandardFrame() {
        BlockState state = ModBlocks.ATTITUDE_REFERENCE_UNIT.get().defaultBlockState();
        assertEquals(Direction.NORTH, state.getValue(AttitudeReferenceUnitBlock.NOSE));
        assertEquals(Direction.UP, state.getValue(AttitudeReferenceUnitBlock.UP));
        assertFalse(state.getValue(AttitudeReferenceUnitBlock.POWERED));
        assertEquals(6 * 6 * 2, ModBlocks.ATTITUDE_REFERENCE_UNIT.get().getStateDefinition().getPossibleStates().size());
    }

    @Test
    void texturesResolveInTheirOwnNamespaceAreOpaqueAndSquare() throws Exception {
        for (String name : List.of(NAME, NAME + "_powered")) {
            try (var input = getClass().getClassLoader()
                    .getResourceAsStream("assets/" + NS + "/textures/block/" + name + ".png")) {
                assertNotNull(input, name);
                BufferedImage image = ImageIO.read(input);
                assertEquals(32, image.getWidth(), name);
                assertEquals(32, image.getHeight(), name);
                for (int x = 0; x < image.getWidth(); x++) {
                    for (int y = 0; y < image.getHeight(); y++) {
                        assertEquals(255, image.getRGB(x, y) >>> 24, name + " opaque at " + x + "," + y);
                    }
                }
            }
        }
    }
}
