package com.mathgulag.client;

import com.mathgulag.net.StatePacket;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.mojang.brigadier.arguments.FloatArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

public class GulagClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(StatePacket.TYPE, (payload, context) ->
                context.client().execute(() -> ClientState.apply(payload)));

        ClientTickEvents.END_CLIENT_TICK.register(client -> ClientState.clientTick());

        FirstPersonSkull.register();
        // ajuste en vivo de la calavera: /skull yaw 90 , /skull scale 0.6 , /skull drop 14
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("skull")
                        .then(ClientCommandManager.literal("yaw").then(ClientCommandManager.argument("valor", FloatArgumentType.floatArg())
                                .executes(ctx -> {
                                    SkullLayer.yaw = FloatArgumentType.getFloat(ctx, "valor");
                                    return 1;
                                })))
                        .then(ClientCommandManager.literal("scale").then(ClientCommandManager.argument("valor", FloatArgumentType.floatArg(0.05f, 3f))
                                .executes(ctx -> {
                                    SkullLayer.scale = FloatArgumentType.getFloat(ctx, "valor");
                                    return 1;
                                })))
                        .then(ClientCommandManager.literal("drop").then(ClientCommandManager.argument("valor", FloatArgumentType.floatArg(-20f, 40f))
                                .executes(ctx -> {
                                    SkullLayer.drop = FloatArgumentType.getFloat(ctx, "valor");
                                    return 1;
                                })))));
        // calavera sobre la cabeza del portador de la bomba
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
            if (entityRenderer instanceof PlayerRenderer playerRenderer) {
                helper.register(new SkullLayer(playerRenderer));
            }
        });

        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            Minecraft mc = Minecraft.getInstance();
            GulagRenderer.renderOverlay(drawContext, 0f,
                    mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        });
    }
}
