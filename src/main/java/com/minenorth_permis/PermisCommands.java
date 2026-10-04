package com.minenorth_permis;

import com.minenorth_permis.data.PermisData;
import com.minenorth_permis.net.MenuStatePacket;
import com.minenorth_permis.net.Network;
import com.minenorth_permis.tests.DrivingTests;
import com.minenorth_permis.tests.ShootingTests;
import com.minenorth_permis.util.Util;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Commandes de compatibilité avec les anciens scripts (mêmes noms, pour les PNJ) + /permis pour le staff.
 */
public final class PermisCommands {
    private static final Map<UUID, BlockPos[]> SELECTIONS = new HashMap<>();

    private static final SuggestionProvider<CommandSourceStack> LICENCES = (c, b) ->
            SharedSuggestionProvider.suggest(PermisConfig.get().licences.stream().map(l -> l.id), b);
    private static final SuggestionProvider<CommandSourceStack> TESTS = (c, b) ->
            SharedSuggestionProvider.suggest(PermisConfig.get().tests.stream().map(t -> t.id), b);
    private static final SuggestionProvider<CommandSourceStack> RANGES = (c, b) ->
            SharedSuggestionProvider.suggest(PermisData.get(c.getSource().getServer()).ranges().keySet(), b);
    private static final SuggestionProvider<CommandSourceStack> KNOWN = (c, b) -> {
        MinecraftServer s = c.getSource().getServer();
        return SharedSuggestionProvider.suggest(PermisData.get(s).holders().values().stream().map(h -> h.name).filter(n -> !n.isEmpty()), b);
    };

