package com.radaeli.aeronauticsflybyinstruments.panel;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/**
 * What the player set on one Attitude Indicator module. The altitude tape is on by default and still needs a
 * Radar Altimeter on the network to show; the speed tape is reserved for a future sensor and is not stored.
 */
public final class AttitudeIndicatorData {
    private boolean altitudeTape = true;

    public AttitudeIndicatorData() {}

    public AttitudeIndicatorData(boolean altitudeTape) {
        this.altitudeTape = altitudeTape;
    }

    public boolean altitudeTape() { return altitudeTape; }

    public void setAltitudeTape(boolean value) { altitudeTape = value; }

    public AttitudeIndicatorData copy() { return new AttitudeIndicatorData(altitudeTape); }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("AltitudeTape", altitudeTape);
        return tag;
    }

    /** A save from before the configuration existed keeps the defaults. */
    public static AttitudeIndicatorData load(CompoundTag tag) {
        return new AttitudeIndicatorData(!tag.contains("AltitudeTape", Tag.TAG_BYTE) || tag.getBoolean("AltitudeTape"));
    }
}
