package com.github.gpluscb.toni.command.menu;

import com.github.gpluscb.toni.menu.ActionMenu;
import com.github.gpluscb.toni.menu.ButtonActionMenu;
import com.github.gpluscb.toni.smashset.Ruleset;
import com.github.gpluscb.toni.smashset.Stage;
import com.github.gpluscb.toni.util.MiscUtil;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Emoji;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static net.dv8tion.jda.api.interactions.components.buttons.Button.LABEL_MAX_LENGTH;

public class PickStageMenu extends ActionMenu {
    private static final Logger log = LogManager.getLogger(PickStageMenu.class);

    @Nonnull
    private final Settings settings;

    @Nonnull
    private final ButtonActionMenu underlying;

    public PickStageMenu(@Nonnull Settings settings) {
        super(settings.actionMenuSettings());
        this.settings = settings;

        if (settings.bannedStageIds().size() >= settings.ruleset().starters().size() + settings.ruleset().counterpicks().size())
            throw new IllegalArgumentException("Fewer stages must be banned than there are stages in total.");

        ButtonActionMenu.Settings.Builder underlyingBuilder = new ButtonActionMenu.Settings.Builder()
                .setActionMenuSettings(getActionMenuSettings())
                .setStart(settings.start())
                .addUsers(settings.pickingUser())
                .setDeletionButton(null)
                .setOnTimeout(this::onTimeout);

        settings.ruleset().getStagesStream().forEach(stage -> {
            int id = stage.stageId();
            Button stageButton = Button.secondary(String.valueOf(id), StringUtils.abbreviate(stage.name(), LABEL_MAX_LENGTH))
                    .withEmoji(Emoji.fromEmote("a", stage.stageEmoteId(), false)) // a as placeholder because it may not be empty
                    .withDisabled(settings.bannedStageIds().contains(id));

            underlyingBuilder.registerButton(stageButton, e -> onPick(id, e));
        });

        underlying = new ButtonActionMenu(underlyingBuilder.build());
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
    public void displaySlashReplying(@Nonnull SlashCommandInteractionEvent event) {
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
    private synchronized MenuAction onPick(int stageId, @Nonnull ButtonInteractionEvent e) {
        if (settings.bannedStageIds().contains(stageId)) {
            log.error("Banned stage was picked: {}", stageId);
            e.reply("This stage cannot be picked.").setEphemeral(true).queue();
            return MenuAction.CONTINUE;
        }

        settings.onResult().accept(new PickStageResult(stageId), e);

        return MenuAction.CANCEL;
    }

    private synchronized void onTimeout(@Nonnull ButtonActionMenu.ButtonActionMenuTimeoutEvent event) {
        settings.onTimeout().accept(new PickStageTimeoutEvent());
    }

    @Nonnull
    @Override
    public JDA getJDA() {
        return underlying.getJDA();
    }

    @Override
    public long getMessageId() {
        return underlying.getMessageId();
    }

    @Override
    public long getChannelId() {
        return underlying.getChannelId();
    }

    @Nonnull
    public Settings getPickStageMenuSettings() {
        return settings;
    }

    private abstract class PickStageInfo extends MenuStateInfo {
        @Nonnull
        public Settings getPickStageMenuSettings() {
            return PickStageMenu.this.getPickStageMenuSettings();
        }
    }

    public class PickStageResult extends PickStageInfo {
        private final int pickedStageId;

        public PickStageResult(int pickedStageId) {
            this.pickedStageId = pickedStageId;
        }

        public int getPickedStageId() {
            return pickedStageId;
        }

        @Nonnull
        public Stage getPickedStage() {
            // Picked stage should exist
            //noinspection OptionalGetWithoutIsPresent
            return settings.ruleset().getStagesStream()
                    .filter(stage -> stage.stageId() == pickedStageId)
                    .findAny()
                    .get();
        }
    }

    public class PickStageTimeoutEvent extends PickStageInfo {
    }

    public record Settings(@Nonnull ActionMenu.Settings actionMenuSettings, long pickingUser, @Nonnull Ruleset ruleset,
                           @Nonnull Set<Integer> bannedStageIds,
                           @Nonnull BiConsumer<PickStageResult, ButtonInteractionEvent> onResult,
                           @Nonnull Message start,
                           @Nonnull Consumer<PickStageTimeoutEvent> onTimeout) {
        @Nonnull
        public static final BiConsumer<PickStageResult, ButtonInteractionEvent> DEFAULT_ON_RESULT = MiscUtil.emptyBiConsumer();
        @Nonnull
        public static final Consumer<PickStageTimeoutEvent> DEFAULT_ON_TIMEOUT = MiscUtil.emptyConsumer();

        public static class Builder {
            @Nullable
            private ActionMenu.Settings actionMenuSettings;
            @Nullable
            private Long pickingUser;
            @Nullable
            private Ruleset ruleset;
            @Nullable
            private Message start;
            @Nonnull
            private Set<Integer> bannedStageIds = new HashSet<>();
            @Nonnull
            private BiConsumer<PickStageResult, ButtonInteractionEvent> onResult = DEFAULT_ON_RESULT;
            @Nonnull
            private Consumer<PickStageTimeoutEvent> onTimeout = DEFAULT_ON_TIMEOUT;

            public Builder setActionMenuSettings(@Nullable ActionMenu.Settings actionMenuSettings) {
                this.actionMenuSettings = actionMenuSettings;
                return this;
            }

            @Nonnull
            public Builder setPickingUser(long pickingUser) {
                this.pickingUser = pickingUser;
                return this;
            }

            @Nonnull
            public Builder setRuleset(@Nonnull Ruleset ruleset) {
                this.ruleset = ruleset;
                return this;
            }

            @Nonnull
            public Builder setStart(@Nonnull Message start) {
                this.start = start;
                return this;
            }

            @Nonnull
            public Builder setBannedStageIds(@Nonnull Set<Integer> bannedStageIds) {
                this.bannedStageIds = bannedStageIds;
                return this;
            }

            @Nonnull
            public Builder setOnResult(@Nonnull BiConsumer<PickStageResult, ButtonInteractionEvent> onResult) {
                this.onResult = onResult;
                return this;
            }

            @Nonnull
            public Builder setOnTimeout(@Nonnull Consumer<PickStageTimeoutEvent> onTimeout) {
                this.onTimeout = onTimeout;
                return this;
            }

            @Nonnull
            public Settings build() {
                if (actionMenuSettings == null) throw new IllegalStateException("ActionMenuSettings must be set");
                if (pickingUser == null) throw new IllegalStateException("PickingUser must be set");
                if (ruleset == null) throw new IllegalStateException("Ruleset must be set");
                if (start == null) throw new IllegalStateException("Start must be set");

                return new Settings(actionMenuSettings, pickingUser, ruleset, bannedStageIds, onResult, start, onTimeout);
            }
        }
    }
}
