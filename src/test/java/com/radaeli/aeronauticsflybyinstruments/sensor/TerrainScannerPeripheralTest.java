package com.radaeli.aeronauticsflybyinstruments.sensor;

import com.radaeli.aeronauticsflybyinstruments.integration.ComputerCraftIntegration;
import com.radaeli.aeronauticsflybyinstruments.avionics.ReadingStatus;
import com.radaeli.aeronauticsflybyinstruments.block.ModBlocks;
import dan200.computercraft.api.peripheral.IComputerAccess;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TerrainScannerPeripheralTest {
    @Test
    void simpleAndAdvancedHaveIndependentPeripheralTypesAndLuaContracts() throws Exception {
        var simple = scanner(false);
        var advanced = scanner(true);

        assertEquals("terrain_scanner", ComputerCraftIntegration.peripheral(simple).getType());
        assertEquals("terrain_scanner_advanced", ComputerCraftIntegration.peripheral(advanced).getType());
        assertNotNull(TerrainScannerPeripheral.class.getMethod("getScan")
                .getAnnotation(dan200.computercraft.api.lua.LuaFunction.class));
        assertNotNull(AdvancedTerrainScannerPeripheral.class.getMethod("getConfiguration")
                .getAnnotation(dan200.computercraft.api.lua.LuaFunction.class));
        assertNotNull(AdvancedTerrainScannerPeripheral.class.getMethod("setOpeningAngle", int.class)
                .getAnnotation(dan200.computercraft.api.lua.LuaFunction.class));
        assertThrows(NoSuchMethodException.class,
                () -> TerrainScannerPeripheral.class.getMethod("setOpeningAngle", int.class));
    }

    @Test
    void luaScanContainsAllContactCoverageAndTimestampFields() {
        var contact = new TerrainScanContact(3, 25.25, 0.2, -0.1, 0.97);
        var snapshot = new TerrainScanSnapshot(ReadingStatus.VALID, List.of(contact), 7, 9, 200);

        var lua = TerrainScannerPeripheral.toLua(snapshot);
        assertEquals("valid", lua.get("status"));
        assertEquals(7, lua.get("sampledRays"));
        assertEquals(9, lua.get("totalRays"));
        assertEquals(7.0 / 9.0, (double) lua.get("coverage"), 1.0E-12);
        assertEquals("blocks", lua.get("unit"));
        assertEquals(200L, lua.get("gameTime"));
        @SuppressWarnings("unchecked")
        var contacts = (List<java.util.Map<String, Object>>) lua.get("contacts");
        assertEquals(3, contacts.getFirst().get("rayIndex"));
        assertEquals(25.25, contacts.getFirst().get("distance"));
    }

    @Test
    void emitsFullScanOnContentChangeButNotTimestampOnly() {
        var scanner = scanner(false);
        var events = new ArrayList<Object[]>();
        var computer = computer(events);
        ComputerCraftIntegration.peripheral(scanner).attach(computer);

        var first = new TerrainScanSnapshot(ReadingStatus.VALID,
                List.of(new TerrainScanContact(1, 10.0, 0, 0, 1)), 9, 9, 10);
        // Default front range is 4-32 blocks: both 10.0 and 9.0 map to power 12.
        scanner.acceptReading(first);
        assertEquals(1, named(events, TerrainScannerPeripheral.SCAN_EVENT).size());
        var outputs = named(events, RadarAltimeterPeripheral.OUTPUT_EVENT);
        assertEquals(1, outputs.size());
        assertEquals(java.util.Map.of("front", 12), ((Object[]) outputs.getFirst()[1])[1]);

        scanner.acceptReading(new TerrainScanSnapshot(ReadingStatus.VALID, first.contacts(), 9, 9, 15));
        assertEquals(1, named(events, TerrainScannerPeripheral.SCAN_EVENT).size());
        scanner.acceptReading(new TerrainScanSnapshot(ReadingStatus.VALID,
                List.of(new TerrainScanContact(1, 9.0, 0, 0, 1)), 9, 9, 20));
        assertEquals(2, named(events, TerrainScannerPeripheral.SCAN_EVENT).size());
        assertEquals(1, named(events, RadarAltimeterPeripheral.OUTPUT_EVENT).size(),
                "Unchanged redstone power must not emit another output event");
        ComputerCraftIntegration.peripheral(scanner).detach(computer);
    }

    @Test
    void advancedAngleValidatesAndPersistsAcrossBlockEntitySaveLoad() {
        var advanced = scanner(true);
        assertEquals(90, advanced.openingAngle());
        assertTrue(advanced.setOpeningAngle(135));
        assertFalse(advanced.setOpeningAngle(135));
        assertFalse(advanced.setOpeningAngle(29));
        assertFalse(scanner(false).setOpeningAngle(120));

        var tag = new net.minecraft.nbt.CompoundTag();
        advanced.saveAdditional(tag, null);
        var restored = scanner(true);
        restored.loadAdditional(tag, null);
        assertEquals(135, restored.openingAngle());

        tag.putInt("OpeningAngle", 181);
        restored.loadAdditional(tag, null);
        assertEquals(90, restored.openingAngle());
    }

    private static List<Object[]> named(List<Object[]> events, String name) {
        return events.stream().filter(event -> name.equals(event[0])).toList();
    }

    private static TerrainScannerBlockEntity scanner(boolean advanced) {
        var block = advanced ? ModBlocks.TERRAIN_SCANNER_ADVANCED.get() : ModBlocks.TERRAIN_SCANNER.get();
        return new TerrainScannerBlockEntity(BlockPos.ZERO, block.defaultBlockState());
    }

    private static IComputerAccess computer(List<Object[]> events) {
        return (IComputerAccess) Proxy.newProxyInstance(IComputerAccess.class.getClassLoader(),
                new Class<?>[]{IComputerAccess.class}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "getAttachmentName" -> "terrain";
                    case "queueEvent" -> { events.add(arguments); yield null; }
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    default -> null;
                });
    }
}
