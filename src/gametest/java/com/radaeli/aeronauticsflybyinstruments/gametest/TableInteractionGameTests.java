package com.radaeli.aeronauticsflybyinstruments.gametest;

import com.mojang.authlib.GameProfile;
import com.radaeli.aeronauticsflybyinstruments.block.ModBlocks;
import com.radaeli.aeronauticsflybyinstruments.block.ModularTableBaseBlock;
import com.radaeli.aeronauticsflybyinstruments.panel.*;
import com.radaeli.aeronauticsflybyinstruments.network.*;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.UUID;

@GameTestHolder("aeronautics_fly_by_instruments")
@PrefixGameTestTemplate(false)
public final class TableInteractionGameTests {
    private static final java.util.Map<UUID, java.util.List<net.minecraft.network.protocol.common.custom.CustomPacketPayload>> sent = new java.util.HashMap<>();
    private record Fixture(GameTestHelper helper, BlockPos pos, ModularTableBlockEntity table, ServerPlayer player) {
        BlockHitResult hit(int col, int row) {
            Direction facing = table.getBlockState().getValue(ModularTableBaseBlock.FACING);
            Vec3 p = PanelCoordinates.fromPanel(facing, new Vec3((col + .5) / 3, 5.0 / 16, (row + .5) / 3));
            return new BlockHitResult(Vec3.atLowerCornerOf(pos).add(p), facing, pos, false);
        }
        void click(InteractionHand hand, int col, int row) {
            player.gameMode.useItemOn(player, helper.getLevel(), player.getItemInHand(hand), hand, hit(col, row));
        }
    }

    private static Fixture setup(GameTestHelper h, Direction facing, GameType mode) {
        BlockPos p = h.absolutePos(new BlockPos(3, 3, 3));
        h.getLevel().setBlockAndUpdate(p.relative(facing.getOpposite()), Blocks.STONE.defaultBlockState());
        h.getLevel().setBlockAndUpdate(p, ModBlocks.MODULAR_TABLE_BASE.get().defaultBlockState()
                .setValue(ModularTableBaseBlock.FACING, facing));
        var player = player(h, mode);
        player.setPos(p.getX() + .5, p.getY() + 1, p.getZ() + .5);
        return new Fixture(h, p, (ModularTableBlockEntity) h.getLevel().getBlockEntity(p), player);
    }

