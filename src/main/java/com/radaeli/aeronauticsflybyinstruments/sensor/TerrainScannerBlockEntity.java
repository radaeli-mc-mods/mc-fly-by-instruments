package com.radaeli.aeronauticsflybyinstruments.sensor;

import com.radaeli.aeronauticsflybyinstruments.integration.PeripheralBinding;
import com.radaeli.aeronauticsflybyinstruments.integration.PeripheralEvents;
import com.mojang.logging.LogUtils;
import com.radaeli.aeronauticsflybyinstruments.avionics.AvionicsEndpoint;
import com.radaeli.aeronauticsflybyinstruments.avionics.ReadingStatus;
import com.radaeli.aeronauticsflybyinstruments.avionics.ScannerSide;
import com.radaeli.aeronauticsflybyinstruments.block.ModBlocks;
import com.radaeli.aeronauticsflybyinstruments.config.ModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.Tag;
import org.slf4j.Logger;

/** Persisted settings, sampled scan snapshot, and independent CC peripherals for both scanner variants. */
public final class TerrainScannerBlockEntity extends BlockEntity implements AvionicsEndpoint {
    private static final Logger LOGGER = LogUtils.getLogger();
    private int openingAngle = TerrainScanGeometry.DEFAULT_ADVANCED_APERTURE_DEGREES;
    /** Side of the aircraft this scanner looks at, chosen by the player; the computer reads it. */
    private ScannerSide side = ScannerSide.UNSET;
    private TerrainScanSnapshot reading = TerrainScanSnapshot.unavailable(0, 0L);
    private boolean loggedScanFailure;
    private final PeripheralBinding<PeripheralEvents.Terrain> peripheral =
            new PeripheralBinding<>(new PeripheralEvents.Terrain() {});
    private final SensorOutputController outputs;

