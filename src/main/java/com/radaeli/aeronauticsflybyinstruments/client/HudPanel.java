package com.radaeli.aeronauticsflybyinstruments.client;

import com.radaeli.ruilib.ui.Bounds;
import com.radaeli.ruilib.ui.Radio;
import com.radaeli.ruilib.ui.UiContext;
import com.radaeli.ruilib.ui.UiPaint;
import com.radaeli.ruilib.ui.UiPalette;
import com.radaeli.ruilib.ui.UiTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Immediate-mode panel in the mod's screen language (visual guide §12, after the Exotic Pets
 * inspector HUD): dark panel, 1 px outline, 2 px accent strip, muted labels on the left, values
 * right-aligned and ellipsized, short uppercase section titles. Screens and the in-world label
 * build it every frame; clickable lines report their rectangles for the last frame drawn.
 */
public final class HudPanel {
    public static final int PAD = 6;
    public static final int ROW = 11;
    /** Item slot: a 16 px icon with one pixel of frame on each side. */
    public static final int SLOT = 18;
    /** Text field line: a 13 px box and one pixel of air below it. */
    private static final int FIELD = 15;
    private static final int SECTION_GAP = 5;
    /** Gap between stacked slot grids, like the inventory and the hotbar. */
    private static final int GRID_GAP = 3;
    private static final long HIGHLIGHT_MILLIS = 2000;

    public static UiPalette palette() { return UiTheme.DARK.palette(); }

    private sealed interface Line permits Header, Highlight, Section, Row, Choice, Stepper, Buttons, Note, Items, Field, Scrolled {}
    private record Header(String title, String meta) implements Line {}
    private record Highlight(String text, int color) implements Line {}
    private record Section(String title) implements Line {}
    private record Row(String label, String value, int color, Runnable action) implements Line {}
    private record Choice(String label, String note, boolean selected, boolean enabled, Runnable action,
                          Runnable secondary) implements Line {}
    private record Stepper(String label, String value, Runnable minus, Runnable plus) implements Line {}
    private record Buttons(List<String> labels, List<Runnable> actions) implements Line {}
    private record Note(String text) implements Line {}
    /** Text input: the screen owns the text and the focus; {@code focus} answers a click on the box. */
    private record Field(String label, String value, String placeholder, boolean focused, boolean invalid,
                         Runnable focus) implements Line {}
    /** One-row lines of {@code content} shown {@code visible} at a time from {@code offset}. */
    private record Scrolled(HudPanel content, int visible, int offset, IntConsumer scrollTo) implements Line {
        int rows() { return content.lines.size(); }
        int shown() { return Math.min(visible, rows()); }
        int first() { return Math.clamp(offset, 0, Math.max(0, rows() - visible)); }
    }
    /** {@code columns} 0: one row when labelled, otherwise as many as fit. */
    private record Items(String label, List<Slot> slots, String note, int noteColor, int columns) implements Line {}

    /**
     * One item slot. {@code background} 0 draws the plain button colour; {@code clear} answers a
     * right click; {@code tooltip} replaces the item name when the slot is empty; {@code drop}, when
     * set, makes the slot accept an item dragged from another slot of the same panel.
     */
    public record Slot(ItemStack stack, int background, boolean selected, Runnable action, Runnable clear,
                       String tooltip, Consumer<ItemStack> drop) {
        public Slot(ItemStack stack, int background, boolean selected, Runnable action, Runnable clear,
                    String tooltip) {
            this(stack, background, selected, action, clear, tooltip, null);
        }
    }

    /** Where a slot was drawn in the last frame, for drag and drop. */
    public record SlotArea(int x0, int y0, int x1, int y1, Slot slot) {
        boolean contains(double x, double y) { return x >= x0 && x < x1 && y >= y0 && y < y1; }
    }

    /** A stepper line of the last frame: the mouse wheel over it steps the value. */
    public record Scroll(int x0, int y0, int x1, int y1, Runnable minus, Runnable plus) {
        boolean contains(double x, double y) { return x >= x0 && x < x1 && y >= y0 && y < y1; }
    }

    /** Scrollbar track of a scrolled list in the last frame: pressing or dragging on it scrolls. */
    public record ScrollBar(int x0, int y0, int x1, int y1, int rows, int visible, IntConsumer scrollTo) {
        boolean contains(double x, double y) { return x >= x0 - 2 && x < x1 + 2 && y >= y0 && y < y1; }

