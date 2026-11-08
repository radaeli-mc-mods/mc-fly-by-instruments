package com.radaeli.aeronauticsflybyinstruments.client.ponder;

import com.almostreliable.ponderjs.PonderItemTagEventJS;
import com.almostreliable.ponderjs.PonderRegistryEventJS;
import com.radaeli.aeronauticsflybyinstruments.attitude.AttitudeOrientation;
import com.radaeli.aeronauticsflybyinstruments.attitude.AttitudeReferenceUnitBlock;
import com.radaeli.aeronauticsflybyinstruments.block.ModBlocks;
import com.radaeli.aeronauticsflybyinstruments.client.AttitudeDisplayClient;
import com.radaeli.aeronauticsflybyinstruments.panel.AttitudeIndicatorFeed.Status;
import com.radaeli.aeronauticsflybyinstruments.panel.PanelCoordinates;
import com.radaeli.aeronauticsflybyinstruments.block.ModularTableBaseBlock;
import com.radaeli.aeronauticsflybyinstruments.block.AvionicsComputerBlock;
import com.radaeli.aeronauticsflybyinstruments.panel.ModularTableBlockEntity;
import com.radaeli.aeronauticsflybyinstruments.fms.FmsModuleData;
import com.radaeli.aeronauticsflybyinstruments.fms.FmsWaypoint;
import com.radaeli.aeronauticsflybyinstruments.wire.WireNodeBlockEntity;
import com.radaeli.aeronauticsflybyinstruments.wire.WireRoute;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.UUID;
import dev.simulated_team.simulated.content.blocks.nav_table.NavTableBlockEntity;
import com.radaeli.aeronauticsflybyinstruments.item.FmsNavigatorItem;
import com.radaeli.aeronauticsflybyinstruments.sound.ModSounds;
import net.createmod.ponder.foundation.instruction.TickingInstruction;
import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlock;

/** Storyboards registered through PonderJS. All demonstration state lives in the Ponder level. */
public final class FbiPonderScenes {
    public static final String MOD = "aeronautics_fly_by_instruments";
    private static final BlockPos COMPUTER = p(5, 2, 4), TABLE = p(5, 3, 4), SENSOR = p(2, 3, 4);
    private static final String[] SENSORS = {"radar_altimeter", "terrain_scanner", "terrain_scanner_advanced"};
    private static final String[] WIRES = {"avionics_wire", "wire_connector", "wire_support"};
    private static final String[] CABIN = {"avionics_computer", "modular_table_base", "lamp_module",
            "lamp_module_2x1", "buzzer_module", "fms_module", "fms_navigator", "attitude_reference_unit",
            "attitude_indicator_module"};

    private FbiPonderScenes() {}

    public static void register(PonderRegistryEventJS event) {
        entry(event, "altimeter", FbiPonderScenes::altimeter, "radar_altimeter");
        entry(event, "scanner", FbiPonderScenes::scanner, "terrain_scanner");
        entry(event, "advanced", FbiPonderScenes::advanced, "terrain_scanner_advanced");
        entry(event, "outputs", FbiPonderScenes::outputs, SENSORS);
        entry(event, "wire_install", FbiPonderScenes::wireInstall, WIRES);
        entry(event, "wire_inspect", FbiPonderScenes::wireInspect, WIRES);
        entry(event, "first_gpws", FbiPonderScenes::firstGpws, "avionics_computer", "modular_table_base");
        entry(event, "expand_gpws", FbiPonderScenes::expandGpws, "avionics_computer", "terrain_scanner", "terrain_scanner_advanced");
        entry(event, "panel", FbiPonderScenes::panel, "modular_table_base", "lamp_module", "lamp_module_2x1", "buzzer_module", "fms_module");
        entry(event, "alerts", FbiPonderScenes::alerts, "avionics_computer", "lamp_module", "lamp_module_2x1", "buzzer_module");
        entry(event, "fms", FbiPonderScenes::fms, "fms_module", "fms_navigator");
        // Experimental devices: the first caption of each scene says so.
        entry(event, "attitude_unit", FbiPonderScenes::attitudeUnit, "attitude_reference_unit");
        entry(event, "attitude_indicator", FbiPonderScenes::attitudeIndicator,
                "attitude_indicator_module", "attitude_reference_unit", "modular_table_base");
        entry(event, "attitude_sources", FbiPonderScenes::attitudeSources,
                "attitude_indicator_module", "attitude_reference_unit", "radar_altimeter");
    }

    public static void tags(PonderItemTagEventJS event) {
        tag(event, "sensors", "radar_altimeter", SENSORS);
        tag(event, "network", "avionics_wire", WIRES);
        tag(event, "cockpit", "avionics_computer", CABIN);
    }

