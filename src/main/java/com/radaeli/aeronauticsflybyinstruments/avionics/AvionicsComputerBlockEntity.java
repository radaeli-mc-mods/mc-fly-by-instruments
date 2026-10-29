package com.radaeli.aeronauticsflybyinstruments.avionics;

import com.mojang.logging.LogUtils;
import com.radaeli.aeronauticsflybyinstruments.block.AvionicsComputerBlock;
import com.radaeli.aeronauticsflybyinstruments.block.ModBlocks;
import com.radaeli.aeronauticsflybyinstruments.config.ModConfig;
import com.radaeli.aeronauticsflybyinstruments.panel.PanelAlertLogic;
import com.radaeli.aeronauticsflybyinstruments.sensor.AbstractTerrainScannerBlock;
import com.radaeli.aeronauticsflybyinstruments.sensor.RadarAltimeterBlockEntity;
import com.radaeli.aeronauticsflybyinstruments.sensor.TerrainScanContact;
import com.radaeli.aeronauticsflybyinstruments.sensor.TerrainScanGeometry;
import com.radaeli.aeronauticsflybyinstruments.sensor.TerrainScanSnapshot;
import com.radaeli.aeronauticsflybyinstruments.sensor.TerrainScannerBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Avionics Computer: the aircraft's single avionics hub. It reads every sensor on the cable,
 * assigns them roles, runs the GPWS function on their cached snapshots (no scans of its own) and is
 * the CC:Tweaked gateway for the whole network. Always active while placed: there is no arming.
 */
public final class AvionicsComputerBlockEntity extends BlockEntity implements AvionicsEndpoint, AvionicsSignalProvider {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Self-test length: every function and the screen show WARNING for three seconds. */
    public static final int TEST_TICKS = 60;

    private GpwsConfiguration configuration = new GpwsConfiguration();
    private final GpwsEngine engine = new GpwsEngine();
    private final AvionicsComputerPeripheral peripheral = new AvionicsComputerPeripheral(this);
    private GpwsSample latestSample = emptySample(0, NetworkStatus.DISCONNECTED);
    private GpwsEngine.Evaluation latestEvaluation = initialEvaluation();
    private List<AvionicsSensor> sensors = List.of();
    private long testUntil;
    private boolean loggedEvaluationFailure;

