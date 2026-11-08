package com.radaeli.aeronauticsflybyinstruments.panel;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AttitudeIndicatorDataTest {
    @Test
    void bothTapesAreOnByDefault() {
        var data = new AttitudeIndicatorData();
        assertTrue(data.altitudeTape());
        assertTrue(data.speedTape());
        assertTrue(AttitudeIndicatorData.load(new CompoundTag()).speedTape(), "a save from before the setting");
    }

    @Test
    void theSpeedTapeSurvivesASaveAndCanBeCopied() {
        var data = new AttitudeIndicatorData();
        data.setSpeedTape(false);
        data.setAltitudeTape(false);
        var loaded = AttitudeIndicatorData.load(data.save());
        assertFalse(loaded.speedTape());
        assertFalse(loaded.altitudeTape());
        assertFalse(data.copy().speedTape());
    }
}
