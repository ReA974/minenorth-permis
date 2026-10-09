# Permis & Licences MineNorthRP (`minenorth_permis`)

Mod Forge 1.20.1 (compatible Arclight) qui remplace `permis_shop.sk`, `permis_test.sk` et `permistir.sk`.

## Installation
- Mettre `minenorth_permis-1.0.0.jar` dans `mods/` du serveur **et** des clients (écrans, HUD, anneaux).
- Optionnels : `minenorth_eurobank` (paiement espèces + carte), `tacz` (épreuve de tir), `mts` (véhicules).
- Une config existante est mise à jour automatiquement (version 2 : carte de conduire groupée, examen de tir payant).
- Premier lancement : création de `config/minenorth_permis.json` (catalogue, prix, véhicules, arme…).
  Après modification : `/permis recharger` (ou `/permisshopload`).

## Commandes pour les PNJ (mêmes noms que les scripts)
| Commande | Effet |
|---|---|
| `/permislicencemenu <joueur>` | Boutique : Permis / Licences / Permis Arme / Licence Pêche |
| `/permistestmenu <joueur>` | Menu des tests de conduite |
| `/permistest <conduire\|camion\|moto> <joueur>` | Lance directement un test |
| `/demarrertirepreuve <joueur> [zone]` | Ouvre le menu de l'épreuve de tir : règles + paiement des frais d'examen (zone la plus proche par défaut) |
| `/mespermis` | Mes permis & licences + points (staff : `/mespermis <pseudo>`) |

Un joueur non-op qui tape ces commandes lui-même est refusé (« Passe par le PNJ »), comme avant.

## Tests de conduite
1. Va au départ du parcours : `/permis parcours conduire depart` (le candidat y sera téléporté, même dans un autre monde Multiverse).
2. Place-toi sur chaque point, dans l'ordre : `/permis parcours conduire ajouter` (`retirer` = annule le dernier, `vider` = tout effacer).
3. Options : `/permis parcours conduire temps 120`, `/permis parcours conduire rayon 6`, `/permis parcours conduire voir` (aperçu 30 s).

Pendant le test, le prochain point est un **gros anneau vert lumineux** (mur qui pulse + cercles qui montent + faisceau vertical visible à travers les blocs + numéro), le suivant est affiché en transparence, l'arrivée est dorée.
Un panneau HUD en haut donne le chrono, la progression, la distance et une flèche vers l'anneau.
Le chrono démarre quand le véhicule est posé. Sneak 3 s = abandon. Échec = cooldown (30 min par défaut, conservé au redémarrage).
Déconnexion / mort : le véhicule est supprimé et le joueur est renvoyé à sa position d'origine à la reconnexion / réapparition.

Véhicules : `vehicleItem` par test dans la config (par défaut `mts:gvp.polestar2_beige`, `mts:gvp.chevrolet_npr`, `mts:gvp.kawasaki_vulcan_vn750` — vérifie les ID exacts avec F3+H).

## Épreuve de tir
Plus besoin de command block : le mod détecte directement les balles qui touchent un bloc cible **dans une zone**.
1. Vise un coin de la zone de tir : `/permis tir pos1`, vise le coin opposé : `/permis tir pos2`.
2. `/permis tir creer stand1` → indique le nombre de cibles détectées (`minecraft:target` et `tacz:target`).
3. `/permis tir voir stand1`, `/permis tir liste`, `/permis tir supprimer stand1`.

Le PNJ ouvre un menu : le joueur paie les frais d'examen (espèces ou carte), puis l'épreuve démarre. Si le lancement échoue après le paiement, il est remboursé.
Frais : `shooting.examPrice` (en €, `-1` = prix de la licence `parme`, 3000 € par défaut). Staff, sans paiement : `/permis tir lancer <joueur> [zone]`.
**Chargeur vide = épreuve ratée** (`failWhenOutOfAmmo`) : quand le revolver prêté n'a plus de balle et qu'il ne reste aucune munition prêtée, l'épreuve échoue (après ~1,5 s pour laisser arriver la dernière balle).

