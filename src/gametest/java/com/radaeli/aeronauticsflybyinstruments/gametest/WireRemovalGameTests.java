package com.radaeli.aeronauticsflybyinstruments.gametest;

import com.mojang.authlib.GameProfile;
import com.radaeli.aeronauticsflybyinstruments.block.ModBlocks;
import com.radaeli.aeronauticsflybyinstruments.wire.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

@GameTestHolder("aeronautics_fly_by_instruments")
@PrefixGameTestTemplate(false)
public final class WireRemovalGameTests {
    private record Fixture(GameTestHelper helper, List<BlockPos> positions, FakePlayer player, WireRoute route) {
        void assertRemoved() {
            for (var pos : positions) {
                if (helper.getLevel().getBlockEntity(pos) instanceof WireNodeBlockEntity node) {
                    helper.assertTrue(node.routes().isEmpty() && node.farEnds().isEmpty(), "wire disconnected at " + pos);
                }
            }
        }

        void assertRefund(int expected) {
            int actual = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                            new AABB(Vec3.atLowerCornerOf(positions.getFirst()),
                                    Vec3.atLowerCornerOf(positions.getLast())).inflate(2)).stream()
                    .filter(entity -> entity.getItem().is(ModBlocks.AVIONICS_WIRE.get()))
                    .mapToInt(entity -> entity.getItem().getCount()).sum();
            helper.assertTrue(actual == expected, "wire refund: expected " + expected + ", got " + actual);
        }
    }

    private static Fixture setup(GameTestHelper h, GameType mode) {
        var positions = List.of(h.absolutePos(new BlockPos(1, 2, 2)),
                h.absolutePos(new BlockPos(3, 2, 2)), h.absolutePos(new BlockPos(5, 2, 2)));
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "wire-test"));
        player.setGameMode(mode);
        player.setPos(Vec3.atCenterOf(positions.getFirst()));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModBlocks.AVIONICS_WIRE.get(), 16));
        for (int i = 0; i < positions.size(); i++) {
            var pos = positions.get(i);
            h.getLevel().setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
            h.getLevel().setBlockAndUpdate(pos, (i == 1 ? ModBlocks.WIRE_SUPPORT : ModBlocks.WIRE_CONNECTOR)
                    .get().defaultBlockState().setValue(WireNodeBlock.FACING, Direction.UP));
            ModBlocks.AVIONICS_WIRE.get().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)));
        }
        var route = ((WireNodeBlockEntity) h.getLevel().getBlockEntity(positions.getFirst())).routes().getFirst();
        for (var pos : positions) {
            var node = (WireNodeBlockEntity) h.getLevel().getBlockEntity(pos);
            h.assertTrue(node.hasRoute(route.id()), "every anchor holds the completed wire");
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        return new Fixture(h, positions, player, route);
    }

    private static void breakEndpoint(GameTestHelper h, int endpoint, GameType mode) {
        var f = setup(h, mode);
        h.assertTrue(f.player.gameMode.destroyBlock(f.positions.get(endpoint)), "player breaks endpoint");
        h.runAfterDelay(3, () -> {
            f.assertRemoved();
            f.assertRefund(mode == GameType.CREATIVE ? 0 : WireMaintenance.refund(f.route).getCount());
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void breakDestinationSurvival(GameTestHelper h) {
        breakEndpoint(h, 2, GameType.SURVIVAL);
    }

    @GameTest(template = "empty")
    public static void breakOriginSurvival(GameTestHelper h) {
        breakEndpoint(h, 0, GameType.SURVIVAL);
    }

    @GameTest(template = "empty")
    public static void breakDestinationCreative(GameTestHelper h) {
        breakEndpoint(h, 2, GameType.CREATIVE);
    }

    @GameTest(template = "empty")
    public static void destinationHostDestroyed(GameTestHelper h) {
        var f = setup(h, GameType.SURVIVAL);
        h.getLevel().destroyBlock(f.positions.getLast().below(), false);
        h.runAfterDelay(3, () -> {
            f.assertRemoved();
            f.assertRefund(WireMaintenance.refund(f.route).getCount());
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void lastRouteRemovalSynchronizesToClient(GameTestHelper h) {
        var f = setup(h, GameType.SURVIVAL);
        var origin = (WireNodeBlockEntity) h.getLevel().getBlockEntity(f.positions.getFirst());
        var client = new WireNodeBlockEntity(origin.getBlockPos(), origin.getBlockState());
        client.onDataPacket(null, (ClientboundBlockEntityDataPacket) origin.getUpdatePacket(), h.getLevel().registryAccess());
        h.assertTrue(client.hasRoute(f.route.id()), "initial packet installs the wire on the client");
        client.renderCache = new Object();
        f.player.gameMode.destroyBlock(f.positions.getLast());
        h.runAfterDelay(3, () -> {
            f.assertRemoved();
            client.onDataPacket(null, (ClientboundBlockEntityDataPacket) origin.getUpdatePacket(), h.getLevel().registryAccess());
            h.assertTrue(client.routes().isEmpty() && client.farEnds().isEmpty(), "removal packet clears client wire data");
            h.assertTrue(client.renderCache == null, "removal packet invalidates cached wire geometry");
            h.succeed();
        });
    }
}
