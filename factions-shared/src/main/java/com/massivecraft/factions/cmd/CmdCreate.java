package com.massivecraft.factions.cmd;

import com.massivecraft.factions.*;
import com.massivecraft.factions.cmd.reserve.ReserveObject;
import com.massivecraft.factions.event.FPlayerJoinEvent;
import com.massivecraft.factions.event.FactionCreateEvent;
import com.massivecraft.factions.Factions;
import com.massivecraft.factions.realfactions.FactionCreationService;
import com.massivecraft.factions.realfactions.RealFactionsEconomyService;
import com.massivecraft.factions.realfactions.RealFactionsServices;
import com.massivecraft.factions.struct.Permission;
import com.massivecraft.factions.struct.Role;
import com.massivecraft.factions.util.Cooldown;
import com.massivecraft.factions.util.Logger;
import com.massivecraft.factions.util.MiscUtil;
import com.massivecraft.factions.zcore.util.TL;
import org.bukkit.Bukkit;

import java.util.ArrayList;


public class CmdCreate extends FCommand {

    /**
     * @author FactionsUUID Team - Modified By CmdrKittens
     */

    public CmdCreate() {
        super();
        this.getAliases().addAll(Aliases.create);

        this.getRequiredArgs().add("faction tag");

        this.setRequirements(new CommandRequirements.Builder(Permission.CREATE)
                .playerOnly()
                .build());
    }

    @Override
    public void perform(CommandContext context) {
        String tag = context.argAsString(0);

        if (context.fPlayer.hasFaction()) {
            context.msg(TL.COMMAND_CREATE_MUSTLEAVE);
            return;
        }

        if (Factions.getInstance().isTagTaken(tag)) {
            context.msg(TL.COMMAND_CREATE_INUSE);
            return;
        }

        if (Cooldown.isOnCooldown(context.fPlayer.getPlayer(), "createCooldown") && !context.fPlayer.isAdminBypassing()) {
            context.msg(TL.COMMAND_COOLDOWN);
            return;
        }

        ReserveObject factionReserve = FactionsPlugin.getInstance().getFactionReserves().stream().filter(factionReserve1 -> factionReserve1.getFactionName().equalsIgnoreCase(tag)).findFirst().orElse(null);
        if (factionReserve != null && !factionReserve.getName().equalsIgnoreCase(context.player.getName())) {
            context.msg(TL.COMMAND_CREATE_ALREADY_RESERVED);
            return;
        }

        ArrayList<String> tagValidationErrors = MiscUtil.validateTag(tag);
        if (tagValidationErrors.size() > 0) {
            context.sendMessage(tagValidationErrors);
            return;
        }

        // if economy is enabled, they're not on the bypass list, and this command has a cost set, make sure they can pay
        RealFactionsServices services = FactionsPlugin.getInstance().getRealFactionsServices();
        RealFactionsEconomyService economy = services.economy();
        if (economy.applyCommandEconomyCosts()
                && !context.canAffordCommand(Conf.econCostCreate, TL.COMMAND_CREATE_TOCREATE.toString())) {
            return;
        }

        //Check for total factions on the server.
        if(Factions.getInstance().getAllNormalFactions().size() >= Conf.totalAllowedFactionsOnServer && Conf.totalAllowedFactionsOnServer > 0 && !context.fPlayer.isAdminBypassing()) {
            context.msg(TL.COMMAND_CREATE_OVER_LIMIT);
            return;
        }

        // trigger the faction creation event (cancellable)
        FactionCreateEvent createEvent = new FactionCreateEvent(context.player, tag);
        Bukkit.getServer().getPluginManager().callEvent(createEvent);
        if (createEvent.isCancelled()) {
            return;
        }

        // then make 'em pay (if applicable)
        if (economy.applyCommandEconomyCosts()
                && !context.payForCommand(Conf.econCostCreate, TL.COMMAND_CREATE_TOCREATE, TL.COMMAND_CREATE_FORCREATE)) {
            return;
        }

        final ReserveObject reservedTag = factionReserve;
        final FactionCreationService creation = services.factionCreation();
        creation.create(faction -> {
            faction.setTag(tag);
            if (reservedTag != null) {
                FactionsPlugin.getInstance().getFactionReserves().remove(reservedTag);
            }

            FPlayerJoinEvent joinEvent = new FPlayerJoinEvent(
                    FPlayers.getInstance().getByPlayer(context.player), faction, FPlayerJoinEvent.PlayerJoinReason.CREATE);
            Bukkit.getServer().getPluginManager().callEvent(joinEvent);

            context.fPlayer.setFaction(faction, false);
            context.fPlayer.setRole(Role.LEADER);

            if (!creation.applyStartingBalance(faction)) {
                creation.rollbackCreation(
                        faction,
                        context.fPlayer,
                        Conf.econCostCreate,
                        TL.COMMAND_CREATE_FORCREATE.toString());
                context.msg(TL.COMMAND_CREATE_ERROR);
                return;
            }

            Cooldown.setCooldown(context.fPlayer.getPlayer(), "createCooldown",
                    FactionsPlugin.getInstance().getConfig().getInt("fcooldowns.f-create"));
            if (FactionsPlugin.getInstance().getConfig().getBoolean("faction-creation-broadcast", true)) {
                for (FPlayer follower : FPlayers.getInstance().getOnlinePlayers()) {
                    follower.msg(TL.COMMAND_CREATE_CREATED, context.fPlayer.getName(), faction.getTag(follower));
                }
            }
            context.msg(TL.COMMAND_CREATE_YOUSHOULD,
                    FactionsPlugin.getInstance().cmdBase.cmdDescription.getUsageTemplate(context));
            if (Conf.logFactionCreate) {
                Logger.print(context.fPlayer.getName() + TL.COMMAND_CREATE_CREATEDLOG + tag, Logger.PrefixType.DEFAULT);
            }
            if (FactionsPlugin.getInstance().getConfig().getBoolean("fpaypal.Enabled")) {
                context.msg(TL.COMMAND_PAYPALSET_CREATED);
            }
            if (Conf.allFactionsPeaceful) {
                faction.setPeaceful(true);
                faction.setPeacefulExplosionsEnabled(false);
            }
            if (Conf.usePermissionHints) {
                context.msg(TL.COMMAND_HINT_PERMISSION);
            }
        }, () -> context.msg(TL.COMMAND_CREATE_ERROR));
    }

    @Override
    public TL getUsageTranslation() {
        return TL.COMMAND_CREATE_DESCRIPTION;
    }

}