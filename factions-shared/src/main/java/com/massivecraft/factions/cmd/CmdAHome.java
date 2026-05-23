package com.massivecraft.factions.cmd;

import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FactionsPlugin;
import com.massivecraft.factions.Faction;
import com.massivecraft.factions.struct.Permission;
import com.massivecraft.factions.util.TeleportUtil;
import com.massivecraft.factions.zcore.util.TL;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;

public class CmdAHome extends FCommand {

    /**
     * @author FactionsUUID Team - Modified By CmdrKittens
     */

    public CmdAHome() {
        super();
        this.getAliases().addAll(Aliases.ahome);

        this.getRequiredArgs().add("player");

        this.setRequirements(new CommandRequirements.Builder(Permission.AHOME).noDisableOnLock().build());
    }


    @Override
    public void perform(CommandContext context) {
        FPlayer target = context.argAsBestFPlayerMatch(0);
        if (target == null) {
            context.msg(TL.GENERIC_NOPLAYERMATCH, context.argAsString(0));
            return;
        }

        if (target.isOnline()) {
            Faction faction = target.getFaction();
            if (faction.hasHome()) {
                Player targetPlayer = target.getPlayer();
                if (targetPlayer == null) {
                    context.msg(TL.COMMAND_AHOME_OFFLINE, target.getName());
                    return;
                }
                context.msg(TL.COMMAND_AHOME_SUCCESS, target.getName());
                FactionsPlugin.getInstance().getFactionScheduler().runForEntity(targetPlayer, () -> {
                    TeleportUtil.teleport(targetPlayer, faction.getHome(), PlayerTeleportEvent.TeleportCause.PLUGIN);
                    target.msg(TL.COMMAND_AHOME_TARGET);
                });
            } else {
                context.msg(TL.COMMAND_AHOME_NOHOME, target.getName());
            }
        } else {
            context.msg(TL.COMMAND_AHOME_OFFLINE, target.getName());
        }
    }

    @Override
    public TL getUsageTranslation() {
        return TL.COMMAND_AHOME_DESCRIPTION;
    }
}
