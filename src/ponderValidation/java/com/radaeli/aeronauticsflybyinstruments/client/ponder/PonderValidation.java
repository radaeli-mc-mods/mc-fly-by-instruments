package com.radaeli.aeronauticsflybyinstruments.client.ponder;

import com.mojang.logging.LogUtils;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.util.*;

/** Opt-in real-client smoke test; excluded from ordinary builds. Uses only a copied dev world. */
@EventBusSubscriber(modid = "aeronautics_fly_by_instruments", value = Dist.CLIENT)
public final class PonderValidation {
    private static List<PonderScene> scenes;
    private static final Map<net.minecraft.resources.ResourceLocation, Integer> durations = new HashMap<>();
    private static int index, wait;
    private static boolean done;
    private static boolean reloading;
    private static int tooltipTicks;
    private static boolean tooltipChecked;
    private static final class TooltipPreview extends net.minecraft.client.gui.screens.Screen {
        final net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(
                com.radaeli.aeronauticsflybyinstruments.block.ModBlocks.RADAR_ALTIMETER.get());
        TooltipPreview() { super(net.minecraft.network.chat.Component.literal("Ponder item tooltip validation")); }
        @Override public void render(net.minecraft.client.gui.GuiGraphics graphics, int x, int y, float partial) {
            renderBackground(graphics, x, y, partial);
            graphics.renderTooltip(font, stack, width / 2, height / 2);
        }
    }
    private static final class Preview extends PonderUI {
        Preview(PonderScene scene) { super(List.of(scene)); }
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (done || reloading || mc.level == null || mc.getOverlay() != null) return;
        try {
            if (!tooltipChecked && net.neoforged.fml.ModList.get().isLoaded("ponderjs")) {
                if (tooltipTicks++ == 0) { mc.setScreen(new TooltipPreview()); return; }
                if (tooltipTicks < 30) return;
                var preview = (TooltipPreview) mc.screen;
                var lines = preview.stack.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(mc.level),
                        mc.player, net.minecraft.world.item.TooltipFlag.NORMAL);
                if (lines.stream().noneMatch(c -> c.getString().toLowerCase(java.util.Locale.ROOT).contains("ponder")))
                    throw new IllegalStateException("Ponder prompt absent from actual item tooltip: " + lines);
                Screenshot.grab(mc.gameDirectory, "ponder-item-tooltip.png", mc.getMainRenderTarget(), c -> {});
                LogUtils.getLogger().info("FBI_PONDER_ITEM_TOOLTIP_PASSED {}", lines);
                tooltipChecked = true;
            }
            if (scenes == null) {
                var entries = PonderIndex.getSceneAccess().getRegisteredEntries().stream()
                        .filter(e -> e.getKey().getNamespace().equals(FbiPonderScenes.MOD)).map(Map.Entry::getValue).toList();
                if (!net.neoforged.fml.ModList.get().isLoaded("ponderjs")) {
                    if (!entries.isEmpty()) throw new IllegalStateException("Scenes registered without PonderJS");
                    done = true; LogUtils.getLogger().info("FBI_PONDER_OPTIONAL_ABSENCE_PASSED kubejs={}",
                            net.neoforged.fml.ModList.get().isLoaded("kubejs")); mc.stop(); return;
                }
                if (entries.size() != 28) throw new IllegalStateException("Expected 28 item/scene associations, got " + entries.size());
                // Entries for multiple items reuse a board but PonderJS wraps it per registration call.
                scenes = PonderIndex.getSceneAccess().compile(entries).stream()
                        .collect(java.util.stream.Collectors.toMap(PonderScene::getId, s -> s, (a,b) -> a, LinkedHashMap::new))
                        .values().stream().toList();
                if (scenes.size() != 11) throw new IllegalStateException("Expected 11 scenes, got " + scenes.size());
                for (PonderScene scene : scenes) {
                    for (int replay = 0; replay < 2; replay++) {
                        scene.begin();
                        for (int t = 0; t < scene.getTotalTime() + 5; t++) scene.tick();
                        if (!scene.isFinished()) throw new IllegalStateException("Unfinished " + scene.getId());
                        boolean hasBlocks = false;
                        for (var pos : net.minecraft.core.BlockPos.betweenClosed(0, 1, 0, 8, 6, 8))
                            if (!scene.getWorld().getBlockState(pos).isAir()) { hasBlocks = true; break; }
                        if (!hasBlocks) throw new IllegalStateException("Empty diorama " + scene.getId());
                        scene.reset();
                    }
                    LogUtils.getLogger().info("FBI_PONDER_VALIDATED {} ticks={} keyframes={}", scene.getId(), scene.getTotalTime(), scene.getKeyframeCount());
                    durations.put(scene.getId(), scene.getTotalTime());
                    if (scene.getTitle().contains("ponder.")) throw new IllegalStateException("Untranslated title " + scene.getTitle());
                }
                scenes.get(0).begin(); mc.setScreen(new Preview(scenes.get(0))); wait = 30;
                return;
            }
            if (--wait > 0) return;
            if (!(mc.screen instanceof Preview)) {
                // A real mouse/key input can open a category or close the preview during the smoke test.
                scenes.get(index).begin(); mc.setScreen(new Preview(scenes.get(index))); wait = 30; return;
            }
            var ui = (PonderUI) mc.screen;
            if (wait == 0) {
                ui.seekToTime(durations.get(ui.getActiveScene().getId()) / 2 + 60);
                LogUtils.getLogger().info("FBI_PONDER_PREVIEW {} time={} finished={}", ui.getActiveScene().getId(), ui.getActiveScene().getCurrentTime(), ui.getActiveScene().isFinished());
                wait = -1; return;
            }
            if (wait > -8) return;
            Screenshot.grab(mc.gameDirectory, "ponder-" + mc.options.languageCode + "-" + scenes.get(index).getId().getPath() + ".png",
                    mc.getMainRenderTarget(), c -> LogUtils.getLogger().info("FBI_PONDER_SCREENSHOT {}", c.getString()));
            if (++index < scenes.size()) { scenes.get(index).begin(); mc.setScreen(new Preview(scenes.get(index))); wait = 30; }
            else if (!mc.options.languageCode.equals("pt_br")) {
                LogUtils.getLogger().info("FBI_PONDER_LANGUAGE_PASSED {}", mc.options.languageCode);
                reloading = true; mc.setScreen(null);
                mc.options.languageCode = "pt_br"; mc.getLanguageManager().setSelected("pt_br");
                mc.reloadResourcePacks().thenRun(() -> mc.execute(() -> {
                    scenes = null; durations.clear(); index = 0; reloading = false;
                }));
            } else { done = true; LogUtils.getLogger().info("FBI_PONDER_ALL_PASSED en_us/pt_br/reload"); mc.stop(); }
        } catch (Throwable failure) {
            done = true; LogUtils.getLogger().error("FBI_PONDER_FAILED", failure); mc.stop();
        }
    }
}