    private static Ingredient items(String... names) {
        return Ingredient.of(java.util.Arrays.stream(names).map(n -> item(n)).toArray(ItemStack[]::new));
    }
    private static void tag(PonderItemTagEventJS event, String name, String icon, String[] items) {
        event.createTag(MOD + ":" + name, item(icon).getItem(), tr("tag." + name), tr("tag." + name + ".description"), items(items));
    }
    private static void entry(PonderRegistryEventJS event, String name, PonderStoryBoard board, String... items) {
        event.create(items(items)).scene(MOD + ":" + name, tr(name + ".title"), id("tutorial_base"), board);
    }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(MOD, path); }
    private static BlockPos p(int x, int y, int z) { return new BlockPos(x, y, z); }
    private static Vec3 v(BlockPos p) { return Vec3.atCenterOf(p); }
    private static ItemStack item(String name) {
        return new ItemStack(BuiltInRegistries.ITEM.get(name.contains(":") ? ResourceLocation.parse(name) : id(name)));
    }
    private static String tr(String key, Object... args) { return Component.translatable("ponder." + MOD + "." + key, args).getString(); }
    private static void begin(SceneBuilder s) {
        s.configureBasePlate(0, 0, 9);
        s.scaleSceneView(.72f);
        s.setSceneOffsetY(-.5f);
        s.showBasePlate();
        s.idle(20);
    }
    private static void put(SceneBuilder s, SceneBuildingUtil u, BlockPos pos, BlockState state) {
        s.world().setBlock(pos, state, false);
        s.world().showSection(u.select().position(pos), Direction.DOWN);
    }
    private static void put(SceneBuilder s, SceneBuildingUtil u, BlockPos pos, Block block) { put(s, u, pos, block.defaultBlockState()); }
    private static void say(SceneBuilder s, String key, BlockPos pos) { say(s, key, pos, PonderPalette.WHITE); }
    private static void say(SceneBuilder s, String key, BlockPos pos, PonderPalette color, Object... args) {
        s.overlay().showText(170).text(tr(key, args)).pointAt(v(pos)).placeNearTarget().colored(color).attachKeyFrame();
        s.idle(180);
    }
    private static void beeps(SceneBuilder s, int interval, int ticks) {
        s.addInstruction(new TickingInstruction(false, ticks) {
            @Override public void tick(PonderScene scene) {
                if (remainingTicks % interval == 0 && Minecraft.getInstance().screen
                        instanceof net.createmod.ponder.foundation.ui.PonderUI) {
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.BUZZER.get(), 1, .3f));
                }
                super.tick(scene);
            }
        });
    }
    private static void click(SceneBuilder s, BlockPos pos, String name, boolean sneak) {
        var control = s.overlay().showControls(v(pos).add(0, .6, 0), Pointing.DOWN, 60).rightClick();
        if (name != null) control.withItem(item(name));
        if (sneak) control.whileSneaking();
    }
    private static void line(SceneBuilder s, BlockPos from, BlockPos to, PonderPalette color) {
        s.overlay().showBigLine(color, v(from), v(to), 170);
    }
    private static void lamp(SceneBuilder s, SceneBuildingUtil u, BlockPos pos, boolean on) {
        put(s, u, pos, Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, on));
    }
    private static void end(SceneBuilder s) { s.idle(20); s.markAsFinished(); }

    private static void altimeter(SceneBuilder s, SceneBuildingUtil u) {
        begin(s);
        BlockPos sensor = p(4, 5, 4), ground = p(4, 1, 4);
        put(s, u, sensor, ModBlocks.RADAR_ALTIMETER.get());
        say(s, "altimeter.mount", sensor);
        s.world().setBlock(ground, Blocks.GRASS_BLOCK.defaultBlockState(), false);
        var terrain = s.world().showIndependentSection(u.select().position(ground), Direction.DOWN);
        line(s, sensor, ground, PonderPalette.INPUT);
        say(s, "altimeter.surface", ground);
        s.world().moveSection(terrain, new Vec3(0, 2.5, 0), 50);
        lamp(s, u, p(6, 1, 4), false);
        say(s, "altimeter.low", sensor, PonderPalette.OUTPUT);
        s.world().moveSection(terrain, new Vec3(0, -2.5, 0), 50);
        lamp(s, u, p(6, 1, 4), true);
        say(s, "altimeter.high", sensor, PonderPalette.OUTPUT);
        click(s, sensor, null, false);
        say(s, "altimeter.range", sensor);
        end(s);
    }
    private static BlockState scannerState(boolean advanced) {
        return (advanced ? ModBlocks.TERRAIN_SCANNER_ADVANCED.get() : ModBlocks.TERRAIN_SCANNER.get())
                .defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
    }
    private static void cone(SceneBuilder s, BlockPos pos, boolean wide) {
        // Ponder keys line outlines by their start position. Distinct outer starts keep
        // both edges visible even though they converge on the same scanner nozzle.
        // Equal lateral/forward offsets illustrate the basic scanner's full 90° cone.
        line(s, pos.offset(wide ? -4 : -1, 0, 4), pos, PonderPalette.INPUT);
        line(s, pos.offset(wide ? 4 : 1, 0, 4), pos, PonderPalette.INPUT);
    }
    private static void scanner(SceneBuilder s, SceneBuildingUtil u) {
        begin(s);
        BlockPos sensor = p(4, 2, 2), rock = p(4, 2, 7);
        put(s, u, sensor, scannerState(false));
        cone(s, sensor, true);
        say(s, "scanner.mount", sensor);
        s.world().setBlock(rock, Blocks.STONE.defaultBlockState(), false);
        var obstacle = s.world().showIndependentSection(u.select().position(rock), Direction.DOWN);
        say(s, "scanner.cone", rock);
        s.world().moveSection(obstacle, new Vec3(0, 0, -3), 60);
        lamp(s, u, p(5, 2, 2), true);
        say(s, "scanner.near", sensor, PonderPalette.OUTPUT);
        s.world().moveSection(obstacle, new Vec3(3, 0, 0), 60);
        lamp(s, u, p(5, 2, 2), false);
        say(s, "scanner.outside", p(7, 2, 4));
        click(s, sensor, null, false);
        say(s, "scanner.range", sensor);
        end(s);
    }
    private static void advanced(SceneBuilder s, SceneBuildingUtil u) {
        begin(s);
        BlockPos sensor = p(4, 2, 3);
        put(s, u, sensor, scannerState(true));
        cone(s, sensor, true);
        say(s, "advanced.sectors", sensor);
        BlockPos[] obstacles = {p(4, 2, 6), p(7, 2, 6), p(1, 2, 6)};
        BlockPos[] outputs = {p(4, 2, 4), p(5, 2, 3), p(3, 2, 3)};
        PonderPalette[] colors = {PonderPalette.RED, PonderPalette.GREEN, PonderPalette.BLUE};
        String[] captions = {"advanced.front", "advanced.left", "advanced.right"};
        for (int i = 0; i < 3; i++) {
            put(s, u, obstacles[i], Blocks.STONE);
            line(s, sensor, obstacles[i], colors[i]);
            lamp(s, u, outputs[i], true);
            say(s, captions[i], outputs[i], colors[i]);
            s.world().setBlock(obstacles[i], Blocks.AIR.defaultBlockState(), false);
            lamp(s, u, outputs[i], false);
        }
        click(s, sensor, null, false);
        cone(s, sensor, false);
        say(s, "advanced.narrow", sensor);
        say(s, "advanced.faces", sensor);
        end(s);
    }
    private static void outputs(SceneBuilder s, SceneBuildingUtil u) {
        begin(s);
        BlockPos sensor = p(2, 2, 4), receiver = p(6, 2, 4);
        put(s, u, sensor, scannerState(false));
        lamp(s, u, sensor.east(), true);
        say(s, "outputs.local", sensor);
        click(s, sensor, null, false);
        say(s, "outputs.range", sensor);
        s.overlay().showControls(v(sensor).add(0, 1, 0), Pointing.DOWN, 120).withItem(item("minecraft:diamond"));
        s.overlay().showControls(v(sensor).add(1, 1, 0), Pointing.DOWN, 120).withItem(item("minecraft:emerald"));
        say(s, "outputs.pair", sensor);
        lamp(s, u, receiver.east(), true);
        put(s, u, receiver, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("create:redstone_link"))
                .defaultBlockState().setValue(RedstoneLinkBlock.RECEIVER, true)
                // The lamp is east of the receiver: attach to its west face.
                .setValue(BlockStateProperties.FACING, Direction.WEST)
                .setValue(RedstoneLinkBlock.POWERED, true));
        line(s, sensor, receiver, PonderPalette.OUTPUT);
        lamp(s, u, sensor.east(), false);
        say(s, "outputs.receiver", receiver);
        put(s, u, sensor, scannerState(true));
        say(s, "outputs.global", sensor, PonderPalette.RED);
        end(s);
    }

    private static void route(SceneBuilder s, BlockPos... points) {
        // Deterministic ids and per-node copies make replay/reset independent of other scenes.
        UUID id = UUID.nameUUIDFromBytes(java.util.Arrays.toString(points).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var route = WireRoute.of(id, List.of(points), java.util.Collections.nCopies(points.length, 0));
        for (BlockPos point : points) s.world().modifyBlockEntity(point, WireNodeBlockEntity.class,
                node -> node.addRoute(route.rebasedTo(point.subtract(points[0]))));
    }
    private static void clearRoutes(SceneBuilder s, BlockPos... points) {
        for (var point : points) s.world().modifyBlockEntity(point, WireNodeBlockEntity.class,
                node -> List.copyOf(node.routes()).forEach(r -> node.removeRoute(r.id())));
    }
    private static void node(SceneBuilder s, SceneBuildingUtil u, BlockPos pos, boolean connector, Direction face) {
        put(s, u, pos, (connector ? ModBlocks.WIRE_CONNECTOR.get() : ModBlocks.WIRE_SUPPORT.get())
                .defaultBlockState().setValue(BlockStateProperties.FACING, face));
    }
    private static void wireInstall(SceneBuilder s, SceneBuildingUtil u) {
        begin(s);
        BlockPos a = p(2, 2, 4), b = p(6, 2, 4), support = p(4, 1, 4);
        put(s, u, p(1, 2, 4), scannerState(false));
        put(s, u, p(7, 2, 4), ModBlocks.AVIONICS_COMPUTER.get());
        node(s, u, a, true, Direction.EAST); node(s, u, b, true, Direction.WEST);
        say(s, "wire_install.connectors", a);
        node(s, u, support, false, Direction.UP);
        say(s, "wire_install.supports", support);
        click(s, a, "avionics_wire", false); s.idle(35);
        click(s, support, "avionics_wire", false); s.idle(35);
        click(s, b, "avionics_wire", false);
        route(s, a, support, b);
        say(s, "wire_install.route", support);
        BlockPos branch = p(6, 2, 6);
        put(s, u, branch.east(), ModBlocks.RADAR_ALTIMETER.get());
        node(s, u, branch, true, Direction.WEST); route(s, b, branch);
        say(s, "wire_install.branch", b);
        click(s, support.above(), "avionics_wire", true);
        say(s, "wire_install.undo", support);
        say(s, "wire_install.structure", b, PonderPalette.RED);
        end(s);
    }
    private static void wireInspect(SceneBuilder s, SceneBuildingUtil u) {
        begin(s);
        BlockPos a = p(2, 2, 4), b = p(6, 2, 4), support = p(4, 1, 4);
        put(s, u, a.west(), ModBlocks.RADAR_ALTIMETER.get());
        put(s, u, b.east(), ModBlocks.AVIONICS_COMPUTER.get());
        node(s, u, a, true, Direction.EAST); node(s, u, b, true, Direction.WEST);
        node(s, u, support, false, Direction.UP); route(s, a, support, b);
        say(s, "wire_inspect.key", support, PonderPalette.WHITE, wiringKey());
        put(s, u, p(4, 2, 5), Blocks.IRON_BLOCK);
        line(s, a, support, PonderPalette.OUTPUT); line(s, support, b, PonderPalette.OUTPUT);
        say(s, "wire_inspect.complete", support, PonderPalette.OUTPUT);
        line(s, a, b, PonderPalette.RED);
        say(s, "wire_inspect.incomplete", b, PonderPalette.RED);
        line(s, a, b, PonderPalette.WHITE);
        click(s, support, "create:wrench", true);
        clearRoutes(s, a, support, b);
        say(s, "wire_inspect.cut", support);
        route(s, a, support, b);
        s.world().destroyBlock(support); clearRoutes(s, a, b);
        say(s, "wire_inspect.remove", support);
        end(s);
    }
    private static String wiringKey() {
        return java.util.Arrays.stream(Minecraft.getInstance().options.keyMappings)
                .filter(k -> k.getName().equals("key." + MOD + ".wiring_view"))
                .map(k -> k.getTranslatedKeyMessage().getString()).findFirst().orElse("?");
    }
    private static void cabin(SceneBuilder s, SceneBuildingUtil u) {
        put(s, u, COMPUTER, ModBlocks.AVIONICS_COMPUTER.get());
        put(s, u, TABLE, ModBlocks.MODULAR_TABLE_BASE.get().defaultBlockState().setValue(ModularTableBaseBlock.FACING, Direction.UP));
        s.world().modifyBlockEntity(TABLE, ModularTableBlockEntity.class, table -> {
            table.placeLamp(0, 0); table.placeBuzzer(1, 0); table.applyBasicSetup();
        });
    }
    private static void indication(SceneBuilder s, int severity, boolean available, boolean muted) {
        s.world().modifyBlockEntityNBT(s.getScene().getSceneBuildingUtil().select().position(TABLE),
                ModularTableBlockEntity.class, tag -> {
                    CompoundTag alerts = new CompoundTag(); alerts.putInt("gpws.too_low", severity); alerts.putInt("gpws.any", severity);
                    tag.put("AlertSeverities", alerts); tag.putString("AlertNetwork", "CONNECTED");
                    tag.putBoolean("AlertAvailable", available); tag.putBoolean("AlertMuted", muted);
                });
        s.world().modifyBlock(COMPUTER, state -> state.setValue(AvionicsComputerBlock.SCREEN,
                severity >= 3 ? AvionicsComputerBlock.Screen.WARNING : severity >= 2
                        ? AvionicsComputerBlock.Screen.CAUTION : available ? AvionicsComputerBlock.Screen.NORMAL
                        : AvionicsComputerBlock.Screen.INOP), false);
    }
    private static void firstGpws(SceneBuilder s, SceneBuildingUtil u) {
        begin(s);
        put(s, u, SENSOR, ModBlocks.RADAR_ALTIMETER.get());
        put(s, u, COMPUTER, ModBlocks.AVIONICS_COMPUTER.get());
        node(s, u, SENSOR.east(), true, Direction.EAST);
        node(s, u, COMPUTER.west(), true, Direction.WEST);
        route(s, SENSOR.east(), COMPUTER.west());
        say(s, "first_gpws.network", COMPUTER);
        cabin(s, u); indication(s, 0, true, false);
        say(s, "first_gpws.dock", TABLE);
        click(s, TABLE, "lamp_module", false);
        say(s, "first_gpws.modules", TABLE);
        s.world().setBlock(p(2, 1, 4), Blocks.GRASS_BLOCK.defaultBlockState(), false);
        var floor = s.world().showIndependentSection(u.select().position(p(2, 1, 4)), Direction.DOWN);
        s.world().moveSection(floor, new Vec3(0, 1, 0), 50);
        indication(s, 3, true, false);
        beeps(s, 10, 60);
        say(s, "first_gpws.approach", TABLE, PonderPalette.RED);
        click(s, COMPUTER, null, false);
        say(s, "first_gpws.automatic", COMPUTER);
        indication(s, 3, true, false); beeps(s, 10, 60);
        s.idle(60); indication(s, 0, true, false);
        say(s, "first_gpws.test", COMPUTER);
        end(s);
    }
    private static void expandGpws(SceneBuilder s, SceneBuildingUtil u) {
        begin(s); cabin(s, u);
        put(s, u, SENSOR, scannerState(false));
        node(s, u, SENSOR.east(), true, Direction.EAST); node(s, u, COMPUTER.west(), true, Direction.WEST);
        route(s, SENSOR.east(), COMPUTER.west());
        say(s, "expand_gpws.add", SENSOR);
        cone(s, SENSOR, true);
        say(s, "expand_gpws.physical", SENSOR);
        click(s, SENSOR, null, false);
        say(s, "expand_gpws.role", SENSOR);
        s.world().setBlock(p(2, 3, 7), Blocks.STONE.defaultBlockState(), false);
        var rock = s.world().showIndependentSection(u.select().position(p(2, 3, 7)), Direction.DOWN);
        indication(s, 2, true, false);
        beeps(s, 20, 100);
        say(s, "expand_gpws.caution", TABLE, PonderPalette.OUTPUT);
        s.world().moveSection(rock, new Vec3(0, 0, -2), 60);
        indication(s, 3, true, false);
        beeps(s, 10, 100);
        say(s, "expand_gpws.warning", TABLE, PonderPalette.RED);
        say(s, "expand_gpws.optional", SENSOR);
        end(s);
    }
    private static void panel(SceneBuilder s, SceneBuildingUtil u) {
        begin(s);
        put(s, u, TABLE.below(), Blocks.IRON_BLOCK);
        put(s, u, TABLE, ModBlocks.MODULAR_TABLE_BASE.get().defaultBlockState().setValue(ModularTableBaseBlock.FACING, Direction.UP));
        for (int i = 1; i < 3; i++) {
            double offset = i / 3.0;
            s.overlay().showLine(PonderPalette.INPUT, new Vec3(5 + offset, 3.27, 4), new Vec3(5 + offset, 3.27, 5), 170);
            s.overlay().showLine(PonderPalette.INPUT, new Vec3(5, 3.27, 4 + offset), new Vec3(6, 3.27, 4 + offset), 170);
        }
        say(s, "panel.grid", TABLE);
        s.world().modifyBlockEntity(TABLE, ModularTableBlockEntity.class, t -> { t.placeLamp(0, 0); t.placeBuzzer(1, 0); });
        click(s, TABLE, "lamp_module", false);
        say(s, "panel.place", TABLE);
        s.world().modifyBlockEntity(TABLE, ModularTableBlockEntity.class, t -> t.placeLamp2x1(0, 1, false));
        s.overlay().showControls(v(TABLE), Pointing.DOWN, 80).scroll().withItem(item("lamp_module_2x1"));
        say(s, "panel.rotate", TABLE);
        s.world().modifyBlockEntity(TABLE, ModularTableBlockEntity.class, t -> { t.removeModule(0, 1); t.placeLamp2x1(0, 1, true); });
        s.overlay().showOutline(PonderPalette.RED, "occupied", u.select().position(TABLE), 170);
        say(s, "panel.overlap", TABLE, PonderPalette.RED);
        put(s, u, TABLE.east().below(), Blocks.IRON_BLOCK);
        put(s, u, TABLE.east(), ModBlocks.MODULAR_TABLE_BASE.get().defaultBlockState().setValue(ModularTableBaseBlock.FACING, Direction.UP));
        s.world().modifyBlockEntity(TABLE, ModularTableBlockEntity.class, t -> t.placeSpanning("lamp_2x1", 2, 0, 2, 1, null));
        say(s, "panel.join", TABLE.east());
        click(s, TABLE, "create:wrench", false);
        s.world().modifyBlockEntity(TABLE, ModularTableBlockEntity.class, t -> t.removeModule(0, 0));
        say(s, "panel.remove", TABLE);
        end(s);
    }
    private static void alerts(SceneBuilder s, SceneBuildingUtil u) {
        begin(s); cabin(s, u);
        indication(s, 2, true, false);
        beeps(s, 20, 100);
        say(s, "alerts.caution", TABLE, PonderPalette.OUTPUT);
        s.world().modifyBlockEntity(TABLE, ModularTableBlockEntity.class, t -> t.setIndication(0, 0, "warning"));
        indication(s, 3, true, false);
        beeps(s, 10, 100);
        say(s, "alerts.warning", TABLE, PonderPalette.RED);
        indication(s, 0, false, false);
        s.world().modifyBlockEntity(TABLE, ModularTableBlockEntity.class, t -> t.setIndication(0, 0, "inop"));
        say(s, "alerts.inop", COMPUTER);
        s.world().modifyBlockEntity(TABLE, ModularTableBlockEntity.class, t -> t.applyBasicSetup());
        indication(s, 3, true, true); click(s, TABLE, null, false);
        say(s, "alerts.mute", TABLE);
        indication(s, 2, true, false);
        beeps(s, 20, 100);
        say(s, "alerts.new", TABLE);
        click(s, TABLE, null, true);
        s.overlay().showControls(v(TABLE), Pointing.DOWN, 70).scroll().withItem(item("create:wrench"));
        say(s, "alerts.configure", TABLE);
        end(s);
    }
    private static void fms(SceneBuilder s, SceneBuildingUtil u) {
        begin(s);
        put(s, u, TABLE.below(), Blocks.IRON_BLOCK);
        put(s, u, TABLE, ModBlocks.MODULAR_TABLE_BASE.get().defaultBlockState().setValue(ModularTableBaseBlock.FACING, Direction.UP));
        s.world().modifyBlockEntity(TABLE, ModularTableBlockEntity.class, t -> t.placeFmsModule(0, 0, new FmsModuleData()));
        click(s, TABLE, "fms_module", false);
        say(s, "fms.install", TABLE);
        click(s, TABLE, null, false);
        say(s, "fms.create", TABLE);
        fmsTarget(s, true);
        line(s, TABLE, p(1, 3, 7), PonderPalette.INPUT);
        say(s, "fms.select", TABLE);
        s.overlay().showControls(v(TABLE), Pointing.DOWN, 100).withItem(item("fms_navigator"));
        say(s, "fms.bind", TABLE);
        BlockPos nav = p(2, 1, 4);
        // Aeronautics' native Navigation Table; a compass item above it illustrates insertion.
        put(s, u, nav, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("simulated:navigation_table")));
        s.world().modifyBlockEntity(nav, NavTableBlockEntity.class, t -> {
            ItemStack compass = item("fms_navigator");
            FmsNavigatorItem.bind(compass, UUID.nameUUIDFromBytes("fbi-ponder-fms".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            t.setHeldItem(compass); t.currentTarget = new Vec3(1, 1, 7); t.forceCurrentAngle(135);
        });
        line(s, nav, p(1, 1, 7), PonderPalette.OUTPUT);
        say(s, "fms.table", nav);
        line(s, nav, p(7, 1, 7), PonderPalette.OUTPUT);
        fmsTarget(s, true, true);
        s.world().modifyBlockEntity(nav, NavTableBlockEntity.class, t -> {
            t.currentTarget = new Vec3(7, 1, 7); t.forceCurrentAngle(45);
        });
        say(s, "fms.change", nav);
        fmsTarget(s, false);
        s.world().modifyBlockEntity(nav, NavTableBlockEntity.class, t -> t.currentTarget = null);
        say(s, "fms.clear", TABLE);
        say(s, "fms.keep", TABLE);
        end(s);
    }
    // --- Attitude Reference Unit and Attitude Indicator (both Experimental) ----------------------------------
    private static BlockState unit(Direction nose, Direction up) {
        return AttitudeReferenceUnitBlock.withOrientation(ModBlocks.ATTITUDE_REFERENCE_UNIT.get().defaultBlockState(),
                new AttitudeOrientation(nose, up));
    }
    private static void orient(SceneBuilder s, BlockPos pos, AttitudeOrientation next) {
        s.world().modifyBlock(pos, state -> AttitudeReferenceUnitBlock.withOrientation(state, next), false);
    }
    /**
     * The attitude scenes have no Avionics Computer: the unit and the table are both endpoints of the wire network,
     * so a single wire between their connectors is the whole installation. The table stands on the ground.
     */
    private static final BlockPos DESK = p(5, 2, 4), UNIT = p(1, 2, 4), UNIT_NODE = p(2, 2, 4), DESK_NODE = p(4, 2, 4);
    /** Mounts a connector on the unit and on the table and runs one wire between them (no computer). */
    private static void unitNetwork(SceneBuilder s, SceneBuildingUtil u) {
        node(s, u, UNIT_NODE, true, Direction.EAST);
        node(s, u, DESK_NODE, true, Direction.WEST);
        route(s, UNIT_NODE, DESK_NODE);
    }
    /** An empty table on the ground, then (optionally) the 2 x 2 indicator at the table's first cell. */
    private static void indicatorTable(SceneBuilder s, SceneBuildingUtil u, boolean indicator) {
        put(s, u, DESK, ModBlocks.MODULAR_TABLE_BASE.get().defaultBlockState().setValue(ModularTableBaseBlock.FACING, Direction.UP));
        if (indicator) installIndicator(s);
    }
    private static void installIndicator(SceneBuilder s) {
        s.world().modifyBlockEntity(DESK, ModularTableBlockEntity.class,
                table -> table.placeSpanning(PanelCoordinates.ATTITUDE_INDICATOR, 0, 0, 2, 2, null, Direction.SOUTH));
    }
    /** Speed the diorama's speed tape shows, in blocks per second (no velocity sensor in the diorama). */
    private static final double DEMO_SPEED = 24;
    /** What the diorama's indicator shows; it eases toward these values like a live feed and never goes stale. */
    private static void horizon(SceneBuilder s, Status status, double pitch, double roll, double agl, boolean tape) {
        s.addInstruction(scene -> AttitudeDisplayClient.demo(DESK, 0, 0, status, pitch, roll, agl, tape,
                DEMO_SPEED, true));
    }
    /** Moves pitch and roll linearly over the next ticks, alongside the caption that follows. */
    private static void sweep(SceneBuilder s, int ticks, double pitch0, double pitch1, double roll0, double roll1,
                              double agl, boolean tape) {
        s.addInstruction(new TickingInstruction(false, ticks) {
            @Override public void tick(PonderScene scene) {
                double t = Math.min(1, Math.max(0, 1 - (double) remainingTicks / ticks));
                AttitudeDisplayClient.demo(DESK, 0, 0, Status.OK, pitch0 + (pitch1 - pitch0) * t,
                        roll0 + (roll1 - roll0) * t, agl, tape, DEMO_SPEED, true);
                super.tick(scene);
            }
        });
    }
    private static void attitudeUnit(SceneBuilder s, SceneBuildingUtil u) {
        begin(s);
        BlockPos unit = p(2, 2, 4);
        put(s, u, unit, unit(Direction.NORTH, Direction.UP));
        say(s, "attitude_unit.mount", unit);
        line(s, unit, unit.relative(Direction.NORTH, 3), PonderPalette.OUTPUT);
        say(s, "attitude_unit.nose", unit, PonderPalette.OUTPUT);
        line(s, unit.above(), unit.above(4), PonderPalette.INPUT);
        say(s, "attitude_unit.up", unit, PonderPalette.INPUT);
        click(s, unit, "create:wrench", false);
        orient(s, unit, new AttitudeOrientation(Direction.NORTH, Direction.UP).withNose(Direction.WEST));
        line(s, unit, unit.relative(Direction.WEST, 3), PonderPalette.OUTPUT);
        say(s, "attitude_unit.wrench", unit);
        click(s, unit, null, false);
        orient(s, unit, new AttitudeOrientation(Direction.WEST, Direction.UP).withUp(Direction.NORTH));
        line(s, unit, unit.relative(Direction.NORTH, 3), PonderPalette.INPUT);
        say(s, "attitude_unit.screen", unit);
        click(s, unit, null, false);
        say(s, "attitude_unit.level", unit);
        say(s, "attitude_unit.damping", unit);
        // No Avionics Computer: a wire from the unit's connector to the table's is the whole installation.
        BlockPos near = unit.east();
        indicatorTable(s, u, true);
        horizon(s, Status.OK, 6, -15, Double.NaN, false);
        node(s, u, near, true, Direction.EAST);
        node(s, u, DESK_NODE, true, Direction.WEST);
        route(s, near, DESK_NODE);
        say(s, "attitude_unit.network", DESK);
        say(s, "attitude_unit.ground", unit, PonderPalette.RED);
        end(s);
    }
    private static void attitudeIndicator(SceneBuilder s, SceneBuildingUtil u) {
        begin(s);
        put(s, u, UNIT, unit(Direction.NORTH, Direction.UP));
        indicatorTable(s, u, false);
        unitNetwork(s, u);
        say(s, "attitude_indicator.network", DESK);
        horizon(s, Status.NO_DATA, 0, 0, Double.NaN, false);
        click(s, DESK, "attitude_indicator_module", false);
        installIndicator(s);
        say(s, "attitude_indicator.install", DESK);
        horizon(s, Status.OK, 0, 0, Double.NaN, false);
        say(s, "attitude_indicator.horizon", DESK);
        sweep(s, 150, 0, 14, 0, 0, Double.NaN, false);
        say(s, "attitude_indicator.pitch", DESK, PonderPalette.OUTPUT);
        sweep(s, 150, 14, -10, 0, 0, Double.NaN, false);
        say(s, "attitude_indicator.pitch_down", DESK, PonderPalette.OUTPUT);
        sweep(s, 150, -10, 4, 0, 28, Double.NaN, false);
        say(s, "attitude_indicator.bank", DESK, PonderPalette.OUTPUT);
        click(s, DESK, null, false);
        say(s, "attitude_indicator.screen", DESK);
        say(s, "attitude_indicator.mounting", DESK);
        end(s);
    }
    private static void attitudeSources(SceneBuilder s, SceneBuildingUtil u) {
        begin(s);
        put(s, u, UNIT, unit(Direction.NORTH, Direction.UP));
        indicatorTable(s, u, true);
        unitNetwork(s, u);
        horizon(s, Status.OK, 4, -12, Double.NaN, false);
        say(s, "attitude_sources.unit", UNIT);
        BlockPos altimeter = p(3, 2, 6), altimeterNode = p(3, 2, 5);
        put(s, u, altimeter, ModBlocks.RADAR_ALTIMETER.get());
        node(s, u, altimeterNode, true, Direction.NORTH);
        route(s, UNIT_NODE, altimeterNode);
        horizon(s, Status.OK, 4, -12, 96, true);
        say(s, "attitude_sources.altimeter", altimeter, PonderPalette.OUTPUT);
        horizon(s, Status.OK, 4, -12, 31, true);
        say(s, "attitude_sources.height", altimeter, PonderPalette.OUTPUT);
        horizon(s, Status.OK, 4, -12, Double.NaN, true);
        say(s, "attitude_sources.range", altimeter);
        click(s, DESK, null, false);
        horizon(s, Status.OK, 4, -12, 96, false);
        say(s, "attitude_sources.tape", DESK);
        horizon(s, Status.DEGRADED, 88, -12, 96, false);
        say(s, "attitude_sources.degraded", DESK, PonderPalette.OUTPUT);
        s.world().destroyBlock(UNIT);
        horizon(s, Status.NO_DATA, 0, 0, Double.NaN, false);
        say(s, "attitude_sources.missing", DESK, PonderPalette.RED);
        say(s, "attitude_sources.vessel", DESK, PonderPalette.RED);
        end(s);
    }

    private static void fmsTarget(SceneBuilder s, boolean selected) {
        fmsTarget(s, selected, false);
    }
    private static void fmsTarget(SceneBuilder s, boolean selected, boolean second) {
        s.world().modifyBlockEntity(TABLE, ModularTableBlockEntity.class, t -> {
            t.removeModule(0, 0);
            UUID waypoint = UUID.nameUUIDFromBytes("fbi-ponder-waypoint".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            UUID other = UUID.nameUUIDFromBytes("fbi-ponder-second".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var data = new FmsModuleData(UUID.nameUUIDFromBytes("fbi-ponder-fms".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                    List.of(new FmsWaypoint(waypoint, tr("fms.destination"), "minecraft:overworld", 1, 3, 7),
                            new FmsWaypoint(other, tr("fms.second"), "minecraft:overworld", 7, 3, 7)), selected ? (second ? other : waypoint) : null);
            t.placeFmsModule(0, 0, data);
        });
    }
}
