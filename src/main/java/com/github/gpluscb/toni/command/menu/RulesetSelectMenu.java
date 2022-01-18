package com.github.gpluscb.toni.command.menu;

import com.github.gpluscb.toni.smashset.Ruleset;
import com.github.gpluscb.toni.util.MiscUtil;
import com.github.gpluscb.toni.util.discord.menu.ActionMenu;
import com.github.gpluscb.toni.util.discord.menu.SelectionActionMenu;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.interaction.SelectionMenuEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
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
                    SelectOption.of(StringUtils.abbreviate(ruleset.getName(), SelectOption.LABEL_MAX_LENGTH), String.valueOf(ruleset.getRulesetId())),
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
