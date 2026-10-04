package com.minenorth_permis.client;

import com.minenorth_permis.net.MarkersPacket;
import com.minenorth_permis.net.MarkersPacket.Marker;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Rendu 3D des marqueurs : gros anneau vert lumineux au sol avec un mur dégradé qui pulse, des cercles qui montent,
 * un faisceau vertical visible à travers les murs (on voit le prochain point de loin) et un numéro flottant.
 */
@OnlyIn(Dist.CLIENT)
public final class WorldMarkers {
    private static final int SEGMENTS = 72;
    private static final float WALL_H = 2.4f;
    private static final float BEAM_H = 140f;

    private WorldMarkers() {}

    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        List<Marker> list = ClientState.visible(mc.level.dimension().location().toString());
        if (list.isEmpty()) return;

        PoseStack ps = e.getPoseStack();
        Camera camera = e.getCamera();
        Vec3 cam = camera.getPosition();
        float time = (mc.level.getGameTime() % 100000L) + e.getPartialTick();
        float pulse = 0.78f + 0.22f * (float) Math.sin(time * 0.15f);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder bb = tess.getBuilder();

        // --- passe 1 : géométrie avec test de profondeur (anneau au sol, mur, cercles montants, faces des cibles)
        RenderSystem.enableDepthTest();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (Marker m : list) {
            ps.pushPose();
            ps.translate(m.x() - cam.x, m.y() - cam.y, m.z() - cam.z);
            Matrix4f mat = ps.last().pose();
            if (m.type() == MarkersPacket.RING) {
                boolean faint = m.has(MarkersPacket.FAINT);
                float a = (faint ? 0.30f : 0.70f) * pulse;
                band(bb, mat, m.radius(), 0.40f, 0.06f, m.color(), a);
                if (m.has(MarkersPacket.WALL) || !faint) {
                    float h = faint ? WALL_H * 0.45f : WALL_H;
                    wall(bb, mat, m.radius(), 0.06f, h, m.color(), a * 0.85f, 0f);
                    if (!faint) {
                        for (int k = 0; k < 3; k++) {
                            float f = ((time * 0.012f) + k / 3f) % 1f;
                            band(bb, mat, m.radius(), 0.10f, 0.06f + f * h, m.color(), 0.55f * (1f - f));
                        }
                    }
                }
            } else {
                double vol = (m.x2() - m.x()) * (m.y2() - m.y()) * (m.z2() - m.z());
                float a = (vol > 8 ? 0.05f : 0.25f) * pulse;
                box(bb, mat, 0, 0, 0, (float) (m.x2() - m.x()), (float) (m.y2() - m.y()), (float) (m.z2() - m.z()), m.color(), a);
            }
            ps.popPose();
        }
        tess.end();

        // --- passe 2 : sans profondeur (faisceau + contour fantôme) : visible à travers les blocs
        RenderSystem.disableDepthTest();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (Marker m : list) {
            if (m.type() != MarkersPacket.RING) continue;
            ps.pushPose();
            ps.translate(m.x() - cam.x, m.y() - cam.y, m.z() - cam.z);
            Matrix4f mat = ps.last().pose();
            if (m.has(MarkersPacket.BEAM)) {
                column(bb, mat, 0.45f, BEAM_H, m.color(), 0.30f * pulse);
                column(bb, mat, 0.12f, BEAM_H, 0xFFFFFFFF, 0.55f * pulse);
            }
            if (m.has(MarkersPacket.NAV)) band(bb, mat, m.radius(), 0.25f, 0.06f, m.color(), 0.20f);
            ps.popPose();
        }
        tess.end();
        RenderSystem.enableDepthTest();

        // --- contours des boîtes (cibles / zones)
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        boolean anyBox = false;
        for (Marker m : list) {
            if (m.type() != MarkersPacket.BOX) continue;
            anyBox = true;
            VertexConsumer vc = buf.getBuffer(RenderType.lines());
            ps.pushPose();
            ps.translate(-cam.x, -cam.y, -cam.z);
            float r = ((m.color() >> 16) & 0xFF) / 255f, g = ((m.color() >> 8) & 0xFF) / 255f, b = (m.color() & 0xFF) / 255f;
            LevelRenderer.renderLineBox(ps, vc, new AABB(m.x(), m.y(), m.z(), m.x2(), m.y2(), m.z2()).inflate(0.01), r, g, b, 1f);
            ps.popPose();
        }
        if (anyBox) buf.endBatch(RenderType.lines());

