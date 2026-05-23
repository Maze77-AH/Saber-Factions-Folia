package com.massivecraft.factions.cmd.claim;

import com.massivecraft.factions.Conf;
import com.massivecraft.factions.FLocation;
import com.massivecraft.factions.Faction;
import com.massivecraft.factions.FactionsPlugin;
import com.massivecraft.factions.cmd.Aliases;
import com.massivecraft.factions.cmd.CommandContext;
import com.massivecraft.factions.cmd.CommandRequirements;
import com.massivecraft.factions.cmd.FCommand;
import com.massivecraft.factions.realfactions.ClaimTransactionService;
import com.massivecraft.factions.struct.Permission;
import com.massivecraft.factions.util.spiral.ChunkProcessingContext;
import com.massivecraft.factions.util.spiral.SpiralTask;
import com.massivecraft.factions.util.spiral.generator.SquareSpiralGenerator;
import com.massivecraft.factions.zcore.fperms.PermissableAction;
import com.massivecraft.factions.zcore.util.TL;

public class CmdUnclaim extends FCommand {

    /**
     * @author FactionsUUID Team - Modified By CmdrKittens
     */

    public CmdUnclaim() {
        this.getAliases().addAll(Aliases.unclaim_unclaim);

        this.getOptionalArgs().put("radius", "1");
        this.getOptionalArgs().put("faction", "yours");

        this.setRequirements(new CommandRequirements.Builder(Permission.UNCLAIM)
                .playerOnly()
                .withAction(PermissableAction.TERRITORY)
                .build());
    }

    @Override
    public void perform(final CommandContext context) {
        // Read and validate input
        int radius = context.argAsInt(0, 1); // Default to 1
        final Faction forFaction = context.argAsFaction(1, context.faction); // Default to own

        if (radius < 1) {
            context.msg(TL.COMMAND_CLAIM_INVALIDRADIUS);
            return;
        }

        final ClaimTransactionService claims = FactionsPlugin.getInstance().getRealFactionsServices().claims();

        if (radius < 2) {
            // single chunk: route the board write through the serialized claim transaction.
            final FLocation flocation = FLocation.wrap(context.fPlayer);
            claims.unclaim(context.fPlayer, forFaction, flocation, true, null);
        } else {
            // radius unclaim
            if (!Permission.CLAIM_RADIUS.has(context.sender, false)) {
                context.msg(TL.COMMAND_CLAIM_DENIED);
                return;
            }

            new SpiralTask(FLocation.wrap(context.fPlayer), radius, new SquareSpiralGenerator()) {
                private final int limit = Conf.radiusClaimFailureLimit - 1;
                private int failCount = 0;

                @Override
                public boolean work(ChunkProcessingContext ctx) {
                    FLocation fLocation = ctx.getFLocation();

                    // The spiral runs on the global region scheduler (the model thread); unclaimNow
                    // keeps the board write inside ClaimTransactionService.
                    boolean success = claims.unclaimNow(context.fPlayer, forFaction, fLocation, true);
                    if (success) {
                        failCount = 0;
                    } else if (failCount++ >= limit) {
                        this.stop();
                        return false;
                    }

                    return true;
                }
            };
        }
    }


    @Override
    public TL getUsageTranslation() {
        return TL.COMMAND_UNCLAIM_DESCRIPTION;
    }

}
