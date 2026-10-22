<div align="center">

<img src="assets/logo.png" alt="SkyHand" width="140">

# SHJoinItems

*Items donnés à la connexion et kits par zone WorldGuard (remplace ItemJoin).*

<img src="https://img.shields.io/badge/Paper-26.3-f2a900?style=for-the-badge" alt="Paper 26.3"> <img src="https://img.shields.io/badge/Java-25-e76f00?style=for-the-badge" alt="Java 25"> <img src="https://img.shields.io/badge/SkyHand-Lobby-3aa655?style=for-the-badge" alt="SkyHand Lobby">

</div>

---

## Présentation

Donne les items de navigation à la connexion, à la réapparition et au changement de monde, puis change l'équipement selon la région WorldGuard où se trouve le joueur : en entrant dans `zone-pvp` il reçoit des épées, en sortant il retrouve ses items habituels.

- Items configurables : matériau, slot(s), nom et lore avec couleurs hex `&#RRGGBB`, enchantements.
- Flags : `glow` (effet enchanté), `unbreakable`, `hide-attributes`, `hide-all`, `locked`, `no-drop`, `keep-on-death`.
- Actions au clic (droit, gauche ou dans l'inventaire) : commande du joueur ou de la console, message, titre, barre d'action, son, délai entre deux clics.
- Chaque item porte un tag interne : le plugin ne retire jamais un objet qui n'est pas à lui, et décale un objet étranger posé sur un slot réservé.
- À la connexion, les anciens items d'ItemJoin sont retirés (tag `ItemJoin Name`), sans message au joueur.
- WorldGuard est facultatif : sans lui, seul le kit par défaut est donné.

Utilisé sur : **Lobby**.

## Commandes

| Commande | Alias | Description | Permission |
|---|---|---|---|
| `/shjoinitems reload` | `/shji reload` | Recharge la configuration et redonne les items aux joueurs connectés. | `shjoinitems.admin` |
| `/shjoinitems refresh [joueur]` | `/shji refresh` | Redonne ses items à un joueur. | `shjoinitems.admin` |

## Permissions

| Permission | Description | Par défaut |
|---|---|---|
| `shjoinitems.admin` | Recharger la configuration et redonner les items. | `op` |

## Configuration

`config.yml` contient les réglages, le kit par défaut, les zones, les kits et les items (le format de chaque clé est expliqué en commentaire).

Une zone associe une région WorldGuard à un kit ; la première zone qui contient le joueur l'emporte :

```yaml
default-kit: lobby

zones:
  - region: zone-pvp
    kit: pvp

kits:
  lobby: [opale, saphir, menu, cosmetiques]
  pvp: [epee-pvp]
```

## Compilation

Java 25 et Maven :

```bash
mvn clean package
```

Le jar est généré dans `target/`.

---

<div align="center">

<sub>Développé par <b>Cry_Legende</b> pour le serveur <b>SkyHand</b> · Tous droits réservés</sub>

</div>
