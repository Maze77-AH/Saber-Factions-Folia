package org.saberdev.corex.addons;

import com.massivecraft.factions.util.Cooldown;
import com.massivecraft.factions.util.Lazy;
import com.massivecraft.factions.util.TimeUtil;
import com.massivecraft.factions.zcore.util.TL;
import com.massivecraft.factions.zcore.util.TextUtil;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.saberdev.corex.CoreAddon;
import org.saberdev.corex.CoreX;

@CoreAddon(configVariable = "God-Apple-Cooldown")
public class GappleCooldown implements Listener {

    private final Lazy<Material> enchantedGoldenApple = Lazy.of(() -> Material.matchMaterial("ENCHANTED_GOLDEN_APPLE"));

    @EventHandler
    public void onEatGapple(PlayerItemConsumeEvent e){
        Material target = this.enchantedGoldenApple.get();
        if(target != null && e.getItem().getType() == target) {
            if(Cooldown.isOnCooldown(e.getPlayer(), "godAppleCooldown")) {
                e.setCancelled(true);
                long remaining = e.getPlayer().getMetadata("godAppleCooldown").get(0).asLong() - System.currentTimeMillis();
                int remainSec = (int) (remaining / 1000L);
                e.getPlayer().sendMessage(TextUtil.parse(TL.GOD_APPLE_COOLDOWN.toString().replace("{seconds}", TimeUtil.formatSeconds(remainSec))));
            } else {
                Cooldown.setCooldown(e.getPlayer(), "godAppleCooldown", CoreX.getConfig().fetchInt("Cooldowns.God_Apple"));
            }
        }
    }
}
