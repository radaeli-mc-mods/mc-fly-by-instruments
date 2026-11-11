package com.radaeli.aeronauticsflybyinstruments.avionics;

import com.radaeli.aeronauticsflybyinstruments.integration.PeripheralEvents;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CC:Tweaked gateway of the whole avionics network: every sensor's reading with its role, the GPWS
 * functions and the few player controls. Individual sensors keep their own peripherals for custom
 * flows built around a single sensor.
 */
public final class AvionicsComputerPeripheral implements IPeripheral, PeripheralEvents.Computer {
    public static final String TYPE = "avionics_computer";
    public static final String ALERT_EVENT = "avionics_alert_changed";
    public static final String SENSORS_EVENT = "avionics_sensors_changed";
    public static final String STATUS_EVENT = "avionics_status_changed";
    public static final String CONFIGURATION_EVENT = "avionics_configuration_changed";

    private final AvionicsComputerBlockEntity computer;
    private final Set<IComputerAccess> computers = ConcurrentHashMap.newKeySet();

    public AvionicsComputerPeripheral(AvionicsComputerBlockEntity computer) { this.computer = computer; }

    @Override public String getType() { return TYPE; }
    @Override public Object getTarget() { return computer; }
    @Override public boolean equals(IPeripheral other) {
        return other instanceof AvionicsComputerPeripheral peripheral && peripheral.computer == computer;
    }
    @Override public void attach(IComputerAccess attached) { computers.add(attached); }
    @Override public void detach(IComputerAccess detached) { computers.remove(detached); }

    @LuaFunction(mainThread = true)
    public Map<String, Object> getStatus() { return computer.statusSnapshot(); }

    @LuaFunction(mainThread = true)
    public List<Map<String, Object>> getSensors() { return describeSensors(computer.sensors()); }

    @LuaFunction(mainThread = true)
    public Map<String, Object> getAlerts() {
        return describeAlerts(computer.evaluation().alerts(), computer.evaluation().reasons());
    }

    @LuaFunction(mainThread = true)
    public Map<String, Object> getConfiguration() { return configurationSnapshot(); }

    @LuaFunction(mainThread = true)
    public boolean test() { return computer.startTest(); }

    @LuaFunction(mainThread = true)
    public boolean setSensitivity(String value) {
        return parse(GpwsSensitivity.class, value).map(computer::setSensitivity).orElse(false);
    }

    @LuaFunction(mainThread = true)
    public boolean setInhibited(String function, boolean inhibited) {
        return parse(AlertChannel.class, function).map(channel -> computer.setInhibited(channel, inhibited))
                .orElse(false);
    }

    public void publishAlertsChanged(Map<AlertChannel, AlertReading> changed, Map<AlertChannel, String> reasons) {
        publish(ALERT_EVENT, describeAlerts(changed, reasons));
    }

    public void publishSensorsChanged() {
        publish(SENSORS_EVENT, describeSensors(computer.sensors()));
    }

    public void publishStatusChanged() {
        publish(STATUS_EVENT, computer.statusSnapshot());
    }

    public void publishConfigurationChanged() {
        publish(CONFIGURATION_EVENT, configurationSnapshot());
    }

    public Map<String, Object> configurationSnapshot() {
        List<String> inhibited = computer.configuration().inhibitedChannels().stream()
                .map(AlertChannel::id).sorted().toList();
        return Map.of("sensitivity", lower(computer.configuration().sensitivity()), "inhibited", inhibited);
    }

    private void publish(String event, Object snapshot) {
        for (IComputerAccess attached : computers) {
            attached.queueEvent(event, attached.getAttachmentName(), snapshot);
        }
    }

    public static Map<String, Object> describeAlerts(Map<AlertChannel, AlertReading> alerts,
                                                     Map<AlertChannel, String> reasons) {
        Map<String, Object> result = new HashMap<>();
        alerts.forEach((channel, reading) -> result.put(channel.id(), describe(reading, reasons.get(channel))));
        return Map.copyOf(result);
    }

    public static Map<String, Object> describe(AlertReading reading, String reason) {
        Map<String, Object> result = new HashMap<>();
        result.put("state", lower(reading.state()));
        result.put("severity", lower(reading.severity()));
        result.put("gameTime", reading.gameTime());
        if (reason != null) result.put("reason", reason);
        return Map.copyOf(result);
    }

    public static List<Map<String, Object>> describeSensors(List<AvionicsSensor> sensors) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (AvionicsSensor sensor : sensors) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("type", sensor.type());
            entry.put("role", sensor.role());
            entry.put("x", sensor.x());
            entry.put("y", sensor.y());
            entry.put("z", sensor.z());
            entry.put("status", sensor.status());
            entry.put("unit", sensor.unit());
            sensor.value().ifPresent(value -> entry.put("value", value));
            if (!sensor.issue().isEmpty()) entry.put("issue", sensor.issue());
            result.add(Map.copyOf(entry));
        }
        return List.copyOf(result);
    }

    public static boolean alertStateChanged(AlertReading previous, AlertReading updated) {
        return AlertReading.stateChanged(previous, updated);
    }

    private static <E extends Enum<E>> java.util.Optional<E> parse(Class<E> type, String value) {
        if (value == null) return java.util.Optional.empty();
        try {
            return java.util.Optional.of(Enum.valueOf(type, value.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            return java.util.Optional.empty();
        }
    }

    private static String lower(Enum<?> value) { return value.name().toLowerCase(Locale.ROOT); }
}