Détection : événement TACZ `AmmoHitBlockEvent` (le tir est attribué au tireur), flèches via Forge ; si TACZ n'est pas branchable, secours par la puissance redstone des cibles.
Le candidat reçoit le Taurus 943 (marqué « objet d'épreuve » : non jetable, retiré à la fin, jamais ses propres armes). Il doit rester à moins de 10 blocs (anneau rouge au sol), cibles surlignées en rouge, HUD avec compteur et chrono.
Réglages dans `shooting` : `hitsRequired`, `timeLimitSeconds`, `maxDistance`, `gunItem`/`gunNbt`, `extraItems` (munitions), `failCooldownMinutes`.

## Points et retrait du permis
Les points (12 par défaut) ne concernent que le permis de conduire. À **0 point**, toutes les catégories du permis de conduire (voiture, poids lourd, moto) sont retirées (`revokeAtZeroPoints`) ; le joueur doit repasser les examens et repart à 12 points au premier permis obtenu.
Retirer des points : `/permis points <pseudo> enlever <n>` ou `PermisApi.removePoints(...)`.

### Carburant du véhicule de test
Les véhicules MTS posés depuis un item neuf ont un réservoir vide. Le mod fait le **plein automatiquement** dès que le
véhicule de test est posé (meilleur carburant accepté par le moteur, d'après la config MTS).
Par test dans la config : `"fillFuel": true` (false pour désactiver) et `"fuelFluid": ""` (ex. `"diesel"` pour imposer un fluide).

## Papiers perdus (duplicata)
Dans la boutique du PNJ, bouton « Papiers perdus ? » : le joueur choisit la carte à refaire et paie le duplicata (`duplicatePrice`, 50 € par défaut, espèces ou carte).
L'ancienne carte est **annulée** : si quelqu'un la retrouve ou l'a volée, elle s'affiche « ANNULÉE » au clic droit.
Un duplicata n'est possible que pour un papier encore valide et si le joueur n'a pas déjà la carte sur lui.

## Administration
**Gestion des joueurs : panneau `/mnadmin` (mod `minenorth_admin`), onglet Permis** — donner / retirer un permis,
nouvelle carte, points, délais d'examen, arrêter une épreuve. L'ancien menu `/permis admin` a été retiré.
Les commandes ci-dessous restent disponibles (console, blocs de commande) :

- `/permis donner <joueur> <licence> [jours]` (0 = permanent) — donne aussi la carte.
- `/permis retirer <pseudo> <licence>` · `/permis voir <pseudo>`
- `/permis points <pseudo> [definir|ajouter|enlever <n>]`
- `/permis arreter <joueur>` (stoppe un test en cours)
- `/permis importskript` : reprend les permis de `plugins/Skript/variables.csv` (`{licence.uuid}` = true et `{permispoints.uuid}`). Les dates Skript ne sont pas lisibles : chaque permis importé repart pour 30 jours.

## Carte de permis
Voiture (B), poids lourd (C) et moto (A) sont sur **un seul Permis de Conduire** : la première réussite donne la carte, les suivantes ajoutent la catégorie dessus (`cardGroups` + `cardGroup`/`code` sur chaque licence dans la config).
Les **points** ne s'affichent que sur le permis de conduire (`"points": true` sur le groupe), plus sur les licences de tir, pêche, etc.
Les autres licences gardent leur propre carte (bleue licence, rouge arme, verte pêche).
Clic droit : vue « carte d'identité » (photo du titulaire, catégories avec leur date de fin, VALIDE/INVALIDE vérifié en direct).
Clic droit sur un joueur : tu lui montres ta carte (contrôle de police).

## Pour tes autres mods
`com.minenorth_permis.PermisApi` : `hasLicence(player, "permisconduire")`, `points`, `removePoints`, `grant`, `revoke`.
Dans la config, `onGrant` / `onRevoke` permettent aussi de lancer des commandes (`{player}`, `{uuid}`, `{licence}`).

## Compiler
`gradlew build` → `build/libs/minenorth_permis-1.0.0.jar` (même structure que ton projet EuroBank).

## Licence

**Tous droits réservés - MineNorthRP.** Réutilisation, copie, modification, décompilation / ingénierie
inverse (y compris par outils d'intelligence artificielle) et utilisation pour entraîner une IA sont
**interdites** sans autorisation écrite. Voir [LICENSE](LICENSE).
