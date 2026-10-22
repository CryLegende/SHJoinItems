package org.Cry_Legende.sHJoinItems;

import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

/**
 * Clics sur les items (actions) et protections : déplacement, jet, pose, mort.
 */
final class ItemListener implements Listener {

    private static final Set<InventoryAction> DROPS = Set.of(InventoryAction.DROP_ALL_CURSOR,
            InventoryAction.DROP_ONE_CURSOR, InventoryAction.DROP_ALL_SLOT, InventoryAction.DROP_ONE_SLOT);

    private final SHJoinItems plugin;

    ItemListener(SHJoinItems plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.PHYSICAL) {
            return;
        }
        JoinItem item = plugin.item(event.getItem());
        if (item == null) {
            return;
        }
        boolean right = event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK;
        if (item.triggeredBy(right ? JoinItem.Click.RIGHT : JoinItem.Click.LEFT)) {
            event.setCancelled(true);
            if (event.getHand() == EquipmentSlot.HAND) {
                plugin.runActions(event.getPlayer(), item);
            }
            return;
        }
        // Un item du plugin ne se pose pas et ne s'utilise pas (corail, poche d'encre lumineuse…)
        if (right && !plugin.bypass(event.getPlayer())) {
            event.setUseItemInHand(Event.Result.DENY);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof ItemFrame && !plugin.bypass(event.getPlayer())
                && plugin.isOwn(event.getPlayer().getInventory().getItem(event.getHand()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (!plugin.bypass(event.getPlayer()) && plugin.isOwn(event.getPlayerItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        JoinItem clicked = plugin.item(event.getCurrentItem());
        if (clicked != null && clicked.triggeredBy(JoinItem.Click.INVENTORY) && !plugin.bypass(player)
                && event.getClickedInventory() != null && event.getClickedInventory().getType() == InventoryType.PLAYER) {
            event.setCancelled(true);
            // Pas d'ouverture de menu pendant un clic d'inventaire : les actions partent au tick suivant
            plugin.getServer().getScheduler().runTask(plugin, () -> plugin.runActions(player, clicked));
            return;
        }
        if (plugin.bypass(player)) {
            return;
        }
        ItemStack hotbar = event.getClick() == ClickType.NUMBER_KEY ? player.getInventory().getItem(event.getHotbarButton()) : null;
        ItemStack offhand = event.getClick() == ClickType.SWAP_OFFHAND ? player.getInventory().getItemInOffHand() : null;
        if (plugin.has(event.getCurrentItem(), JoinItem.Flag.LOCKED) || plugin.has(event.getCursor(), JoinItem.Flag.LOCKED)
                || plugin.has(hotbar, JoinItem.Flag.LOCKED) || plugin.has(offhand, JoinItem.Flag.LOCKED)) {
            event.setCancelled(true);
        } else if (DROPS.contains(event.getAction()) && (plugin.has(event.getCurrentItem(), JoinItem.Flag.NO_DROP)
                || plugin.has(event.getCursor(), JoinItem.Flag.NO_DROP))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && !plugin.bypass(player)
                && plugin.has(event.getOldCursor(), JoinItem.Flag.LOCKED)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (!plugin.bypass(event.getPlayer()) && (plugin.has(event.getMainHandItem(), JoinItem.Flag.LOCKED)
                || plugin.has(event.getOffHandItem(), JoinItem.Flag.LOCKED))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (!plugin.bypass(event.getPlayer()) && plugin.has(event.getItemDrop().getItemStack(), JoinItem.Flag.NO_DROP)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!plugin.bypass(event.getPlayer()) && plugin.isOwn(event.getItemInHand())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        // Pas lâchés au sol : la réapparition redonne le kit
        event.getDrops().removeIf(stack -> plugin.has(stack, JoinItem.Flag.KEEP_ON_DEATH));
    }
}