    public TerrainScannerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.TERRAIN_SCANNER_ENTITY.get(), pos, state);
        outputs = new SensorOutputController(this, false, state.is(ModBlocks.TERRAIN_SCANNER_ADVANCED.get()),
                128);
    }

    public TerrainScanSnapshot reading() { return reading; }
    public int openingAngle() { return openingAngle; }
    public ScannerSide side() { return side; }

    public boolean setSide(ScannerSide updated) {
        if (updated == null || updated == side) return false;
        side = updated;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
        return true;
    }
    public boolean advanced() { return getBlockState().is(ModBlocks.TERRAIN_SCANNER_ADVANCED.get()); }
    public SensorOutputConfig outputConfig() { return outputs.config(); }
    public int outputPower(net.minecraft.core.Direction face) { return outputs.weakPower(face); }
    public java.util.Map<SensorOutputConfig.Channel, Integer> outputLevels() { return outputs.outputs(); }
    public boolean applyOutputConfig(SensorOutputConfig config) {
        boolean changed = outputs.apply(config, ModConfig.TERRAIN_SCANNER_MAX_RANGE.get());
        if (changed) { recalculateOutputs(); peripheral.events().publishOutputConfiguration(); }
        return changed;
    }
    public boolean validOutputConfig(SensorOutputConfig config) {
        return outputs.valid(config, ModConfig.TERRAIN_SCANNER_MAX_RANGE.get());
    }
    boolean setOutputRange(SensorOutputConfig.Channel channel, int min, int max) {
        boolean changed = outputs.setRange(channel, min, max, ModConfig.TERRAIN_SCANNER_MAX_RANGE.get());
        if (changed) { recalculateOutputs(); peripheral.events().publishOutputConfiguration(); }
        return changed;
    }
    boolean setOutputMode(String mode) {
        boolean changed = outputs.setMode(mode, ModConfig.TERRAIN_SCANNER_MAX_RANGE.get());
        if (changed) { recalculateOutputs(); peripheral.events().publishOutputConfiguration(); }
        return changed;
    }
    boolean setLinkFrequencies(SensorOutputConfig.Channel channel, ItemStack first, ItemStack second) {
        boolean changed = outputs.setFrequencies(channel, first, second, ModConfig.TERRAIN_SCANNER_MAX_RANGE.get());
        if (changed) { recalculateOutputs(); peripheral.events().publishOutputConfiguration(); }
        return changed;
    }

    public PeripheralBinding<PeripheralEvents.Terrain> peripheral() {
        return peripheral;
    }

    @Override public String avionicsType() {
        return advanced() ? "terrain_scanner_advanced" : "terrain_scanner";
    }

    public boolean setOpeningAngle(int degrees) {
        if (!advanced() || degrees < TerrainScanGeometry.MIN_APERTURE_DEGREES
                || degrees > TerrainScanGeometry.MAX_APERTURE_DEGREES || degrees == openingAngle) return false;
        openingAngle = degrees;
        if (outputs.acceptTerrain(reading, openingAngle)) peripheral.events().publishOutputs();
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            peripheral.events().publishConfiguration(degrees);
        }
        return true;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TerrainScannerBlockEntity scanner) {
        if (level.isClientSide) return;
        int interval = ModConfig.SENSOR_SCAN_INTERVAL_TICKS.get();
        long offset = Math.floorMod(pos.asLong(), (long) interval);
        if (Math.floorMod(level.getGameTime(), interval) != offset) return;
        scanner.scan();
    }

    private void scan() {
        if (level == null || level.isClientSide) return;
        int maxRange = ModConfig.TERRAIN_SCANNER_MAX_RANGE.get();
        int totalRays = advanced() ? TerrainScanGeometry.advancedRayCount(openingAngle)
                : TerrainScanGeometry.SIMPLE_RAY_COUNT;
        try {
            acceptReading(TerrainScannerScanner.scan(level, this, maxRange, advanced(), openingAngle));
        } catch (RuntimeException exception) {
            acceptReading(TerrainScanSnapshot.failed(totalRays, level.getGameTime()));
            if (!loggedScanFailure) {
                LOGGER.warn("Terrain Scanner scan failed at {}", worldPosition, exception);
                loggedScanFailure = true;
            }
        }
    }

    void acceptReading(TerrainScanSnapshot updated) {
        TerrainScanSnapshot previous = reading;
        reading = updated;
        if (TerrainScanSnapshot.meaningfullyChanged(previous, updated)) {
            peripheral.events().publishScan(updated);
        }
        if (outputs.acceptTerrain(updated, openingAngle)) peripheral.events().publishOutputs();
    }

    private void recalculateOutputs() {
        if (outputs.acceptTerrain(reading, openingAngle)) peripheral.events().publishOutputs();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("OpeningAngle", openingAngle);
        tag.putString("AircraftSide", side.id());
        tag.put("OutputConfig", outputs.config().save(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        int loadedAngle = tag.contains("OpeningAngle", Tag.TAG_INT)
                ? tag.getInt("OpeningAngle") : TerrainScanGeometry.DEFAULT_ADVANCED_APERTURE_DEGREES;
        openingAngle = loadedAngle >= TerrainScanGeometry.MIN_APERTURE_DEGREES
                && loadedAngle <= TerrainScanGeometry.MAX_APERTURE_DEGREES
                ? loadedAngle : TerrainScanGeometry.DEFAULT_ADVANCED_APERTURE_DEGREES;
        side = ScannerSide.parse(tag.getString("AircraftSide")).orElse(ScannerSide.UNSET);
        SensorOutputConfig config = tag.contains("OutputConfig", Tag.TAG_COMPOUND)
                ? SensorOutputConfig.load(tag.getCompound("OutputConfig"), registries, advanced(), false,
                    configuredMaximumRange())
                : new SensorOutputConfig(advanced(), false);
        outputs.apply(config, configuredMaximumRange());
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private static int configuredMaximumRange() {
        try { return ModConfig.TERRAIN_SCANNER_MAX_RANGE.get(); }
        catch (IllegalStateException ignored) { return 128; }
    }

    @Override public void onLoad() { super.onLoad(); outputs.onLoad(); }
    public void onChunkUnloaded() { outputs.onChunkUnloaded(); }
    @Override public void setRemoved() { outputs.onRemoved(); super.setRemoved(); }
}
