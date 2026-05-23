package org.saberdev.corex.addons;

import com.massivecraft.factions.util.Lazy;
import com.massivecraft.factions.util.XPotionEffect;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.saberdev.corex.CoreAddon;
import org.saberdev.corex.CoreX;

import java.util.List;
import java.util.Optional;

@CoreAddon(configVariable = "Anti-Mob-Movement")
public class AntiMobMovement implements Listener {

    public List<String> entList = CoreX.getConfig().fetchStringList("Anti-Mob-Movement.Mob-List");

    // Resolved lazily through Bukkit's own EntityType registry to avoid shaded XSeries
    // (whose server-version parse <clinit> fails on MC 26). Names match modern Bukkit;
    // legacy aliases ("DROPPED_ITEM", "PRIMED_TNT") are kept as fallbacks.
    private final Lazy<EntityType> itemEntity = Lazy.of(() -> resolveEntityType("ITEM", "DROPPED_ITEM"));
    private final Lazy<EntityType> tntEntity = Lazy.of(() -> resolveEntityType("TNT", "PRIMED_TNT"));

    private static EntityType resolveEntityType(String... names) {
        for (String name : names) {
            try {
                return EntityType.valueOf(name);
            } catch (IllegalArgumentException ignored) {
                // Try next alias.
            }
        }
        return null;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void entitySpawnEvent(EntitySpawnEvent event) {
        if (entList.isEmpty()) return;
        EntityType type = event.getEntityType();
        EntityType item = this.itemEntity.get();
        EntityType tnt = this.tntEntity.get();
        if (type == EntityType.PLAYER || (item != null && type == item) || (tnt != null && type == tnt)) {
            return;
        }
        if (!entList.contains(type.name())) {
            return;
        }
        LivingEntity entity = (LivingEntity) event.getEntity();
        Optional<PotionEffectType> slowness = XPotionEffect.SLOWNESS.toPotionEffectType();
        if (slowness.isPresent()) {
            PotionEffect potionEffect = new PotionEffect(slowness.get(), Integer.MAX_VALUE, 25);
            entity.addPotionEffect(potionEffect);
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent e) {
        if (e.getEntity() instanceof Creeper) {
            Creeper creeper = (Creeper) e.getEntity();
            removeAllPotionEffects(creeper);
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent e) {
        if (entList.contains(e.getEntity().getType().name())) {
            removeAllPotionEffects(e.getEntity());
        }
    }

    private void removeAllPotionEffects(LivingEntity entity) {
        for (PotionEffect activePotionEffect : entity.getActivePotionEffects()) {
            entity.removePotionEffect(activePotionEffect.getType());
        }
    }
}
