package org.Cry_Legende.sHJoinItems;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.StringUtil;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SHJoinItems extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    // Codes & et couleurs hex au format &#RRGGBB (celui des anciens items d'ItemJoin)
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&').hexColors().build();
    private static final Title.Times TITLE_TIMES = Title.Times.times(
            Duration.ofMillis(500), Duration.ofSeconds(2), Duration.ofMillis(500));

    private record Zone(String region, String world, String kit) {
    }

    private final Map<String, JoinItem> items = new LinkedHashMap<>();
    private final Map<String, List<JoinItem>> kits = new LinkedHashMap<>();
    private final List<Zone> zones = new ArrayList<>();
    private final Map<UUID, String> currentKits = new HashMap<>();
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();

    private NamespacedKey itemKey;
    private WorldGuardZones worldGuard;
    private Set<String> worlds;
    private int heldSlot;
    private long delay;
    private boolean creativeBypass;
    private List<String> legacyTags;
    private String defaultKit;

    @Override
    public void onEnable() {
        itemKey = new NamespacedKey(this, "item");
        saveDefaultConfig();
        loadConfiguration();

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new ItemListener(this), this);
        if (getServer().getPluginManager().isPluginEnabled("WorldGuard")) {
            worldGuard = new WorldGuardZones(this);
            worldGuard.register();
        } else if (!zones.isEmpty()) {
            getLogger().warning("WorldGuard absent : les zones sont ignorées, seul le kit par défaut est donné.");
        }
        PluginCommand command = getCommand("shjoinitems");
        command.setExecutor(this);
        command.setTabCompleter(this);

        // Activation à chaud : les joueurs déjà connectés reçoivent leurs items tout de suite
        getServer().getOnlinePlayers().forEach(player -> refresh(player, true, false));
        getLogger().info(items.size() + " items, " + kits.size() + " kits et " + zones.size() + " zones chargés.");
    }

    @Override
    public void onDisable() {
        currentKits.clear();
        cooldowns.clear();
    }

    // ------------------------------------------------------------------ configuration

    private void loadConfiguration() {
        reloadConfig();
        FileConfiguration c = getConfig();
        worlds = new HashSet<>();
        c.getStringList("settings.worlds").forEach(world -> worlds.add(world.toLowerCase(Locale.ROOT)));
        heldSlot = c.getInt("settings.held-slot", 0);
        delay = Math.max(0, c.getLong("settings.delay", 2));
        creativeBypass = c.getBoolean("settings.creative-bypass", true);
        legacyTags = c.getStringList("settings.legacy-tags");

        items.clear();
        ConfigurationSection itemSection = c.getConfigurationSection("items");
        if (itemSection != null) {
            for (String id : itemSection.getKeys(false)) {
                JoinItem item = JoinItem.load(this, id.toLowerCase(Locale.ROOT), itemSection.getConfigurationSection(id));
                if (item != null) {
                    items.put(item.id(), item);
                }
            }
        }

        kits.clear();
        ConfigurationSection kitSection = c.getConfigurationSection("kits");
        if (kitSection != null) {
            for (String name : kitSection.getKeys(false)) {
                List<JoinItem> kit = new ArrayList<>();
                Set<Integer> usedSlots = new HashSet<>();
                for (String id : kitSection.getStringList(name)) {
                    JoinItem item = items.get(id.toLowerCase(Locale.ROOT));
                    if (item == null) {
                        getLogger().warning("Kit " + name + " : item inconnu « " + id + " ».");
                        continue;
                    }
                    for (int slot : item.slots()) {
                        if (!usedSlots.add(slot)) {
                            getLogger().warning("Kit " + name + " : le slot " + slot + " est pris par plusieurs items.");
                        }
                    }
                    kit.add(item);
                }
                kits.put(name.toLowerCase(Locale.ROOT), kit);
            }
        }

        defaultKit = c.getString("default-kit", "lobby").toLowerCase(Locale.ROOT);
        if (!kits.containsKey(defaultKit)) {
            getLogger().warning("Kit par défaut inconnu : « " + defaultKit + " ».");
        }

        zones.clear();
        for (Map<?, ?> zone : c.getMapList("zones")) {
            Object region = zone.get("region");
            Object kit = zone.get("kit");
            Object world = zone.get("world");
            if (region == null || kit == null) {
                getLogger().warning("Zone ignorée : region et kit sont obligatoires.");
                continue;
            }
            String kitName = kit.toString().toLowerCase(Locale.ROOT);
            if (!kits.containsKey(kitName)) {
                getLogger().warning("Zone " + region + " ignorée : kit inconnu « " + kit + " ».");
                continue;
            }
            zones.add(new Zone(region.toString().toLowerCase(Locale.ROOT), world == null ? null : world.toString(), kitName));
        }
    }

    // ------------------------------------------------------------------ kits

    /**
     * Kit à donner pour ces régions : la première zone de la liste qui correspond l'emporte.
     */
    private String kitFor(World world, Set<String> regions) {
        for (Zone zone : zones) {
            if ((zone.world() == null || zone.world().equalsIgnoreCase(world.getName())) && regions.contains(zone.region())) {
                return zone.kit();
            }
        }
        return defaultKit;
    }

    private boolean managedWorld(World world) {
        return worlds.isEmpty() || worlds.contains(world.getName().toLowerCase(Locale.ROOT));
    }

    /**
     * Donne le kit qui correspond à la position du joueur.
     *
     * @param force       redonne le kit même s'il n'a pas changé
     * @param cleanLegacy retire aussi les items des anciens plugins (connexion, commande refresh)
     */
    private void refresh(Player player, boolean force, boolean cleanLegacy) {
        if (!managedWorld(player.getWorld())) {
            removeItems(player, false);
            currentKits.remove(player.getUniqueId());
            return;
        }
        Set<String> regions = worldGuard == null || zones.isEmpty() ? Set.of() : worldGuard.regionsAt(player);
        applyKit(player, kitFor(player.getWorld(), regions), force, cleanLegacy);
    }

    /**
     * Appelé par WorldGuard quand le joueur entre dans une région ou en sort.
     */
    void regionsChanged(Player player, Set<String> regions) {
        // WorldGuard prévient pendant le déplacement : l'inventaire est modifié au tick suivant
        getServer().getScheduler().runTask(this, () -> {
            if (player.isOnline() && managedWorld(player.getWorld())) {
                applyKit(player, kitFor(player.getWorld(), regions), false, false);
            }
        });
    }

    private void applyKit(Player player, String kitName, boolean force, boolean cleanLegacy) {
        if (!force && kitName.equals(currentKits.get(player.getUniqueId()))) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        removeItems(player, cleanLegacy);

        List<JoinItem> kit = kits.getOrDefault(kitName, List.of());
        // Un objet étranger posé sur un slot du kit est mis de côté puis rangé ailleurs
        List<ItemStack> displaced = new ArrayList<>();
        for (JoinItem item : kit) {
            for (int slot : item.slots()) {
                ItemStack current = inventory.getItem(slot);
                if (current != null && !current.getType().isAir()) {
                    displaced.add(current);
                    inventory.setItem(slot, null);
                }
            }
        }
        for (JoinItem item : kit) {
            for (int slot : item.slots()) {
                inventory.setItem(slot, item.create());
            }
        }
        for (ItemStack stack : displaced) {
            for (ItemStack rest : inventory.addItem(stack).values()) {
                player.getWorld().dropItem(player.getLocation(), rest);
                getLogger().warning("Inventaire plein : " + rest.getAmount() + " " + rest.getType()
                        + " de " + player.getName() + " posé(s) au sol.");
            }
        }
        currentKits.put(player.getUniqueId(), kitName);
    }

    /**
     * Retire les items du plugin et, si demandé, ceux des anciens plugins.
     */
    private void removeItems(Player player, boolean cleanLegacy) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        int legacy = 0;
        for (int i = 0; i < contents.length; i++) {
            if (isOwn(contents[i])) {
                inventory.setItem(i, null);
            } else if (cleanLegacy && isLegacy(contents[i])) {
                inventory.setItem(i, null);
                legacy++;
            }
        }
        if (isOwn(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
        }
        if (legacy > 0) {
            getLogger().info(legacy + " ancien(s) item(s) retiré(s) de l'inventaire de " + player.getName() + ".");
        }
    }

    /**
     * Item du plugin (porte le tag), même s'il n'existe plus dans la configuration.
     */
    boolean isOwn(ItemStack stack) {
        return stack != null && stack.hasItemMeta()
                && stack.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.STRING);
    }

    private boolean isLegacy(ItemStack stack) {
        if (legacyTags.isEmpty() || stack == null || !stack.hasItemMeta()) {
            return false;
        }
        String data = stack.getItemMeta().getAsComponentString();
        return legacyTags.stream().anyMatch(data::contains);
    }

    /**
     * Définition de l'item, ou null si ce n'est pas un item du plugin.
     */
    JoinItem item(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        String id = stack.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
        return id == null ? null : items.get(id);
    }

    boolean has(ItemStack stack, JoinItem.Flag flag) {
        JoinItem item = item(stack);
        return item != null && item.flags().contains(flag);
    }

    boolean bypass(Player player) {
        return creativeBypass && player.getGameMode() == GameMode.CREATIVE;
    }

    NamespacedKey itemKey() {
        return itemKey;
    }

    // ------------------------------------------------------------------ actions

    void runActions(Player player, JoinItem item) {
        if (item.cooldown() > 0) {
            Map<String, Long> playerCooldowns = cooldowns.computeIfAbsent(player.getUniqueId(), uuid -> new HashMap<>());
            long now = System.currentTimeMillis();
            Long until = playerCooldowns.get(item.id());
            if (until != null && until > now) {
                return;
            }
            playerCooldowns.put(item.id(), now + item.cooldown());
        }
        for (String raw : item.actions()) {
            String action = raw.replace("%player%", player.getName());
            int colon = action.indexOf(':');
            String type = colon < 0 ? "player" : action.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = colon < 0 ? action.trim() : action.substring(colon + 1).trim();
            switch (type) {
                case "player" -> player.performCommand(stripSlash(value));
                case "console" -> getServer().dispatchCommand(getServer().getConsoleSender(), stripSlash(value));
                case "message" -> player.sendMessage(LEGACY.deserialize(value));
                case "title" -> {
                    String[] parts = value.split("\\|", 2);
                    Component subtitle = parts.length > 1 ? LEGACY.deserialize(parts[1]) : Component.empty();
                    player.showTitle(Title.title(LEGACY.deserialize(parts[0]), subtitle, TITLE_TIMES));
                }
                case "actionbar" -> player.sendActionBar(LEGACY.deserialize(value));
                default -> getLogger().warning("Action inconnue sur l'item " + item.id() + " : " + raw);
            }
        }
        if (item.sound() != null) {
            player.playSound(player.getLocation(), item.sound(), 1.0f, 1.0f);
        }
    }

    private static String stripSlash(String command) {
        return command.startsWith("/") ? command.substring(1) : command;
    }

    /**
     * Texte d'item : codes & et hex, sans l'italique que Minecraft met par défaut sur les noms.
     */
    static Component text(String legacy) {
        return LEGACY.deserialize(legacy).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    // ------------------------------------------------------------------ événements joueur

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        getServer().getScheduler().runTaskLater(this, () -> {
            if (player.isOnline()) {
                refresh(player, true, true);
                selectHeldSlot(player);
            }
        }, delay);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        getServer().getScheduler().runTaskLater(this, () -> {
            if (player.isOnline()) {
                refresh(player, true, false);
            }
        }, Math.max(1, delay));
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        refresh(event.getPlayer(), true, false);
        selectHeldSlot(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        currentKits.remove(event.getPlayer().getUniqueId());
        cooldowns.remove(event.getPlayer().getUniqueId());
    }

    private void selectHeldSlot(Player player) {
        if (heldSlot >= 0 && heldSlot <= 8) {
            player.getInventory().setHeldItemSlot(heldSlot);
        }
    }

    // ------------------------------------------------------------------ commandes

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("shjoinitems.admin")) {
            send(sender, "no-permission");
            return true;
        }
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                loadConfiguration();
                getServer().getOnlinePlayers().forEach(player -> refresh(player, true, false));
                send(sender, "reloaded", "{items}", String.valueOf(items.size()),
                        "{kits}", String.valueOf(kits.size()), "{zones}", String.valueOf(zones.size()));
            }
            case "refresh" -> {
                Player target = args.length > 1 ? getServer().getPlayerExact(args[1])
                        : sender instanceof Player player ? player : null;
                if (target == null) {
                    send(sender, "unknown-player");
                    return true;
                }
                refresh(target, true, true);
                send(sender, "refreshed", "{player}", target.getName());
            }
            default -> send(sender, "usage");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("shjoinitems.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return StringUtil.copyPartialMatches(args[0], List.of("reload", "refresh"), new ArrayList<>());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("refresh")) {
            List<String> names = getServer().getOnlinePlayers().stream().map(Player::getName).toList();
            return StringUtil.copyPartialMatches(args[1], names, new ArrayList<>());
        }
        return List.of();
    }

    private void send(CommandSender sender, String key, String... replacements) {
        String message = getConfig().getString("messages." + key, "");
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            message = message.replace(replacements[i], replacements[i + 1]);
        }
        if (!message.isEmpty()) {
            sender.sendMessage(LEGACY.deserialize(message));
        }
    }
}