    public AvionicsComputerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.AVIONICS_COMPUTER_ENTITY.get(), pos, state);
    }

    @Override public String avionicsType() { return "avionics_computer"; }
    public AvionicsComputerPeripheral peripheral() { return peripheral; }
    public GpwsConfiguration configuration() { return configuration; }
    public GpwsEngine.Evaluation evaluation() { return latestEvaluation; }
    public GpwsSample latestSample() { return latestSample; }
    public List<AvionicsSensor> sensors() { return sensors; }

    static boolean isScanner(String type) {
        return type.equals("terrain_scanner") || type.equals("terrain_scanner_advanced");
    }

    public boolean testing() {
        return level != null && testUntil > level.getGameTime();
    }

    public AvionicsComputerStatus status() {
        return AvionicsComputerStatus.of(latestEvaluation.alerts(), latestSample.networkStatus(), testing());
    }

    @Override
    public List<AvionicsDataChannel<?>> avionicsSignals() {
        List<AvionicsDataChannel<?>> result = new ArrayList<>();
        int maximum = 0;
        boolean anyUsable = false;
        for (AlertChannel channel : AlertChannel.values()) {
            AlertReading reading = latestEvaluation.alerts().get(channel);
            int rank = PanelAlertLogic.severity(reading);
            maximum = Math.max(maximum, rank);
            boolean usable = reading != null && reading.state() != AlertState.UNAVAILABLE;
            anyUsable |= usable;
            var value = !usable
                    ? SensorReading.<Integer>unavailable("severity", latestSample.gameTime())
                    : SensorReading.valid(rank, "severity", reading.gameTime());
            result.add(new AvionicsDataChannel<>("gpws." + channel.id(), value));
        }
        result.add(new AvionicsDataChannel<>("gpws.any", anyUsable
                ? SensorReading.valid(maximum, "severity", latestSample.gameTime())
                : SensorReading.unavailable("severity", latestSample.gameTime())));
        return List.copyOf(result);
    }

    public Map<String, Object> statusSnapshot() {
        AvionicsComputerStatus status = status();
        Map<String, Object> result = new HashMap<>();
        result.put("state", status.level().id());
        result.put("alert", status.channelId());
        result.put("testing", testing());
        result.put("sensitivity", lower(configuration.sensitivity()));
        result.put("flightPhase", lower(latestEvaluation.phase()));
        result.put("networkStatus", lower(latestSample.networkStatus()));
        result.put("networkComplete", latestSample.networkStatus() == NetworkStatus.CONNECTED);
        result.put("endpointCount", latestSample.endpointCount());
        result.put("sensorCount", sensors.size());
        result.put("gameTime", latestSample.gameTime());
        result.put("clearance", readingSnapshot(latestSample.clearance(), latestSample.altimeterFault(),
                latestSample.clearanceGameTime(), "blocks"));
        result.put("verticalSpeed", readingSnapshot(latestEvaluation.verticalSpeed(),
                false, latestSample.gameTime(), "blocks_per_second"));
        result.put("bankAngle", readingSnapshot(latestSample.bankAngleDegrees(), false,
                latestSample.gameTime(), "degrees"));
        return Map.copyOf(result);
    }

    private static Map<String, Object> readingSnapshot(OptionalDouble value, boolean failed, long gameTime,
                                                        String unit) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", value.isPresent() ? "valid" : failed ? "failed" : "unavailable");
        value.ifPresent(reading -> result.put("value", reading));
        result.put("unit", unit);
        result.put("gameTime", gameTime);
        return Map.copyOf(result);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AvionicsComputerBlockEntity computer) {
        if (level.isClientSide) return;
        if (computer.testUntil != 0 && level.getGameTime() >= computer.testUntil) {
            computer.testUntil = 0;
            computer.evaluate();
            computer.peripheral.publishStatusChanged();
        } else {
            int interval = ModConfig.SENSOR_SCAN_INTERVAL_TICKS.get();
            long offset = Math.floorMod(pos.asLong(), (long) interval);
            if (Math.floorMod(level.getGameTime(), interval) == offset) computer.evaluate();
        }
        // The front screen follows the overall state (blockstate swaps only the screen texture).
        var screen = AvionicsComputerBlock.Screen.of(computer.status().level());
        if (state.getValue(AvionicsComputerBlock.SCREEN) != screen) {
            level.setBlock(pos, state.setValue(AvionicsComputerBlock.SCREEN, screen), Block.UPDATE_CLIENTS);
        }
    }

    /** Press-to-test, from the screen, the block's button or Lua. Returns false while a test runs. */
    public boolean startTest() {
        if (level == null || level.isClientSide || testing()) return false;
        testUntil = level.getGameTime() + TEST_TICKS;
        evaluate();
        peripheral.publishStatusChanged();
        return true;
    }

    private void evaluate() {
        if (level == null || level.isClientSide) return;
        GpwsEngine.Evaluation previousEvaluation = latestEvaluation;
        List<AvionicsSensor> previousSensors = sensors;
        AvionicsComputerStatus previousStatus = status();
        int interval = ModConfig.SENSOR_SCAN_INTERVAL_TICKS.get();
        try {
            List<AvionicsSensor> gathered = new ArrayList<>();
            latestSample = gatherSample(gathered);
            sensors = List.copyOf(gathered);
            latestEvaluation = engine.evaluate(latestSample, configuration, testing(), interval);
            loggedEvaluationFailure = false;
        } catch (RuntimeException exception) {
            latestSample = emptySample(level.getGameTime(), NetworkStatus.INCOMPLETE);
            sensors = List.of();
            latestEvaluation = engine.evaluate(latestSample, configuration, testing(), interval);
            if (!loggedEvaluationFailure) {
                LOGGER.warn("Avionics Computer evaluation failed at {}", worldPosition, exception);
                loggedEvaluationFailure = true;
            }
        }
        publishEvaluationChanges(previousEvaluation, latestEvaluation);
        if (sensorsChanged(previousSensors, sensors)) peripheral.publishSensorsChanged();
        if (!previousStatus.equals(status())) peripheral.publishStatusChanged();
    }

    static boolean sensorsChanged(List<AvionicsSensor> previous, List<AvionicsSensor> updated) {
        if (previous.size() != updated.size()) return true;
        for (int index = 0; index < previous.size(); index++) {
            if (!previous.get(index).sameIdentityAndHealth(updated.get(index))) return true;
        }
        return false;
    }

    private void publishEvaluationChanges(GpwsEngine.Evaluation previous, GpwsEngine.Evaluation updated) {
        EnumMap<AlertChannel, AlertReading> changed = new EnumMap<>(AlertChannel.class);
        for (AlertChannel channel : AlertChannel.values()) {
            AlertReading oldReading = previous.alerts().get(channel);
            AlertReading newReading = updated.alerts().get(channel);
            if (AvionicsComputerPeripheral.alertStateChanged(oldReading, newReading)) changed.put(channel, newReading);
        }
        if (!changed.isEmpty()) peripheral.publishAlertsChanged(changed, updated.reasons());
    }

    private GpwsSample gatherSample(List<AvionicsSensor> found) {
        long now = level.getGameTime();
        int interval = ModConfig.SENSOR_SCAN_INTERVAL_TICKS.get();
        long maxAge = interval * 2L;
        NetworkSnapshot network = AvionicsNetworkManager.snapshot(level, worldPosition);
        if (network.status() != NetworkStatus.CONNECTED) {
            return new GpwsSample(now, network.status(), network.endpointCount(),
                    0, false, OptionalDouble.empty(), now, 0, false, false, false, List.of(),
                    bankAngle(getBlockState().getValue(AvionicsComputerBlock.FACING)));
        }

        int altimeterCount = 0;
        boolean altimeterFault = false;
        Double minimumClearance = null;
        long minimumTime = now;
        int primaryAltimeter = -1;
        int scannerCount = 0;
        boolean scannerFault = false;
        boolean allScannerCoverageComplete = true;
        boolean pullUpCoverageComplete = false;
        List<GpwsSample.TerrainContact> contacts = new ArrayList<>();
        SubLevel host = containingSubLevel(this);
        Optional<Direction> nose = noseDirection(network, host);
        Vec3 forward = nose.map(direction -> worldDirection(this, direction)).orElse(null);

        for (NetworkDevice device : network.devices()) {
            boolean altimeter = device.type().equals("radar_altimeter");
            boolean scanner = isScanner(device.type());
            if (!altimeter && !scanner) continue;
            BlockPos sourcePos = worldPosition.offset(device.x(), device.y(), device.z());
            String unit = "blocks";
            String fallbackRole = altimeter ? "secondary" : "auxiliary";
            if (!level.hasChunkAt(sourcePos)) {
                if (altimeter) altimeterFault = true;
                else { scannerFault = true; allScannerCoverageComplete = false; }
                found.add(sensor(device, fallbackRole, AvionicsSensor.UNLOADED,
                        OptionalDouble.empty(), unit, AvionicsSensor.ISSUE_NONE));
                continue;
            }
            BlockEntity source = level.getBlockEntity(sourcePos);
            if (altimeter) {
                altimeterCount++;
                if (!(source instanceof RadarAltimeterBlockEntity radar) || containingSubLevel(radar) != host) {
                    altimeterFault = true;
                    found.add(sensor(device, "secondary", AvionicsSensor.OTHER_SUBLEVEL, OptionalDouble.empty(),
                            unit, AvionicsSensor.ISSUE_NONE));
                    continue;
                }
                SensorReading<Double> reading = radar.reading();
                if (!fresh(reading.status(), reading.gameTime(), now, maxAge)
                        || !Double.isFinite(reading.value().orElse(Double.NaN))) {
                    altimeterFault = true;
                    found.add(sensor(device, "secondary", reading.status() == ReadingStatus.FAILED
                            ? AvionicsSensor.FAILED : AvionicsSensor.STALE, OptionalDouble.empty(), unit,
                            AvionicsSensor.ISSUE_NONE));
                    continue;
                }
                double value = reading.value().orElseThrow();
                found.add(sensor(device, "secondary", AvionicsSensor.OK, OptionalDouble.of(value), unit,
                        AvionicsSensor.ISSUE_NONE));
                if (minimumClearance == null || value < minimumClearance
                        || (Double.compare(value, minimumClearance) == 0 && reading.gameTime() > minimumTime)) {
                    minimumClearance = value;
                    minimumTime = reading.gameTime();
                    primaryAltimeter = found.size() - 1;
                }
            } else {
                scannerCount++;
                if (!(source instanceof TerrainScannerBlockEntity terrain) || containingSubLevel(terrain) != host) {
                    scannerFault = true;
                    allScannerCoverageComplete = false;
                    found.add(sensor(device, fallbackRole, AvionicsSensor.OTHER_SUBLEVEL, OptionalDouble.empty(),
                            unit, AvionicsSensor.ISSUE_NONE));
                    continue;
                }
                Direction facing = terrain.getBlockState().getValue(AbstractTerrainScannerBlock.FACING);
                // The role is only what the player chose in the scanner's screen.
                ScannerSide side = terrain.side();
                String role = side.id();
                TerrainScanSnapshot reading = terrain.reading();
                if (!fresh(reading.status(), reading.gameTime(), now, maxAge)) {
                    scannerFault = true;
                    allScannerCoverageComplete = false;
                    found.add(sensor(device, role, reading.status() == ReadingStatus.FAILED
                            ? AvionicsSensor.FAILED : AvionicsSensor.STALE, OptionalDouble.empty(), unit,
                            AvionicsSensor.ISSUE_NONE));
                    continue;
                }
                boolean complete = reading.sampledRays() == reading.totalRays();
                allScannerCoverageComplete &= complete;
                int aperture = terrain.advanced() ? terrain.openingAngle() : TerrainScanGeometry.SIMPLE_APERTURE_DEGREES;
                boolean front = role.equals("front");
                if (front && complete && aperture >= 45) pullUpCoverageComplete = true;
                String issue = !side.set() ? AvionicsSensor.ISSUE_NO_SIDE
                        : !complete ? AvionicsSensor.ISSUE_PARTIAL_COVERAGE
                        : front && aperture < 45 ? AvionicsSensor.ISSUE_NARROW_APERTURE : AvionicsSensor.ISSUE_NONE;
                double nearest = Double.POSITIVE_INFINITY;
                for (TerrainScanContact contact : reading.contacts()) {
                    nearest = Math.min(nearest, contact.distance());
                    Vec3 direction = TerrainScanGeometry.toLevelLocal(facing,
                            new TerrainScanGeometry.DirectionVector(contact.directionX(), contact.directionY(),
                                    contact.directionZ()));
                    if (host != null) direction = host.logicalPose().transformNormal(direction).normalize();
                    // Without a known nose no contact counts as ahead (PULL UP cone).
                    double angle = forward == null ? 180
                            : Math.toDegrees(Math.acos(Math.clamp(direction.dot(forward), -1.0, 1.0)));
                    contacts.add(new GpwsSample.TerrainContact(contact.distance(), angle));
                }
                found.add(sensor(device, role, AvionicsSensor.OK, Double.isFinite(nearest)
                        ? OptionalDouble.of(nearest) : OptionalDouble.empty(), unit, issue));
            }
        }
        if (primaryAltimeter >= 0) {
            AvionicsSensor primary = found.get(primaryAltimeter);
            found.set(primaryAltimeter, new AvionicsSensor(primary.type(), "primary", primary.x(), primary.y(),
                    primary.z(), primary.status(), primary.value(), primary.unit(), primary.issue()));
        }
        // Stable order for the screen and the API: altimeters, then scanners, each by position.
        found.sort(java.util.Comparator.comparing((AvionicsSensor sensor) -> !sensor.type().equals("radar_altimeter"))
                .thenComparing(sensor -> !sensor.role().equals("primary"))
                .thenComparingInt(AvionicsSensor::x).thenComparingInt(AvionicsSensor::y)
                .thenComparingInt(AvionicsSensor::z));

        return new GpwsSample(now, network.status(), network.endpointCount(),
                altimeterCount, altimeterFault,
                minimumClearance == null ? OptionalDouble.empty() : OptionalDouble.of(minimumClearance),
                minimumTime, scannerCount, scannerFault,
                scannerCount > 0 && allScannerCoverageComplete, scannerCount > 0 && pullUpCoverageComplete,
                contacts, bankAngle(nose.orElse(getBlockState().getValue(AvionicsComputerBlock.FACING))));
    }

    /**
     * The nose from the scanners the player gave a side, front first, then rear, left and right,
     * each by position. Empty while no readable scanner on this aircraft has a side.
     */
    private Optional<Direction> noseDirection(NetworkSnapshot network, SubLevel host) {
        List<TerrainScannerBlockEntity> scanners = new ArrayList<>();
        List<NetworkDevice> devices = new ArrayList<>(network.devices());
        devices.sort(java.util.Comparator.comparingInt(NetworkDevice::x).thenComparingInt(NetworkDevice::y)
                .thenComparingInt(NetworkDevice::z));
        for (NetworkDevice device : devices) {
            if (!isScanner(device.type())) continue;
            BlockPos sourcePos = worldPosition.offset(device.x(), device.y(), device.z());
            if (level.hasChunkAt(sourcePos)
                    && level.getBlockEntity(sourcePos) instanceof TerrainScannerBlockEntity terrain
                    && containingSubLevel(terrain) == host && terrain.side().set()) {
                scanners.add(terrain);
            }
        }
        return scanners.stream().min(java.util.Comparator.comparingInt(terrain -> terrain.side().ordinal()))
                .flatMap(terrain -> terrain.side().nose(
                        terrain.getBlockState().getValue(AbstractTerrainScannerBlock.FACING)));
    }

    private static AvionicsSensor sensor(NetworkDevice device, String role, String status, OptionalDouble value,
                                         String unit, String issue) {
        return new AvionicsSensor(device.type(), role, device.x(), device.y(), device.z(), status, value, unit, issue);
    }

    private OptionalDouble bankAngle(Direction nose) {
        if (level == null) return OptionalDouble.empty();
        SubLevel host = containingSubLevel(this);
        if (host == null) return OptionalDouble.of(0);
        Vec3 localForward = new Vec3(nose.getStepX(), nose.getStepY(), nose.getStepZ());
        Vec3 worldForward = host.logicalPose().transformNormal(localForward).normalize();
        Vec3 worldUp = host.logicalPose().transformNormal(new Vec3(0, 1, 0)).normalize();
        return GpwsAttitude.bankDegrees(worldForward, worldUp);
    }

    private static Vec3 worldDirection(BlockEntity entity, Direction facing) {
        Vec3 direction = new Vec3(facing.getStepX(), facing.getStepY(), facing.getStepZ());
        SubLevel host = containingSubLevel(entity);
        return host == null ? direction : host.logicalPose().transformNormal(direction).normalize();
    }

    private static SubLevel containingSubLevel(BlockEntity entity) {
        var containing = Sable.HELPER.getContaining(entity);
        return containing instanceof SubLevel subLevel ? subLevel : null;
    }

    private static boolean fresh(ReadingStatus status, long readingTime, long now, long maxAge) {
        return status == ReadingStatus.VALID && readingTime <= now && now - readingTime <= maxAge;
    }

    private static GpwsSample emptySample(long gameTime, NetworkStatus status) {
        return new GpwsSample(gameTime, status, 0, 0, false, OptionalDouble.empty(), gameTime,
                0, false, false, false, List.of(), OptionalDouble.empty());
    }

    private static GpwsEngine.Evaluation initialEvaluation() {
        EnumMap<AlertChannel, AlertReading> alerts = new EnumMap<>(AlertChannel.class);
        for (AlertChannel channel : AlertChannel.values()) {
            alerts.put(channel, new AlertReading(channel, AlertState.UNAVAILABLE, AlertSeverity.ADVISORY, 0));
        }
        return new GpwsEngine.Evaluation(FlightPhase.UNKNOWN, FlightPhase.UNKNOWN, OptionalDouble.empty(),
                Map.copyOf(alerts), Map.of());
    }

    public boolean setSensitivity(GpwsSensitivity updated) {
        if (!configuration.setSensitivity(updated)) return false;
        configurationChanged();
        return true;
    }

    public boolean setInhibited(AlertChannel channel, boolean inhibited) {
        if (!configuration.setInhibited(channel, inhibited)) return false;
        configurationChanged();
        evaluate();
        return true;
    }

    private void configurationChanged() {
        setChanged();
        peripheral.publishConfigurationChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        configuration.save(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        configuration = GpwsConfiguration.load(tag);
    }

    static String lower(Enum<?> value) { return value.name().toLowerCase(Locale.ROOT); }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