        /** Scrolls so the thumb centres on the mouse. */
        void dragTo(double y) {
            int thumb = thumbHeight(y1 - y0, rows, visible);
            double travel = Math.max(1, y1 - y0 - thumb);
            double fraction = Math.clamp((y - y0 - thumb / 2.0) / travel, 0, 1);
            scrollTo.accept((int) Math.round(fraction * (rows - visible)));
        }
    }

    /** Click target of the last frame, in screen coordinates; {@code secondary} answers a right click. */
    public record Hit(int x0, int y0, int x1, int y1, Runnable action, Runnable secondary) {
        public Hit(int x0, int y0, int x1, int y1, Runnable action) { this(x0, y0, x1, y1, action, null); }
        boolean contains(double x, double y) { return x >= x0 && x < x1 && y >= y0 && y < y1; }
    }

    private final List<Line> lines = new ArrayList<>();
    private final Map<Integer, String> tooltips = new HashMap<>();
    private String hoveredTooltip;
    private final List<SlotArea> slotAreas = new ArrayList<>();
    private final List<Scroll> scrolls = new ArrayList<>();
    private final List<ScrollBar> scrollBars = new ArrayList<>();
    private final int width;
    private int accent;

    public HudPanel(int width) {
        this.width = width;
        this.accent = palette().accent();
    }

    public HudPanel accent(int color) { accent = color; return this; }
    public HudPanel header(String title, String meta) { lines.add(new Header(title, meta)); return this; }
    public HudPanel highlight(String text, int color) { lines.add(new Highlight(text, color)); return this; }
    public HudPanel section(String title) { lines.add(new Section(title)); return this; }
    public HudPanel row(String label, String value) { return row(label, value, palette().text(), null); }
    public HudPanel row(String label, String value, int color, Runnable action) {
        lines.add(new Row(label, value, color, action));
        return this;
    }
    public HudPanel choice(String label, String note, boolean selected, boolean enabled, Runnable action) {
        return choice(label, note, selected, enabled, action, null);
    }
    /** A choice whose right click runs {@code secondary} (editing the option, for instance). */
    public HudPanel choice(String label, String note, boolean selected, boolean enabled, Runnable action,
                           Runnable secondary) {
        lines.add(new Choice(label, note, selected, enabled, action, secondary));
        return this;
    }
    /** Labelled text box; {@code invalid} draws the text in the error colour. */
    public HudPanel field(String label, String value, String placeholder, boolean focused, boolean invalid,
                          Runnable focus) {
        lines.add(new Field(label, value, placeholder, focused, invalid, focus));
        return this;
    }
    public HudPanel stepper(String label, String value, Runnable minus, Runnable plus) {
        lines.add(new Stepper(label, value, minus, plus));
        return this;
    }
    public HudPanel buttons(List<String> labels, List<Runnable> actions) {
        lines.add(new Buttons(List.copyOf(labels), List.copyOf(actions)));
        return this;
    }
    public HudPanel note(String text) { lines.add(new Note(text)); return this; }
    /**
     * A list that shows at most {@code visibleRows} of the one-row lines {@code content} adds (rows,
     * choices, notes), starting at row {@code offset}; the mouse wheel and the scrollbar call
     * {@code scrollTo} with the new first row, already clamped. Tooltips added inside stay per row.
     */
    public HudPanel scroll(int visibleRows, int offset, IntConsumer scrollTo, Consumer<HudPanel> content) {
        HudPanel inner = new HudPanel(width);
        content.accept(inner);
        lines.add(new Scrolled(inner, Math.max(1, visibleRows), offset, scrollTo));
        return this;
    }
    /** A labelled strip of item slots; without a label the slots fill the width and wrap. */
    public HudPanel items(String label, List<Slot> slots, String note, int noteColor) {
        lines.add(new Items(label, List.copyOf(slots), note, noteColor, 0));
        return this;
    }
    /** Unlabelled grid of item slots with a fixed column count (an inventory is 9 wide). */
    public HudPanel grid(List<Slot> slots, int columns) {
        lines.add(new Items("", List.copyOf(slots), "", 0, columns));
        return this;
    }
    /** Hover text of the line added last (the guide keeps help in tooltips, never on the panel). */
    public HudPanel tooltip(String text) {
        if (!lines.isEmpty() && text != null && !text.isEmpty()) tooltips.put(lines.size() - 1, text);
        return this;
    }

    public int width() { return width; }

