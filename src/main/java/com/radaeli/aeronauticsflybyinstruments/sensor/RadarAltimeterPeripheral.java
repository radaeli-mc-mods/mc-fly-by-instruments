package com.radaeli.aeronauticsflybyinstruments.sensor;

import com.radaeli.aeronauticsflybyinstruments.integration.PeripheralEvents;
import com.radaeli.aeronauticsflybyinstruments.avionics.SensorReading;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.world.item.ItemStack;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Independent CC:Tweaked peripheral for one Radar Altimeter block. */
public final class RadarAltimeterPeripheral implements IPeripheral, PeripheralEvents.Radar {
    public static final String EVENT_NAME = "radar_altimeter_reading_changed";
    public static final String OUTPUT_CONFIG_EVENT = "sensor_output_configuration_changed";
    public static final String OUTPUT_EVENT = "sensor_outputs_changed";
    private final RadarAltimeterBlockEntity sensor;
    private final Set<IComputerAccess> computers = ConcurrentHashMap.newKeySet();

    public RadarAltimeterPeripheral(RadarAltimeterBlockEntity sensor) {
        this.sensor = sensor;
    }

    @Override
    public String getType() {
        return "radar_altimeter";
    }

    @Override
    public Object getTarget() {
        return sensor;
    }

    @Override
    public boolean equals(IPeripheral other) {
        return other instanceof RadarAltimeterPeripheral peripheral && peripheral.sensor == sensor;
    }

    @Override
    public void attach(IComputerAccess computer) {
        computers.add(computer);
    }

    @Override
    public void detach(IComputerAccess computer) {
        computers.remove(computer);
    }

    @LuaFunction(mainThread = true)
    public Map<String, Object> getReading() {
        return toLua(sensor.reading());
    }

    @LuaFunction(mainThread = true)
    public Map<String, Object> getOutputConfiguration() { return SensorOutputLua.configuration(sensor.outputConfig()); }

    @LuaFunction(mainThread = true)
    public Map<String, Object> getOutputs() { return SensorOutputLua.outputs(sensor.outputLevels()); }

    @LuaFunction(mainThread = true)
    public boolean setOutputRange(String channel, int min, int max) {
        return "altitude".equals(channel) && sensor.setOutputRange(SensorOutputConfig.Channel.ALTITUDE, min, max);
    }

    @LuaFunction(mainThread = true)
    public boolean setOutputMode(String mode) { return sensor.setOutputMode(mode); }

    @LuaFunction(mainThread = true)
    public boolean setLinkFrequencies(String channel, Map<?, ?> first, Map<?, ?> second) {
        if (!"altitude".equals(channel)) return false;
        ItemStack a = SensorOutputLua.frequency(first), b = SensorOutputLua.frequency(second);
        return a != null && b != null && sensor.setLinkFrequencies(SensorOutputConfig.Channel.ALTITUDE, a, b);
    }

    public void publishReading(SensorReading<Double> reading) {
        Map<String, Object> snapshot = toLua(reading);
        for (IComputerAccess computer : computers) {
            computer.queueEvent(EVENT_NAME, computer.getAttachmentName(), snapshot);
        }
    }

    public void publishOutputConfiguration() {
        Map<String, Object> value = SensorOutputLua.configuration(sensor.outputConfig());
        publishEvent(OUTPUT_CONFIG_EVENT, value);
    }
    public void publishOutputs() { publishEvent(OUTPUT_EVENT, SensorOutputLua.outputs(sensor.outputLevels())); }
    private void publishEvent(String event, Map<String, Object> value) {
        for (IComputerAccess computer : computers) computer.queueEvent(event, computer.getAttachmentName(), value);
    }

    public static Map<String, Object> toLua(SensorReading<Double> reading) {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("status", reading.status().name().toLowerCase(java.util.Locale.ROOT));
        reading.value().ifPresent(value -> snapshot.put("value", value));
        snapshot.put("unit", reading.unit());
        snapshot.put("gameTime", reading.gameTime());
        return Map.copyOf(snapshot);
    }
}
