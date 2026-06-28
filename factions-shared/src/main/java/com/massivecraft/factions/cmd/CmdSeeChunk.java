package com.massivecraft.factions.cmd;

import com.massivecraft.factions.FLocation;
import com.massivecraft.factions.FactionsPlugin;
import com.massivecraft.factions.scheduler.ScheduledTaskHandle;
import com.massivecraft.factions.struct.Permission;
import com.massivecraft.factions.util.FastMath;
import com.massivecraft.factions.util.VisualizeUtil;
import com.massivecraft.factions.zcore.util.TL;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CmdSeeChunk extends FCommand {

    // Concurrent: written from commands, removed from move/quit listeners, and iterated by the
    // particle task - all across region threads on Folia (a HashMap would race / throw CME).
    public static Map<String, Boolean> seeChunkMap = new ConcurrentHashMap<>();
    Long interval;
    //private boolean useParticles;
    //private final ParticleEffect effect = ParticleEffect.REDSTONE;

    private ScheduledTaskHandle taskHandle;


    //I remade it cause of people getting mad that I had the same seechunk as drtshock

    // Resolved lazily through Bukkit's name registry to avoid shaded XMaterial <clinit>
    // (which fails to parse the server version on MC 26 / Folia). Both fields fall back
    // to AIR if the running runtime does not expose the modern material name.
    private Material redstoneLamp;
    private Material blackStainedGlass;

    private static final int[][] OFFSETS = new int[][] {{0, 0}, {15, 0}, {0, 15}, {15, 15}};


    public CmdSeeChunk() {
        super();

        getAliases().addAll(Aliases.seeChunk);

        //this.useParticles = FactionsPlugin.getInstance().getConfig().getBoolean("see-chunk.particles", true);
        interval = FactionsPlugin.getInstance().getConfig().getLong("see-chunk.interval", 10L);

        this.setRequirements(new CommandRequirements.Builder(Permission.SEECHUNK)
                .playerOnly()
                .build());

    }

    private Material redstoneLamp() {
        if (redstoneLamp == null) {
            Material match = Material.matchMaterial("REDSTONE_LAMP");
            if (match == null) {
                match = Material.matchMaterial("REDSTONE_LAMP_ON");
            }
            redstoneLamp = match != null ? match : Material.AIR;
        }
        return redstoneLamp;
    }

    private Material blackStainedGlass() {
        if (blackStainedGlass == null) {
            Material match = Material.matchMaterial("BLACK_STAINED_GLASS");
            if (match == null) {
                match = Material.matchMaterial("STAINED_GLASS");
            }
            blackStainedGlass = match != null ? match : Material.AIR;
        }
        return blackStainedGlass;
    }

    @Override
    public void perform(CommandContext context) {
        if (seeChunkMap.remove(context.player.getName()) != null) {
            context.msg(TL.COMMAND_SEECHUNK_DISABLED);
        } else {
            seeChunkMap.put(context.player.getName(), true);
            context.msg(TL.COMMAND_SEECHUNK_ENABLED);
            manageTask();
        }
    }

    private void manageTask() {
        if (taskHandle != null) {
            if (seeChunkMap.isEmpty()) {
                taskHandle.cancel();
                taskHandle = null;
            }
        } else {
            startTask();
        }
    }

    private void startTask() {
        // Global cadence driver; the per-player visualizer work is dispatched to each player's
        // entity scheduler so the world/block access stays region-local (required on Folia).
        taskHandle = FactionsPlugin.getInstance().getFactionScheduler().runGlobalTimer(() -> {
            Iterator<Map.Entry<String, Boolean>> iterator = seeChunkMap.entrySet().iterator();

            while (iterator.hasNext()) {
                Map.Entry<String, Boolean> entry = iterator.next();
                Player player = Bukkit.getPlayer(entry.getKey());

                if (player == null || !player.isOnline()) {
                    iterator.remove();
                    continue;
                }
                FactionsPlugin.getInstance().getFactionScheduler().runForEntity(player, () -> showBorders(player));
            }
            manageTask();
        }, 0, interval);
    }

    private void showBorders(Player me) {
        World world = me.getWorld();
        FLocation flocation = FLocation.wrap(me);

        int blockX = flocation.toBlockX();
        int blockZ = flocation.toBlockZ();

        for (int[] coords : OFFSETS) {
            int pillarX = blockX + coords[0];
            int pillarZ = blockZ + coords[1];

            showPillar(me, world, pillarX, pillarZ);
        }
    }

    private void showPillar(Player player, World world, int blockX, int blockZ) {
        int baseY = FastMath.floor(player.getLocation().getY());
        int maxY = baseY + 15;

        for (int y = baseY; y < maxY; y++) {
            Block block = world.getBlockAt(blockX, y, blockZ);

            if (block.getType() != Material.AIR) {
                continue;
            }
            //if (useParticles) {
            //    new ParticleBuilder(this.effect, block.getLocation().add(0.5, 0, 0.5)).setColor(Color.RED).display(player);
            //} else {
                VisualizeUtil.addLocation(player, block.getLocation(), y % 5 == 0 ? redstoneLamp() : blackStainedGlass());
           // }
        }
    }

    @Override
    public TL getUsageTranslation() {
        return TL.GENERIC_PLACEHOLDER;
    }

}