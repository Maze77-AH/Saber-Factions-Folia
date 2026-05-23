package com.massivecraft.factions.cmd;

import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FPlayers;
import com.massivecraft.factions.Faction;
import com.massivecraft.factions.FactionsPlugin;
import com.massivecraft.factions.struct.Permission;
import com.massivecraft.factions.zcore.util.TL;

public class CmdPeaceful extends FCommand {

    /**
     * @author FactionsUUID Team - Modified By CmdrKittens
     */

    public CmdPeaceful() {
        super();
        this.getAliases().addAll(Aliases.peaceful);
        this.getRequiredArgs().add("faction tag");

        this.setRequirements(new CommandRequirements.Builder(Permission.SET_PEACEFUL)
                .build());

    }

    @Override
    public void perform(CommandContext context) {
        Faction faction = context.argAsFaction(0);
        if (faction == null) {
            return;
        }

        final Faction target = faction;
        // Route the peaceful-flag write and inform loop through the single-writer model thread.
        FactionsPlugin.getInstance().getRealFactionsServices().executor().runFactionWrite(() -> {
            String change;
            if (target.isPeaceful()) {
                change = TL.COMMAND_PEACEFUL_REVOKE.toString();
                target.setPeaceful(false);
            } else {
                change = TL.COMMAND_PEACEFUL_GRANT.toString();
                target.setPeaceful(true);
            }

            // Inform all players
            for (FPlayer fplayer : FPlayers.getInstance().getOnlinePlayers()) {
                String blame = (context.fPlayer == null ? TL.GENERIC_SERVERADMIN.toString() : context.fPlayer.describeTo(fplayer, true));
                if (fplayer.getFaction() == target) {
                    fplayer.msg(TL.COMMAND_PEACEFUL_YOURS, blame, change);
                } else {
                    fplayer.msg(TL.COMMAND_PEACEFUL_OTHER, blame, change, target.getTag(fplayer));
                }
            }
        });
    }

    @Override
    public TL getUsageTranslation() {
        return TL.COMMAND_PEACEFUL_DESCRIPTION;
    }

}
