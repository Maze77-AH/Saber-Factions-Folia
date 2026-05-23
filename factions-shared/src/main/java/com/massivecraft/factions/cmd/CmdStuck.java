package com.massivecraft.factions.cmd;

import com.massivecraft.factions.*;
import com.massivecraft.factions.scheduler.ScheduledTaskHandle;
import com.massivecraft.factions.util.TeleportUtil;
import com.massivecraft.factions.struct.Permission;
import com.massivecraft.factions.util.WorldUtil;
import com.massivecraft.factions.util.spiral.ChunkProcessingContext;
import com.massivecraft.factions.util.spiral.SpiralTask;
import com.massivecraft.factions.util.spiral.generator.SquareSpiralGenerator;
import com.massivecraft.factions.zcore.util.TL;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

public class CmdStuck extends FCommand {

    /**
     * @author FactionsUUID Team - Modified By CmdrKittens
     */

    public CmdStuck() {
        super();
        this.getAliases().addAll(Aliases.stuck);


        this.setRequirements(new CommandRequirements.Builder(Permission.STUCK)
                .playerOnly()
                .build());
    }

    @Override
    public void perform(CommandContext context) {
        final Player player = context.player;
        final Location sentAt = player.getLocation();
        final FLocation chunk = context.fPlayer.getLastStoodAt();
        final long delay = FactionsPlugin.getInstance().getConfig().getLong("hcf.stuck.delay", 30);
        final int radius = FactionsPlugin.getInstance().getConfig().getInt("hcf.stuck.radius", 10);

        if (!FactionsPlugin.getInstance().getConfig().getBoolean("hcf.stuck.Enabled", false)) {
            context.msg(TL.GENERIC_DISABLED, "Factions Stuck");
            return;
        }


        if (FactionsPlugin.getInstance().getStuckMap().containsKey(player.getUniqueId())) {
            long wait = FactionsPlugin.getInstance().getTimers().get(player.getUniqueId()) - System.currentTimeMillis();
            String time = DurationFormatUtils.formatDuration(wait, TL.COMMAND_STUCK_TIMEFORMAT.toString(), true);
            context.msg(TL.COMMAND_STUCK_EXISTS, time);
        } else {

            // if economy is enabled, they're not on the bypass list, and this command has a cost set, make 'em pay
            if (!context.payForCommand(Conf.econCostStuck, TL.COMMAND_STUCK_TOSTUCK.format(context.fPlayer.getName()), TL.COMMAND_STUCK_FORSTUCK.format(context.fPlayer.getName()))) {
                return;
            }

            final ScheduledTaskHandle handle = FactionsPlugin.getInstance().getFactionScheduler().runForEntityLater(player, new Runnable() {

                @Override
                public void run() {
                    if (!FactionsPlugin.getInstance().getStuckMap().containsKey(player.getUniqueId())) {
                        return;
                    }

                    // check for world difference or radius exceeding
                    final World world = chunk.getWorld();
                    if (world.getUID() != player.getWorld().getUID() || sentAt.distance(player.getLocation()) > radius) {
                        context.msg(TL.COMMAND_STUCK_OUTSIDE.format(radius));
                        FactionsPlugin.getInstance().getTimers().remove(player.getUniqueId());
                        FactionsPlugin.getInstance().getStuckMap().remove(player.getUniqueId());
                        return;
                    }

                    final Board board = Board.getInstance();
                    // spiral task to find nearest wilderness chunk
                    new SpiralTask(FLocation.wrap(context.player), radius * 2, new SquareSpiralGenerator()) {
                        @Override
                        public boolean work(ChunkProcessingContext ctx) {
                            FLocation chunk = ctx.getFLocation();
                            Faction faction = board.getFactionAt(chunk);
                            int buffer = FactionsPlugin.getInstance().getConfig().getInt("world-border.buffer", 0);
                            if (faction.isWilderness() && !chunk.isOutsideWorldBorder(buffer)) {
                                int cx = WorldUtil.chunkToBlock(chunk.getIntX());
                                int cz = WorldUtil.chunkToBlock(chunk.getIntZ());
                                FactionsPlugin.getInstance().getTimers().remove(player.getUniqueId());
                                FactionsPlugin.getInstance().getStuckMap().remove(player.getUniqueId());
                                this.stop();
                                // getHighestBlockYAt loads/reads the target chunk, so it must run on
                                // the region that owns that chunk (Folia). Resolve the safe Y there,
                                // then message + teleport on the player's own entity scheduler.
                                Location probe = new Location(world, cx, 0, cz);
                                FactionsPlugin.getInstance().getFactionScheduler().runAt(probe, () -> {
                                    int y = world.getHighestBlockYAt(cx, cz);
                                    Location tp = new Location(world, cx, y, cz);
                                    FactionsPlugin.getInstance().getFactionScheduler().runForEntity(player, () -> {
                                        context.msg(TL.COMMAND_STUCK_TELEPORT, tp.getBlockX(), tp.getBlockY(), tp.getBlockZ());
                                        TeleportUtil.teleport(player, tp);
                                    });
                                });
                                return false;
                            }
                            return true;
                        }
                    };
                }
            }, delay * 20);

            FactionsPlugin.getInstance().getTimers().put(player.getUniqueId(), System.currentTimeMillis() + (delay * 1000));
            long wait = FactionsPlugin.getInstance().getTimers().get(player.getUniqueId()) - System.currentTimeMillis();
            String time = DurationFormatUtils.formatDuration(wait, TL.COMMAND_STUCK_TIMEFORMAT.toString(), true);
            context.msg(TL.COMMAND_STUCK_START, time);
            FactionsPlugin.getInstance().getStuckMap().put(player.getUniqueId(), handle);
        }
    }

    @Override
    public TL getUsageTranslation() {
        return TL.COMMAND_STUCK_DESCRIPTION;
    }
}
