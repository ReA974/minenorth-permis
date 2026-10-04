package com.minenorth_permis;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration JSON : config/minenorth_permis.json (créée au premier lancement, rechargée par /permis recharger).
 * Les prix sont en euros.
 */
public final class PermisConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static Root current = defaults();

    private PermisConfig() {}

    public static Root get() {
        return current;
    }

    public static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("minenorth_permis.json");
    }

    /** Charge le fichier (le crée avec les valeurs par défaut s'il n'existe pas). Renvoie un message d'erreur ou null. */
    public static String load() {
        Path f = file();
        try {
            if (!Files.exists(f)) {
                current = defaults();
                try (Writer w = Files.newBufferedWriter(f, StandardCharsets.UTF_8)) {
                    GSON.toJson(current, w);
                }
                return null;
            }
            try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                Root loaded = GSON.fromJson(r, Root.class);
                if (loaded == null) return "fichier vide";
                boolean migrated = loaded.migrate();
                loaded.sanitize();
                current = loaded;
                if (migrated) {
                    try (Writer w = Files.newBufferedWriter(f, StandardCharsets.UTF_8)) {
                        GSON.toJson(current, w);
                    }
                    MinenorthPermis.LOG.info("[Permis] Config mise à jour en version {}", Root.VERSION);
                }
            }
            return null;
        } catch (Exception e) {
            MinenorthPermis.LOG.error("[Permis] Erreur de lecture de {}", f, e);
            return e.getMessage();
        }
    }

    // ------------------------------------------------------------------ modèles

    public static class Root {
        public static final int VERSION = 2;
        /** Absent dans les anciennes configs (= 0) : déclenche la mise à jour automatique. */
        public int configVersion;
        /** Durée de validité d'un permis/licence en jours (0 = permanent). */
        public int validityDays = 30;
        /** Points de permis (partagés entre tous les permis). */
        public int maxPoints = 12;
        /** Donne une carte physique à chaque permis/licence obtenu. */
        public boolean giveCard = true;
        /** À 0 point, tous les permis des cartes à points (voiture, poids lourd, moto) sont retirés. */
        public boolean revokeAtZeroPoints = true;
        /** Prix d'un duplicata de carte (papiers perdus), en euros. 0 = gratuit. */
        public double duplicatePrice = 50;
        public boolean allowCash = true;
        public boolean allowCard = true;
        public List<Category> categories = new ArrayList<>();
        /** Cartes regroupant plusieurs permis (ex. permis de conduire : B voiture, C poids lourd, A moto). */
        public List<CardGroup> cardGroups = new ArrayList<>();
        public List<Licence> licences = new ArrayList<>();
        public List<DrivingTest> tests = new ArrayList<>();
        /** Types d'entité considérés comme "le véhicule posé" (MTS : mts:builder_existing). "mts:*" = tout le namespace. */
        public List<String> vehicleEntityTypes = new ArrayList<>(List.of("mts:builder_existing"));
        /** Distance max (blocs) entre le joueur et le véhicule qui apparaît pour le lier au test. */
        public double vehicleDetectRadius = 8;
        public Shooting shooting = new Shooting();

        /** Anciennes configs (v1) : ajoute la carte "permis de conduire" et l'examen payant du permis arme. */
        boolean migrate() {
            if (configVersion >= VERSION) return false;
            if (cardGroups == null || cardGroups.isEmpty()) cardGroups = new ArrayList<>(defaults().cardGroups);
            if (licences != null) {
                for (Licence l : licences) {
                    if (l == null || l.id == null) continue;
                    Licence d = defaults().licence(l.id);
                    if (d == null) continue;
                    if (l.cardGroup == null || l.cardGroup.isEmpty()) l.cardGroup = d.cardGroup;
                    if (l.code == null || l.code.isEmpty()) l.code = d.code;
                    if (l.id.equals("parme") && (l.test == null || l.test.isEmpty())) l.test = "tir";
                }
            }
            configVersion = VERSION;
            return true;
        }

        void sanitize() {
            if (categories == null) categories = new ArrayList<>();
            if (cardGroups == null) cardGroups = new ArrayList<>();
            cardGroups.removeIf(g -> g == null || g.id == null || g.id.isBlank());
            if (licences == null) licences = new ArrayList<>();
            if (tests == null) tests = new ArrayList<>();
            if (vehicleEntityTypes == null) vehicleEntityTypes = new ArrayList<>();
            if (shooting == null) shooting = new Shooting();
            if (shooting.targetBlocks == null) shooting.targetBlocks = new ArrayList<>();
            if (shooting.extraItems == null) shooting.extraItems = new ArrayList<>();
            licences.removeIf(l -> l == null || l.id == null || l.id.isBlank());
            tests.removeIf(t -> t == null || t.id == null || t.id.isBlank());
            categories.removeIf(c -> c == null || c.id == null || c.id.isBlank());
            for (Licence l : licences) {
                if (l.name == null) l.name = l.id;
                if (l.category == null) l.category = "";
                if (l.test == null) l.test = "";
                if (l.onGrant == null) l.onGrant = new ArrayList<>();
                if (l.onRevoke == null) l.onRevoke = new ArrayList<>();
                if (l.cardGroup == null) l.cardGroup = "";
                if (l.code == null) l.code = "";
            }
            if (maxPoints <= 0) maxPoints = 12;
            // la licence délivrée par l'épreuve de tir ne s'achète jamais directement
            Licence arme = licence(shooting.licence);
            if (arme != null && arme.test.isEmpty()) arme.test = "tir";
        }

        public Licence licence(String id) {
            for (Licence l : licences) if (l.id.equalsIgnoreCase(id)) return l;
            return null;
        }

        public DrivingTest test(String id) {
            for (DrivingTest t : tests) if (t.id.equalsIgnoreCase(id)) return t;
            return null;
        }

        public CardGroup group(String id) {
            if (id == null || id.isEmpty()) return null;
            for (CardGroup g : cardGroups) if (g.id.equalsIgnoreCase(id)) return g;
            return null;
        }

        /** Licences rattachées à une carte groupée, dans l'ordre de la config. */
        public List<Licence> inGroup(String group) {
            List<Licence> out = new ArrayList<>();
            for (Licence l : licences) if (l.cardGroup.equalsIgnoreCase(group)) out.add(l);
            return out;
        }

        /** Vrai si au moins une carte affiche des points (sinon pas de points du tout). */
        public boolean anyPoints() {
            for (CardGroup g : cardGroups) if (g.points) return true;
            return false;
        }

        public Category category(String id) {
            for (Category c : categories) if (c.id.equalsIgnoreCase(id)) return c;
            return null;
        }
    }

    public static class Category {
        public String id;
        public String name;
        /** Couleur d'accent de la tuile (hex). */
        public String color = "#20AAEB";
        /** Style de carte : permis, licence, arme, peche. */
        public String cardStyle = "licence";

        Category() {}

        Category(String id, String name, String color, String cardStyle) {
            this.id = id;
            this.name = name;
            this.color = color;
            this.cardStyle = cardStyle;
        }

        public int argb() {
            try {
                return 0xFF000000 | Integer.parseInt(color.replace("#", ""), 16);
            } catch (Exception e) {
                return 0xFF20AAEB;
            }
        }
    }

    public static class CardGroup {
        public String id;
        public String name;
        /** Style de la carte : permis, licence, arme, peche. */
        public String style = "permis";
        /** Les points de permis sont affichés sur cette carte. */
        public boolean points = true;

        CardGroup() {}

        CardGroup(String id, String name, String style, boolean points) {
            this.id = id;
            this.name = name;
            this.style = style;
            this.points = points;
        }
    }

    public static class Licence {
        public String id;
        public String name;
        public double price;
        public String category;
        /** "" = achat direct ; id d'un test de conduite (conduire, camion, moto) ; ou "tir" pour l'épreuve de tir. */
        public String test = "";
        /** Carte groupée (ex. "conduire") : toutes les catégories sont sur la même carte. Vide = carte à part. */
        public String cardGroup = "";
        /** Lettre de catégorie sur la carte groupée (B, C, A…). */
        public String code = "";
        /** Commandes console lancées à l'obtention / la perte ({player}, {uuid}, {licence}). */
        public List<String> onGrant = new ArrayList<>();
        public List<String> onRevoke = new ArrayList<>();

        Licence() {}

        Licence(String id, String name, double price, String category, String test) {
            this.id = id;
            this.name = name;
            this.price = price;
            this.category = category;
            this.test = test;
        }

        Licence group(String group, String code) {
            this.cardGroup = group;
            this.code = code;
            return this;
        }

        public long priceCents() {
            return Math.round(price * 100);
        }
    }

    public static class DrivingTest {
        public String id;
        public String name;
        /** Licence débloquée en cas de réussite. */
        public String licence;
        /** Item du véhicule donné au joueur (ex. mts:gvp.polestar2_beige). */
        public String vehicleItem;
        /** NBT optionnel du véhicule (SNBT), vide par défaut. */
        public String vehicleNbt = "";
        public int defaultTimeLimit = 120;
        public double defaultRadius = 6;
        public int failCooldownMinutes = 30;
        /** Temps max pour poser le véhicule avant annulation. */
        public int placementTimeoutSeconds = 90;

        DrivingTest() {}

        DrivingTest(String id, String name, String licence, String vehicleItem) {
            this.id = id;
            this.name = name;
            this.licence = licence;
            this.vehicleItem = vehicleItem;
        }
    }

    public static class Shooting {
        public String licence = "parme";
        public String name = "Épreuve de tir";
        /** Frais d'examen en euros, payés dans le menu avant l'épreuve. -1 = prix de la licence (parme). */
        public double examPrice = -1;
        public int hitsRequired = 5;
        /** 0 = pas de limite de temps. */
        public int timeLimitSeconds = 120;
        /** Distance max par rapport à la position de départ. */
        public double maxDistance = 10;
        public int failCooldownMinutes = 0;
        public String gunItem = "tacz:modern_kinetic_gun";
        public String gunNbt = "{GunId:\"tacz:taurus943\",GunCurrentAmmoCount:8}";
        /** Items supplémentaires (munitions…) : "id" ou "id{nbt}" ou "id*quantité". Retirés à la fin. */
        public List<String> extraItems = new ArrayList<>();
        /** Blocs qui comptent comme cibles. */
        public List<String> targetBlocks = new ArrayList<>(List.of("minecraft:target", "tacz:target"));
        /** Compte aussi les flèches / projectiles vanilla (sinon seulement les balles TACZ). */
        public boolean countOtherProjectiles = true;
        /** Échec automatique quand l'arme est vide et qu'il ne reste aucune munition prêtée. */
        public boolean failWhenOutOfAmmo = true;
    }

    // ------------------------------------------------------------------ défauts (repris de permis_shop.sk)

    private static Root defaults() {
        Root r = new Root();
        r.configVersion = Root.VERSION;
        r.categories.add(new Category("permis", "Permis", "#20AAEB", "permis"));
        r.categories.add(new Category("licence", "Licences", "#4A3CB4", "licence"));
        r.categories.add(new Category("arme", "Permis Arme", "#E04E6A", "arme"));
        r.categories.add(new Category("peche", "Licence Pêche", "#2EC4A6", "peche"));

        r.cardGroups.add(new CardGroup("conduire", "Permis de Conduire", "permis", true));

        r.licences.add(new Licence("permisconduire", "Permis de Conduire", 800, "permis", "conduire").group("conduire", "B"));
        r.licences.add(new Licence("permisconduirep", "Permis Poids Lourd", 2000, "permis", "camion").group("conduire", "C"));
        r.licences.add(new Licence("motop", "Permis Moto", 600, "permis", "moto").group("conduire", "A"));
        r.licences.add(new Licence("permisconduireb", "Permis Bateau", 1500, "permis", ""));
        r.licences.add(new Licence("permisconduirea", "Permis Aérien", 5000, "permis", ""));
        r.licences.add(new Licence("parme", "Permis Arme", 3000, "arme", "tir"));
        r.licences.add(new Licence("dpro", "Droit de Propriétaire", 10000, "permis", ""));
        r.licences.add(new Licence("pecheur", "Licence Pêcheur", 500, "peche", ""));
        r.licences.add(new Licence("mfer", "Licence Mineur de Fer", 800, "licence", ""));
        r.licences.add(new Licence("mor", "Licence Mineur d'Or", 1500, "licence", ""));
        r.licences.add(new Licence("mdiam", "Licence Mineur de Diamants", 3000, "licence", ""));
        r.licences.add(new Licence("mbtp", "Licence BTP", 2000, "licence", ""));
        r.licences.add(new Licence("mdp", "Licence Pétrole", 4000, "licence", ""));
        r.licences.add(new Licence("msable", "Licence Sable", 600, "licence", ""));
        r.licences.add(new Licence("agriculteur", "Licence Agriculteur", 700, "licence", ""));
        r.licences.add(new Licence("tabac", "Licence Tabac", 1200, "licence", ""));

        r.tests.add(new DrivingTest("conduire", "Permis de Conduire", "permisconduire", "mts:gvp.polestar2_beige"));
        r.tests.add(new DrivingTest("camion", "Permis Poids Lourd", "permisconduirep", "mts:gvp.chevrolet_npr"));
        r.tests.add(new DrivingTest("moto", "Permis Moto", "motop", "mts:gvp.kawasaki_vulcan_vn750"));
        return r;
    }
}
