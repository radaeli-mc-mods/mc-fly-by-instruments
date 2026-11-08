package com.radaeli.aeronauticsflybyinstruments.panel;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/**
 * What the player set on one Attitude Indicator module. Both tapes are on by default; each one shows only while its
 * source is on the network: the altitude tape needs a Radar Altimeter, the speed tape a Velocity Sensor.
 */
public final class AttitudeIndicatorData {
    private boolean altitudeTape = true;
    private boolean speedTape = true;

    public AttitudeIndicatorData() {}

    public AttitudeIndicatorData(boolean altitudeTape) {
        this(altitudeTape, true);
    }

    public AttitudeIndicatorData(boolean altitudeTape, boolean speedTape) {
        this.altitudeTape = altitudeTape;
        this.speedTape = speedTape;
    }

    public boolean altitudeTape() { return altitudeTape; }

    public void setAltitudeTape(boolean value) { altitudeTape = value; }

    public boolean speedTape() { return speedTape; }

    public void setSpeedTape(boolean value) { speedTape = value; }

    public AttitudeIndicatorData copy() { return new AttitudeIndicatorData(altitudeTape, speedTape); }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("AltitudeTape", altitudeTape);
        tag.putBoolean("SpeedTape", speedTape);
        return tag;
    }

    /** A save from before a setting existed keeps that setting's default. */
    public static AttitudeIndicatorData load(CompoundTag tag) {
        return new AttitudeIndicatorData(
                !tag.contains("AltitudeTape", Tag.TAG_BYTE) || tag.getBoolean("AltitudeTape"),
                !tag.contains("SpeedTape", Tag.TAG_BYTE) || tag.getBoolean("SpeedTape"));
    }
}