    private static ServerPlayer player(GameTestHelper h, GameType mode) {
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "table-test"), false);
        var player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        // No login events: unrelated bundled mods require a negotiated real client there.
        // Keep the actual ServerPlayerGameMode and block/item callbacks; capture UI destinations.
        sent.put(player.getUUID(), new java.util.ArrayList<>());
        player.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(
                h.getLevel().getServer(), connection, player, cookie) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {
                if (packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket p)
                    sent.get(player.getUUID()).add(p.payload());
            }
        };
        player.setGameMode(mode);
        return player;
    }

    @GameTest(template = "empty")
    public static void lastItemAndRepeatedEmptyClicks(GameTestHelper h) {
        var f = setup(h, Direction.UP, GameType.SURVIVAL);
        f.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModBlocks.BUZZER_MODULE.get()));
        f.click(InteractionHand.MAIN_HAND, 0, 0);
        h.assertTrue(f.table.grid().modules().size() == 1 && f.player.getMainHandItem().isEmpty(), "last item consumed once");
        for (int i = 0; i < 5; i++) f.click(InteractionHand.MAIN_HAND, 1, 1);
        h.assertTrue(f.table.grid().modules().size() == 1, "empty hand never creates modules");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void offhandAndStaleContextCannotPlace(GameTestHelper h) {
        var f = setup(h, Direction.UP, GameType.SURVIVAL);
        var buzzer = new ItemStack(ModBlocks.BUZZER_MODULE.get(), 2);
        f.player.setItemInHand(InteractionHand.OFF_HAND, buzzer);
        f.click(InteractionHand.MAIN_HAND, 0, 0);
        f.click(InteractionHand.OFF_HAND, 0, 0);
        h.assertTrue(f.table.grid().modules().isEmpty() && buzzer.getCount() == 2, "secondary hand is inert");
        f.player.setItemInHand(InteractionHand.MAIN_HAND, buzzer.copy());
        var stale = new UseOnContext(f.player, InteractionHand.MAIN_HAND, f.hit(0, 0));
        f.player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        ModBlocks.BUZZER_MODULE.get().useOn(stale);
        h.assertTrue(f.table.grid().modules().isEmpty(), "stale callback cannot place");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void occupiedAndOutOfBoundsDoNotConsume(GameTestHelper h) {
        var f = setup(h, Direction.UP, GameType.SURVIVAL);
        f.table.placeBuzzer(0, 0);
        var stack = new ItemStack(ModBlocks.LAMP_MODULE_2X1.get(), 4);
        f.player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        f.click(InteractionHand.MAIN_HAND, 0, 0);
        f.click(InteractionHand.MAIN_HAND, 2, 2);
        h.assertTrue(stack.getCount() == 4 && f.table.grid().modules().size() == 1, "rejected placements are inert");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void sixOrientationsAndFmsCoveredCells(GameTestHelper h) {
        for (Direction facing : Direction.values()) {
            var f = setup(h, facing, GameType.CREATIVE);
            f.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModBlocks.FMS_MODULE.get()));
            f.click(InteractionHand.MAIN_HAND, 0, 0);
            h.assertTrue(f.table.grid().modules().size() == 1 && f.player.getMainHandItem().getCount() == 1, "creative placement " + facing);
            for (int row = 0; row < 3; row++) for (int col = 0; col < 2; col++) {
                var target = TableInteraction.target(f.table, facing, f.hit(col, row).getLocation());
                h.assertTrue(target.module() != null && target.column() == 0 && target.row() == 0, "covered cell maps to anchor");
            }
            h.getLevel().destroyBlock(f.pos, false);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void racingPlayersInstallOnlyOne(GameTestHelper h) {
        var f = setup(h, Direction.UP, GameType.SURVIVAL);
        f.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModBlocks.BUZZER_MODULE.get(), 2));
        var second = player(h, GameType.SURVIVAL);
        second.setPos(f.player.getX(), f.player.getY(), f.player.getZ());
        second.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModBlocks.BUZZER_MODULE.get(), 2));
        f.click(InteractionHand.MAIN_HAND, 0, 0);
        second.gameMode.useItemOn(second, h.getLevel(), second.getMainHandItem(), InteractionHand.MAIN_HAND, f.hit(0, 0));
        h.assertTrue(f.table.grid().modules().size() == 1 && second.getMainHandItem().getCount() == 2, "loser does not consume");
        h.succeed();
    }

    private static ItemStack wrench() {
        return new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                net.minecraft.resources.ResourceLocation.parse("create:wrench")));
    }

    private static void panelAction(Fixture f, PanelActionPayload payload) throws Exception {
        var method = PanelNetwork.class.getDeclaredMethod("handle", PanelActionPayload.class, ServerPlayer.class);
        method.setAccessible(true);
        method.invoke(null, payload, f.player);
    }

    private static PanelActionPayload action(Fixture f, String action, int col, int row, int volume, UUID token) {
        return new PanelActionPayload(f.pos, col, row, action, "gpws", "caution", 2, volume, "test", .5, token);
    }

    @GameTest(template = "empty")
    public static void uiRoutingShiftAndPanelList(GameTestHelper h) throws Exception {
        var f = setup(h, Direction.UP, GameType.SURVIVAL);
        f.table.placeBuzzer(0, 0);
        f.player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModBlocks.BUZZER_MODULE.get()));
        f.click(InteractionHand.MAIN_HAND, 1, 1);
        var messages = sent.get(f.player.getUUID());
        h.assertTrue(messages.getLast() instanceof PanelScreenPayload p && p.column() == -1, "vacant opens general despite offhand");
        f.click(InteractionHand.MAIN_HAND, 0, 0);
        h.assertTrue(messages.getLast() instanceof PanelScreenPayload p && p.moduleType().equals("buzzer"), "occupied opens individual");
        f.player.setShiftKeyDown(true);
        f.click(InteractionHand.MAIN_HAND, 0, 0);
        h.assertTrue(messages.getLast() instanceof PanelScreenPayload p && p.column() == -1, "shift opens general over module");
        f.table.placeLamp(2, 2);
        f.table.testPanel();
        f.click(InteractionHand.MAIN_HAND, 2, 2);
        h.assertTrue(messages.getLast() instanceof PanelScreenPayload p && p.moduleType().equals("lamp"), "shift opens lit lamp");
        f.player.setShiftKeyDown(false);
        int before = messages.size();
        f.click(InteractionHand.MAIN_HAND, 2, 2);
        h.assertTrue(messages.size() == before, "lit lamp click acknowledges without a screen");
        f.table.setMuted(false);
        f.click(InteractionHand.MAIN_HAND, 0, 0);
        h.assertTrue(messages.size() == before && f.table.muted(), "sounding buzzer click acknowledges without a screen");
        f.player.setShiftKeyDown(true);
        f.click(InteractionHand.MAIN_HAND, 0, 0);
        h.assertTrue(messages.getLast() instanceof PanelScreenPayload p && p.moduleType().equals("buzzer"),
                "shift opens sounding buzzer");
        f.player.setShiftKeyDown(false);
        panelAction(f, action(f, "open_module", 0, 0, 100, f.table.interactionToken()));
        h.assertTrue(messages.getLast() instanceof PanelScreenPayload p && p.moduleType().equals("buzzer"), "panel list opens module");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void sideClicksOpenAndRemoveButBaseSidesDoNot(GameTestHelper h) {
        for (Direction facing : Direction.values()) {
            var f = setup(h, facing, GameType.SURVIVAL);
            f.table.placeBuzzer(0, 0);
            Vec3 p = PanelCoordinates.fromPanel(facing, new Vec3(.06, .25, 1.0 / 6));
            Vec3 sideNormal = PanelCoordinates.fromPanel(facing, new Vec3(0,0,0))
                    .subtract(PanelCoordinates.fromPanel(facing, new Vec3(1,0,0)));
            Direction side = Direction.getNearest(sideNormal.x, sideNormal.y, sideNormal.z);
            var hit = new BlockHitResult(Vec3.atLowerCornerOf(f.pos).add(p), side, f.pos, false);
            f.player.gameMode.useItemOn(f.player, h.getLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit);
            h.assertTrue(sent.get(f.player.getUUID()).getLast() instanceof PanelScreenPayload s
                    && s.moduleType().equals("buzzer"), "side click opens buzzer " + facing);
            f.player.setItemInHand(InteractionHand.MAIN_HAND, wrench());
            f.player.gameMode.useItemOn(f.player, h.getLevel(), f.player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
            h.assertTrue(f.table.grid().modules().isEmpty(), "side removal " + facing);
            f.player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            int before = sent.get(f.player.getUUID()).size();
            Vec3 base = PanelCoordinates.fromPanel(facing, new Vec3(0, .1, .5));
            var baseHit = new BlockHitResult(Vec3.atLowerCornerOf(f.pos).add(base), side, f.pos, false);
            f.player.gameMode.useItemOn(f.player, h.getLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND, baseHit);
            h.assertTrue(sent.get(f.player.getUUID()).size() == before, "base side cannot open UI");
            h.getLevel().destroyBlock(f.pos, false);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void creativeFullInventoryReturnsModuleByDrop(GameTestHelper h) {
        var f = setup(h, Direction.UP, GameType.CREATIVE);
        f.table.placeBuzzer(0, 0);
        for (int i = 0; i < 36; i++) f.player.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
        f.player.setItemInHand(InteractionHand.MAIN_HAND, wrench());
        f.click(InteractionHand.MAIN_HAND, 0, 0);
        int count = h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(f.pos).inflate(1)).stream()
                .filter(e -> e.getItem().is(ModBlocks.BUZZER_MODULE.get())).mapToInt(e -> e.getItem().getCount()).sum();
        h.assertTrue(f.table.grid().modules().isEmpty() && count == 1, "creative full inventory drops exactly one module");
        f.click(InteractionHand.MAIN_HAND, 0, 0);
        h.assertTrue(sent.get(f.player.getUUID()).isEmpty(), "wrench on vacant never opens UI");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void staleScreensAndBuzzerPreviewAreInert(GameTestHelper h) throws Exception {
        var f = setup(h, Direction.UP, GameType.SURVIVAL);
        f.table.placeBuzzer(0, 0);
        UUID old = f.table.moduleToken(0, 0);
        panelAction(f, action(f, "test_buzzer", 0, 0, 20, old));
        h.assertTrue(f.table.grid().at(0,0).orElseThrow().buzzerVolume() == 100, "test sound does not save draft");
        f.table.removeModuleStack(0,0);
        f.table.placeBuzzer(0,0);
        panelAction(f, action(f, "volume", 0,0,20,old));
        h.assertTrue(f.table.grid().at(0,0).orElseThrow().buzzerVolume() == 100, "removed installation token rejected");
        panelAction(f, action(f, "volume", 0,0,20,f.table.moduleToken(0,0)));
        h.assertTrue(f.table.grid().at(0,0).orElseThrow().buzzerVolume() == 20, "current volume saves");
        UUID oldTable = f.table.interactionToken();
        h.getLevel().destroyBlock(f.pos, false);
        h.getLevel().setBlockAndUpdate(f.pos, ModBlocks.MODULAR_TABLE_BASE.get().defaultBlockState());
        var replacement = (ModularTableBlockEntity) h.getLevel().getBlockEntity(f.pos);
        panelAction(f, action(f, "mute", -1,-1,100,oldTable));
        h.assertTrue(!replacement.muted(), "old general screen cannot mutate replacement");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void fmsUiAndStaleCommands(GameTestHelper h) throws Exception {
        var f = setup(h, Direction.UP, GameType.SURVIVAL);
        f.table.placeFmsModule(0,0,new com.radaeli.aeronauticsflybyinstruments.fms.FmsModuleData());
        UUID token = f.table.moduleToken(0,0);
        for(int row=0;row<3;row++) for(int col=0;col<2;col++) {
            f.click(InteractionHand.MAIN_HAND,col,row);
            h.assertTrue(sent.get(f.player.getUUID()).getLast() instanceof FmsScreenPayload p
                    && p.column()==0 && p.row()==0, "every covered cell opens FMS");
        }
        panelAction(f, action(f,"open_module",0,0,100,f.table.interactionToken()));
        h.assertTrue(sent.get(f.player.getUUID()).getLast() instanceof FmsScreenPayload, "FMS opens from general list");
        var removed=f.table.removeModuleStack(1,2);
        f.table.placeFmsModule(0,0,com.radaeli.aeronauticsflybyinstruments.item.FmsModuleItem.load(removed));
        var method=FmsNetwork.class.getDeclaredMethod("handle", FmsActionPayload.class, net.minecraft.world.entity.player.Player.class);
        method.setAccessible(true);
        method.invoke(null,new FmsActionPayload(f.pos,0,0,"create","","stale",1,2,3,-1,false,token),f.player);
        h.assertTrue(f.table.fmsAt(0,0).orElseThrow().data().waypoints().isEmpty(), "stale FMS command rejected");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void shiftPlacementAndOffhandWrench(GameTestHelper h) {
        var f=setup(h,Direction.UP,GameType.SURVIVAL);
        f.player.setShiftKeyDown(true);
        f.player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ModBlocks.LAMP_MODULE.get(),2));
        f.click(InteractionHand.MAIN_HAND,0,0);
        h.assertTrue(f.table.grid().modules().size()==1 && f.player.getMainHandItem().getCount()==1,"shift still installs one");
        f.player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        f.player.setItemInHand(InteractionHand.OFF_HAND,wrench());
        f.click(InteractionHand.OFF_HAND,0,0);
        h.assertTrue(h.getLevel().getBlockEntity(f.pos)==f.table && f.table.grid().modules().size()==1,"offhand cannot pick up table");
        f.player.setShiftKeyDown(false);
        f.click(InteractionHand.OFF_HAND,0,0);
        h.assertTrue(f.table.grid().modules().size()==1,"offhand cannot remove module");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void supportLossReturnsContents(GameTestHelper h) {
        var f=setup(h,Direction.UP,GameType.SURVIVAL);
        f.table.placeBuzzer(0,0);
        h.getLevel().destroyBlock(f.pos.below(),false);
        h.assertTrue(h.getLevel().getBlockState(f.pos).isAir(),"table breaks on support loss");
        int count=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(f.pos).inflate(1)).stream()
                .filter(e->e.getItem().is(ModBlocks.BUZZER_MODULE.get())).mapToInt(e->e.getItem().getCount()).sum();
        h.assertTrue(count==1,"support loss drops one module");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void nativePickupReturnsFmsDataOnce(GameTestHelper h) {
        var f=setup(h,Direction.UP,GameType.SURVIVAL);
        var data=new com.radaeli.aeronauticsflybyinstruments.fms.FmsModuleData();
        var waypoint=new com.radaeli.aeronauticsflybyinstruments.fms.FmsWaypoint(UUID.randomUUID(),"Home",
                "minecraft:overworld",10,20,30);
        data.add(waypoint); data.select(waypoint.id());
        f.table.placeFmsModule(0,0,data);
        f.player.setItemInHand(InteractionHand.MAIN_HAND,wrench());
        f.player.setShiftKeyDown(true);
        f.click(InteractionHand.MAIN_HAND,1,2);
        h.assertTrue(h.getLevel().getBlockState(f.pos).isAir(),"native wrench picks up table");
        var drops=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(f.pos).inflate(1)).stream()
                .filter(e->e.getItem().is(ModBlocks.FMS_MODULE.get())).toList();
        h.assertTrue(drops.size()==1 && drops.getFirst().getItem().getCount()==1,"one FMS returned");
        var restored=com.radaeli.aeronauticsflybyinstruments.item.FmsModuleItem.load(drops.getFirst().getItem());
        h.assertTrue(restored.id().equals(data.id()) && restored.waypoints().equals(data.waypoints())
                && restored.selectedWaypointId().equals(java.util.Optional.of(waypoint.id())),"identity database and target preserved");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void unrelatedItemAndInvalidPlacementNeverOpenUi(GameTestHelper h) {
        var f=setup(h,Direction.UP,GameType.SURVIVAL);
        f.player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STICK));
        f.click(InteractionHand.MAIN_HAND,0,0);
        f.table.placeBuzzer(0,0);
        f.player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ModBlocks.BUZZER_MODULE.get(),3));
        f.click(InteractionHand.MAIN_HAND,0,0);
        h.assertTrue(sent.get(f.player.getUUID()).isEmpty() && f.player.getMainHandItem().getCount()==3,
                "other items and failed placement cannot open UI");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void tableAndFmsUseGlobalSublevelDistance(GameTestHelper h) {
        var container=dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(h.getLevel());
        var destination=Vec3.atCenterOf(h.absolutePos(new BlockPos(3,3,3)));
        var sub=dev.ryanhcode.sable.neoforge.gametest.SableTestHelper.spawnSubLevel(container,
                new org.joml.Vector3d(destination.x,destination.y,destination.z), access->{
                    access.setBlock(BlockPos.ZERO,Blocks.STONE.defaultBlockState(),3);
                    access.setBlock(BlockPos.ZERO.above(),ModBlocks.MODULAR_TABLE_BASE.get().defaultBlockState(),3);
                });
        try {
            BlockPos raw=sub.getPlot().getCenterBlock().above();
            var table=(ModularTableBlockEntity) h.getLevel().getBlockEntity(raw);
            h.assertTrue(table!=null,"table exists inside sublevel plot");
            table.placeFmsModule(0,0,new com.radaeli.aeronauticsflybyinstruments.fms.FmsModuleData());
            var player=player(h,GameType.SURVIVAL);
            Vec3 global=PanelSpatial.tableWorldPosition(table);
            player.setPos(global.x,global.y+1,global.z);
            h.assertTrue(player.position().distanceToSqr(Vec3.atCenterOf(raw))>64,"raw plot coordinates are far away");
            h.assertTrue(TableInteraction.canReach(player,table),"global distance grants table access");
            PanelNetwork.open(player,table,null); FmsNetwork.open(player,raw,0,0,table);
            var messages=sent.get(player.getUUID());
            h.assertTrue(messages.stream().anyMatch(p->p instanceof PanelScreenPayload)
                    && messages.stream().anyMatch(p->p instanceof FmsScreenPayload),"both screens open using transformed distance");
            int before=messages.size(); player.setPos(global.x+9,global.y,global.z);
            PanelNetwork.open(player,table,null); FmsNetwork.open(player,raw,0,0,table);
            h.assertTrue(messages.size()==before,"both screens reject global distance over eight blocks");
        } finally {
            dev.ryanhcode.sable.neoforge.gametest.SableTestHelper.removeSubLevel(container,sub);
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void everyFootprintPlacesAndRemovesInAllOrientations(GameTestHelper h) {
        for(Direction facing:Direction.values()) for(int kind=0;kind<5;kind++) {
            var f=setup(h,facing,GameType.SURVIVAL);
            var item=switch(kind) {
                case 0 -> ModBlocks.LAMP_MODULE.get();
                case 1 -> ModBlocks.BUZZER_MODULE.get();
                case 2,3 -> ModBlocks.LAMP_MODULE_2X1.get();
                default -> ModBlocks.FMS_MODULE.get();
            };
            f.player.getPersistentData().putBoolean("AeronauticsFlyByInstrumentsLamp2x1Vertical",kind==3);
            f.player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(item));
            f.player.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(ModBlocks.FMS_MODULE.get()));
            var held=TableInteraction.heldModule(f.player).orElseThrow();
            f.click(InteractionHand.MAIN_HAND,0,0);
            var installed=f.table.grid().modules().getFirst();
            h.assertTrue(installed.width()==held.width() && installed.height()==held.height(),"preview and placement agree");
            h.assertTrue(f.player.getMainHandItem().isEmpty(),"survival consumes exactly one");
            f.player.setItemInHand(InteractionHand.MAIN_HAND,wrench());
            f.click(InteractionHand.MAIN_HAND,held.width()-1,held.height()-1);
            int returned=java.util.stream.IntStream.range(0,36).map(i->{
                var stack=f.player.getInventory().getItem(i); return stack.is(item)?stack.getCount():0;
            }).sum();
            h.assertTrue(f.table.grid().modules().isEmpty() && returned==1,
                    "last covered cell returns one item: " + facing + " kind=" + kind + " count=" + returned);
            h.getLevel().destroyBlock(f.pos,false);
        }
        h.succeed();
    }
}
