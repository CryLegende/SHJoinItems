package org.Cry_Legende.sHJoinItems;

import com.sk89q.worldedit.util.Location;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.session.MoveType;
import com.sk89q.worldguard.session.Session;
import com.sk89q.worldguard.session.handler.Handler;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Liaison avec WorldGuard. Isolée dans cette classe : sans WorldGuard elle n'est jamais chargée
 * et le plugin démarre quand même (kit par défaut seulement).
 */
final class WorldGuardZones {

    private final SHJoinItems plugin;
    private final Factory factory = new Factory();

    WorldGuardZones(SHJoinItems plugin) {
        this.plugin = plugin;
    }

    void register() {
        WorldGuard.getInstance().getPlatform().getSessionManager().registerHandler(factory, null);
    }

    /**
     * Retire le handler : sans ça, un rechargement du plugin en laisse un second actif sur l'ancienne instance.
     */
    void unregister() {
        WorldGuard.getInstance().getPlatform().getSessionManager().unregisterHandler(factory);
    }

    /**
     * Identifiants (en minuscules) des régions où se trouve le joueur.
     */
    Set<String> regionsAt(Player player) {
        LocalPlayer local = WorldGuardPlugin.inst().wrapPlayer(player);
        return ids(WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery()
                .getApplicableRegions(local.getLocation()));
    }

    private static Set<String> ids(ApplicableRegionSet set) {
        Set<String> ids = new HashSet<>();
        for (ProtectedRegion region : set) {
            ids.add(region.getId().toLowerCase(Locale.ROOT));
        }
        return ids;
    }

    private final class Factory extends Handler.Factory<ZoneHandler> {
        @Override
        public ZoneHandler create(Session session) {
            return new ZoneHandler(session);
        }
    }

    /**
     * Prévenu par WorldGuard à chaque entrée ou sortie de région (marche, téléportation, véhicule…).
     */
    private final class ZoneHandler extends Handler {

        ZoneHandler(Session session) {
            super(session);
        }

        @Override
        public boolean onCrossBoundary(LocalPlayer player, Location from, Location to, ApplicableRegionSet toSet,
                                       Set<ProtectedRegion> entered, Set<ProtectedRegion> exited, MoveType moveType) {
            Player bukkit = plugin.getServer().getPlayer(player.getUniqueId());
            if (bukkit != null) {
                plugin.regionsChanged(bukkit, ids(toSet));
            }
            return true;
        }
    }
}
