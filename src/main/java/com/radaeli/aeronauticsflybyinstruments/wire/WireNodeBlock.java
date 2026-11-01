package com.radaeli.aeronauticsflybyinstruments.wire;

import com.radaeli.aeronauticsflybyinstruments.block.AvionicsWrenchable;
import com.radaeli.aeronauticsflybyinstruments.block.CableSocketProvider;
import com.radaeli.aeronauticsflybyinstruments.block.ModularTableBaseBlock;
import com.radaeli.aeronauticsflybyinstruments.panel.PanelCoordinates;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Small node mounted on a block face, like a button. {@code FACING} points away from the host block.
 * Models and shapes are authored facing UP; the blockstate rotates them like an end rod.
 */
public abstract class WireNodeBlock extends Block implements EntityBlock, SimpleWaterloggedBlock, AvionicsWrenchable {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private final Map<Direction, VoxelShape> shapes;

    /** @param parts boxes as {inPlaneMin, inPlaneMax, heightMin, heightMax} in pixels, square in the face plane */
    protected WireNodeBlock(BlockBehaviour.Properties properties, int[]... parts) {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.UP).setValue(WATERLOGGED, false));
        shapes = shapes(parts);
    }

    static Map<Direction, VoxelShape> shapes(int[]... parts) {
        var shapes = new EnumMap<Direction, VoxelShape>(Direction.class);
        for (Direction facing : Direction.values()) {
            VoxelShape shape = Shapes.empty();
            for (int[] part : parts) shape = Shapes.or(shape, box(facing, part[0], part[1], part[2], part[3]));
            shapes.put(facing, shape.optimize());
        }
        return shapes;
    }

    /** Box square in the face plane ({@code min..max} on both axes) and {@code low..high} above the host face. */
    static VoxelShape box(Direction facing, double min, double max, double low, double high) {
        return switch (facing) {
            case UP -> Block.box(min, low, min, max, high, max);
            case DOWN -> Block.box(min, 16 - high, min, max, 16 - low, max);
            case NORTH -> Block.box(min, min, 16 - high, max, max, 16 - low);
            case SOUTH -> Block.box(min, min, low, max, max, high);
            case WEST -> Block.box(16 - high, min, min, 16 - low, max, max);
            case EAST -> Block.box(low, min, min, high, max, max);
        };
    }

    public static BlockPos host(BlockState state, BlockPos pos) {
        return pos.relative(state.getValue(FACING).getOpposite());
    }

    /**
     * Shift that seats a connector on its device's cable socket, or mid-thickness on a modular table edge; zero
     * on ordinary blocks and for supports.
     */
    public static Vec3 socketOffset(BlockGetter level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof WireConnectorBlock)) return Vec3.ZERO;
        Direction facing = state.getValue(FACING);
        BlockState hostState = level.getBlockState(host(state, pos));
        Vec3 socket = hostState.getBlock() instanceof CableSocketProvider sockets
                ? sockets.cableSocket(hostState, facing) : tableEdgeSocket(hostState, facing);
        return socket == null ? Vec3.ZERO : WireAttachPoints.socketOffset(facing, socket);
    }

    /** Middle of a modular table's edge on {@code face}, host-local; null unless the face is an edge of a table. */
    @Nullable
    static Vec3 tableEdgeSocket(BlockState host, Direction face) {
        if (!(host.getBlock() instanceof ModularTableBaseBlock)) return null;
        Direction table = host.getValue(ModularTableBaseBlock.FACING);
        if (table.getAxis() == face.getAxis()) return null;
        double half = PanelCoordinates.SURFACE / 2;
        double depth = table.getAxisDirection() == Direction.AxisDirection.POSITIVE ? half : 1 - half;
        Vec3 centre = new Vec3(.5 + .5 * face.getStepX(), .5 + .5 * face.getStepY(), .5 + .5 * face.getStepZ());
        return switch (table.getAxis()) {
            case X -> new Vec3(depth, centre.y, centre.z);
            case Y -> new Vec3(centre.x, depth, centre.z);
            case Z -> new Vec3(centre.x, centre.y, depth);
        };
    }

    /** Shape before the socket shift. */
    protected VoxelShape baseShape(BlockState state) {
        return shapes.get(state.getValue(FACING));
    }

    /** State that depends on the host block; recomputed on placement and whenever the host changes. */
    protected BlockState withHost(BlockState state, BlockGetter level, BlockPos pos) {
        return state;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WATERLOGGED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Vec3 offset = socketOffset(level, pos, state);
        return offset == Vec3.ZERO ? baseShape(state) : baseShape(state).move(offset.x, offset.y, offset.z);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        var state = defaultBlockState().setValue(FACING, context.getClickedFace())
                .setValue(WATERLOGGED, context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER);
        return state.canSurvive(context.getLevel(), context.getClickedPos())
                ? withHost(state, context.getLevel(), context.getClickedPos()) : null;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos host = host(state, pos);
        BlockState hostState = level.getBlockState(host);
        // Lenient on purpose: sensors and the legacy cable are not full faces but must take a connector.
        return !hostState.isAir() && !(hostState.getBlock() instanceof WireNodeBlock)
                && !hostState.getCollisionShape(level, host).isEmpty();
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        if (direction == state.getValue(FACING).getOpposite()) {
            if (!state.canSurvive(level, pos)) return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
            state = withHost(state, level, pos);
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
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
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof WireNodeBlockEntity node) {
            if (node.isConnector()) {
                // A connector is a real endpoint, so breaking either end must sever every loaded copy
                // before the block can be replaced. Supports keep the deferred path for contraption moves.
                for (var route : List.copyOf(node.routes())) {
                    var refund = WireMaintenance.cutEverywhere(level, pos, route);
                    if (!player.getAbilities().instabuild) {
                        Containers.dropItemStack(level, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, refund);
                    }
                }
            } else {
                if (player.isCreative()) WireMaintenance.suppressRefund(level, pos);
                WireMaintenance.nodeRemoved(level, pos, node.routes());
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && !state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof WireNodeBlockEntity node) {
            WireMaintenance.nodeRemoved(level, pos, node.routes());
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WireNodeBlockEntity(pos, state);
    }
}
