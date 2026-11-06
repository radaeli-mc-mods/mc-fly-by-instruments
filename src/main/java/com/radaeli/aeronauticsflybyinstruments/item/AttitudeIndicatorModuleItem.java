package com.radaeli.aeronauticsflybyinstruments.item;

import com.radaeli.aeronauticsflybyinstruments.panel.PanelCoordinates;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/**
 * 2x2 attitude indicator (artificial horizon): four cells of a table, or across joined tables.
 * The display shows the nearest Attitude Reference Unit and the lowest Radar Altimeter on the table's cable
 * network; an empty hand opens its screen.
 */
public final class AttitudeIndicatorModuleItem extends Item {
    public AttitudeIndicatorModuleItem(Properties properties) { super(properties.stacksTo(1)); }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return LampModuleItem.place(context, PanelCoordinates.ATTITUDE_INDICATOR, 2, 2);
    }
}
