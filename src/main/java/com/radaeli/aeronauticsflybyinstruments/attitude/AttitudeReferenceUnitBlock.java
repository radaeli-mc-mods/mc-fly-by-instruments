package com.radaeli.aeronauticsflybyinstruments.attitude;

import com.mojang.serialization.MapCodec;
import com.radaeli.aeronauticsflybyinstruments.block.AvionicsWrenchable;
import com.radaeli.aeronauticsflybyinstruments.block.CableSocketProvider;
import com.radaeli.aeronauticsflybyinstruments.block.ModBlocks;
import com.radaeli.aeronauticsflybyinstruments.network.AttitudeReferenceNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Attitude Reference Unit: the aircraft's pitch, roll and heading, taken from the pose of its
 * sublevel. Its two faces Nose and Up define the aircraft frame in any mounting; the blockstate keeps
 * them, so they survive schematics, contraptions and item use. Its model is a glass cube with a model
 * airplane inside: its nose points at the Nose face and its tail fin at the Up face. The socket cores are a
 * status light ({@link #STATUS}). Model: docs/attitude-reference-unit-plan.md.
 */
public final class AttitudeReferenceUnitBlock extends BaseEntityBlock implements AvionicsWrenchable, CableSocketProvider {
    public static final MapCodec<AttitudeReferenceUnitBlock> CODEC = simpleCodec(AttitudeReferenceUnitBlock::new);
    public static final DirectionProperty NOSE = DirectionProperty.create("nose");
    public static final DirectionProperty UP = DirectionProperty.create("up");
    /** Socket status light only (same geometry): follows the unit's {@link AttitudeReading.Status}. */
    public static final EnumProperty<Status> STATUS = EnumProperty.create("status", Status.class);

    public enum Status implements StringRepresentable {
        OK, DEGRADED, INOP;

        public static Status of(AttitudeReading.Status status) {
            return switch (status) {
                case VALID, GROUND -> OK;
                case DEGRADED -> DEGRADED;
                case UNAVAILABLE -> INOP;
            };
        }

        @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    }

    // Same geometry as blockbench/attitude-reference-unit: the glass cube, four corner posts and the six sockets
    // (the airplane is inside the glass).
    public static final VoxelShape SHAPE = Shapes.or(
            Block.box(1, 1, 1, 15, 15, 15),
            Block.box(0, 0, 0, 2, 16, 2), Block.box(14, 0, 0, 16, 16, 2),
            Block.box(0, 0, 14, 2, 16, 16), Block.box(14, 0, 14, 16, 16, 16),
            Block.box(5, 5, 0, 11, 11, 2), Block.box(5, 5, 14, 11, 11, 16),
            Block.box(0, 5, 5, 2, 11, 11), Block.box(14, 5, 5, 16, 11, 11),
            Block.box(5, 0, 5, 11, 2, 11), Block.box(5, 14, 5, 11, 16, 11)).optimize();

    public AttitudeReferenceUnitBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(NOSE, Direction.NORTH).setValue(UP, Direction.UP).setValue(STATUS, Status.OK));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NOSE, UP, STATUS);
    }

    /** The Nose faces where the player looks; Up is the block's own up. */
    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(NOSE, context.getHorizontalDirection()).setValue(UP, Direction.UP);
    }

    public static AttitudeOrientation orientation(BlockState state) {
        return new AttitudeOrientation(state.getValue(NOSE), state.getValue(UP));
    }

    public static BlockState withOrientation(BlockState state, AttitudeOrientation orientation) {
        return state.setValue(NOSE, orientation.nose()).setValue(UP, orientation.up());
    }

    /** Wrench on a face: that face becomes the Nose (see {@link AttitudeOrientation#withNose}). */
    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        if (!level.isClientSide) {
            var next = withOrientation(state, orientation(state).withNose(context.getClickedFace()));
            if (next != state) level.setBlock(context.getClickedPos(), next, Block.UPDATE_ALL);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof AttitudeReferenceUnitBlockEntity unit) {
            AttitudeReferenceNetwork.open(serverPlayer, unit);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return withOrientation(state, orientation(state).rotated(rotation));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return withOrientation(state, orientation(state).mirrored(mirror));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AttitudeReferenceUnitBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /**
     * Socket at the centre of every face, on the face plane: the connector seats on the outer face of the 6x6x2
     * socket block, so its 2-unit stem stays buried in it and never crosses the glass.
     */
    @Override
    public Vec3 cableSocket(BlockState state, Direction face) {
        return new Vec3(.5 + .5 * face.getStepX(), .5 + .5 * face.getStepY(), .5 + .5 * face.getStepZ());
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlocks.ATTITUDE_REFERENCE_UNIT_ENTITY.get(),
                AttitudeReferenceUnitBlockEntity::serverTick);
    }
}
