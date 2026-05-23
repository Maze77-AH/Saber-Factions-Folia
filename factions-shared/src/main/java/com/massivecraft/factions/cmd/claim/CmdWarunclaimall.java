package com.massivecraft.factions.cmd.claim;

import com.massivecraft.factions.Conf;
import com.massivecraft.factions.Factions;
import com.massivecraft.factions.FactionsPlugin;
import com.massivecraft.factions.cmd.Aliases;
import com.massivecraft.factions.cmd.CommandContext;
import com.massivecraft.factions.cmd.CommandRequirements;
import com.massivecraft.factions.cmd.FCommand;
import com.massivecraft.factions.realfactions.ClaimTransactionService;
import com.massivecraft.factions.struct.Permission;
import com.massivecraft.factions.util.Logger;
import com.massivecraft.factions.zcore.util.TL;
import org.bukkit.Bukkit;
import org.bukkit.World;

public class CmdWarunclaimall extends FCommand {

    /**
     * @author FactionsUUID Team - Modified By CmdrKittens
     */

    public CmdWarunclaimall() {
        this.getAliases().addAll(Aliases.unclaim_all_war);
        this.getOptionalArgs().put("world", "all");

        this.setRequirements(new CommandRequirements.Builder(Permission.MANAGE_WAR_ZONE)
                .build());
    }

    @Override
    public void perform(CommandContext context) {
        String worldName = context.argAsString(0);
        World world = null;

        if (worldName != null) world = Bukkit.getWorld(worldName);

        String id = Factions.getInstance().getWarZone().getId();

        Runnable afterCommit = () -> {
            context.msg(TL.COMMAND_WARUNCLAIMALL_SUCCESS);
            if (Conf.logLandUnclaims) {
                Logger.print(TL.COMMAND_WARUNCLAIMALL_LOG.format(context.fPlayer.getName()), Logger.PrefixType.DEFAULT);
            }
        };

        // Route the board write through the transaction layer (single-writer model thread).
        ClaimTransactionService claims = FactionsPlugin.getInstance().getRealFactionsServices().claims();
        if (world == null) {
            claims.unclaimAll(id, afterCommit);
        } else {
            claims.unclaimAllInWorld(id, world, afterCommit);
        }
    }

    @Override
    public TL getUsageTranslation() {
        return TL.COMMAND_WARUNCLAIMALL_DESCRIPTION;
    }

}
