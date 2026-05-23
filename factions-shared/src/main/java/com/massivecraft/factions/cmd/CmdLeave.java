package com.massivecraft.factions.cmd;

import com.massivecraft.factions.FactionsPlugin;
import com.massivecraft.factions.struct.Permission;
import com.massivecraft.factions.zcore.util.TL;

public class CmdLeave extends FCommand {

    /**
     * @author FactionsUUID Team - Modified By CmdrKittens
     */

    public CmdLeave() {
        super();
        this.getAliases().addAll(Aliases.leave);

        this.setRequirements(new CommandRequirements.Builder(Permission.LEAVE)
                .playerOnly()
                .memberOnly()
                .build());
    }

    @Override
    public void perform(CommandContext context) {
        // leave() performs faction membership model writes (and a possible disband) and
        // self-schedules its Bukkit flight effect onto the player's entity scheduler. Route the
        // model writes through the single-writer model thread.
        FactionsPlugin.getInstance().getRealFactionsServices().executor().runFactionWrite(() -> context.fPlayer.leave(true));
    }

    @Override
    public TL getUsageTranslation() {
        return TL.LEAVE_DESCRIPTION;
    }

}