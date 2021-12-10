package com.github.gpluscb.toni.command.menu;

import com.github.gpluscb.toni.smashset.Ruleset;
import com.github.gpluscb.toni.util.discord.menu.ActionMenu;
import com.github.gpluscb.toni.util.discord.menu.SelectionActionMenu;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.SelectionMenuEvent;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

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
                .setStart(settings.start());

        menuBuilder.registerOption(SelectOption.of("List All", "-1").withDefault(true), (info, e) -> onSelect(info, e, -1));

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

    private synchronized MenuAction onSelect(@Nonnull SelectionActionMenu.SelectionInfo info, @Nonnull SelectionMenuEvent event, int idx) {
        // TODO
        throw new NotImplementedException();
    }

    public record Settings(@Nonnull ActionMenu.Settings actionMenuSettings, long user, @Nonnull List<Ruleset> rulesets,
                           @Nonnull Message start) {
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
            public Settings build() {
                if (actionMenuSettings == null) throw new IllegalStateException("ActionMenuSettings must be set");
                if (user == null) throw new IllegalStateException("User must be set");
                if (rulesets == null) throw new IllegalStateException("Rulesets must be set");
                if (start == null) throw new IllegalStateException("Start must be set");

                return new Settings(actionMenuSettings, user, rulesets, start);
            }
        }
    }
}
