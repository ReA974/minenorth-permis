package com.minenorth_permis.client;

import com.minenorth_permis.net.HudPacket;
import com.minenorth_permis.net.MarkersPacket;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** État client : panneau HUD du test en cours et marqueurs 3D par canal. */
@OnlyIn(Dist.CLIENT)
public final class ClientState {
    public static HudPacket hud;
    public static long hudReceived;

    public record MarkerSet(String dim, List<MarkersPacket.Marker> markers, long expires) {}

    public static final Map<String, MarkerSet> MARKERS = new LinkedHashMap<>();

    private ClientState() {}

    public static void setHud(HudPacket m) {
        hud = m.active ? m : null;
        hudReceived = System.currentTimeMillis();
    }

    public static void setMarkers(MarkersPacket m) {
        if (m.markers.isEmpty()) {
            MARKERS.remove(m.channel);
            return;
        }
        long exp = m.durationTicks > 0 ? System.currentTimeMillis() + m.durationTicks * 50L : -1;
        MARKERS.put(m.channel, new MarkerSet(m.dim, new ArrayList<>(m.markers), exp));
    }

    /** Marqueurs visibles dans la dimension donnée (purge ceux expirés). */
    public static List<MarkersPacket.Marker> visible(String dim) {
        long now = System.currentTimeMillis();
        MARKERS.values().removeIf(s -> s.expires > 0 && s.expires < now);
        List<MarkersPacket.Marker> out = new ArrayList<>();
        for (MarkerSet s : MARKERS.values()) if (s.dim.equals(dim)) out.addAll(s.markers);
        return out;
    }

    public static void clear() {
        hud = null;
        MARKERS.clear();
    }
}
