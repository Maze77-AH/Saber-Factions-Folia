package org.saberdev.corex.addons;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.saberdev.corex.CoreAddon;

@CoreAddon(configVariable = "Anti-Book-Quill-Crash")
public class AntiBookQuillCrash implements Listener {

    // Resolved lazily so this addon does not pull in shaded XMaterial during <clinit>.
    private Material writableBook;

    private Material writableBook() {
        if (writableBook == null) {
            Material match = Material.matchMaterial("WRITABLE_BOOK");
            writableBook = match != null ? match : Material.AIR;
        }
        return writableBook;
    }

    @EventHandler
    public void onAttemptCrash(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack item = player.getItemInHand();
        Material target = writableBook();
        if (target != Material.AIR && item != null && item.getType() == target) {
            event.setCancelled(true);
        }
    }
}