    private PermisCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        // ---------------- compatibilité PNJ (mêmes noms que les scripts Skript)
        d.register(Commands.literal("permislicencemenu")
                .executes(c -> openFor(c, null, MenuStatePacket.SHOP))
                .then(Commands.argument("joueur", EntityArgument.player())
                        .executes(c -> openFor(c, EntityArgument.getPlayer(c, "joueur"), MenuStatePacket.SHOP))));
        d.register(Commands.literal("permistestmenu")
                .executes(c -> openFor(c, null, MenuStatePacket.TESTS))
                .then(Commands.argument("joueur", EntityArgument.player())
                        .executes(c -> openFor(c, EntityArgument.getPlayer(c, "joueur"), MenuStatePacket.TESTS))));
        d.register(Commands.literal("permistest")
                .then(Commands.argument("test", StringArgumentType.word()).suggests(TESTS)
                        .executes(c -> startTest(c, null))
                        .then(Commands.argument("joueur", EntityArgument.player())
                                .executes(c -> startTest(c, EntityArgument.getPlayer(c, "joueur"))))));
        d.register(Commands.literal("demarrertirepreuve")
                .executes(c -> startTir(c, null, null))
                .then(Commands.argument("joueur", EntityArgument.player())
                        .executes(c -> startTir(c, EntityArgument.getPlayer(c, "joueur"), null))
                        .then(Commands.argument("zone", StringArgumentType.word()).suggests(RANGES)
                                .executes(c -> startTir(c, EntityArgument.getPlayer(c, "joueur"), StringArgumentType.getString(c, "zone"))))));
        d.register(Commands.literal("mespermis")
                .executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    Network.openMyLicences(p, p.getUUID(), p.getGameProfile().getName());
                    return 1;
                })
                .then(Commands.argument("nom", StringArgumentType.word()).suggests(KNOWN)
                        .requires(s -> s.hasPermission(2))
                        .executes(c -> viewOther(c, StringArgumentType.getString(c, "nom")))));
        d.register(Commands.literal("permisshopload").requires(s -> s.hasPermission(2)).executes(PermisCommands::reload));

        // ---------------- administration
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("permis").requires(s -> s.hasPermission(2));
        root.then(Commands.literal("recharger").executes(PermisCommands::reload));

        root.then(Commands.literal("donner")
                .then(Commands.argument("joueur", EntityArgument.player())
                        .then(Commands.argument("licence", StringArgumentType.word()).suggests(LICENCES)
                                .executes(c -> give(c, -1))
                                .then(Commands.argument("jours", IntegerArgumentType.integer(0))
                                        .executes(c -> give(c, IntegerArgumentType.getInteger(c, "jours")))))));
        root.then(Commands.literal("retirer")
                .then(Commands.argument("nom", StringArgumentType.word()).suggests(KNOWN)
                        .then(Commands.argument("licence", StringArgumentType.word()).suggests(LICENCES)
                                .executes(PermisCommands::revoke))));
        root.then(Commands.literal("voir")
                .then(Commands.argument("nom", StringArgumentType.word()).suggests(KNOWN)
                        .executes(c -> viewOther(c, StringArgumentType.getString(c, "nom")))));
        root.then(Commands.literal("points")
                .then(Commands.argument("nom", StringArgumentType.word()).suggests(KNOWN)
                        .executes(c -> points(c, "voir", 0))
                        .then(Commands.literal("definir").then(Commands.argument("n", IntegerArgumentType.integer(0))
                                .executes(c -> points(c, "definir", IntegerArgumentType.getInteger(c, "n")))))
                        .then(Commands.literal("ajouter").then(Commands.argument("n", IntegerArgumentType.integer(1))
                                .executes(c -> points(c, "ajouter", IntegerArgumentType.getInteger(c, "n")))))
                        .then(Commands.literal("enlever").then(Commands.argument("n", IntegerArgumentType.integer(1))
                                .executes(c -> points(c, "enlever", IntegerArgumentType.getInteger(c, "n")))))));
        root.then(Commands.literal("arreter")
                .then(Commands.argument("joueur", EntityArgument.player()).executes(c -> {
                    ServerPlayer p = EntityArgument.getPlayer(c, "joueur");
                    boolean ok = DrivingTests.stop(p) | ShootingTests.stop(p);
                    c.getSource().sendSuccess(() -> ok ? Msg.okc("Épreuve arrêtée.") : Msg.errc("Ce joueur ne passe aucune épreuve."), false);
                    return ok ? 1 : 0;
                })));
        root.then(Commands.literal("importskript").executes(c -> {
            String r = SkriptImport.run(c.getSource().getServer());
            c.getSource().sendSuccess(() -> Msg.okc(r), true);
            return 1;
        }));

        // parcours de conduite
        root.then(Commands.literal("parcours")
                .then(Commands.argument("test", StringArgumentType.word()).suggests(TESTS)
                        .then(Commands.literal("depart").executes(c -> course(c, "depart", 0)))
                        .then(Commands.literal("ajouter").executes(c -> course(c, "ajouter", 0)))
                        .then(Commands.literal("retirer").executes(c -> course(c, "retirer", 0)))
                        .then(Commands.literal("vider").executes(c -> course(c, "vider", 0)))
                        .then(Commands.literal("voir").executes(c -> course(c, "voir", 0)))
                        .then(Commands.literal("temps").then(Commands.argument("secondes", IntegerArgumentType.integer(10))
                                .executes(c -> course(c, "temps", IntegerArgumentType.getInteger(c, "secondes")))))
                        .then(Commands.literal("rayon").then(Commands.argument("blocs", DoubleArgumentType.doubleArg(1, 30))
                                .executes(c -> course(c, "rayon", DoubleArgumentType.getDouble(c, "blocs")))))));

        // zones de tir
        root.then(Commands.literal("tir")
                .then(Commands.literal("pos1").executes(c -> select(c, 0)))
                .then(Commands.literal("pos2").executes(c -> select(c, 1)))
                .then(Commands.literal("creer").then(Commands.argument("nom", StringArgumentType.word()).executes(PermisCommands::createRange)))
                .then(Commands.literal("supprimer").then(Commands.argument("nom", StringArgumentType.word()).suggests(RANGES).executes(c -> {
                    String n = StringArgumentType.getString(c, "nom");
                    PermisData data = PermisData.get(c.getSource().getServer());
                    boolean ok = data.ranges().remove(n) != null;
                    data.setDirty();
                    c.getSource().sendSuccess(() -> ok ? Msg.okc("Zone de tir " + n + " supprimée.") : Msg.errc("Zone inconnue."), true);
                    return ok ? 1 : 0;
                })))
                .then(Commands.literal("voir").then(Commands.argument("nom", StringArgumentType.word()).suggests(RANGES).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    int n = ShootingTests.preview(p, StringArgumentType.getString(c, "nom"));
                    c.getSource().sendSuccess(() -> n < 0 ? Msg.errc("Zone inconnue.") : Msg.okc("Aperçu 30 s : " + n + " cible(s) détectée(s) dans la zone."), false);
                    return 1;
                })))
                .then(Commands.literal("lancer").then(Commands.argument("joueur", EntityArgument.player())
                        .executes(c -> {
                            ShootingTests.startFree(EntityArgument.getPlayer(c, "joueur"), null);
                            return 1;
                        })
                        .then(Commands.argument("zone", StringArgumentType.word()).suggests(RANGES).executes(c -> {
                            ShootingTests.startFree(EntityArgument.getPlayer(c, "joueur"), StringArgumentType.getString(c, "zone"));
                            return 1;
                        }))))
                .then(Commands.literal("liste").executes(c -> {
                    Map<String, PermisData.Range> r = ShootingTests.ranges(c.getSource().getServer());
                    if (r.isEmpty()) c.getSource().sendSuccess(() -> Msg.errc("Aucune zone de tir."), false);
                    r.forEach((k, v) -> c.getSource().sendSuccess(() -> Msg.okc(k + " : " + v.dim + " " + v.min.toShortString() + " → " + v.max.toShortString()), false));
                    return r.size();
                })));

        d.register(root);
    }

    // ------------------------------------------------------------------ helpers

    /** Commandes "PNJ" : un joueur sans permission ne peut pas les taper lui-même. */
    private static boolean npcOnly(CommandSourceStack src) {
        if (src.getEntity() instanceof ServerPlayer && !src.hasPermission(2)) {
            src.sendFailure(Component.literal("Cette commande n'est pas accessible directement. Passe par le PNJ."));
            return false;
        }
        return true;
    }

    private static ServerPlayer resolve(CommandSourceStack src, ServerPlayer arg) throws CommandSyntaxException {
        return arg != null ? arg : src.getPlayerOrException();
    }

    private static int openFor(CommandContext<CommandSourceStack> c, ServerPlayer arg, byte screen) throws CommandSyntaxException {
        if (!npcOnly(c.getSource())) return 0;
        ServerPlayer p = resolve(c.getSource(), arg);
        Network.openMenu(p, screen, true, "", true);
        return 1;
    }

    private static int startTest(CommandContext<CommandSourceStack> c, ServerPlayer arg) throws CommandSyntaxException {
        if (!npcOnly(c.getSource())) return 0;
        DrivingTests.start(resolve(c.getSource(), arg), StringArgumentType.getString(c, "test"));
        return 1;
    }

    private static int startTir(CommandContext<CommandSourceStack> c, ServerPlayer arg, String zone) throws CommandSyntaxException {
        if (!npcOnly(c.getSource())) return 0;
        ShootingTests.openExam(resolve(c.getSource(), arg), zone);
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> c) {
        String err = PermisConfig.load();
        if (err == null) {
            c.getSource().sendSuccess(() -> Msg.okc("Configuration rechargée (" + PermisConfig.get().licences.size() + " permis/licences)."), true);
            return 1;
        }
        c.getSource().sendFailure(Msg.errc("Erreur dans minenorth_permis.json : " + err));
        return 0;
    }

    /** Nom -> UUID (joueur en ligne, déjà connu du mod, ou cache des profils). */
    private static Optional<UUID> lookup(MinecraftServer s, String name) {
        ServerPlayer online = s.getPlayerList().getPlayerByName(name);
        if (online != null) return Optional.of(online.getUUID());
        for (Map.Entry<UUID, PermisData.Holder> e : PermisData.get(s).holders().entrySet()) {
            if (e.getValue().name.equalsIgnoreCase(name)) return Optional.of(e.getKey());
        }
        if (s.getProfileCache() != null) return s.getProfileCache().get(name).map(GameProfile::getId);
        return Optional.empty();
    }

    private static int viewOther(CommandContext<CommandSourceStack> c, String name) throws CommandSyntaxException {
        ServerPlayer viewer = c.getSource().getPlayerOrException();
        Optional<UUID> id = lookup(c.getSource().getServer(), name);
        if (id.isEmpty()) {
            c.getSource().sendFailure(Msg.errc("Joueur inconnu : " + name));
            return 0;
        }
        Network.openMyLicences(viewer, id.get(), name);
        return 1;
    }

    private static int give(CommandContext<CommandSourceStack> c, int days) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(c, "joueur");
        String lic = StringArgumentType.getString(c, "licence");
        PermisConfig.Licence def = PermisConfig.get().licence(lic);
        if (def == null) {
            c.getSource().sendFailure(Msg.errc("Licence inconnue : " + lic));
            return 0;
        }
        Shop.obtain(p, def.id, days);
        Msg.ok(p, "Tu as obtenu : " + def.name + ".");
        c.getSource().sendSuccess(() -> Msg.okc(def.name + " donné à " + p.getGameProfile().getName() + "."), true);
        return 1;
    }

    private static int revoke(CommandContext<CommandSourceStack> c) {
        String name = StringArgumentType.getString(c, "nom");
        String lic = StringArgumentType.getString(c, "licence");
        MinecraftServer s = c.getSource().getServer();
        Optional<UUID> id = lookup(s, name);
        if (id.isEmpty() || !Licences.revoke(s, id.get(), lic)) {
            c.getSource().sendFailure(Msg.errc(name + " ne possède pas " + lic + "."));
            return 0;
        }
        ServerPlayer p = s.getPlayerList().getPlayer(id.get());
        PermisConfig.Licence def = PermisConfig.get().licence(lic);
        if (p != null) Msg.err(p, "Ton " + (def != null ? def.name : lic) + " t'a été retiré.");
        c.getSource().sendSuccess(() -> Msg.okc(lic + " retiré à " + name + "."), true);
        return 1;
    }

    private static int points(CommandContext<CommandSourceStack> c, String op, int n) {
        String name = StringArgumentType.getString(c, "nom");
        MinecraftServer s = c.getSource().getServer();
        Optional<UUID> id = lookup(s, name);
        if (id.isEmpty()) {
            c.getSource().sendFailure(Msg.errc("Joueur inconnu : " + name));
            return 0;
        }
        int cur = Licences.points(s, id.get());
        int v = switch (op) {
            case "definir" -> Licences.setPoints(s, id.get(), n);
            case "ajouter" -> Licences.setPoints(s, id.get(), cur + n);
            case "enlever" -> Licences.setPoints(s, id.get(), cur - n);
            default -> cur;
        };
        int max = PermisConfig.get().maxPoints;
        ServerPlayer p = s.getPlayerList().getPlayer(id.get());
        if (p != null && !op.equals("voir")) Msg.warn(p, "Points de permis : " + v + "/" + max);
        c.getSource().sendSuccess(() -> Msg.okc(name + " : " + v + "/" + max + " points."), !op.equals("voir"));
        return v;
    }

    private static int course(CommandContext<CommandSourceStack> c, String op, double value) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        String test = StringArgumentType.getString(c, "test");
        PermisConfig.DrivingTest t = PermisConfig.get().test(test);
        if (t == null) {
            c.getSource().sendFailure(Msg.errc("Test inconnu. Disponibles : " + String.join(", ", PermisConfig.get().tests.stream().map(x -> x.id).toList())));
            return 0;
        }
        PermisData data = PermisData.get(p.server);
        PermisData.Course course = data.courseOrCreate(t.id);
        String msg;
        switch (op) {
            case "depart" -> {
                course.start = Util.loc(p);
                msg = "Point de départ du test \"" + t.id + "\" défini (le joueur y sera téléporté).";
            }
            case "ajouter" -> {
                if (course.start != null && !course.start.dim().equals(Util.dim(p))) {
                    c.getSource().sendFailure(Msg.errc("Les points doivent être dans le même monde que le départ."));
                    return 0;
                }
                Vec3 v = p.position();
                course.checkpoints.add(v);
                msg = "Point de contrôle #" + course.checkpoints.size() + " ajouté au test \"" + t.id + "\".";
            }
            case "retirer" -> {
                if (course.checkpoints.isEmpty()) {
                    c.getSource().sendFailure(Msg.errc("Aucun point à retirer."));
                    return 0;
                }
                course.checkpoints.remove(course.checkpoints.size() - 1);
                msg = "Dernier point retiré (" + course.checkpoints.size() + " restants).";
            }
            case "vider" -> {
                course.checkpoints.clear();
                msg = "Points de contrôle du test \"" + t.id + "\" effacés.";
            }
            case "temps" -> {
                course.timeLimit = (int) value;
                msg = "Temps limite du test \"" + t.id + "\" : " + (int) value + " secondes.";
            }
            case "rayon" -> {
                course.radius = value;
                msg = "Rayon des anneaux du test \"" + t.id + "\" : " + value + " blocs.";
            }
            default -> {
                DrivingTests.preview(p, t.id);
                int tl = course.timeLimit > 0 ? course.timeLimit : t.defaultTimeLimit;
                double r = course.radius > 0 ? course.radius : t.defaultRadius;
                msg = "Aperçu 30 s : " + course.checkpoints.size() + " points, " + tl + " s, rayon " + r
                        + (course.start == null ? " — départ NON défini !" : "");
            }
        }
        data.setDirty();
        String fm = msg;
        c.getSource().sendSuccess(() -> Msg.okc(fm), false);
        if (!op.equals("voir")) DrivingTests.preview(p, t.id);
        return 1;
    }

    private static int select(CommandContext<CommandSourceStack> c, int idx) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        HitResult hit = p.pick(12, 0, false);
        BlockPos pos = hit instanceof BlockHitResult bh && hit.getType() == HitResult.Type.BLOCK ? bh.getBlockPos() : p.blockPosition();
        SELECTIONS.computeIfAbsent(p.getUUID(), k -> new BlockPos[2])[idx] = pos;
        c.getSource().sendSuccess(() -> Msg.okc("Coin " + (idx + 1) + " de la zone de tir : " + pos.toShortString()
                + " (bloc visé). Puis /permis tir creer <nom>."), false);
        return 1;
    }

    private static int createRange(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(c, "nom");
        BlockPos[] sel = SELECTIONS.get(p.getUUID());
        if (sel == null || sel[0] == null || sel[1] == null) {
            c.getSource().sendFailure(Msg.errc("Vise un coin puis /permis tir pos1, l'autre coin puis /permis tir pos2."));
            return 0;
        }
        PermisData.Range r = new PermisData.Range();
        r.dim = Util.dim(p);
        r.min = new BlockPos(Math.min(sel[0].getX(), sel[1].getX()), Math.min(sel[0].getY(), sel[1].getY()), Math.min(sel[0].getZ(), sel[1].getZ()));
        r.max = new BlockPos(Math.max(sel[0].getX(), sel[1].getX()), Math.max(sel[0].getY(), sel[1].getY()), Math.max(sel[0].getZ(), sel[1].getZ()));
        PermisData data = PermisData.get(p.server);
        data.ranges().put(name, r);
        data.setDirty();
        SELECTIONS.remove(p.getUUID());
        int n = ShootingTests.preview(p, name);
        c.getSource().sendSuccess(() -> Msg.okc("Zone de tir \"" + name + "\" créée : " + n + " cible(s) détectée(s)."), true);
        return 1;
    }
}
