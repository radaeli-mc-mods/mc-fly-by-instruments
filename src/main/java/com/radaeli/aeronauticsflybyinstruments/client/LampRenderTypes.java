package com.radaeli.aeronauticsflybyinstruments.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;

/**
 * The lit lamp pass. Same shader, blending and vertex format as the vanilla
 * {@code entityTranslucentEmissive}, which shader packs already map to their glowing-entity
 * program, but it also writes depth. The vanilla type masks depth out, so packs that draw the sky
 * and clouds from the depth buffer (Complementary, Solas) painted them over a lamp seen against
 * the sky.
 */
final class LampRenderTypes extends RenderType {
    static final RenderType EMISSIVE = create("aeronautics_fly_by_instruments_lamp_emissive",
            DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, true, true,
            CompositeState.builder().setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                    .setTextureState(new TextureStateShard(TextureAtlas.LOCATION_BLOCKS, false, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_DEPTH_WRITE).setOverlayState(OVERLAY).createCompositeState(false));

    private LampRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                            boolean affectsCrumbling, boolean sortOnUpload, Runnable setup, Runnable clear) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
    }
}
