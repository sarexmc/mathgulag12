package com.mathgulag.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * HUD del gulag (estilo DEDsafio) dibujado SOLO con texturas HD: numeros de sangre, circulos con pinchos,
 * red de venas, vineta negra y humo. Todas las animaciones usan el tiempo real del fotograma (fluidas).
 */
public class GulagRenderer {
    private static final String NS = "mathgulag";
    private static final ResourceLocation[] DIGIT_R = new ResourceLocation[10];
    private static final ResourceLocation[] DIGIT_G = new ResourceLocation[10];
    /** Simbolos: 0 X, 1 +, 2 -, 3 ?, 4 = */
    private static final String[] OP_NAMES = {"x", "plus", "minus", "question", "equal"};
    private static final ResourceLocation[] OP_R = new ResourceLocation[5];
    private static final ResourceLocation[] OP_G = new ResourceLocation[5];
    private static final ResourceLocation SPIKE_R = gui("spike");
    private static final ResourceLocation SPIKE_G = gui("spike_g");
    private static final ResourceLocation VEINS = gui("veins");
    private static final ResourceLocation VIGNETTE = gui("vignette");
    private static final ResourceLocation SMOKE = gui("smoke");
    private static final ResourceLocation TINT = gui("tint");

    // tamano de cada textura en pixeles (el PNG)
    private static final int GLYPH_PX = 320, SPIKE_PX = 512, VEIN_W = 2560, VEIN_H = 1440,
            VIG_W = 960, VIG_H = 540, SMOKE_W = 512, SMOKE_H = 256;

    static {
        for (int i = 0; i < 10; i++) {
            DIGIT_R[i] = gui("digit_" + i);
            DIGIT_G[i] = gui("digit_g_" + i);
        }
        for (int i = 0; i < 5; i++) {
            OP_R[i] = gui("op_" + OP_NAMES[i]);
            OP_G[i] = gui("op_g_" + OP_NAMES[i]);
        }
    }

    private static float beatPhase;

    private static ResourceLocation gui(String name) {
        return ResourceLocation.fromNamespaceAndPath(NS, "textures/gui/" + name + ".png");
    }

    // ------------------------------------------------------------------ utilidades

