package com.mathgulag.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;

/** En primera persona el modelo del jugador no se dibuja, asi que la calavera se dibuja aparte, siguiendo la mirada. */
public final class FirstPersonSkull {
    private FirstPersonSkull() {
    }

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null || !ClientState.hasSkull(mc.player)) return;
            Camera camera = context.camera();
            if (camera.isDetached()) return; // en tercera persona ya la dibuja SkullLayer
            PoseStack ps = context.matrixStack();
            MultiBufferSource consumers = context.consumers();
            if (ps == null || consumers == null) return;

            float time = mc.level.getGameTime() + camera.getPartialTickTime();
            ps.pushPose();
            // el origen de este espacio es la camara (los ojos); se orienta como la mirada
            ps.mulPose(Axis.YP.rotationDegrees(180f - camera.getYRot()));
            ps.mulPose(Axis.XP.rotationDegrees(-camera.getXRot()));
            ps.translate(0f, -0.214f, 0f);                                   // de los ojos al cuello
            ps.translate(0f, (0.5f + SkullLayer.LIFT) * 0.9375f, 0f);        // parte alta de la cabeza
            ps.mulPose(Axis.YP.rotationDegrees(SkullLayer.yaw));
            float sc = SkullLayer.scale * 0.9375f;
            ps.scale(sc, sc, sc);
            ps.translate(0f, -SkullLayer.drop / 16f, 0f);
            SkullModel.render(ps, consumers, LightTexture.FULL_BRIGHT, time);
            ps.popPose();
            if (consumers instanceof MultiBufferSource.BufferSource bs) bs.endBatch();
        });
    }
}