    /** Tooltip under the mouse in the last frame drawn, or null. */
    public String hoveredTooltip() { return hoveredTooltip; }
    public List<SlotArea> slotAreas() { return List.copyOf(slotAreas); }
    public List<Scroll> scrolls() { return List.copyOf(scrolls); }
    public List<ScrollBar> scrollBars() { return List.copyOf(scrollBars); }

    /**
     * Right edge of a line's tooltip area. Lines with a value or slots keep the tooltip on the
     * label, so it never covers the value or the arrows the mouse is about to click.
     */
    private static int tooltipRight(Line line, Font font, int left, int right, int inner) {
        return switch (line) {
            case Stepper stepper -> com.radaeli.ruilib.ui.Stepper.layout(font, UiTheme.DARK,
                    stepperBounds(left, right, 0), Component.literal(stepper.label()),
                    Component.literal(stepper.value())).labelRight();
            case Row row -> left + font.width(fit(font, row.label(), inner / 2)) + 2;
            case Field field -> left + font.width(fit(font, field.label(), inner / 3)) + 2;
            case Items items -> items.label().isEmpty() ? right + 2
                    : left + font.width(fit(font, items.label(), inner / 3)) + 2;
            default -> right + 2;
        };
    }

    private int lineHeight(Line line) {
        return switch (line) {
            case Buttons ignored -> 16;
            case Header ignored -> 12;
            case Field ignored -> FIELD;
            case Scrolled scrolled -> scrolled.shown() * ROW;
            case Items items -> itemRows(items) * (SLOT + 1);
            default -> ROW;
        };
    }

    private int itemColumns(Items items) {
        if (items.columns() > 0) return items.columns();
        return items.label().isEmpty() ? Math.max(1, (width - 2 * PAD - 2 + 1) / (SLOT + 1))
                : Math.max(1, items.slots().size());
    }

    private int itemRows(Items items) {
        return Math.max(1, (items.slots().size() + itemColumns(items) - 1) / itemColumns(items));
    }

    public int height() {
        int height = PAD;
        Line previous = null;
        for (Line line : lines) {
            if (line instanceof Section && previous != null) height += SECTION_GAP;
            if (line instanceof Buttons) height += 4;
            if (line instanceof Items && previous instanceof Items) height += GRID_GAP;
            height += lineHeight(line);
            previous = line;
        }
        return height + PAD - 2;
    }

