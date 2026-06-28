package com.massivecraft.factions.cmd;

import com.massivecraft.factions.struct.Permission;
import com.massivecraft.factions.zcore.util.TL;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CmdFGlobal extends FCommand {

    /**
     * @author Trent
     */

    // Read from the async chat handler (per recipient) and written from this command on region
    // threads, so it must be concurrent. A Set also makes the per-recipient contains() check O(1).
    public static Set<UUID> toggled = ConcurrentHashMap.newKeySet();

    public CmdFGlobal() {
        super();
        this.getAliases().addAll(Aliases.global);

        this.setRequirements(new CommandRequirements.Builder(Permission.GLOBALCHAT)
                .playerOnly()
                .memberOnly()
                .build());
    }

    @Override
    public void perform(CommandContext context) {
        // /f global

        if (toggled.contains(context.player.getUniqueId())) {
            toggled.remove(context.player.getUniqueId());
        } else {
            toggled.add(context.player.getUniqueId());
        }

        context.msg(TL.COMMAND_F_GLOBAL_TOGGLE, toggled.contains(context.player.getUniqueId()) ? "disabled" : "enabled");
    }

    @Override
    public TL getUsageTranslation() {
        return TL.COMMAND_F_GLOBAL_DESCRIPTION;
    }

}
