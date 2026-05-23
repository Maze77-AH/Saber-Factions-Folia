package org.saberdev.corex.addons;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.EnchantingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.saberdev.corex.CoreAddon;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author: Driftay
 * @Date: 3/28/2023 12:31 PM
 */
@CoreAddon(configVariable = "Enchanting-Lapis")
public class AutoLapisEnchant implements Listener {

    private final List<EnchantingInventory> inventories = new ArrayList<>();

    // Built lazily so this addon does not pull in shaded XMaterial during <clinit>.
    private ItemStack cachedLapis;

    private ItemStack lapis() {
        if (cachedLapis != null) {
            return cachedLapis;
        }
        Material material = Material.matchMaterial("LAPIS_LAZULI");
        if (material == null) {
            material = Material.matchMaterial("INK_SACK");
        }
        cachedLapis = material != null ? new ItemStack(material, 64) : null;
        return cachedLapis;
    }

    @EventHandler
    public void openInventoryEvent(InventoryOpenEvent e) {
        Inventory i = e.getInventory();
        if (i instanceof EnchantingInventory) {
            ItemStack lapis = lapis();
            if (lapis != null) {
                i.setItem(1, lapis);
            }
            this.inventories.add((EnchantingInventory) i);
        }
    }

    @EventHandler
    public void closeInventoryEvent(InventoryCloseEvent e) {
        Inventory i = e.getInventory();
        if (i instanceof EnchantingInventory &&
                this.inventories.contains(i)) {
            i.setItem(1, null);
            this.inventories.remove(i);
        }
    }

    @EventHandler
    public void inventoryClickEvent(InventoryClickEvent e) {
        Inventory i = e.getClickedInventory();
        if (i instanceof EnchantingInventory &&
                this.inventories.contains(i) && e.getSlot() == 1)
            e.setCancelled(true);
    }

    @EventHandler
    public void enchantItemEvent(EnchantItemEvent e) {
        Inventory i = e.getInventory();
        if (i instanceof EnchantingInventory &&
                this.inventories.contains(i)) {
            ItemStack lapis = lapis();
            if (lapis != null) {
                e.getInventory().setItem(1, lapis);
            }
        }
    }
}
