package com.radaeli.aeronauticsflybyinstruments.sensor;

import com.radaeli.aeronauticsflybyinstruments.integration.PeripheralBinding;
import com.radaeli.aeronauticsflybyinstruments.integration.PeripheralEvents;
import com.mojang.logging.LogUtils;
import com.radaeli.aeronauticsflybyinstruments.avionics.AvionicsEndpoint;
import com.radaeli.aeronauticsflybyinstruments.avionics.ReadingStatus;
import com.radaeli.aeronauticsflybyinstruments.avionics.SensorReading;
import com.radaeli.aeronauticsflybyinstruments.block.ModBlocks;
import com.radaeli.aeronauticsflybyinstruments.config.ModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import java.util.Map;
import org.slf4j.Logger;

/** Periodically samples clearance beneath its lower face and exposes the reading to CC:Tweaked. */
public final class RadarAltimeterBlockEntity extends BlockEntity implements AvionicsEndpoint {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final PeripheralBinding<PeripheralEvents.Radar> peripheral =
            new PeripheralBinding<>(new PeripheralEvents.Radar() {});
    private SensorReading<Double> reading = SensorReading.unavailable("blocks", 0L);
    private boolean loggedScanFailure;
    private final SensorOutputController outputs = new SensorOutputController(this, true, false,
            128);

    public RadarAltimeterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.RADAR_ALTIMETER_ENTITY.get(), pos, state);
    }

    public PeripheralBinding<PeripheralEvents.Radar> peripheral() {
        return peripheral;
    }

    public SensorReading<Double> reading() {
        return reading;
    }

    public SensorOutputConfig outputConfig() { return outputs.config(); }
    public int outputPower(net.minecraft.core.Direction face) { return outputs.weakPower(face); }
    public Map<SensorOutputConfig.Channel, Integer> outputLevels() { return outputs.outputs(); }
    public boolean applyOutputConfig(SensorOutputConfig config) {
        boolean changed = outputs.apply(config, ModConfig.RADAR_ALTIMETER_MAX_RANGE.get());
        if (changed) { recalculateOutput(); peripheral.events().publishOutputConfiguration(); }
        return changed;
    }
    public boolean validOutputConfig(SensorOutputConfig config) {
        return outputs.valid(config, ModConfig.RADAR_ALTIMETER_MAX_RANGE.get());
    }
    boolean setOutputRange(SensorOutputConfig.Channel channel, int min, int max) {
        boolean changed = outputs.setRange(channel, min, max, ModConfig.RADAR_ALTIMETER_MAX_RANGE.get());
        if (changed) { recalculateOutput(); peripheral.events().publishOutputConfiguration(); }
        return changed;
    }
    boolean setOutputMode(String mode) {
        boolean changed = outputs.setMode(mode, ModConfig.RADAR_ALTIMETER_MAX_RANGE.get());
        if (changed) { recalculateOutput(); peripheral.events().publishOutputConfiguration(); }
        return changed;
    }
    boolean setLinkFrequencies(SensorOutputConfig.Channel channel, ItemStack first, ItemStack second) {
        boolean changed = outputs.setFrequencies(channel, first, second, ModConfig.RADAR_ALTIMETER_MAX_RANGE.get());
        if (changed) { recalculateOutput(); peripheral.events().publishOutputConfiguration(); }
        return changed;
    }

    @Override
    public String avionicsType() {
        return "radar_altimeter";
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RadarAltimeterBlockEntity sensor) {
        if (level.isClientSide) return;
        int interval = ModConfig.SENSOR_SCAN_INTERVAL_TICKS.get();
        long offset = Math.floorMod(pos.asLong(), (long) interval);
        if (Math.floorMod(level.getGameTime(), interval) != offset) return;
        sensor.scan();
    }

    private void scan() {
        if (level == null || level.isClientSide) return;
        try {
            acceptReading(RadarAltimeterScanner.scan(level, this, ModConfig.RADAR_ALTIMETER_MAX_RANGE.get()));
        } catch (RuntimeException exception) {
            acceptReading(SensorReading.failed("blocks", level.getGameTime()));
            if (!loggedScanFailure) {
                LOGGER.warn("Radar Altimeter scan failed at {}", worldPosition, exception);
                loggedScanFailure = true;
            }
        }
    }

    void acceptReading(SensorReading<Double> updated) {
        SensorReading<Double> previous = reading;
        reading = updated;
        if (outputs.acceptAltitude(new SensorOutputLogic.SensorReadingView(updated.status(),
                updated.value().orElse(null)))) peripheral.events().publishOutputs();
        if (changed(previous, updated)) peripheral.events().publishReading(updated);
    }

    private void recalculateOutput() {
        if (outputs.acceptAltitude(new SensorOutputLogic.SensorReadingView(reading.status(), reading.value().orElse(null)))) {
            peripheral.events().publishOutputs();
        }
    }

    @Override public void onLoad() { super.onLoad(); outputs.onLoad(); }
    public void onChunkUnloaded() { outputs.onChunkUnloaded(); }
    @Override public void setRemoved() { outputs.onRemoved(); super.setRemoved(); }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("OutputConfig", outputs.config().save(registries));
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        SensorOutputConfig loaded = tag.contains("OutputConfig", Tag.TAG_COMPOUND)
                ? SensorOutputConfig.load(tag.getCompound("OutputConfig"), registries, false, true,
                    configuredMaximumRange())
                : new SensorOutputConfig(false, true);
        outputs.apply(loaded, configuredMaximumRange());
    }

    private static int configuredMaximumRange() {
        try { return ModConfig.RADAR_ALTIMETER_MAX_RANGE.get(); }
        catch (IllegalStateException ignored) { return 128; }
    }

    private static boolean changed(SensorReading<Double> previous, SensorReading<Double> updated) {
        if (previous.status() != updated.status()) return true;
        if (updated.status() != ReadingStatus.VALID) return false;
        return Double.compare(previous.value().orElseThrow(), updated.value().orElseThrow()) != 0;
    }
}
