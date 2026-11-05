package com.radaeli.aeronauticsflybyinstruments.attitude;

import java.util.Locale;

/** Optional exponential filter on the published attitude. The display interpolates on its own. */
public enum AttitudeDamping {
    OFF(1.0), LIGHT(0.2), STRONG(0.05);

    private final double alpha;

    AttitudeDamping(double alpha) {
        this.alpha = alpha;
    }

    /** Weight of each new server tick sample (1 = no filter). */
    public double alpha() {
        return alpha;
    }

    public AttitudeDamping next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static java.util.Optional<AttitudeDamping> parse(String id) {
        for (AttitudeDamping mode : values()) {
            if (mode.id().equals(id)) return java.util.Optional.of(mode);
        }
        return java.util.Optional.empty();
    }
}
