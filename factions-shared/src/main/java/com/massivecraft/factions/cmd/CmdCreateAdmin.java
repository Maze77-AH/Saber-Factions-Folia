package com.massivecraft.factions.cmd;

import com.massivecraft.factions.Conf;
import com.massivecraft.factions.Factions;
import com.massivecraft.factions.FactionsPlugin;
import com.massivecraft.factions.cmd.reserve.ReserveObject;
import com.massivecraft.factions.event.FactionCreateEvent;
import com.massivecraft.factions.realfactions.FactionCreationService;
import com.massivecraft.factions.realfactions.RealFactionsServices;
import com.massivecraft.factions.struct.Permission;
import com.massivecraft.factions.util.Logger;
import com.massivecraft.factions.util.MiscUtil;
import com.massivecraft.factions.zcore.util.TL;
import org.bukkit.Bukkit;

import java.util.ArrayList;

public class CmdCreateAdmin extends FCommand {

    public CmdCreateAdmin() {
        super();
        this.getAliases().addAll(Aliases.createAdmin);
        this.getRequiredArgs().add("faction tag");

        this.setRequirements(new CommandRequirements.Builder(Permission.CREATE_ADMIN)
                .playerOnly()
                .build());
    }

    @Override
    public void perform(CommandContext context) {
        String tag = context.argAsString(0);

        if (Factions.getInstance().isTagTaken(tag)) {
            context.msg(TL.COMMAND_CREATE_INUSE);
            return;
        }

        ArrayList<String> tagValidationErrors = MiscUtil.validateTag(tag);
        if (!tagValidationErrors.isEmpty()) {
            context.sendMessage(tagValidationErrors);
            return;
        }

        FactionCreateEvent createEvent = new FactionCreateEvent(context.player, tag);
        Bukkit.getServer().getPluginManager().callEvent(createEvent);
        if (createEvent.isCancelled()) {
            return;
        }

        ReserveObject factionReserve = FactionsPlugin.getInstance().getFactionReserves().stream()
                .filter(factionReserve1 -> factionReserve1.getFactionName().equalsIgnoreCase(tag))
                .findFirst()
                .orElse(null);

        RealFactionsServices services = FactionsPlugin.getInstance().getRealFactionsServices();
        FactionCreationService creation = services.factionCreation();
        creation.create(faction -> {
            faction.setTag(tag);
            faction.setAdminFaction(true);
            faction.setPermanent(true);

            if (!creation.applyStartingBalance(faction)) {
                creation.rollbackCreation(faction, null, 0.0D, null);
                context.msg(TL.COMMAND_CREATE_ERROR);
                return;
            }

            if (Conf.allFactionsPeaceful) {
                faction.setPeaceful(true);
                faction.setPeacefulExplosionsEnabled(false);
            }
            if (FactionsPlugin.getInstance().getFactionDataHelper() != null) {
                FactionsPlugin.getInstance().getFactionDataHelper().getOrLoadFactionData(faction);
            }
            if (factionReserve != null) {
                FactionsPlugin.getInstance().getFactionReserves().remove(factionReserve);
            }

            context.msg(TL.COMMAND_CREATEADMIN_SUCCESS, faction.getTag(context.fPlayer));

            if (Conf.logFactionCreate) {
                Logger.print(context.fPlayer.getName() + " created admin faction: " + tag, Logger.PrefixType.DEFAULT);
            }
        }, () -> context.msg(TL.COMMAND_CREATE_ERROR));
    }

    @Override
    public TL getUsageTranslation() {
        return TL.COMMAND_CREATEADMIN_DESCRIPTION;
    }
}
