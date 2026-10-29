package com.radaeli.aeronauticsflybyinstruments.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.radaeli.aeronauticsflybyinstruments.AeronauticsFlyByInstruments;
import com.radaeli.aeronauticsflybyinstruments.panel.LampIndication;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.model.data.ModelData;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Reuses the authored models, keeping the opaque housing out of the emissive pass. */
final class LampModuleRenderer {
    static final RenderType EMISSIVE = LampRenderTypes.EMISSIVE;
    // Shared by the 1x1 and 2x1 models, red and amber: glass tube (lens) and filament (emitter).
    private static final Set<ResourceLocation> LENS = Set.of(texture("lamp_glass_red"), texture("lamp_glass_amber"));
    private static final Set<ResourceLocation> EMITTER = Set.of(texture("lamp_filament_on"),
            texture("lamp_filament_off"), texture("lamp_filament_on_amber"));

    /** Item model variants by custom model data: 0 red off, 1 red on, 2 amber off, 3 amber on. */
    private final ItemStack[] stacks = new ItemStack[4];
    private final Layers[] layers = new Layers[4];

    LampModuleRenderer(Item item) {
        for (int index = 0; index < stacks.length; index++) {
            stacks[index] = new ItemStack(item);
            if (index > 0) stacks[index].set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(index));
        }
    }

    void render(int intensity, LampIndication.Color color, Level level, PoseStack poses, MultiBufferSource buffers,
                int light, int overlay) {
        boolean lit = intensity > 0;
        int variant = (color == LampIndication.Color.AMBER ? 2 : 0) + (lit ? 1 : 0);
        var renderer = Minecraft.getInstance().getItemRenderer();
        var stack = stacks[variant];
        var model = renderer.getModel(stack, level, null, 0);
        var cached = layers[variant];
        // Model identity changes on resource reload: never retain quads from the old atlas.
        if (cached == null || cached.model() != model) {
            cached = Layers.from(model);
            layers[variant] = cached;
        }

        var opaque = buffers.getBuffer(Sheets.cutoutBlockSheet());
        renderer.renderQuadList(poses, opaque, cached.housing(), stack, light, overlay);
        if (lit) {
            // The core and tinted lens share a sorted translucent pass. This preserves
            // visibility through the glass while the shader ignores ambient light.
            var emissive = buffers.getBuffer(EMISSIVE);
            float brightness = intensity / 15.0f;
            for (var quad : cached.luminous()) {
                emissive.putBulkData(poses.last(), quad, brightness, brightness, brightness, 1.0f,
                        LightTexture.FULL_BRIGHT, overlay);
            }
        } else {
            renderer.renderQuadList(poses, opaque, cached.emitter(), stack, light, overlay);
            renderer.renderQuadList(poses, buffers.getBuffer(Sheets.translucentCullBlockSheet()),
                    cached.lens(), stack, light, overlay);
        }
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(AeronauticsFlyByInstruments.MOD_ID, "block/" + name);
    }

    private record Layers(BakedModel model, List<BakedQuad> housing, List<BakedQuad> lens,
                          List<BakedQuad> emitter, List<BakedQuad> luminous) {
        static Layers from(BakedModel model) {
            var quads = new ArrayList<BakedQuad>();
            var random = RandomSource.create(42);
            for (var direction : Direction.values()) {
                random.setSeed(42);
                quads.addAll(model.getQuads(null, direction, random, ModelData.EMPTY, null));
            }
            random.setSeed(42);
            quads.addAll(model.getQuads(null, null, random, ModelData.EMPTY, null));

            var housing = new ArrayList<BakedQuad>();
            var lens = new ArrayList<BakedQuad>();
            var emitter = new ArrayList<BakedQuad>();
            for (var quad : quads) {
                var name = quad.getSprite().contents().name();
                if (LENS.contains(name)) lens.add(quad);
                else if (EMITTER.contains(name)) emitter.add(quad);
                else housing.add(quad);
            }
            var luminous = new ArrayList<>(emitter);
            luminous.addAll(lens);
            return new Layers(model, List.copyOf(housing), List.copyOf(lens), List.copyOf(emitter), List.copyOf(luminous));
        }
    }
}
