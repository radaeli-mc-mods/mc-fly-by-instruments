package com.radaeli.aeronauticsflybyinstruments.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import com.radaeli.aeronauticsflybyinstruments.panel.ModularTableBlockEntity;
import com.radaeli.aeronauticsflybyinstruments.panel.PanelGrid;
import com.radaeli.aeronauticsflybyinstruments.panel.PanelCoordinates;
import com.radaeli.aeronauticsflybyinstruments.panel.TablePanel;
import com.radaeli.aeronauticsflybyinstruments.network.FmsNetwork;
import com.radaeli.aeronauticsflybyinstruments.network.PanelNetwork;
import com.radaeli.aeronauticsflybyinstruments.item.BuzzerModuleItem;
import com.radaeli.aeronauticsflybyinstruments.item.FmsModuleItem;
import com.radaeli.aeronauticsflybyinstruments.item.LampModule2x1Item;
import com.radaeli.aeronauticsflybyinstruments.item.LampModuleItem;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;

/** Thin mounting surface with a persistent virtual grid. */
public final class ModularTableBaseBlock extends Block implements EntityBlock, AvionicsWrenchable {
    public static final MapCodec<ModularTableBaseBlock> CODEC = simpleCodec(ModularTableBaseBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    /** Block light from the brightest lit lamp; shaders light the surroundings from it. */
    public static final IntegerProperty LIGHT = IntegerProperty.create("light", 0, 15);
    private static final ResourceLocation CREATE_WRENCH_ID =
            ResourceLocation.fromNamespaceAndPath("create", "wrench");

    // 4 px (PanelCoordinates.SURFACE): a wire connector's body fits the edge.
    private static final VoxelShape UP = box(0, 0, 0, 16, 4, 16);
    private static final VoxelShape DOWN = box(0, 12, 0, 16, 16, 16);
    private static final VoxelShape NORTH = box(0, 0, 12, 16, 16, 16);
    private static final VoxelShape SOUTH = box(0, 0, 0, 16, 16, 4);
    private static final VoxelShape WEST = box(12, 0, 0, 16, 16, 16);
    private static final VoxelShape EAST = box(0, 0, 0, 4, 16, 16);

    public ModularTableBaseBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP).setValue(LIGHT, 0));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIGHT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos supportPos = support(state, pos);
        BlockState support = level.getBlockState(supportPos);
        // Any face of the Avionics Computer, recessed sides included: the table docks to it (one network device).
        return support.is(ModBlocks.AVIONICS_COMPUTER.get()) || support.isFaceSturdy(level, supportPos, facing);
    }

    /** The block the table is mounted on. */
    public static BlockPos support(BlockState state, BlockPos pos) {
        return pos.relative(state.getValue(FACING).getOpposite());
    }

    public static boolean isCreateWrench(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(CREATE_WRENCH_ID);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ModularTableBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide || type != ModBlocks.MODULAR_TABLE_ENTITY.get()) return null;
        @SuppressWarnings("unchecked")
        BlockEntityTicker<T> ticker = (currentLevel, pos, blockState, blockEntity) ->
                ModularTableBlockEntity.serverTick((net.minecraft.server.level.ServerLevel) currentLevel, pos, blockState,
                        (ModularTableBlockEntity) blockEntity);
        return ticker;
    }

    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                                    Player player, BlockHitResult hit) {
        // Default interaction is exclusively the empty MAIN hand, regardless of the offhand.
        if (!player.getMainHandItem().isEmpty()) {
            return net.minecraft.world.InteractionResult.PASS;
        }
        if (!(level.getBlockEntity(pos) instanceof ModularTableBlockEntity table)) {
            return net.minecraft.world.InteractionResult.PASS;
        }
        var target = com.radaeli.aeronauticsflybyinstruments.panel.TableInteraction.target(table,
                hit.getDirection(), hit.getLocation());
        if (!target.interactive()) return net.minecraft.world.InteractionResult.PASS;
        // The module may belong to a joined table: the FMS opens on its owner, the panel on the whole surface.
        // Sneaking opens the general panel, except over a lit lamp or sounding buzzer, whose own
        // configuration it opens.
        var hitModule = target.module();
        boolean alerting = hitModule != null && (ModularTableBlockEntity.isLampModule(hitModule.module())
                || hitModule.module().type().equals("buzzer"))
                && hitModule.owner().isModuleActive(hitModule.module());
        var placed = java.util.Optional.ofNullable(player.isShiftKeyDown() && !alerting ? null : hitModule);
        var module = placed.map(TablePanel.Placed::module);
        // A lit lamp or sounding buzzer works like a master warning: pressing it acknowledges (silences the buzzers).
        if (alerting && !player.isShiftKeyDown()) {
            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                TablePanel.of(table).setMuted(true);
                serverPlayer.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.aeronautics_fly_by_instruments.panel.acknowledged"), true);
            }
            return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (module.isPresent() && module.get().type().equals("fms")
                && !level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            var owner = placed.get().owner();
            FmsNetwork.open(serverPlayer, owner.getBlockPos(), module.get().column(), module.get().row(), owner);
        }
        boolean attitude = module.filter(value -> value.type().equals(PanelCoordinates.ATTITUDE_INDICATOR)).isPresent();
        if (attitude && !level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            var owner = placed.get().owner();
            com.radaeli.aeronauticsflybyinstruments.network.AttitudeIndicatorNetwork.open(serverPlayer, owner,
                    module.get().column(), module.get().row());
        }
        boolean controlPanel = module.isEmpty() || module.filter(value -> value.type().equals("lamp")
                || value.type().equals("lamp_2x1") || value.type().equals("buzzer")).isPresent();
        if (controlPanel && !level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            PanelNetwork.open(serverPlayer, table, placed.map(found -> TablePanel.of(table).toPanel(table, found))
                    .orElse(null));
        }
        return module.filter(value -> value.type().equals("fms")).isPresent() || attitude || controlPanel
                ? net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide)
                : net.minecraft.world.InteractionResult.PASS;
    }

    static boolean isPanelModule(ItemStack stack) {
        return com.radaeli.aeronauticsflybyinstruments.panel.TableInteraction.isModule(stack);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                               Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) {
            return isPanelModule(stack) || isCreateWrench(stack)
                    ? ItemInteractionResult.sidedSuccess(level.isClientSide)
                    : ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        if (isPanelModule(stack)) return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        if (!stack.isEmpty() && !isCreateWrench(stack)) return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        if (!isCreateWrench(stack) || !(level.getBlockEntity(pos) instanceof ModularTableBlockEntity table)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        var target = com.radaeli.aeronauticsflybyinstruments.panel.TableInteraction.target(table,
                hit.getDirection(), hit.getLocation());
        var placed = target.module();
        if (placed == null) return ItemInteractionResult.sidedSuccess(level.isClientSide);
        if (!level.isClientSide) {
            var moduleStack = placed.owner().removeModuleStack(placed.module().column(), placed.module().row());
            if (moduleStack.isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            var inventory = new net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper(player.getInventory());
            var remainder = net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(inventory, moduleStack, false);
            if (!remainder.isEmpty()) popResource(level, pos, remainder);
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public net.minecraft.world.InteractionResult onSneakWrenched(BlockState state,
                                                                 net.minecraft.world.item.context.UseOnContext context) {
        if (context.getHand() != InteractionHand.MAIN_HAND || context.getPlayer() == null
                || !isCreateWrench(context.getPlayer().getMainHandItem())) return net.minecraft.world.InteractionResult.FAIL;
        return AvionicsWrenchable.super.onSneakWrenched(state, context);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
                                     net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return direction == state.getValue(FACING).getOpposite() && !canSurvive(state, level, pos)
                ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
                : super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    public static ItemStack itemStack(PanelGrid.Module module) {
        return switch (module.type()) {
            case "lamp" -> new ItemStack(ModBlocks.LAMP_MODULE.get());
            case "lamp_2x1" -> new ItemStack(ModBlocks.LAMP_MODULE_2X1.get());
            case "buzzer" -> new ItemStack(ModBlocks.BUZZER_MODULE.get());
            case "fms" -> new ItemStack(ModBlocks.FMS_MODULE.get());
            case com.radaeli.aeronauticsflybyinstruments.panel.PanelCoordinates.ATTITUDE_INDICATOR ->
                    new ItemStack(ModBlocks.ATTITUDE_INDICATOR_MODULE.get());
            default -> ItemStack.EMPTY;
        };
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide
                && level.getBlockEntity(pos) instanceof ModularTableBlockEntity table) {
            // Modules running in from the joined tables before this one lose part of their support too.
            for (var placed : TablePanel.covering(table)) {
                if (placed.owner() == table) continue;
                var stack = placed.owner().removeModuleStack(placed.module().column(), placed.module().row());
                if (!stack.isEmpty()) popResource(level, pos, stack);
            }
            for (var stack : table.drainModuleStacks()) {
                popResource(level, pos, stack);
            }
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        var facing = state.getValue(FACING);
        VoxelShape shape = switch (facing) {
            case UP -> UP;
            case DOWN -> DOWN;
            case NORTH -> NORTH;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            case EAST -> EAST;
        };
        if (level.getBlockEntity(pos) instanceof ModularTableBlockEntity table) {
            // Each table outlines its share of the modules over it, clipped at its own edges.
            for (var placed : TablePanel.covering(table)) {
                var module = placed.module();
                double x = Math.max(0, placed.column() / 3.0 + 0.06);
                double z = Math.max(0, placed.row() / 3.0 + 0.06);
                var start = PanelCoordinates.fromPanel(facing, new Vec3(x, PanelCoordinates.SURFACE, z));
                var end = PanelCoordinates.fromPanel(facing, new Vec3(
                        Math.min(1, (placed.column() + module.width()) / 3.0 - 0.06), PanelCoordinates.moduleTop(module.type(), facing),
                        Math.min(1, (placed.row() + module.height()) / 3.0 - 0.06)));
                shape = Shapes.or(shape, Shapes.box(Math.min(start.x, end.x), Math.min(start.y, end.y), Math.min(start.z, end.z),
                        Math.max(start.x, end.x), Math.max(start.y, end.y), Math.max(start.z, end.z)));
            }
        }
        return shape;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }
}
