package org.saberdev.corex.addons;

import com.massivecraft.factions.util.Lazy;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.saberdev.corex.CoreAddon;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;


@CoreAddon(configVariable = "Anti-Piston-Glitch")
public class AntiPistonGlitch implements Listener {

    // Resolved lazily through Bukkit's name registry to avoid shaded XMaterial <clinit> on MC 26.
    private final Lazy<Set<Material>> materials = Lazy.of(() -> {
        Set<Material> set = new HashSet<>();
        addMaterialByName(set, "SUGAR_CANE");
        addMaterialByName(set, "MELON");
        addMaterialByName(set, "MELON_STEM");
        addMaterialByName(set, "GLISTERING_MELON_SLICE");
        return Collections.unmodifiableSet(set);
    });

    private static void addMaterialByName(Set<Material> target, String name) {
        Material material = Material.matchMaterial(name);
        if (material != null) {
            target.add(material);
        }
    }

    @EventHandler
    public void onRetract(BlockPistonExtendEvent event) {
        BlockFace direction = event.getDirection();
        Block to = event.getBlock().getRelative(direction);
        Block nextBlock = to.getRelative(direction);

        Set<Material> against = this.materials.get();
        if (against.contains(nextBlock.getType()) || against.contains(to.getType())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onFluxPatch(BlockPistonExtendEvent event) {
        BlockFace direction = event.getDirection();
        Block to = event.getBlock().getRelative(direction);

        String toBlockName = to.getType().toString();
        String nextBlockName = to.getRelative(direction).getType().toString();

        if ((toBlockName.endsWith("_GATE") || toBlockName.endsWith("_FENCE"))
                || (nextBlockName.endsWith("_GATE")
                || nextBlockName.endsWith("_FENCE"))) {
            event.setCancelled(true);
        }
    }
}
