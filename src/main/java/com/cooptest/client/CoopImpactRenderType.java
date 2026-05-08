package com.cooptest.client;

import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.*;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public class CoopImpactRenderType {

    @Nullable private static ShaderProgram entityWhiteProgram = null;
    @Nullable private static ShaderProgram entityBlackProgram = null;

    public static void reload(ResourceManager manager) {
        if (entityWhiteProgram != null) { entityWhiteProgram.close(); entityWhiteProgram = null; }
        if (entityBlackProgram != null) { entityBlackProgram.close(); entityBlackProgram = null; }

        try {


            entityWhiteProgram = new ShaderProgram(
                    manager,
                    "cooptest_entity_white",
                    VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL
            );
        } catch (IOException e) {
            System.err.println("[CoopMod] cooptest_entity_white shader failed: " + e.getMessage());
        }

        try {
            entityBlackProgram = new ShaderProgram(
                    manager,
                    "cooptest_entity_black",
                    VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL
            );
        } catch (IOException e) {
            System.err.println("[CoopMod] cooptest_entity_black shader failed: " + e.getMessage());
        }
    }

    @Nullable public static ShaderProgram getWhiteProgram() { return entityWhiteProgram; }
    @Nullable public static ShaderProgram getBlackProgram() { return entityBlackProgram; }
    public static boolean isReady() { return entityWhiteProgram != null && entityBlackProgram != null; }

    public static RenderLayer getWhiteLayer(Identifier texture) {
        if (entityWhiteProgram == null) return RenderLayer.getEntityCutout(texture);
        return RenderLayer.of(
                "cooptest_entity_white",
                VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL,
                VertexFormat.DrawMode.QUADS,
                1536, false, false,
                RenderLayer.MultiPhaseParameters.builder()
                        .program(new RenderPhase.ShaderProgram(CoopImpactRenderType::getWhiteProgram))
                        .texture(new RenderPhase.Texture(texture, false, false))
                        .transparency(RenderPhase.NO_TRANSPARENCY)
                        .lightmap(RenderPhase.ENABLE_LIGHTMAP)
                        .overlay(RenderPhase.ENABLE_OVERLAY_COLOR)
                        .build(false)
        );
    }

    public static RenderLayer getBlackLayer(Identifier texture) {
        if (entityBlackProgram == null) return RenderLayer.getEntityCutout(texture);
        return RenderLayer.of(
                "cooptest_entity_black",
                VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL,
                VertexFormat.DrawMode.QUADS,
                1536, false, false,
                RenderLayer.MultiPhaseParameters.builder()
                        .program(new RenderPhase.ShaderProgram(CoopImpactRenderType::getBlackProgram))
                        .texture(new RenderPhase.Texture(texture, false, false))
                        .transparency(RenderPhase.NO_TRANSPARENCY)
                        .lightmap(RenderPhase.ENABLE_LIGHTMAP)
                        .overlay(RenderPhase.ENABLE_OVERLAY_COLOR)
                        .build(false)
        );
    }

    public static net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener createReloadListener() {
        return new net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return Identifier.of("cooptest", "impact_shaders");
            }
            @Override
            public void reload(ResourceManager manager) {
                CoopImpactRenderType.reload(manager);
            }
        };
    }
}