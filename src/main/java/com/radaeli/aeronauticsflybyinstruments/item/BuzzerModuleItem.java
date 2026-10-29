package com.radaeli.aeronauticsflybyinstruments.item;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/** Physical 1x1 panel module; audio/control behavior is reserved for a later phase. */
public final class BuzzerModuleItem extends Item {
    public BuzzerModuleItem(Properties properties) { super(properties); }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return LampModuleItem.place(context, "buzzer", 1, 1);
    }
}
