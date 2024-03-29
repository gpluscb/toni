package com.github.gpluscb.toni.command.game;

import com.github.gpluscb.toni.command.Command;
import com.github.gpluscb.toni.command.CommandContext;
import com.github.gpluscb.toni.command.CommandInfo;
import com.github.gpluscb.toni.menu.ActionMenu;
import com.github.gpluscb.toni.menu.SelectionActionMenu;
import com.github.gpluscb.toni.smashset.Ruleset;
import com.github.gpluscb.toni.util.discord.EmbedUtil;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class RulesetsCommand implements Command {
    private static final Logger log = LogManager.getLogger(RulesetsCommand.class);

    @Nonnull
    private final EventWaiter waiter;
    @Nonnull
    private final List<Ruleset> rulesets;

    public RulesetsCommand(@Nonnull EventWaiter waiter, @Nonnull List<Ruleset> rulesets) {
        this.waiter = waiter;
        this.rulesets = rulesets;
    }

    @Override
    public void execute(@Nonnull CommandContext ctx) {
        EmbedBuilder template = EmbedUtil.getPreparedAuthor(ctx.getMember(), ctx.getUser());

        MessageCreateData start = new MessageCreateBuilder()
                .setEmbeds(applyRulesetList(new EmbedBuilder(template)).build())
                .build();

        RulesetPaginator paginator = new RulesetPaginator(template);

        SelectionActionMenu.Settings.Builder menuBuilder = new SelectionActionMenu.Settings.Builder()
                .setActionMenuSettings(new ActionMenu.Settings.Builder()
                        .setWaiter(waiter)
                        .build())
                .addUsers(ctx.getUser().getIdLong())
                .setStart(start);

        menuBuilder.registerOption(SelectOption.of("List All", "-1").withDefault(true), (info, e) -> paginator.onPageSelect(info, e, -1));

        for (int i = 0; i < rulesets.size(); i++) {
            Ruleset ruleset = rulesets.get(i);
            int i_ = i;
            menuBuilder.registerOption(
                    SelectOption.of(StringUtils.abbreviate(ruleset.name(), SelectOption.LABEL_MAX_LENGTH), String.valueOf(ruleset.rulesetId())),
                    (info, e) -> paginator.onPageSelect(info, e, i_)
            );
        }

        SelectionActionMenu menu = new SelectionActionMenu(menuBuilder.build());


        menu.displaySlashReplying(ctx.getEvent());
    }

    @Nonnull
    private EmbedBuilder applyRulesetList(@Nonnull EmbedBuilder builder) {
        builder.setTitle("Available Rulesets");

        String rulesetsString = rulesets.stream()
                .map(ruleset -> String.format("`%d` - [%s](%s)", ruleset.rulesetId(), ruleset.name(), ruleset.url()))
                .collect(Collectors.joining("\n"));

        builder.appendDescription(rulesetsString);

        return builder;
    }

    @Nonnull
    @Override
    public CommandInfo getInfo() {
        return new CommandInfo.Builder()
                .setRequiredBotPerms(new Permission[]{Permission.MESSAGE_HISTORY, Permission.MESSAGE_EMBED_LINKS})
                .setShortHelp("[Beta] Lists all the available rulesets. Usage: `rulesets`")
                .setDetailedHelp("""
                        Lists all the available rulesets with their IDs. You can select a specific ruleset through the menu to see how it operates.""")
                .setCommandData(Commands.slash("rulesets", "Lists all the available rulesets"))
                .build();
    }

    private class RulesetPaginator {
        @Nonnull
        private final EmbedBuilder template;

        private RulesetPaginator(@Nonnull EmbedBuilder template) {
            this.template = template;
        }

        /**
         * @param idx -1 is rulesets info page
         */
        @Nonnull
        public synchronized ActionMenu.MenuAction onPageSelect(@Nonnull SelectionActionMenu.SelectionInfo info, @Nonnull StringSelectInteractionEvent event, int idx) {
            try {
                EmbedBuilder builder = new EmbedBuilder(template);

                if (idx == -1) applyRulesetList(builder);
                else EmbedUtil.applyRuleset(builder, rulesets.get(idx));

                StringSelectMenu menu = StringSelectMenu.create(info.getSelectionActionMenuSettings().id())
                        .addOptions(info.getInitialSelectOptions())
                        .setDefaultValues(Collections.singleton(String.valueOf(idx)))
                        .build();

                event.editMessageEmbeds(builder.build()).setActionRow(menu).queue();
                return ActionMenu.MenuAction.CONTINUE;
            } catch (Exception e) {
                log.catching(e);
                event.editMessage("There was a severe unexpected problem with displaying the ruleset, I don't really know how that happened. " +
                        "I've told my dev, you can go shoot them a message about this too if you want to.").queue();
                return ActionMenu.MenuAction.CANCEL;
            }
        }
    }
}
