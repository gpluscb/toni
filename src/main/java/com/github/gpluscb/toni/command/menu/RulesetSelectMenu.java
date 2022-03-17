package com.github.gpluscb.toni.command.menu;

import com.github.gpluscb.toni.menu.ActionMenu;
import com.github.gpluscb.toni.menu.ConfirmableSelectionActionMenu;
import com.github.gpluscb.toni.smashset.Ruleset;
import com.github.gpluscb.toni.util.MiscUtil;
import com.github.gpluscb.toni.util.discord.EmbedUtil;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class RulesetSelectMenu extends ActionMenu {
    @Nonnull
    private final Settings settings;

    // TODO: ConfirmableSelectionChoiceMenu?
    @Nonnull
    private final ConfirmableSelectionActionMenu<Ruleset> underlying;

    public RulesetSelectMenu(@Nonnull Settings settings) {
        super(settings.actionMenuSettings());
        this.settings = settings;

        ConfirmableSelectionActionMenu.Settings.Builder<Ruleset> menuBuilder = new ConfirmableSelectionActionMenu.Settings.Builder<Ruleset>()
                .setActionMenuSettings(getActionMenuSettings())
                .addUsers(settings.user())
                .setStart(settings.start())
                .setOnOptionChoice((info, e) -> e.deferEdit().queue())
                .setOnAllConfirmation(this::onConfirmation)
                .setOnTimeout(this::onTimeout);

        List<Ruleset> rulesets = settings.rulesets();
        for (Ruleset ruleset : rulesets) {
            menuBuilder.registerChoice(
                    SelectOption.of(StringUtils.abbreviate(ruleset.name(), SelectOption.LABEL_MAX_LENGTH), String.valueOf(ruleset.rulesetId())),
                    ruleset
            );
        }

        underlying = new ConfirmableSelectionActionMenu<>(menuBuilder.build());
    }

    @Override
    public void display(@Nonnull MessageChannel channel) {
        underlying.display(channel);
    }

    @Override
    public void display(@Nonnull MessageChannel channel, long messageId) {
        underlying.display(channel, messageId);
    }

    @Override
    public void displayReplying(@Nonnull MessageChannel channel, long messageId) {
        underlying.displayReplying(channel, messageId);
    }

    @Override
    public void displaySlashReplying(@Nonnull SlashCommandEvent event) {
        underlying.displaySlashReplying(event);
    }

    @Override
    public void displayDeferredReplying(@Nonnull InteractionHook hook) {
        underlying.displayDeferredReplying(hook);
    }

    @Nonnull
    @Override
    public List<ActionRow> getComponents() {
        return underlying.getComponents();
    }

    @Override
    public void start(@Nonnull Message message) {
        underlying.start(message);
    }

    @Nonnull
    public Settings getRulesetSelectMenuSettings() {
        return settings;
    }

    @Nonnull
    private synchronized MenuAction onConfirmation(@Nonnull ConfirmableSelectionActionMenu<Ruleset>.ConfirmationInfo info, @Nonnull ButtonClickEvent event) {
        settings.onRulesetSelect().accept(new RulesetSelectionInfo(info), event);

        return MenuAction.CANCEL;
    }

    private synchronized void onTimeout(@Nonnull ConfirmableSelectionActionMenu<Ruleset>.ConfirmationInfoTimeoutEvent timeout) {
        settings.onTimeout().accept(new RulesetSelectTimeoutEvent(timeout));
    }

    private abstract class RulesetSelectMenuInfo extends MenuStateInfo {
        @Nonnull
        public Settings getRulesetSelectMenuSettings() {
            return RulesetSelectMenu.this.getRulesetSelectMenuSettings();
        }
    }

    public class RulesetSelectionInfo extends RulesetSelectMenuInfo {
        @Nonnull
        private final ConfirmableSelectionActionMenu<Ruleset>.ConfirmationInfo info;

        public RulesetSelectionInfo(@Nonnull ConfirmableSelectionActionMenu<Ruleset>.ConfirmationInfo info) {
            this.info = info;
        }

        @Nonnull
        public ConfirmableSelectionActionMenu<Ruleset>.ConfirmationInfo getInfo() {
            return info;
        }

        @Nonnull
        public Ruleset getSelectedRuleset() {
            return info.getUserSelection();
        }
    }

    public class RulesetSelectTimeoutEvent extends RulesetSelectMenuInfo {
        @Nonnull
        private final ConfirmableSelectionActionMenu<Ruleset>.ConfirmationInfoTimeoutEvent timeout;

        public RulesetSelectTimeoutEvent(@Nonnull ConfirmableSelectionActionMenu<Ruleset>.ConfirmationInfoTimeoutEvent timeout) {
            this.timeout = timeout;
        }

        @Nonnull
        public ConfirmableSelectionActionMenu<Ruleset>.ConfirmationInfoTimeoutEvent getTimeout() {
            return timeout;
        }
    }

    public record Settings(@Nonnull ActionMenu.Settings actionMenuSettings, long user, @Nonnull List<Ruleset> rulesets,
                           @Nonnull Message start,
                           @Nonnull BiConsumer<RulesetSelectionInfo, ButtonClickEvent> onRulesetSelect,
                           @Nonnull Consumer<RulesetSelectTimeoutEvent> onTimeout) {
        @Nonnull
        public static final BiConsumer<RulesetSelectionInfo, ButtonClickEvent> DEFAULT_ON_RULESET_SELECT = MiscUtil.emptyBiConsumer();
        @Nonnull
        public static final Consumer<RulesetSelectTimeoutEvent> DEFAULT_ON_TIMEOUT = MiscUtil.emptyConsumer();

        @Nonnull
        public static Settings getDefaultSettings(@Nonnull EventWaiter waiter, @Nullable Member member, @Nonnull User user, @Nonnull List<Ruleset> rulesets,
                                                  @Nonnull BiConsumer<RulesetSelectMenu.RulesetSelectionInfo, ButtonClickEvent> afterRulesetSelect) {
            return new RulesetSelectMenu.Settings.Builder()
                    .setActionMenuSettings(new ActionMenu.Settings.Builder()
                            .setWaiter(waiter)
                            .setTimeout(15, TimeUnit.MINUTES)
                            .build())
                    .setUser(user.getIdLong())
                    .setRulesets(rulesets)
                    .setStart(new MessageBuilder(EmbedUtil.getPreparedAuthor(member, user)
                            .setTitle("Ruleset Selection")
                            .setDescription(String.format("**%s**, please select a ruleset from the list below.", user.getName()))
                            .build()).build())
                    .setOnRulesetSelect((info, event) -> {
                        event.editMessageEmbeds(new EmbedBuilder()
                                .setTitle("Ruleset Selection")
                                .setDescription(String.format("You chose: **%s**", info.getSelectedRuleset().name()))
                                .build()).setActionRows().queue();
                        afterRulesetSelect.accept(info, event);
                    })
                    .setOnTimeout(timeout -> {
                        MessageChannel channel = timeout.getChannel();
                        if (channel == null) return;
                        if (channel instanceof TextChannel textChannel) {
                            if (!textChannel.getGuild().getSelfMember().hasPermission(textChannel, Permission.MESSAGE_HISTORY))
                                return;
                        }

                        channel.retrieveMessageById(timeout.getMessageId())
                                .flatMap(m -> m.editMessage("You didn't choose the ruleset in time.").setActionRows())
                                .queue(null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_MESSAGE));
                    })
                    .build();
        }

        public static class Builder {
            @Nullable
            private ActionMenu.Settings actionMenuSettings;
            @Nullable
            private Long user;
            @Nullable
            private List<Ruleset> rulesets;
            @Nullable
            private Message start;
            @Nonnull
            private BiConsumer<RulesetSelectionInfo, ButtonClickEvent> onRulesetSelect = DEFAULT_ON_RULESET_SELECT;
            @Nonnull
            private Consumer<RulesetSelectTimeoutEvent> onTimeout = DEFAULT_ON_TIMEOUT;

            @Nonnull
            public Builder setActionMenuSettings(@Nonnull ActionMenu.Settings actionMenuSettings) {
                this.actionMenuSettings = actionMenuSettings;
                return this;
            }

            @Nonnull
            public Builder setUser(long user) {
                this.user = user;
                return this;
            }

            @Nonnull
            public Builder setRulesets(@Nonnull List<Ruleset> rulesets) {
                this.rulesets = rulesets;
                return this;
            }

            @Nonnull
            public Builder setStart(@Nullable Message start) {
                this.start = start;
                return this;
            }

            @Nonnull
            public Builder setOnRulesetSelect(@Nonnull BiConsumer<RulesetSelectionInfo, ButtonClickEvent> onRulesetSelect) {
                this.onRulesetSelect = onRulesetSelect;
                return this;
            }

            @Nonnull
            public Builder setOnTimeout(@Nonnull Consumer<RulesetSelectTimeoutEvent> onTimeout) {
                this.onTimeout = onTimeout;
                return this;
            }

            @Nonnull
            public Settings build() {
                if (actionMenuSettings == null) throw new IllegalStateException("ActionMenuSettings must be set");
                if (user == null) throw new IllegalStateException("User must be set");
                if (rulesets == null) throw new IllegalStateException("Rulesets must be set");
                if (start == null) throw new IllegalStateException("Start must be set");

                return new Settings(actionMenuSettings, user, rulesets, start, onRulesetSelect, onTimeout);
            }
        }
    }
}
