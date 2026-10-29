package com.radaeli.aeronauticsflybyinstruments.item;

import com.radaeli.aeronauticsflybyinstruments.network.PanelNetwork;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/** A two-cell lamp whose footprint orientation is selected with the mouse wheel. */
public final class LampModule2x1Item extends Item {
    public LampModule2x1Item(Properties properties) { super(properties); }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        boolean vertical = PanelNetwork.isVertical(context.getPlayer());
        return LampModuleItem.place(context, "lamp_2x1", vertical ? 1 : 2, vertical ? 2 : 1);
    }
}
