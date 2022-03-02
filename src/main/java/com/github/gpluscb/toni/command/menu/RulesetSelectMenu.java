package com.github.gpluscb.toni.command.menu;

import com.github.gpluscb.toni.menu.ActionMenu;
import com.github.gpluscb.toni.menu.SelectionActionMenu;
import com.github.gpluscb.toni.smashset.Ruleset;
import com.github.gpluscb.toni.util.MiscUtil;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.interaction.SelectionMenuEvent;
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
    private final SelectionActionMenu underlying;

    public RulesetSelectMenu(@Nonnull Settings settings) {
        super(settings.actionMenuSettings());
        this.settings = settings;

        SelectionActionMenu.Settings.Builder menuBuilder = new SelectionActionMenu.Settings.Builder()
                .setActionMenuSettings(getActionMenuSettings())
                .addUsers(settings.user())
                .setStart(settings.start())
                .setOnTimeout(this::onTimeout);

        List<Ruleset> rulesets = settings.rulesets();
        for (int i = 0; i < rulesets.size(); i++) {
            Ruleset ruleset = rulesets.get(i);
            int i_ = i;
            menuBuilder.registerOption(
                    SelectOption.of(StringUtils.abbreviate(ruleset.name(), SelectOption.LABEL_MAX_LENGTH), String.valueOf(ruleset.rulesetId())),
                    (info, e) -> onSelect(info, e, i_)
            );
        }

        underlying = new SelectionActionMenu(menuBuilder.build());
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
    private synchronized MenuAction onSelect(@Nonnull SelectionActionMenu.SelectionInfo info, @Nonnull SelectionMenuEvent event, int idx) {
        settings.onRulesetSelect().accept(new RulesetSelectionInfo(info, idx), event);

        return MenuAction.CANCEL;
    }

    private synchronized void onTimeout(@Nonnull SelectionActionMenu.SelectionMenuTimeoutEvent timeout) {
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
        private final SelectionActionMenu.SelectionInfo selectionInfo;
        private final int idx;

        public RulesetSelectionInfo(@Nonnull SelectionActionMenu.SelectionInfo selectionInfo, int idx) {
            this.selectionInfo = selectionInfo;
            this.idx = idx;
        }

        @Nonnull
        public SelectionActionMenu.SelectionInfo getSelectionInfo() {
            return selectionInfo;
        }

        public int getSelectedRulesetIdx() {
            return idx;
        }

        @Nonnull
        public Ruleset getSelectedRuleset() {
            return settings.rulesets().get(idx);
        }
    }

    public class RulesetSelectTimeoutEvent extends RulesetSelectMenuInfo {
        @Nonnull
        private final SelectionActionMenu.SelectionMenuTimeoutEvent selectionMenuTimeoutEvent;

        public RulesetSelectTimeoutEvent(@Nonnull SelectionActionMenu.SelectionMenuTimeoutEvent selectionMenuTimeoutEvent) {
            this.selectionMenuTimeoutEvent = selectionMenuTimeoutEvent;
        }

        @Nonnull
        public SelectionActionMenu.SelectionMenuTimeoutEvent getSelectionMenuTimeoutEvent() {
            return selectionMenuTimeoutEvent;
        }
    }

    public record Settings(@Nonnull ActionMenu.Settings actionMenuSettings, long user, @Nonnull List<Ruleset> rulesets,
                           @Nonnull Message start,
                           @Nonnull BiConsumer<RulesetSelectionInfo, SelectionMenuEvent> onRulesetSelect,
                           @Nonnull Consumer<RulesetSelectTimeoutEvent> onTimeout) {
        @Nonnull
        public static final BiConsumer<RulesetSelectionInfo, SelectionMenuEvent> DEFAULT_ON_RULESET_SELECT = MiscUtil.emptyBiConsumer();
        @Nonnull
        public static final Consumer<RulesetSelectTimeoutEvent> DEFAULT_ON_TIMEOUT = MiscUtil.emptyConsumer();

        @Nonnull
        public static Settings getDefaultSettings(@Nonnull EventWaiter waiter, long user, @Nonnull List<Ruleset> rulesets,
                                                  @Nonnull BiConsumer<RulesetSelectMenu.RulesetSelectionInfo, SelectionMenuEvent> afterRulesetSelect) {
            return new RulesetSelectMenu.Settings.Builder()
                    .setActionMenuSettings(new ActionMenu.Settings.Builder()
                            .setWaiter(waiter)
                            .setTimeout(15, TimeUnit.MINUTES)
                            .build())
                    .setUser(user)
                    .setRulesets(rulesets)
                    .setStart(new MessageBuilder(String.format("%s, please select a ruleset.", MiscUtil.mentionUser(user)))
                            .mentionUsers(user)
                            .build())
                    .setOnRulesetSelect((info, event) -> {
                        event.editMessage(String.format("You chose: %s", info.getSelectedRuleset().name())).setActionRows().queue();
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
            private BiConsumer<RulesetSelectionInfo, SelectionMenuEvent> onRulesetSelect = DEFAULT_ON_RULESET_SELECT;
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
            public Builder setOnRulesetSelect(@Nonnull BiConsumer<RulesetSelectionInfo, SelectionMenuEvent> onRulesetSelect) {
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
