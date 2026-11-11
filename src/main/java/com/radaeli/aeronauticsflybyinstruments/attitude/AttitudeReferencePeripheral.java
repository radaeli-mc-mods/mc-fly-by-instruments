package com.radaeli.aeronauticsflybyinstruments.attitude;

import com.radaeli.aeronauticsflybyinstruments.integration.PeripheralEvents;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.core.Direction;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** CC:Tweaked peripheral for one Attitude Reference Unit. */
public final class AttitudeReferencePeripheral implements IPeripheral, PeripheralEvents.Attitude {
    public static final String EVENT_NAME = "attitude_reading_changed";
    private final AttitudeReferenceUnitBlockEntity unit;
    private final Set<IComputerAccess> computers = ConcurrentHashMap.newKeySet();

    public AttitudeReferencePeripheral(AttitudeReferenceUnitBlockEntity unit) {
        this.unit = unit;
    }

    @Override
    public String getType() {
        return "attitude_reference_unit";
    }

    @Override
    public Object getTarget() {
        return unit;
    }

    @Override
    public boolean equals(IPeripheral other) {
        return other instanceof AttitudeReferencePeripheral peripheral && peripheral.unit == unit;
    }

    @Override
    public void attach(IComputerAccess computer) {
        computers.add(computer);
    }

    @Override
    public void detach(IComputerAccess computer) {
        computers.remove(computer);
    }

    /** pitch (positive nose up), roll (positive right wing down), heading (0 = north, clockwise). */
    @LuaFunction(mainThread = true)
    public Map<String, Object> getAttitude() {
        return toLua(unit.reading());
    }

    /** turnRate (degrees per second, positive turning right), verticalSpeed (blocks per second), slip (degrees). */
    @LuaFunction(mainThread = true)
    public Map<String, Object> getRates() {
        var reading = unit.reading();
        Map<String, Object> rates = new HashMap<>();
        rates.put("status", reading.status().lowerName());
        if (reading.hasValues()) {
            rates.put("turnRate", reading.turnRate());
            rates.put("verticalSpeed", reading.verticalSpeed());
            rates.put("slip", reading.slip());
        }
        rates.put("time", reading.gameTime());
        return Map.copyOf(rates);
    }

    @LuaFunction(mainThread = true)
    public Map<String, Object> getConfig() {
        var orientation = unit.orientation();
        Map<String, Object> config = new HashMap<>();
        config.put("nose", orientation.nose().getSerializedName());
        config.put("up", orientation.up().getSerializedName());
        config.put("pitchOffset", unit.pitchOffset());
        config.put("rollOffset", unit.rollOffset());
        config.put("damping", unit.damping().id());
        return Map.copyOf(config);
    }

    @LuaFunction(mainThread = true)
    public void setOrientation(String nose, String up) throws LuaException {
        Direction noseFace = direction(nose, "nose");
        Direction upFace = direction(up, "up");
        if (!AttitudeOrientation.valid(noseFace, upFace)) {
            throw new LuaException("nose and up must be perpendicular");
        }
        unit.setOrientation(new AttitudeOrientation(noseFace, upFace));
    }

    /** Returns false while the reading is not valid or lies beyond the calibration limit. */
    @LuaFunction(mainThread = true)
    public boolean calibrateLevel() {
        return unit.calibrateLevel();
    }

    @LuaFunction(mainThread = true)
    public void resetCalibration() {
        unit.resetCalibration();
    }

    @LuaFunction(mainThread = true)
    public boolean setDamping(String mode) {
        var parsed = AttitudeDamping.parse(mode);
        if (parsed.isEmpty()) return false;
        unit.setDamping(parsed.orElseThrow());
        return true;
    }

    private static Direction direction(String name, String role) throws LuaException {
        Direction face = name == null ? null : Direction.byName(name.toLowerCase(java.util.Locale.ROOT));
        if (face == null) throw new LuaException(role + " must be north, south, east, west, up or down");
        return face;
    }

    public void publishReading(AttitudeReading reading) {
        Map<String, Object> snapshot = toLua(reading);
        for (IComputerAccess computer : computers) {
            computer.queueEvent(EVENT_NAME, computer.getAttachmentName(), snapshot);
        }
    }

    static Map<String, Object> toLua(AttitudeReading reading) {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("status", reading.status().lowerName());
        if (reading.hasValues()) {
            snapshot.put("pitch", reading.pitch());
            snapshot.put("roll", reading.roll());
            snapshot.put("heading", reading.heading());
        }
        snapshot.put("time", reading.gameTime());
        return Map.copyOf(snapshot);
    }
}
