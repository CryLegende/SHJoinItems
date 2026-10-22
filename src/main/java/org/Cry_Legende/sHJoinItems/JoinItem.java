package org.Cry_Legende.sHJoinItems;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Item défini dans config.yml : l'objet à donner, ses slots, ses protections et ses actions au clic.
 *
 * @param cooldown délai entre deux utilisations, en millisecondes
 */
record JoinItem(String id, ItemStack template, int[] slots, Set<Flag> flags, Set<Click> clicks,
                List<String> actions, String sound, long cooldown) {

    enum Flag {GLOW, UNBREAKABLE, HIDE_ATTRIBUTES, HIDE_ALL, LOCKED, NO_DROP, KEEP_ON_DEATH}

    enum Click {RIGHT, LEFT, INVENTORY}

    private static final Set<String> ACTION_TYPES = Set.of("player", "console", "message", "title", "actionbar");

    // Indices de PlayerInventory : 0-35 inventaire, 36 pieds, 37 jambes, 38 torse, 39 tête, 40 main secondaire
    private static final Map<String, Integer> NAMED_SLOTS = Map.of(
            "feet", 36, "legs", 37, "chest", 38, "head", 39, "offhand", 40);

    ItemStack create() {
        return template.clone();
    }

    boolean triggeredBy(Click click) {
        return !actions.isEmpty() && clicks.contains(click);
    }

    /**
     * Lit un item de la configuration ; null (avec un avertissement) s'il est inutilisable.
     */
    static JoinItem load(SHJoinItems plugin, String id, ConfigurationSection section) {
        Logger log = plugin.getLogger();
        Material material = Material.matchMaterial(section.getString("material", ""));
        if (material == null || material.isAir() || !material.isItem()) {
            log.warning("Item " + id + " ignoré : matériau inconnu « " + section.getString("material") + " ».");
            return null;
        }
        int[] slots = slots(section.get("slot"));
        if (slots.length == 0) {
            log.warning("Item " + id + " ignoré : slot invalide « " + section.get("slot") + " ».");
            return null;
        }

        Set<Flag> flags = EnumSet.noneOf(Flag.class);
        for (String name : section.getStringList("flags")) {
            try {
                flags.add(Flag.valueOf(name.trim().toUpperCase(Locale.ROOT).replace('-', '_')));
            } catch (IllegalArgumentException e) {
                log.warning("Item " + id + " : flag inconnu « " + name + " ».");
            }
        }
        Set<Click> clicks = EnumSet.noneOf(Click.class);
        for (String name : section.isList("click") ? section.getStringList("click") : List.of("right")) {
            try {
                clicks.add(Click.valueOf(name.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                log.warning("Item " + id + " : clic inconnu « " + name + " » (right, left ou inventory).");
            }
        }
        List<String> actions = section.getStringList("actions");
        for (String action : actions) {
            int colon = action.indexOf(':');
            String type = colon < 0 ? "player" : action.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            if (!ACTION_TYPES.contains(type)) {
                log.warning("Item " + id + " : type d'action inconnu « " + type + " » dans « " + action + " ».");
            }
        }
        long cooldown = Math.max(0, Math.round(section.getDouble("cooldown", 0) * 1000));

        int amount = Math.clamp(section.getInt("amount", 1), 1, material.getMaxStackSize());
        ItemStack stack = new ItemStack(material, amount);
        ItemMeta meta = stack.getItemMeta();
        if (section.isString("name")) {
            meta.displayName(SHJoinItems.text(section.getString("name")));
        }
        List<String> lore = section.getStringList("lore");
        if (!lore.isEmpty()) {
            meta.lore(lore.stream().map(SHJoinItems::text).toList());
        }
        ConfigurationSection enchantments = section.getConfigurationSection("enchantments");
        if (enchantments != null) {
            for (String key : enchantments.getKeys(false)) {
                NamespacedKey enchantmentKey = NamespacedKey.fromString(key.toLowerCase(Locale.ROOT));
                Enchantment enchantment = enchantmentKey == null ? null
                        : RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(enchantmentKey);
                if (enchantment == null) {
                    log.warning("Item " + id + " : enchantement inconnu « " + key + " ».");
                } else {
                    meta.addEnchant(enchantment, Math.max(1, enchantments.getInt(key, 1)), true);
                }
            }
        }
        if (flags.contains(Flag.GLOW)) {
            meta.setEnchantmentGlintOverride(true);
        }
        if (flags.contains(Flag.UNBREAKABLE)) {
            meta.setUnbreakable(true);
        }
        if (flags.contains(Flag.HIDE_ALL)) {
            meta.addItemFlags(ItemFlag.values());
        } else if (flags.contains(Flag.HIDE_ATTRIBUTES)) {
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        }
        meta.getPersistentDataContainer().set(plugin.itemKey(), PersistentDataType.STRING, id);
        stack.setItemMeta(meta);

        return new JoinItem(id, stack, slots, Collections.unmodifiableSet(flags), Collections.unmodifiableSet(clicks),
                List.copyOf(actions), section.getString("sound"), cooldown);
    }

    /**
     * slot : un numéro, un nom (head, chest, legs, feet, offhand), une liste, ou « 0, 1, 2 » comme dans ItemJoin.
     */
    private static int[] slots(Object value) {
        List<?> values;
        if (value instanceof List<?> list) {
            values = list;
        } else if (value == null) {
            values = List.of();
        } else {
            values = Arrays.asList(value.toString().split(","));
        }
        List<Integer> slots = new ArrayList<>();
        for (Object entry : values) {
            String name = entry.toString().trim().toLowerCase(Locale.ROOT);
            Integer slot = NAMED_SLOTS.get(name);
            if (slot == null) {
                try {
                    slot = Integer.parseInt(name);
                } catch (NumberFormatException e) {
                    return new int[0];
                }
            }
            if (slot < 0 || slot > 40) {
                return new int[0];
            }
            slots.add(slot);
        }
        return slots.stream().mapToInt(Integer::intValue).toArray();
    }
}
