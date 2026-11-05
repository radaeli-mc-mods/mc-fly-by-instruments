package com.radaeli.aeronauticsflybyinstruments.attitude;

import com.mojang.serialization.MapCodec;
import com.radaeli.aeronauticsflybyinstruments.block.AvionicsWrenchable;
import com.radaeli.aeronauticsflybyinstruments.block.CableSocketProvider;
import com.radaeli.aeronauticsflybyinstruments.block.ModBlocks;
import com.radaeli.aeronauticsflybyinstruments.network.AttitudeReferenceNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Attitude Reference Unit: the aircraft's pitch, roll and heading, taken from the pose of its
 * sublevel. Its two faces Nose and Up define the aircraft frame in any mounting; the blockstate keeps
 * them, so they survive schematics, contraptions and item use. Model: docs/attitude-reference-unit-plan.md.
 */
public final class AttitudeReferenceUnitBlock extends BaseEntityBlock implements AvionicsWrenchable, CableSocketProvider {
    public static final MapCodec<AttitudeReferenceUnitBlock> CODEC = simpleCodec(AttitudeReferenceUnitBlock::new);
    public static final DirectionProperty NOSE = DirectionProperty.create("nose");
    public static final DirectionProperty UP = DirectionProperty.create("up");
    /** Lights the slit on the neutral faces while the unit drives a redstone output (guide §8.5). */
    public static final BooleanProperty POWERED = BooleanProperty.create("powered");

    // Same geometry as blockbench/attitude-reference-unit: a 14-unit andesite body plus the brass rims.
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(1, 1, 1, 15, 15, 15),
            Block.box(0, 0, 0, 16, 1, 2), Block.box(0, 0, 14, 16, 1, 16),
            Block.box(0, 0, 2, 2, 1, 14), Block.box(14, 0, 2, 16, 1, 14),
            Block.box(0, 15, 0, 16, 16, 2), Block.box(0, 15, 14, 16, 16, 16),
            Block.box(0, 15, 2, 2, 16, 14), Block.box(14, 15, 2, 16, 16, 14)).optimize();

    public AttitudeReferenceUnitBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(NOSE, Direction.NORTH).setValue(UP, Direction.UP).setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NOSE, UP, POWERED);
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

    /** Socket at the centre of every face (guide §8.7): the cable lands in the same place on any face. */
    @Override
    public Vec3 cableSocket(BlockState state, Direction face) {
        return (switch (face) {
            case NORTH -> new Vec3(8, 8, 1);
            case SOUTH -> new Vec3(8, 8, 15);
            case WEST -> new Vec3(1, 8, 8);
            case EAST -> new Vec3(15, 8, 8);
            case DOWN -> new Vec3(8, 1, 8);
            case UP -> new Vec3(8, 15, 8);
        }).scale(1.0 / 16);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlocks.ATTITUDE_REFERENCE_UNIT_ENTITY.get(),
                AttitudeReferenceUnitBlockEntity::serverTick);
    }
}
