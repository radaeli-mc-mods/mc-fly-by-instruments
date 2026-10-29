package com.radaeli.aeronauticsflybyinstruments.panel;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PanelCoordinatesTest {
    @Test
    void authoredModelMountsAtExactCellCenterForEverySlotAndFacing() {
        for (var facing : Direction.values()) {
            for (int row = 0; row < PanelGrid.SIZE; row++) {
                for (int column = 0; column < PanelGrid.SIZE; column++) {
                    var module = new PanelGrid.Module("buzzer", column, row, 1, 1);
                    var base = new Vec3(0.5, 0, 0.5).add(PanelCoordinates.modelOffset(module));
                    var expected = new Vec3((column + 0.5) / 3, PanelCoordinates.SURFACE, (row + 0.5) / 3);
                    assertEquals(0, PanelCoordinates.fromPanel(facing, expected)
                            .distanceTo(PanelCoordinates.fromPanel(facing, base)), 1e-6);
                    var top = base.add(0, 3.0 / 16, 0);
                    assertEquals(3.0 / 16,
                            PanelCoordinates.fromPanel(facing, top).distanceTo(PanelCoordinates.fromPanel(facing, base)), 1e-6);
                }
            }
        }
    }

    @Test
    void clicksOnEveryCellMapCorrectlyForEveryMountingDirection() {
        for (var facing : Direction.values()) {
            for (int row = 0; row < 3; row++) {
                for (int column = 0; column < 3; column++) {
                    var panelPoint = new Vec3((column + 0.5) / 3, PanelCoordinates.SURFACE, (row + 0.5) / 3);
                    var hit = PanelCoordinates.fromPanel(facing, panelPoint);
                    var transformed = PanelCoordinates.toPanel(facing, hit);
                    assertEquals(column, PanelCoordinates.cell(transformed.x));
                    assertEquals(row, PanelCoordinates.cell(transformed.z));
                    assertEquals(panelPoint.x, transformed.x, 0.000001);
                    assertEquals(panelPoint.y, transformed.y, 0.000001);
                    assertEquals(panelPoint.z, transformed.z, 0.000001);
                }
            }
        }
    }

    @Test
    void outerEdgesRemainInsideButInvalidCoordinatesAreRejected() {
        assertEquals(0, PanelCoordinates.cell(0));
        assertEquals(1, PanelCoordinates.cell(1.0 / 3));
        assertEquals(2, PanelCoordinates.cell(2.0 / 3));
        assertEquals(2, PanelCoordinates.cell(1));
        assertEquals(-1, PanelCoordinates.cell(-0.01));
        assertEquals(-1, PanelCoordinates.cell(1.01));
        assertEquals(-1, PanelCoordinates.cell(Double.NaN));
    }
}
