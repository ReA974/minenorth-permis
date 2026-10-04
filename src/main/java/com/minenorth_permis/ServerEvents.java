package com.minenorth_permis;

import com.minenorth_permis.data.PermisData;
import com.minenorth_permis.net.Network;
import com.minenorth_permis.tests.DrivingTests;
import com.minenorth_permis.tests.ShootingTests;
import com.minenorth_permis.util.Util;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

public final class ServerEvents {
    private static int clock;

    private ServerEvents() {}

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) {
        PermisCommands.register(e.getDispatcher());
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        MinecraftServer s = ServerLifecycleHooks.getCurrentServer();
        if (s == null) return;
        DrivingTests.tick(s);
        ShootingTests.tick(s);
        if (++clock >= 1200) {
            clock = 0;
            Licences.sweep(s);
        }
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide() || e.loadedFromDisk()) return;
        if (e.getLevel() instanceof ServerLevel sl) DrivingTests.onEntityJoin(e.getEntity(), sl);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onImpact(ProjectileImpactEvent e) {
        ShootingTests.onProjectileImpact(e);
    }

    /** Les objets prêtés pendant une épreuve ne peuvent pas être jetés. */
    @SubscribeEvent
    public static void onToss(ItemTossEvent e) {
        ItemEntity it = e.getEntity();
        if (!Util.isTestItem(it.getItem())) return;
        e.setCanceled(true);
        if (e.getPlayer() instanceof ServerPlayer p) {
            Util.give(p, it.getItem().copy());
            Msg.err(p, "Tu ne peux pas jeter cet objet pendant l'épreuve !");
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            DrivingTests.onDeath(p);
            ShootingTests.onLogoutOrDeath(p);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        PermisData d = PermisData.get(p.server);
        PermisData.Holder h = d.peek(p.getUUID());
        if (h != null && !p.getGameProfile().getName().equals(h.name)) {
            h.name = p.getGameProfile().getName();
            d.setDirty();
        }
        // Objets d'épreuve restés après un crash : on les retire
        if (!ShootingTests.inTest(p.getUUID())) Util.removeAll(p, Util::isTestItem);
        returnIfPending(p);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && !e.isEndConquered()) returnIfPending(p);
    }

    private static void returnIfPending(ServerPlayer p) {
        PermisData d = PermisData.get(p.server);
        PermisData.Holder h = d.peek(p.getUUID());
        if (h == null || h.pendingReturn == null) return;
        PermisData.Loc l = h.pendingReturn;
        h.pendingReturn = null;
        d.setDirty();
        p.server.execute(() -> Util.teleport(p, l));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        DrivingTests.onLogout(p);
        ShootingTests.onLogoutOrDeath(p);
        Network.MENU_SESSIONS.remove(p.getUUID());
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent e) {
        DrivingTests.stopAll(e.getServer());
        ShootingTests.stopAll(e.getServer());
    }
}