    /**
     * Draws at (x0, y0) and returns the click targets. {@code changes} lights values that just changed
     * (pass null for static drawing such as the in-world label).
     */
    public List<Hit> draw(GuiGraphics g, Font font, int x0, int y0, double mouseX, double mouseY, Changes changes) {
        UiPalette palette = palette();
        int height = height();
        g.fill(x0, y0, x0 + width, y0 + height, withAlpha(palette.panel(), 0xE6));
        g.renderOutline(x0, y0, width, height, withAlpha(palette.border(), 0xFF));
        g.fill(x0, y0, x0 + 2, y0 + height, accent);
        int left = x0 + PAD + 2, right = x0 + width - PAD, inner = right - left;
        int y = y0 + PAD;
        List<Hit> hits = new ArrayList<>();
        hoveredTooltip = null;
        slotAreas.clear();
        scrolls.clear();
        scrollBars.clear();
        Map<String, Integer> seenLabels = new HashMap<>();
        Line previous = null;
        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            Line line = lines.get(lineIndex);
            if (line instanceof Section && previous != null) y += SECTION_GAP;
            if (line instanceof Items && previous instanceof Items) y += GRID_GAP;
            String tooltip = tooltips.get(lineIndex);
            if (tooltip != null && !(line instanceof Scrolled) && mouseX >= left - 4 && mouseX < tooltipRight(line, font, left, right, inner)
                    && mouseY >= y - 1 && mouseY < y + lineHeight(line) - 1) {
                hoveredTooltip = tooltip;
            }
            y = drawLine(line, g, font, left, right, y, mouseX, mouseY, changes, hits, seenLabels);
            previous = line;
        }
        return hits;
    }

    /** Draws one line with its text on {@code y} between {@code left} and {@code right}; returns the next y. */
    private int drawLine(Line line, GuiGraphics g, Font font, int left, int right, int y, double mouseX, double mouseY,
                         Changes changes, List<Hit> hits, Map<String, Integer> seenLabels) {
        UiPalette palette = palette();
        int inner = right - left;
        switch (line) {
            case Header header -> {
                int metaWidth = font.width(header.meta());
                g.drawString(font, fit(font, header.title(), inner - metaWidth - 6), left, y, palette.text(), false);
                g.drawString(font, header.meta(), right - metaWidth, y, palette.mutedText(), false);
                y += 12;
            }
            case Highlight highlight -> {
                g.drawString(font, fit(font, "» " + highlight.text(), inner), left, y, highlight.color(), false);
                y += ROW;
            }
            case Section section -> {
                g.fill(left, y - 3, right, y - 2, palette.divider());
                g.drawString(font, fit(font, section.title().toUpperCase(java.util.Locale.ROOT), inner), left, y,
                        palette.mutedText(), false);
                y += ROW;
            }
            case Row row -> {
                boolean hover = row.action() != null && mouseX >= left - 4 && mouseX < right + 2
                        && mouseY >= y - 1 && mouseY < y + ROW - 1;
                if (hover) g.fill(left - 4, y - 1, right + 2, y + ROW - 1, withAlpha(palette.buttonHover(), 0x80));
                // Rows may share a label (two auxiliary scanners): key by label and occurrence.
                int occurrence = seenLabels.merge(row.label(), 1, Integer::sum);
                float changed = changes == null ? 0
                        : changes.intensity(row.label() + "#" + occurrence, row.value());
                if (changed > 0) g.fill(left - 5, y, left - 3, y + 8, withAlpha(palette.warning(), (int) (0xFF * changed)));
                String label = fit(font, row.label(), inner / 2);
                g.drawString(font, label, left, y, palette.mutedText(), false);
                int valueLeft = left + font.width(label) + 6;
                String value = fit(font, row.value(), right - valueLeft);
                g.drawString(font, value, right - font.width(value), y, lerp(row.color(), palette.warning(), changed), false);
                if (row.action() != null) hits.add(new Hit(left - 4, y - 1, right + 2, y + ROW - 1, row.action()));
                y += ROW;
            }
            case Choice choice -> {
                boolean hover = choice.enabled() && choice.action() != null && mouseX >= left - 4
                        && mouseX < right + 2 && mouseY >= y - 1 && mouseY < y + ROW - 1;
                if (hover) g.fill(left - 4, y - 1, right + 2, y + ROW - 1, withAlpha(palette.buttonHover(), 0x80));
                int color = !choice.enabled() ? palette.disabled() : choice.selected() ? palette.text() : palette.mutedText();
                UiPaint.radio(new UiContext(g, font, UiTheme.DARK, mouseX, mouseY, g.guiWidth(), g.guiHeight()),
                        new Bounds(left, y, Radio.INDICATOR, Radio.INDICATOR), choice.selected(), hover,
                        choice.enabled(), false);
                int labelLeft = left + Radio.INDICATOR + 4;
                int noteWidth = font.width(choice.note());
                g.drawString(font, fit(font, choice.label(), right - labelLeft - noteWidth - 6), labelLeft, y, color, false);
                g.drawString(font, choice.note(), right - noteWidth, y,
                        choice.enabled() ? palette.mutedText() : palette.disabled(), false);
                if (choice.enabled() && (choice.action() != null || choice.secondary() != null)) {
                    hits.add(new Hit(left - 4, y - 1, right + 2, y + ROW - 1, choice.action(),
                            choice.secondary()));
                }
                y += ROW;
            }
            case Stepper stepper -> {
                // Drawn by R-UI Lib's stepper so the HUD and component screens share one look.
                Bounds row = stepperBounds(left, right, y);
                var context = new UiContext(g, font, UiTheme.DARK, (int) mouseX, (int) mouseY, g.guiWidth(),
                        g.guiHeight());
                var layout = com.radaeli.ruilib.ui.Stepper.layout(font, UiTheme.DARK, row,
                        Component.literal(stepper.label()), Component.literal(stepper.value()));
                com.radaeli.ruilib.ui.Stepper.paint(context, row, Component.literal(stepper.label()),
                        Component.literal(stepper.value()), true, 0);
                int slack = com.radaeli.ruilib.ui.Stepper.ARROW_SLACK;
                hits.add(new Hit(layout.minus().x() - slack, y - 1, layout.minus().right() + slack, y + ROW - 1,
                        stepper.minus()));
                hits.add(new Hit(layout.plus().x() - slack, y - 1, layout.plus().right() + slack, y + ROW - 1,
                        stepper.plus()));
                scrolls.add(new Scroll(left - 4, y - 1, right + 3, y + ROW - 1, stepper.minus(), stepper.plus()));
                y += ROW;
            }
            case Buttons buttons -> {
                y += 4;
                int gap = 4, count = buttons.labels().size();
                int buttonWidth = (inner - gap * (count - 1)) / Math.max(1, count);
                for (int index = 0; index < count; index++) {
                    int bx = left + index * (buttonWidth + gap);
                    boolean hover = mouseX >= bx && mouseX < bx + buttonWidth && mouseY >= y && mouseY < y + 14;
                    g.fill(bx, y, bx + buttonWidth, y + 14, hover ? palette.buttonHover() : palette.button());
                    g.renderOutline(bx, y, buttonWidth, 14, palette.border());
                    String label = fit(font, buttons.labels().get(index), buttonWidth - 6);
                    g.drawString(font, label, bx + (buttonWidth - font.width(label)) / 2, y + 3, palette.text(), false);
                    hits.add(new Hit(bx, y, bx + buttonWidth, y + 14, buttons.actions().get(index)));
                }
                y += 16;
            }
            case Field field -> {
                String label = fit(font, field.label(), inner / 3);
                g.drawString(font, label, left, y + 2, palette.mutedText(), false);
                int boxLeft = left + inner / 3 + 4;
                Bounds box = new Bounds(boxLeft, y - 1, right - boxLeft, FIELD - 2);
                boolean hover = box.contains(mouseX, mouseY);
                UiPaint.input(new UiContext(g, font, UiTheme.DARK, (int) mouseX, (int) mouseY, g.guiWidth(),
                        g.guiHeight()), box, hover, field.focused(), true);
                int textWidth = box.width() - 8;
                boolean caret = field.focused() && System.currentTimeMillis() / 500 % 2 == 0;
                if (field.value().isEmpty() && !field.focused()) {
                    g.drawString(font, fit(font, field.placeholder(), textWidth), box.x() + 4, y + 2,
                            palette.disabled(), false);
                } else {
                    // Typing happens at the end, so a long value shows its tail.
                    String shown = font.plainSubstrByWidth(field.value(), textWidth - 6, true);
                    int color = field.invalid() ? palette.error() : palette.text();
                    g.drawString(font, shown, box.x() + 4, y + 2, color, false);
                    if (caret) g.drawString(font, "_", box.x() + 4 + font.width(shown), y + 2, palette.text(), false);
                }
                if (field.focus() != null) hits.add(new Hit(box.x(), box.y(), box.right(), box.bottom(), field.focus()));
                y += FIELD;
            }
            case Scrolled scrolled -> {
                int first = scrolled.first(), rows = scrolled.rows(), top = y;
                boolean scrollable = rows > scrolled.visible();
                int contentRight = scrollable ? right - 5 : right;
                for (int row = first; row < first + scrolled.shown(); row++) {
                    String tooltip = scrolled.content().tooltips.get(row);
                    if (tooltip != null && mouseX >= left - 4 && mouseX < contentRight + 2
                            && mouseY >= y - 1 && mouseY < y + ROW - 1) {
                        hoveredTooltip = tooltip;
                    }
                    y = drawLine(scrolled.content().lines.get(row), g, font, left, contentRight, y, mouseX, mouseY,
                            changes, hits, seenLabels);
                }
                if (scrollable) {
                    int trackTop = top - 1, trackHeight = y - top;
                    int thumb = thumbHeight(trackHeight, rows, scrolled.visible());
                    int thumbTop = trackTop + Math.round((trackHeight - thumb) * first / (float) (rows - scrolled.visible()));
                    ScrollBar bar = new ScrollBar(right - 2, trackTop, right, trackTop + trackHeight, rows,
                            scrolled.visible(), scrolled.scrollTo());
                    g.fill(right - 2, trackTop, right, trackTop + trackHeight, palette.divider());
                    g.fill(right - 2, thumbTop, right, thumbTop + thumb,
                            bar.contains(mouseX, mouseY) ? palette.text() : palette.mutedText());
                    scrollBars.add(bar);
                    int last = rows - scrolled.visible();
                    // HudScreen runs plus on wheel up, which moves the list towards its first row.
                    scrolls.add(new Scroll(left - 4, trackTop, right + 2, trackTop + trackHeight,
                            () -> scrolled.scrollTo().accept(Math.min(last, first + 1)),
                            () -> scrolled.scrollTo().accept(Math.max(0, first - 1))));
                }
            }
            case Note note -> {
                g.drawString(font, fit(font, note.text(), inner), left, y, withAlpha(palette.mutedText(), 0xB0), false);
                y += ROW;
            }
            case Items items -> {
                int slotsLeft = left;
                int textY = y + (SLOT - 8) / 2;
                if (!items.label().isEmpty()) {
                    String label = fit(font, items.label(), inner / 3);
                    g.drawString(font, label, left, textY, palette.mutedText(), false);
                    slotsLeft = left + font.width(label) + 6;
                }
                int columns = itemColumns(items);
                for (int slotIndex = 0; slotIndex < items.slots().size(); slotIndex++) {
                    Slot slot = items.slots().get(slotIndex);
                    int sx = slotsLeft + slotIndex % columns * (SLOT + 1);
                    int sy = y + slotIndex / columns * (SLOT + 1);
                    boolean hover = mouseX >= sx && mouseX < sx + SLOT && mouseY >= sy && mouseY < sy + SLOT;
                    int background = slot.background() != 0 ? slot.background() : palette.button();
                    g.fill(sx, sy, sx + SLOT, sy + SLOT, hover ? lerp(background, palette.buttonHover(), 0.5f) : background);
                    g.renderOutline(sx, sy, SLOT, SLOT, slot.selected() ? palette.text() : palette.border());
                    if (!slot.stack().isEmpty()) g.renderItem(slot.stack(), sx + 1, sy + 1);
                    slotAreas.add(new SlotArea(sx, sy, sx + SLOT, sy + SLOT, slot));
                    if (slot.action() != null || slot.clear() != null) {
                        hits.add(new Hit(sx, sy, sx + SLOT, sy + SLOT, slot.action(), slot.clear()));
                    }
                    if (hover) {
                        String name = slot.stack().isEmpty() ? slot.tooltip()
                                : slot.stack().getHoverName().getString();
                        if (name != null && !name.isEmpty()) hoveredTooltip = name;
                    }
                }
                if (!items.note().isEmpty()) {
                    int notesLeft = slotsLeft + Math.min(columns, items.slots().size()) * (SLOT + 1) + 4;
                    String note = fit(font, items.note(), right - notesLeft);
                    g.drawString(font, note, right - font.width(note), textY, items.noteColor(), false);
                }
                y += lineHeight(items);
            }
        }
        return y;
    }

    static int thumbHeight(int track, int rows, int visible) {
        return Math.max(6, Math.round(track * visible / (float) Math.max(1, rows)));
    }

    /** A stepper row as R-UI Lib lays it out: text on line {@code y}, the full panel width. */
    private static Bounds stepperBounds(int left, int right, int y) {
        return new Bounds(left, y - 1, right - left, ROW - 1);
    }

    public static boolean click(List<Hit> hits, double x, double y) {
        return click(hits, x, y, 0);
    }

    /** Runs the target under the mouse: left button the action, right button the secondary one. */
    public static boolean click(List<Hit> hits, double x, double y, int button) {
        for (Hit hit : hits) {
            if (!hit.contains(x, y)) continue;
            Runnable target = button == 1 ? hit.secondary() : button == 0 ? hit.action() : null;
            if (target == null) return false;
            target.run();
            return true;
        }
        return false;
    }

    static String fit(Font font, String text, int maxWidth) {
        if (maxWidth <= 0) return "";
        if (font.width(text) <= maxWidth) return text;
        String ellipsis = "…";
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width(ellipsis))) + ellipsis;
    }

    public static int withAlpha(int argb, int alpha) {
        return (alpha & 0xFF) << 24 | (argb & 0xFFFFFF);
    }

    static int lerp(int from, int to, float t) {
        if (t <= 0) return from;
        int result = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int a = from >>> shift & 0xFF;
            int b = to >>> shift & 0xFF;
            result |= Math.round(a + (b - a) * t) << shift;
        }
        return result;
    }

    /** Lights a row's value in the warning colour for two seconds after it changes, then fades. */
    public static final class Changes {
        private final Map<String, String> values = new HashMap<>();
        private final Map<String, Long> changedAt = new HashMap<>();

        float intensity(String key, String value) {
            long now = System.currentTimeMillis();
            String previous = values.put(key, value);
            if (previous != null && !previous.equals(value)) changedAt.put(key, now);
            Long at = changedAt.get(key);
            if (at == null) return 0;
            float remaining = 1 - (now - at) / (float) HIGHLIGHT_MILLIS;
            if (remaining <= 0) changedAt.remove(key);
            return Math.max(0, remaining);
        }
    }
}
