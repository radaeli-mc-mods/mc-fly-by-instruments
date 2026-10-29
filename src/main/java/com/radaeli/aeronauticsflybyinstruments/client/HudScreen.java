package com.radaeli.aeronauticsflybyinstruments.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Screen that rebuilds one centred {@link HudPanel} every frame; choices apply immediately. The
 * mouse wheel steps the stepper under it, and on panels with drop slots an item can be dragged
 * from one slot to another (a press and release on the same slot is still a click). The wheel
 * scrolls a scrolled list under it, and its scrollbar can be pressed or dragged.
 */
abstract class HudScreen extends Screen {
    /** Pixels the mouse must travel before a press on a slot becomes a drag. */
    private static final double DRAG_THRESHOLD = 3;

    private final HudPanel.Changes changes = new HudPanel.Changes();
    private List<HudPanel.Hit> hits = List.of();
    private List<HudPanel.SlotArea> slotAreas = List.of();
    private List<HudPanel.Scroll> scrolls = List.of();
    private List<HudPanel.ScrollBar> scrollBars = List.of();
    private HudPanel.ScrollBar draggedBar;
    private HudPanel.Slot pressed;
    private double pressX, pressY;
    private boolean dragging;

    protected HudScreen(Component title) {
        super(title);
    }

    protected abstract HudPanel build();

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        HudPanel panel = build();
        int x = (width - panel.width()) / 2;
        int y = Math.max(6, (height - panel.height()) / 2);
        hits = panel.draw(graphics, font, x, y, mouseX, mouseY, changes);
        slotAreas = panel.slotAreas();
        scrolls = panel.scrolls();
        scrollBars = panel.scrollBars();
        if (dragging) {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 232);
            graphics.renderItem(pressed.stack(), mouseX - 8, mouseY - 8);
            graphics.pose().popPose();
            return;
        }
        String tooltip = panel.hoveredTooltip();
        if (tooltip != null) {
            graphics.renderTooltip(font, font.split(Component.literal(tooltip), 200), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (HudPanel.ScrollBar bar : scrollBars) {
                if (!bar.contains(mouseX, mouseY)) continue;
                draggedBar = bar;
                bar.dragTo(mouseY);
                return true;
            }
            HudPanel.Slot source = draggableAt(mouseX, mouseY);
            if (source != null) {
                // The click waits for the release: it may still turn into a drag.
                pressed = source;
                pressX = mouseX;
                pressY = mouseY;
                dragging = false;
                return true;
            }
        }
        if ((button == 0 || button == 1) && HudPanel.click(hits, mouseX, mouseY, button)) {
            playClick();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggedBar != null && button == 0) {
            draggedBar.dragTo(mouseY);
            return true;
        }
        if (pressed != null && button == 0) {
            if (Math.hypot(mouseX - pressX, mouseY - pressY) >= DRAG_THRESHOLD) dragging = true;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggedBar != null && button == 0) {
            draggedBar = null;
            return true;
        }
        if (pressed != null && button == 0) {
            HudPanel.Slot source = pressed;
            boolean dragged = dragging;
            pressed = null;
            dragging = false;
            if (!dragged) {
                if (source.action() != null) {
                    source.action().run();
                    playClick();
                }
            } else {
                HudPanel.Slot target = slotAt(mouseX, mouseY);
                if (target != null && target.drop() != null) {
                    target.drop().accept(source.stack().copyWithCount(1));
                    playClick();
                }
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) {
            for (HudPanel.Scroll scroll : scrolls) {
                if (!scroll.contains(mouseX, mouseY)) continue;
                (scrollY > 0 ? scroll.plus() : scroll.minus()).run();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /** A non-empty slot that is not itself a drop target, on a panel that has drop targets. */
    private HudPanel.Slot draggableAt(double mouseX, double mouseY) {
        if (slotAreas.stream().noneMatch(area -> area.slot().drop() != null)) return null;
        HudPanel.Slot slot = slotAt(mouseX, mouseY);
        return slot != null && slot.drop() == null && !slot.stack().isEmpty() ? slot : null;
    }

    private HudPanel.Slot slotAt(double mouseX, double mouseY) {
        for (HudPanel.SlotArea area : slotAreas) {
            if (area.contains(mouseX, mouseY)) return area.slot();
        }
        return null;
    }

    private static void playClick() {
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    protected static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    @Override public boolean isPauseScreen() { return false; }
}
