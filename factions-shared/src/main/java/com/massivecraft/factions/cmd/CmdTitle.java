package com.massivecraft.factions.cmd;

import com.massivecraft.factions.Conf;
import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FactionsPlugin;
import com.massivecraft.factions.struct.Permission;
import com.massivecraft.factions.struct.Role;
import com.massivecraft.factions.zcore.util.TL;
import com.massivecraft.factions.zcore.util.TextUtil;

public class CmdTitle extends FCommand {

    /**
     * @author FactionsUUID Team - Modified By CmdrKittens
     */

    public CmdTitle() {
        this.getAliases().addAll(Aliases.title);
        this.getRequiredArgs().add("player name");
        this.getOptionalArgs().put("title", "");

        this.setRequirements(new CommandRequirements.Builder(Permission.TITLE)
                .memberOnly()
                .withRole(Role.MODERATOR)
                .playerOnly()
                .build());
    }

    @Override
    public void perform(CommandContext context) {
        FPlayer you = context.argAsBestFPlayerMatch(0);
        if (you == null) {
            return;
        }

        context.args.remove(0);
        String title = TextUtil.implode(context.args, " ");

        title = title.replaceAll(",", "");

        if (!context.canIAdministerYou(context.fPlayer, you)) {
            return;
        }

        // if economy is enabled, they're not on the bypass list, and this command has a cost set, make 'em pay
        if (!context.payForCommand(Conf.econCostTitle, TL.COMMAND_TITLE_TOCHANGE, TL.COMMAND_TITLE_FORCHANGE)) {
            return;
        }

        // Route the title write (FPlayer model state) through the single-writer model thread.
        final FPlayer target = you;
        final String finalTitle = title;
        FactionsPlugin.getInstance().getRealFactionsServices().executor().runPlayerWrite(() -> {
            target.setTitle(context.sender, finalTitle);

            // Inform
            context.faction.msg(TL.COMMAND_TITLE_CHANGED, context.fPlayer.describeTo(context.faction, true), target.describeTo(context.faction, true));
        });
    }

    @Override
    public TL getUsageTranslation() {
        return TL.COMMAND_TITLE_DESCRIPTION;
    }

}