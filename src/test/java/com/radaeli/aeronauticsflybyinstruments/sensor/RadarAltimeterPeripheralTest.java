package com.radaeli.aeronauticsflybyinstruments.sensor;

import com.radaeli.aeronauticsflybyinstruments.integration.ComputerCraftIntegration;
import com.radaeli.aeronauticsflybyinstruments.block.ModBlocks;
import com.radaeli.aeronauticsflybyinstruments.avionics.SensorReading;
import dan200.computercraft.api.peripheral.IComputerAccess;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class RadarAltimeterPeripheralTest {
    @Test
    void exposesTypedReadingAndLuaAnnotation() throws Exception {
        var sensor = sensor();
        var peripheral = ComputerCraftIntegration.peripheral(sensor);
        assertEquals("radar_altimeter", peripheral.getType());
        assertEquals(Map.of("status", "unavailable", "unit", "blocks", "gameTime", 0L),
                peripheral.getReading());
        assertNotNull(RadarAltimeterPeripheral.class.getMethod("getReading")
                .getAnnotation(dan200.computercraft.api.lua.LuaFunction.class));
    }

    @Test
    void sendsSnapshotOnStatusOrValueChangeButNotTimestampOnly() {
        var sensor = sensor();
        var events = new ArrayList<Object[]>();
        var computer = (IComputerAccess) Proxy.newProxyInstance(IComputerAccess.class.getClassLoader(),
                new Class<?>[]{IComputerAccess.class}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "getAttachmentName" -> "ralt";
                    case "queueEvent" -> { events.add(arguments); yield null; }
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    default -> null;
                });
        ComputerCraftIntegration.peripheral(sensor).attach(computer);

        // Default altitude range is 0-32 blocks: 12.5 -> 6, 11.0 -> 5, unavailable -> 0.
        sensor.acceptReading(SensorReading.valid(12.5, "blocks", 5));
        var readings = named(events, RadarAltimeterPeripheral.EVENT_NAME);
        assertEquals(1, readings.size());
        Object[] arguments = (Object[]) readings.getFirst()[1];
        assertEquals("ralt", arguments[0]);
        assertEquals(Map.of("status", "valid", "value", 12.5, "unit", "blocks", "gameTime", 5L),
                arguments[1]);
        var outputs = named(events, RadarAltimeterPeripheral.OUTPUT_EVENT);
        assertEquals(1, outputs.size());
        assertEquals(Map.of("altitude", 6), ((Object[]) outputs.getFirst()[1])[1]);

        sensor.acceptReading(SensorReading.valid(12.5, "blocks", 10));
        assertEquals(1, named(events, RadarAltimeterPeripheral.EVENT_NAME).size());
        assertEquals(1, named(events, RadarAltimeterPeripheral.OUTPUT_EVENT).size());
        sensor.acceptReading(SensorReading.valid(11.0, "blocks", 15));
        assertEquals(2, named(events, RadarAltimeterPeripheral.EVENT_NAME).size());
        assertEquals(2, named(events, RadarAltimeterPeripheral.OUTPUT_EVENT).size());
        sensor.acceptReading(SensorReading.unavailable("blocks", 20));
        assertEquals(3, named(events, RadarAltimeterPeripheral.EVENT_NAME).size());
        outputs = named(events, RadarAltimeterPeripheral.OUTPUT_EVENT);
        assertEquals(3, outputs.size());
        assertEquals(Map.of("altitude", 0), ((Object[]) outputs.getLast()[1])[1]);
        ComputerCraftIntegration.peripheral(sensor).detach(computer);
    }

    private static java.util.List<Object[]> named(java.util.List<Object[]> events, String name) {
        return events.stream().filter(event -> name.equals(event[0])).toList();
    }

    private static RadarAltimeterBlockEntity sensor() {
        return new RadarAltimeterBlockEntity(BlockPos.ZERO, ModBlocks.RADAR_ALTIMETER.get().defaultBlockState());
    }
}
