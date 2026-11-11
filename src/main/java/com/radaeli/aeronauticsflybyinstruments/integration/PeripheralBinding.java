package com.radaeli.aeronauticsflybyinstruments.integration;

import java.util.Objects;
import java.util.function.Supplier;

/** Holds an optional integration listener without referencing its external API. */
public final class PeripheralBinding<E> {
    private volatile E events;
    private boolean bound;

    public PeripheralBinding(E fallback) {
        events = Objects.requireNonNull(fallback);
    }

    public E events() { return events; }

    /** Capability queries share one peripheral and its attached computers for this block entity. */
    public synchronized E getOrCreate(Supplier<? extends E> factory) {
        if (!bound) {
            events = Objects.requireNonNull(factory.get());
            bound = true;
        }
        return events;
    }
}
