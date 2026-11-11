package com.radaeli.aeronauticsflybyinstruments.attitude;

import com.radaeli.aeronauticsflybyinstruments.integration.PeripheralBinding;
import com.radaeli.aeronauticsflybyinstruments.integration.PeripheralEvents;
import com.radaeli.aeronauticsflybyinstruments.avionics.AvionicsDataChannel;
import com.radaeli.aeronauticsflybyinstruments.avionics.AvionicsEndpoint;
import com.radaeli.aeronauticsflybyinstruments.avionics.AvionicsSignalProvider;
import com.radaeli.aeronauticsflybyinstruments.avionics.SensorReading;
import com.radaeli.aeronauticsflybyinstruments.block.ModBlocks;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * Samples the aircraft's attitude every server tick from the pose of its sublevel and publishes
 * {@code attitude.pitch}, {@code attitude.roll} and {@code attitude.heading} on the cable network.
 * Keeps the level calibration and the damping mode; the Nose and Up faces live in the blockstate.
 */
public final class AttitudeReferenceUnitBlockEntity extends BlockEntity implements AvionicsEndpoint, AvionicsSignalProvider {
    public static final String PITCH_CHANNEL = "attitude.pitch";
    public static final String ROLL_CHANNEL = "attitude.roll";
    public static final String HEADING_CHANNEL = "attitude.heading";
    public static final String TURN_RATE_CHANNEL = "attitude.turn_rate";
    public static final String VERTICAL_SPEED_CHANNEL = "attitude.vertical_speed";
    public static final String SLIP_CHANNEL = "attitude.slip";
    /** Change, in degrees, that makes the peripheral publish a new reading. */
    static final double PUBLISH_THRESHOLD = 0.5;
    private static final double TICKS_PER_SECOND = 20.0;
    /** Vanilla gravity (0.08 blocks per tick squared) in blocks per second squared. */
    private static final double GRAVITY = 32.0;
    /** Faster than this (blocks per second) the sublevel jumped instead of moving. */
    private static final double MAX_SPEED = 400.0;

    private final PeripheralBinding<PeripheralEvents.Attitude> peripheral =
            new PeripheralBinding<>(new PeripheralEvents.Attitude() {});
    private AttitudeReading reading = AttitudeReading.unavailable(0L);
    private double pitchOffset;
    private double rollOffset;
    private AttitudeDamping damping = AttitudeDamping.OFF;
    /** Unfiltered pitch and the roll last defined, both before the level offsets. */
    private double rawPitch;
    private double rawRoll;
    private double lastHeading;
    /** Damped values. The first sample after a reset is taken as-is. */
    private boolean primed;
    private double dampedPitch;
    private double dampedRoll;
    private double dampedHeading;
    /** Previous tick's position and velocity of the sublevel, for the rates (fase 4). */
    private boolean hasPrevious;
    private Vec3 previousPosition = Vec3.ZERO;
    private Vec3 previousVelocity = Vec3.ZERO;
    private double turnRate;
    private double verticalSpeed;
    private double slip;

    public AttitudeReferenceUnitBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.ATTITUDE_REFERENCE_UNIT_ENTITY.get(), pos, state);
    }

    public PeripheralBinding<PeripheralEvents.Attitude> peripheral() {
        return peripheral;
    }

    public AttitudeReading reading() {
        return reading;
    }

    public double pitchOffset() { return pitchOffset; }
    public double rollOffset() { return rollOffset; }
    public AttitudeDamping damping() { return damping; }
    public AttitudeOrientation orientation() { return AttitudeReferenceUnitBlock.orientation(getBlockState()); }

    /** False when the unit is not inside a sublevel: it then reports level flight on the ground. */
    public boolean onSubLevel() {
        return containingSubLevel(this) != null;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AttitudeReferenceUnitBlockEntity unit) {
        if (level.isClientSide) return;
        unit.update(level.getGameTime());
    }

    void update(long gameTime) {
        if (level == null || level.isClientSide) return;
        SubLevel host = containingSubLevel(this);
        if (host == null) {
            rawPitch = 0;
            rawRoll = 0;
            hasPrevious = false;
            turnRate = 0;
            verticalSpeed = 0;
            slip = 0;
            accept(AttitudeReading.Status.GROUND, 0, 0, 0, gameTime);
            return;
        }
        BlockState state = getBlockState();
        Vec3 forward = host.logicalPose().transformNormal(vector(state.getValue(AttitudeReferenceUnitBlock.NOSE))).normalize();
        Vec3 up = host.logicalPose().transformNormal(vector(state.getValue(AttitudeReferenceUnitBlock.UP))).normalize();
        double pitch = AttitudeMath.pitch(forward);
        OptionalDouble roll = AttitudeMath.roll(forward, up);
        OptionalDouble heading = AttitudeMath.heading(forward);
        boolean defined = AttitudeMath.rollDefined(pitch) && roll.isPresent() && heading.isPresent();
        double headingBefore = lastHeading;
        // Near the vertical, roll and heading are held at their last defined values.
        if (defined) {
            rawRoll = roll.orElseThrow();
            lastHeading = heading.orElseThrow();
        }
        rawPitch = pitch;
        updateRates(host, forward, up, defined, headingBefore);
        accept(defined ? AttitudeReading.Status.VALID : AttitudeReading.Status.DEGRADED,
                pitch - pitchOffset, AttitudeMath.wrapDegrees(rawRoll - rollOffset), lastHeading, gameTime);
    }

    /**
     * Rates from the change since the previous tick: turn rate from the heading, vertical speed from the
     * sublevel's height, and slip from the lateral specific force (the ball of a turn-and-slip indicator).
     * A jump of the sublevel (split, merge, teleport) restarts the history instead of reporting a spike.
     */
    private void updateRates(SubLevel host, Vec3 forward, Vec3 up, boolean defined, double headingBefore) {
        var position = host.logicalPose().position();
        Vec3 current = new Vec3(position.x(), position.y(), position.z());
        Vec3 velocity = hasPrevious ? current.subtract(previousPosition).scale(TICKS_PER_SECOND) : Vec3.ZERO;
        if (hasPrevious && velocity.lengthSqr() > MAX_SPEED * MAX_SPEED) hasPrevious = false;
        if (!hasPrevious) {
            velocity = Vec3.ZERO;
            turnRate = 0;
            verticalSpeed = 0;
            slip = 0;
        } else {
            Vec3 acceleration = velocity.subtract(previousVelocity).scale(TICKS_PER_SECOND);
            verticalSpeed = velocity.y;
            turnRate = defined ? AttitudeMath.shortestDelta(headingBefore, lastHeading) * TICKS_PER_SECOND : 0;
            slip = slip(forward, up, acceleration);
        }
        previousPosition = current;
        previousVelocity = velocity;
        hasPrevious = true;
    }

    /**
     * Sideways specific force over the vertical one, in degrees (the sensor reading, acceleration minus
     * gravity). Positive when the force points to the aircraft's right, which puts the ball on the left wing;
     * a level, unaccelerated aircraft reads 0.
     */
    static double slip(Vec3 forward, Vec3 up, Vec3 acceleration) {
        Vec3 specific = acceleration.add(0, GRAVITY, 0);
        Vec3 right = forward.cross(up);
        if (right.lengthSqr() < 1.0E-8) return 0;
        right = right.normalize();
        double lateral = specific.dot(right);
        double vertical = specific.dot(up);
        if (Math.abs(lateral) < 1.0E-9 && Math.abs(vertical) < 1.0E-9) return 0;
        return Math.toDegrees(Math.atan2(lateral, vertical));
    }

    private void accept(AttitudeReading.Status status, double pitch, double roll, double heading, long gameTime) {
        if (!primed || damping == AttitudeDamping.OFF) {
            dampedPitch = pitch;
            dampedRoll = roll;
            dampedHeading = heading;
            primed = true;
        } else {
            double alpha = damping.alpha();
            dampedPitch += alpha * (pitch - dampedPitch);
            dampedRoll = AttitudeMath.wrapDegrees(dampedRoll + alpha * AttitudeMath.shortestDelta(dampedRoll, roll));
            dampedHeading = AttitudeMath.wrapHeading(dampedHeading + alpha * AttitudeMath.shortestDelta(dampedHeading, heading));
        }
        AttitudeReading updated = new AttitudeReading(status, dampedPitch, dampedRoll, dampedHeading,
                turnRate, verticalSpeed, slip, gameTime);
        AttitudeReading previous = reading;
        reading = updated;
        if (changed(previous, updated)) peripheral.events().publishReading(updated);
    }

    private static boolean changed(AttitudeReading previous, AttitudeReading updated) {
        if (previous.status() != updated.status()) return true;
        return Math.abs(previous.pitch() - updated.pitch()) >= PUBLISH_THRESHOLD
                || Math.abs(AttitudeMath.shortestDelta(previous.roll(), updated.roll())) >= PUBLISH_THRESHOLD
                || Math.abs(AttitudeMath.shortestDelta(previous.heading(), updated.heading())) >= PUBLISH_THRESHOLD;
    }

    /**
     * Records the current level as 0 degrees pitch and roll. Refused unless the reading is valid and
     * both axes are within {@link AttitudeMath#CALIBRATION_LIMIT}, so a unit mounted on a tilted hull
     * cannot hide a real attitude.
     */
    public boolean calibrateLevel() {
        if (reading.status() != AttitudeReading.Status.VALID) return false;
        if (Math.abs(rawPitch) > AttitudeMath.CALIBRATION_LIMIT
                || Math.abs(rawRoll) > AttitudeMath.CALIBRATION_LIMIT) return false;
        pitchOffset = rawPitch;
        rollOffset = rawRoll;
        primed = false;
        setChanged();
        return true;
    }

    public void resetCalibration() {
        pitchOffset = 0;
        rollOffset = 0;
        primed = false;
        setChanged();
    }

    public void setDamping(AttitudeDamping mode) {
        if (mode == damping) return;
        damping = mode;
        primed = false;
        setChanged();
    }

    /** Moves the Nose and Up faces. Never produces an invalid pair (see {@link AttitudeOrientation}). */
    public void setOrientation(AttitudeOrientation next) {
        if (level == null) return;
        BlockState state = getBlockState();
        BlockState updated = AttitudeReferenceUnitBlock.withOrientation(state, next);
        if (updated == state) return;
        level.setBlock(worldPosition, updated, Block.UPDATE_ALL);
        primed = false;
    }

    @Override
    public String avionicsType() {
        return "attitude_reference_unit";
    }

    @Override
    public List<AvionicsDataChannel<?>> avionicsSignals() {
        return List.of(channel(PITCH_CHANNEL, reading.pitch(), "degrees"),
                channel(ROLL_CHANNEL, reading.roll(), "degrees"),
                channel(HEADING_CHANNEL, reading.heading(), "degrees"),
                channel(TURN_RATE_CHANNEL, reading.turnRate(), "degrees/s"),
                channel(VERTICAL_SPEED_CHANNEL, reading.verticalSpeed(), "blocks/s"),
                channel(SLIP_CHANNEL, reading.slip(), "degrees"));
    }

    private AvionicsDataChannel<Double> channel(String name, double value, String unit) {
        SensorReading<Double> sample = reading.hasValues()
                ? SensorReading.valid(value, unit, reading.gameTime())
                : SensorReading.unavailable(unit, reading.gameTime());
        return new AvionicsDataChannel<>(name, sample, Map.of("status", reading.status().lowerName()));
    }

    static Vec3 vector(Direction direction) {
        return new Vec3(direction.getStepX(), direction.getStepY(), direction.getStepZ());
    }

    private static SubLevel containingSubLevel(BlockEntity entity) {
        var containing = Sable.HELPER.getContaining(entity);
        return containing instanceof SubLevel subLevel ? subLevel : null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putDouble("PitchOffset", pitchOffset);
        tag.putDouble("RollOffset", rollOffset);
        tag.putString("Damping", damping.id());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        pitchOffset = clampCalibration(tag.getDouble("PitchOffset"));
        rollOffset = clampCalibration(tag.getDouble("RollOffset"));
        damping = AttitudeDamping.parse(tag.getString("Damping")).orElse(AttitudeDamping.OFF);
    }

    private static double clampCalibration(double degrees) {
        return Math.clamp(degrees, -AttitudeMath.CALIBRATION_LIMIT, AttitudeMath.CALIBRATION_LIMIT);
    }
}