        // --- étiquettes flottantes
        Font font = mc.font;
        for (Marker m : list) {
            if (m.label().isEmpty()) continue;
            double ly = m.type() == MarkersPacket.RING ? m.y() + WALL_H + 1.2 : m.y2() + 0.6;
            double lx = m.type() == MarkersPacket.RING ? m.x() : (m.x() + m.x2()) / 2;
            double lz = m.type() == MarkersPacket.RING ? m.z() : (m.z() + m.z2()) / 2;
            double dist = cam.distanceTo(new Vec3(lx, ly, lz));
            float s = 0.03f * (float) Math.max(1.0, Math.min(dist / 10.0, 8.0));
            ps.pushPose();
            ps.translate(lx - cam.x, ly - cam.y, lz - cam.z);
            ps.mulPose(camera.rotation());
            ps.scale(-s, -s, s);
            Matrix4f mat = ps.last().pose();
            float w = -font.width(m.label()) / 2f;
            font.drawInBatch(m.label(), w, 0, 0xFFFFFFFF, false, mat, buf, Font.DisplayMode.SEE_THROUGH, 0x90000000, 0xF000F0);
            ps.popPose();
        }
        buf.endBatch();

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    // ------------------------------------------------------------------ primitives (coordonnées relatives au marqueur)

    private static void v(BufferBuilder bb, Matrix4f m, float x, float y, float z, int c, float a) {
        bb.vertex(m, x, y, z).color(((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f, a).endVertex();
    }

    /** Anneau plat (couronne) à la hauteur y. */
    private static void band(BufferBuilder bb, Matrix4f m, float r, float half, float y, int c, float a) {
        float ri = Math.max(0.05f, r - half), ro = r + half;
        for (int i = 0; i < SEGMENTS; i++) {
            double a0 = i * Math.PI * 2 / SEGMENTS, a1 = (i + 1) * Math.PI * 2 / SEGMENTS;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            v(bb, m, c0 * ri, y, s0 * ri, c, a);
            v(bb, m, c0 * ro, y, s0 * ro, c, a);
            v(bb, m, c1 * ro, y, s1 * ro, c, a);
            v(bb, m, c1 * ri, y, s1 * ri, c, a);
        }
    }

    /** Mur cylindrique en dégradé (opaque en bas, transparent en haut). */
    private static void wall(BufferBuilder bb, Matrix4f m, float r, float y0, float h, int c, float aBottom, float aTop) {
        for (int i = 0; i < SEGMENTS; i++) {
            double a0 = i * Math.PI * 2 / SEGMENTS, a1 = (i + 1) * Math.PI * 2 / SEGMENTS;
            float x0 = (float) Math.cos(a0) * r, z0 = (float) Math.sin(a0) * r, x1 = (float) Math.cos(a1) * r, z1 = (float) Math.sin(a1) * r;
            v(bb, m, x0, y0, z0, c, aBottom);
            v(bb, m, x1, y0, z1, c, aBottom);
            v(bb, m, x1, y0 + h, z1, c, aTop);
            v(bb, m, x0, y0 + h, z0, c, aTop);
        }
    }

    /** Faisceau vertical carré. */
    private static void column(BufferBuilder bb, Matrix4f m, float w, float h, int c, float a) {
        float[][] pts = {{-w, -w}, {w, -w}, {w, w}, {-w, w}};
        for (int i = 0; i < 4; i++) {
            float[] p0 = pts[i], p1 = pts[(i + 1) % 4];
            v(bb, m, p0[0], 0, p0[1], c, a);
            v(bb, m, p1[0], 0, p1[1], c, a);
            v(bb, m, p1[0], h, p1[1], c, 0f);
            v(bb, m, p0[0], h, p0[1], c, 0f);
        }
    }

    /** Pavé translucide (6 faces), légèrement agrandi. */
    private static void box(BufferBuilder bb, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, int c, float a) {
        float e = 0.02f;
        x0 -= e; y0 -= e; z0 -= e; x1 += e; y1 += e; z1 += e;
        // bas / haut
        v(bb, m, x0, y0, z0, c, a); v(bb, m, x1, y0, z0, c, a); v(bb, m, x1, y0, z1, c, a); v(bb, m, x0, y0, z1, c, a);
        v(bb, m, x0, y1, z0, c, a); v(bb, m, x0, y1, z1, c, a); v(bb, m, x1, y1, z1, c, a); v(bb, m, x1, y1, z0, c, a);
        // nord / sud
        v(bb, m, x0, y0, z0, c, a); v(bb, m, x0, y1, z0, c, a); v(bb, m, x1, y1, z0, c, a); v(bb, m, x1, y0, z0, c, a);
        v(bb, m, x0, y0, z1, c, a); v(bb, m, x1, y0, z1, c, a); v(bb, m, x1, y1, z1, c, a); v(bb, m, x0, y1, z1, c, a);
        // ouest / est
        v(bb, m, x0, y0, z0, c, a); v(bb, m, x0, y0, z1, c, a); v(bb, m, x0, y1, z1, c, a); v(bb, m, x0, y1, z0, c, a);
        v(bb, m, x1, y0, z0, c, a); v(bb, m, x1, y1, z0, c, a); v(bb, m, x1, y1, z1, c, a); v(bb, m, x1, y0, z1, c, a);
    }
}