    private static float time() {
        return (System.currentTimeMillis() % 3600000L) / 1000f;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static int clamp255(float v) {
        return Math.max(0, Math.min(255, (int) v));
    }

    private static int argb(int a, int r, int g, int b) {
        return (Math.max(0, Math.min(255, a)) << 24) | (clamp255(r) << 16) | (clamp255(g) << 8) | clamp255(b);
    }

    private static float heartbeat(float phase) {
        float c = phase % 1f;
        float a = (float) Math.exp(-Math.pow((c - 0.08f) / 0.05f, 2));
        float b = 0.7f * (float) Math.exp(-Math.pow((c - 0.30f) / 0.06f, 2));
        return Math.max(a, b);
    }

    private static float easeOutBack(float x) {
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        float p = x - 1f;
        return 1f + c3 * p * p * p + c1 * p * p;
    }

    /** Dibuja una textura entera centrada en (cx, cy) con tamano sx por sy, giro y color/alpha. */
    private static void sprite(GuiGraphics g, ResourceLocation tex, int tw, int th, float cx, float cy,
                               float sx, float sy, float rotDeg, float r, float gr, float b, float a) {
        if (a <= 0.004f || sx <= 0.01f || sy <= 0.01f) return;
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(cx, cy, 0f);
        if (rotDeg != 0f) ps.mulPose(Axis.ZP.rotationDegrees(rotDeg));
        ps.scale(sx / 100f, sy / 100f, 1f);
        RenderSystem.setShaderColor(r, gr, b, Math.min(1f, a));
        g.blit(tex, -50, -50, 100, 100, 0f, 0f, tw, th, tw, th);
        ps.popPose();
    }

    private static int glyphIndex(char ch) {
        return switch (ch) {
            case 'X', 'x', '*', '\u00d7' -> 0;
            case '+' -> 1;
            case '-' -> 2;
            case '?' -> 3;
            case '=' -> 4;
            default -> -1;
        };
    }

    /** Dibuja un numero o simbolo de sangre; G mezcla de rojo (0) a verde (1). */
    private static void glyph(GuiGraphics g, char ch, float cx, float cy, float size, float rot, float G, float alpha) {
        ResourceLocation red;
        ResourceLocation grn;
        if (ch >= '0' && ch <= '9') {
            red = DIGIT_R[ch - '0'];
            grn = DIGIT_G[ch - '0'];
        } else {
            int i = glyphIndex(ch);
            if (i < 0) return;
            red = OP_R[i];
            grn = OP_G[i];
        }
        sprite(g, red, GLYPH_PX, GLYPH_PX, cx, cy, size, size, rot, 1f, 1f, 1f, alpha * (1f - G));
        sprite(g, grn, GLYPH_PX, GLYPH_PX, cx, cy, size, size, rot, 1f, 1f, 1f, alpha * G);
    }

    /** Actualiza el temblor de pantalla (lo usan el HUD, la pantalla y el clic). */
    private static void updateShake(float t, int h) {
        if (ClientState.intensity < 0.01f) {
            ClientState.shakeX = 0f;
            ClientState.shakeY = 0f;
            return;
        }
        float urgency = ClientState.phase != 0
                ? 1f - clamp01(ClientState.ticksLeft / (float) ClientState.ticksTotal) : 0f;
        float low = Math.max(0f, urgency - 0.72f) / 0.28f;
        float amp = (ClientState.shakeAmount * 7f + low * 2.4f) * Math.max(0.6f, h / 540f);
        ClientState.shakeX = amp * (float) Math.sin(t * 87.3f);
        ClientState.shakeY = amp * (float) Math.sin(t * 71.7f + 1.3f);
    }

    private static void beginDraw(GuiGraphics g) {
        g.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
    }

    private static void endDraw() {
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    // ------------------------------------------------------------------ HUD (debajo de la pantalla de preguntas)

    public static void renderOverlay(GuiGraphics g, float partialTick, int w, int h) {
        ClientState.frame();
        float I = ClientState.intensity;
        float t = time();
        if (I < 0.01f) {
            ClientState.shakeX = 0f;
            ClientState.shakeY = 0f;
            beatPhase = 0f;
            return;
        }
        float G = ClientState.green;
        float dt = ClientState.dt;
        float urgency = 1f - clamp01(ClientState.ticksLeft / (float) ClientState.ticksTotal);
        beatPhase += dt * (1.2f + 2.6f * urgency * (ClientState.phase == 3 ? 0.45f : 1f));
        float beat = heartbeat(beatPhase);
        updateShake(t, h);
        float s = h / 1080f;

        beginDraw(g);
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(ClientState.shakeX, ClientState.shakeY, 0f);
        float cx = w / 2f, cy = h / 2f;

        // tinte general (rojo, y verde al acertar)
        sprite(g, TINT, 4, 4, cx, cy, w * 1.06f, h * 1.06f, 0f,
                lerp(0.85f, 0.0f, G), lerp(0.0f, 0.75f, G), lerp(0.03f, 0.12f, G), (0.26f + 0.05f * beat) * I);
        if (ClientState.hurtFlash > 0.01f) {
            sprite(g, TINT, 4, 4, cx, cy, w * 1.06f, h * 1.06f, 0f, 0.7f, 0f, 0f, 0.38f * ClientState.hurtFlash * I);
        }
        if (ClientState.wrongFlash > 0.01f) {
            sprite(g, TINT, 4, 4, cx, cy, w * 1.06f, h * 1.06f, 0f, 0.9f, 0f, 0f, 0.32f * ClientState.wrongFlash * I);
        }
        if (ClientState.correctFlash > 0.01f) {
            sprite(g, TINT, 4, 4, cx, cy, w * 1.06f, h * 1.06f, 0f, 0.15f, 1f, 0.3f, 0.30f * ClientState.correctFlash * I);
        }

        // red de venas: dos capas que respiran con el latido y se desplazan despacio
        float vr = lerp(1.0f, 0.22f, G), vg = lerp(0.16f, 1.0f, G), vb = lerp(0.20f, 0.38f, G);
        float breathe = 1.07f + 0.014f * beat + 0.012f * (float) Math.sin(t * 0.45f);
        sprite(g, VEINS, VEIN_W, VEIN_H, cx + 6f * s * (float) Math.sin(t * 0.21f), cy + 4f * s * (float) Math.cos(t * 0.17f),
                w * breathe, h * breathe, 0.5f * (float) Math.sin(t * 0.13f), vr, vg, vb,
                (0.80f + 0.20f * beat) * I);
        float breathe2 = 1.16f + 0.02f * beat + 0.02f * (float) Math.sin(t * 0.33f + 1f);
        sprite(g, VEINS, VEIN_W, VEIN_H, cx - 8f * s * (float) Math.sin(t * 0.19f), cy + 5f * s * (float) Math.sin(t * 0.23f),
                w * breathe2, h * breathe2, 180f + 0.8f * (float) Math.sin(t * 0.11f), vr, vg, vb,
                (0.34f + 0.12f * beat) * I);

        // vineta negra alrededor de la pantalla
        float vig = clamp01((0.80f + 0.12f * beat + 0.18f * urgency) * I);
        sprite(g, VIGNETTE, VIG_W, VIG_H, cx, cy, w * 1.04f, h * 1.04f, 0f, 0f, 0f, 0f, vig);
        if (urgency > 0.7f) {
            sprite(g, VIGNETTE, VIG_W, VIG_H, cx, cy, w * 1.04f, h * 1.04f, 0f, 0.1f, 0f, 0f,
                    (urgency - 0.7f) / 0.3f * 0.45f * I * (0.5f + 0.5f * beat));
        }

        if (ClientState.phase == 2) {
            // pregunta resuelta arriba (en verde, sin bloquear el golpe)
            String q = ClientState.question.replace(" ", "");
            String solved = q + "=" + ClientState.typed;
            float size = 0.095f * h * (1f + 0.12f * ClientState.correctFlash);
            float adv = 0.082f * h;
            float x0 = w / 2f - (solved.length() - 1) * adv / 2f;
            for (int i = 0; i < solved.length(); i++) {
                float pop = easeOutBack(clamp01((ClientState.qTime - i * 0.03f) / 0.3f));
                float rot = 2f * (float) Math.sin(t * 1.1f + i * 1.3f);
                glyph(g, solved.charAt(i), x0 + i * adv, h * 0.11f + 3f * s * (float) Math.sin(t * 1.4f + i),
                        size * Math.max(0.05f, Math.min(1.1f, pop)) * (1f + 0.03f * beat), rot, 1f, I);
            }
        }
        endDraw();

        if (ClientState.phase == 2) {
            float secs = Math.max(0f, ClientState.ticksLeft / 20f);
            String count = String.format(java.util.Locale.ROOT, "%.1f", secs);
            passBanner(g, "\u00a1CORRECTO!  Golpea a otro jugador para pasar la bomba  \u00b7  " + count + "s",
                    w / 2f, h - 86 * Math.max(0.8f, s), 1.55f * Math.max(0.75f, s * 1.6f), I, beat, 1f);
        } else if (ClientState.phase == 3) {
            passBanner(g, "BOMBA: " + ClientState.holder,
                    w / 2f, h - 40 * Math.max(0.8f, s), 1.15f * Math.max(0.75f, s * 1.6f), I, beat, 0f);
        }
        ps.popPose();
    }

    // ------------------------------------------------------------------ pantalla de preguntas (encima)

    public static void renderQuestionLayer(GuiGraphics g, int w, int h, int mouseX, int mouseY) {
        ClientState.frame();
        float I = clamp01(ClientState.intensity);
        if (I < 0.01f) return;
        float G = ClientState.green;
        float t = time();
        float beat = heartbeat(beatPhase);
        float s = h / 1080f;
        float dt = ClientState.dt;
        updateShake(t, h);

        beginDraw(g);
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(ClientState.shakeX, ClientState.shakeY, 0f);

        // humo oscuro detras de la pregunta
        float smokeIn = clamp01(ClientState.qTime / 0.5f);
        sprite(g, SMOKE, SMOKE_W, SMOKE_H, w / 2f, h * 0.36f, w * 0.44f, h * 0.44f * (0.9f + 0.1f * smokeIn),
                1.2f * (float) Math.sin(t * 0.35f), 0f, 0f, 0f, 0.95f * I * smokeIn);

        // ---- numeros con circulos de pinchos
        float radius = 0.125f * h;         // radio de cada circulo con pinchos
        float glyphSize = 0.082f * h;      // tamano del numero
        float kHover = 1f - (float) Math.exp(-14.0 * dt);
        for (int d = 0; d < 10; d++) {
            float spawn = clamp01((ClientState.qTime - d * 0.045f) / 0.30f);
            if (spawn <= 0f) continue;
            float ease = easeOutBack(spawn);
            float alpha = I * clamp01(spawn * 1.6f);
            float x = ClientState.cur[d][0] * w + 4f * s * (float) Math.sin(t * 0.85f + d * 1.9f);
            float y = ClientState.cur[d][1] * h + 3.5f * s * (float) Math.cos(t * 0.7f + d * 2.3f);
            double dx = mouseX - x, dy = mouseY - y;
            float hoverTarget = (Math.sqrt(dx * dx + dy * dy) < radius * 0.62f && spawn > 0.5f) ? 1f : 0f;
            ClientState.hover[d] += (hoverTarget - ClientState.hover[d]) * kHover;
            float hover = ClientState.hover[d];
            float press = ClientState.press[d];
            float sc = (0.30f + 0.70f * Math.max(0.01f, ease)) * (1f + 0.10f * hover + 0.24f * press * press);
            float pulse = 1f + 0.045f * beat;
            float dirSign = (d % 2 == 0) ? 1f : -1f;
            float rot = dirSign * (t * 7f + d * 37f) + 3f * (float) Math.sin(t * 1.3f + d);

            // estela de movimiento al reordenarse
            float vx = (ClientState.cur[d][0] - ClientState.prev[d][0]) * w;
            float vy = (ClientState.cur[d][1] - ClientState.prev[d][1]) * h;
            float speed = (float) Math.sqrt(vx * vx + vy * vy);
            if (speed > 0.4f) {
                float f = Math.min(1f, speed / 14f);
                for (int k = 1; k <= 3; k++) {
                    float back = k * 2.6f;
                    float ta = alpha * f * (0.30f / k);
                    sprite(g, SPIKE_R, SPIKE_PX, SPIKE_PX, x - vx * back, y - vy * back, 2f * radius * sc, 2f * radius * sc,
                            rot, 1f, 1f, 1f, ta * (1f - G));
                    sprite(g, SPIKE_G, SPIKE_PX, SPIKE_PX, x - vx * back, y - vy * back, 2f * radius * sc, 2f * radius * sc,
                            rot, 1f, 1f, 1f, ta * G);
                }
            }
            // onda de impacto al pulsar
            if (press > 0.02f) {
                float grow = 1f + 0.9f * (1f - press);
                sprite(g, SPIKE_R, SPIKE_PX, SPIKE_PX, x, y, 2f * radius * sc * grow, 2f * radius * sc * grow,
                        -rot * 1.6f, 1f, 0.55f, 0.55f, press * 0.6f * alpha * (1f - G));
                sprite(g, SPIKE_G, SPIKE_PX, SPIKE_PX, x, y, 2f * radius * sc * grow, 2f * radius * sc * grow,
                        -rot * 1.6f, 0.7f, 1f, 0.7f, press * 0.6f * alpha * G);
            }
            float size = 2f * radius * sc * pulse * (1f + 0.04f * hover);
            sprite(g, SPIKE_R, SPIKE_PX, SPIKE_PX, x, y, size, size, rot, 1f, 1f, 1f, alpha * (1f - G));
            sprite(g, SPIKE_G, SPIKE_PX, SPIKE_PX, x, y, size, size, rot, 1f, 1f, 1f, alpha * G);

            float tilt = 4f * (float) Math.sin(t * 0.9f + d * 1.7f) + (d % 3 - 1) * 2f;
            glyph(g, (char) ('0' + d), x, y, glyphSize * sc * (1f + 0.05f * beat) * (1f + 0.10f * hover),
                    tilt, G, alpha);
        }

        // ---- pregunta "2   X   8"
        float qAge = ClientState.qTime;
        float spread = 0.145f * h;
        float qSize = 0.15f * h;
        String[] parts = ClientState.question.trim().split("\\s+");
        float qy = h * 0.27f;
        if (parts.length == 3) {
            for (int i = 0; i < 3; i++) {
                float e = easeOutBack(clamp01((qAge - i * 0.07f) / 0.34f));
                float a = I * clamp01((qAge - i * 0.07f) / 0.18f);
                float px = w / 2f + (i - 1) * spread;
                float py = qy + 4f * s * (float) Math.sin(t * 0.8f + i * 1.9f);
                float rot = (i - 1) * 2.0f + 2.4f * (float) Math.sin(t * 0.9f + i * 1.7f);
                char ch = parts[i].charAt(0);
                float sz = qSize * Math.max(0.05f, e) * (1f + 0.035f * beat);
                if (i == 1) sz *= 0.92f;
                // los numeros de dos cifras se ensanchan
                if (i != 1 && parts[i].length() > 1) {
                    for (int k = 0; k < parts[i].length(); k++) {
                        float off = (k - (parts[i].length() - 1) / 2f) * qSize * 0.55f;
                        glyph(g, parts[i].charAt(k), px + off, py, sz * 0.85f, rot, G, a);
                    }
                } else {
                    glyph(g, ch, px, py, sz, rot, G, a);
                }
            }
        }

        // ---- respuesta tecleada, debajo de la pregunta (dentro del humo)
        String typed = ClientState.typed;
        float ay = h * 0.425f;
        float aSize = 0.11f * h * Math.max(0.05f, easeOutBack(clamp01((qAge - 0.2f) / 0.35f)));
        float aAlpha = I * clamp01((qAge - 0.2f) / 0.2f);
        float flashG = Math.max(G, ClientState.answerFlash);
        if (typed.isEmpty()) {
            float throb = 1f + 0.06f * (float) Math.sin(t * 3.2f) + 0.04f * beat;
            glyph(g, '?', w / 2f, ay, aSize * throb, 3f * (float) Math.sin(t * 1.1f), G, aAlpha * 0.9f);
        } else {
            float adv = 0.075f * h;
            float x0 = w / 2f - (typed.length() - 1) * adv / 2f;
            for (int i = 0; i < typed.length(); i++) {
                float pop = (i == typed.length() - 1) ? 1f + 0.22f * ClientState.press[Math.max(0, typed.charAt(i) - '0')] : 1f;
                glyph(g, typed.charAt(i), x0 + i * adv, ay + 2f * s * (float) Math.sin(t * 1.6f + i),
                        aSize * (1f + 0.10f * ClientState.answerFlash) * pop, 2f * (float) Math.sin(t + i * 2f),
                        flashG, aAlpha);
            }
        }
        endDraw();
        ps.popPose();
    }

    // ------------------------------------------------------------------ banner de aviso

    private static void passBanner(GuiGraphics g, String text, float cx, float cy, float scale,
                                   float I, float beat, float G) {
        Font font = Minecraft.getInstance().font;
        Component c = Component.literal(text).withStyle(ChatFormatting.BOLD);
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(cx, cy, 0);
        float pop = 1f + 0.05f * beat;
        ps.scale(scale * pop, scale * pop, 1f);
        int wd = font.width(c);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;
                g.drawString(font, c, -wd / 2 + dx, dy,
                        argb((int) (170 * I), (int) lerp(76, 8, G), (int) lerp(30, 64, G), (int) lerp(6, 18, G)), false);
            }
        }
        g.drawString(font, c, -wd / 2, 0,
                argb((int) (255 * I), (int) lerp(255, 70, G), (int) lerp(176, 255, G), (int) lerp(32, 105, G)), false);
        g.drawString(font, c, -wd / 2, -1,
                argb((int) (110 * I), (int) lerp(255, 185, G), (int) lerp(224, 255, G), (int) lerp(140, 190, G)), false);
        ps.popPose();
    }
}
