package com.github.gpluscb.toni.command.components;

import com.github.gpluscb.toni.util.OneOfTwo;
import com.github.gpluscb.toni.util.discord.ActionMenu;
import com.github.gpluscb.toni.util.discord.ButtonActionMenu;
import com.github.gpluscb.toni.util.smash.Ruleset;
import com.github.gpluscb.toni.util.smash.Stage;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.Button;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static net.dv8tion.jda.api.interactions.components.Button.LABEL_MAX_LENGTH;

public class PickStageMenu extends ActionMenu {
    private static final Logger log = LogManager.getLogger(PickStageMenu.class);

    private final long pickingUser;
    @Nonnull
    private final Ruleset ruleset;
    @Nonnull
    private final List<Integer> bannedStageIds;
    @Nonnull
    private final BiConsumer<PickStageResult, ButtonClickEvent> onResult;
    @Nonnull
    private final Consumer<PickStageTimeoutEvent> onTimeout;
    @Nonnull
    private final ButtonActionMenu underlying;

    public PickStageMenu(@Nonnull EventWaiter waiter, long pickingUser, long timeout, @Nonnull TimeUnit unit, @Nonnull Ruleset ruleset, @Nonnull List<Integer> bannedStageIds, @Nonnull BiConsumer<PickStageResult, ButtonClickEvent> onResult, @Nonnull Message start, @Nonnull Consumer<PickStageTimeoutEvent> onTimeout) {
        super(waiter, timeout, unit);

        this.pickingUser = pickingUser;
        this.ruleset = ruleset;
        this.bannedStageIds = bannedStageIds;
        this.onResult = onResult;
        this.onTimeout = onTimeout;

        ButtonActionMenu.Builder underlyingBuilder = new ButtonActionMenu.Builder()
                .setWaiter(waiter)
                .setStart(start)
                .addUsers(pickingUser)
                .setDeletionButton(null)
                .setTimeout(timeout, unit)
                .setTimeoutAction(this::onTimeout);

        ruleset.getStagesStream().forEach(stage -> {
            int id = stage.getStageId();
            Button stageButton = Button.secondary(String.valueOf(id), StringUtils.abbreviate(stage.getName(), LABEL_MAX_LENGTH))
                    .withDisabled(bannedStageIds.contains(id));

            underlyingBuilder.registerButton(stageButton, e -> onPick(id, e));
        });

        underlying = underlyingBuilder.build();
    }

    @Override
    public void display(@Nonnull MessageChannel channel) {
        underlying.display(channel);
    }

    @Override
    public void display(@Nonnull Message message) {
        underlying.display(message);
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
    private OneOfTwo<Message, ButtonActionMenu.MenuAction> onPick(int stageId, @Nonnull ButtonClickEvent e) {
        if (bannedStageIds.contains(stageId)) {
            log.error("Banned stage was picked: {}", stageId);
            e.reply("This stage cannot be picked.").setEphemeral(true).queue();
            return OneOfTwo.ofU(ButtonActionMenu.MenuAction.NOTHING);
        }

        onResult.accept(new PickStageResult(stageId), e);
        return OneOfTwo.ofU(ButtonActionMenu.MenuAction.CANCEL);
    }

    private void onTimeout(@Nullable MessageChannel channel, long messageId) {
        onTimeout.accept(new PickStageTimeoutEvent(channel, messageId));
    }

    private abstract class PickStageInfo extends MenuStateInfo {
        public long getPickingUser() {
            return pickingUser;
        }

        @Nonnull
        public Ruleset getRuleset() {
            return ruleset;
        }

        @Nonnull
        public List<Integer> getBannedStageIds() {
            return bannedStageIds;
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
            return ruleset.getStagesStream()
                    .filter(stage -> stage.getStageId() == pickedStageId)
                    .findAny()
                    .get();
        }
    }

    public class PickStageTimeoutEvent extends PickStageInfo implements MenuTimeoutEvent {
        @Nullable
        private final MessageChannel channel;
        private final long messageId;

        public PickStageTimeoutEvent(@Nullable MessageChannel channel, long messageId) {
            this.channel = channel;
            this.messageId = messageId;
        }

        @Nullable
        @Override
        public MessageChannel getChannel() {
            return channel;
        }

        @Override
        public long getMessageId() {
            return messageId;
        }
    }

    public static class Builder extends ActionMenu.Builder<Builder, PickStageMenu> {
        @Nullable
        private Long pickingUser;
        @Nullable
        private Ruleset ruleset;
        @Nullable
        private Message start;
        @Nonnull
        private List<Integer> bannedStageIds;
        @Nonnull
        private BiConsumer<PickStageResult, ButtonClickEvent> onResult;
        @Nonnull
        private Consumer<PickStageTimeoutEvent> onTimeout;

        public Builder() {
            super(Builder.class);

            bannedStageIds = new ArrayList<>();
            onResult = (result, e) -> {
            };
            onTimeout = timeout -> {
            };
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
        public Builder setBannedStageIds(@Nonnull List<Integer> bannedStageIds) {
            this.bannedStageIds = bannedStageIds;
            return this;
        }

        @Nonnull
        public Builder setOnResult(@Nonnull BiConsumer<PickStageResult, ButtonClickEvent> onResult) {
            this.onResult = onResult;
            return this;
        }

        @Nonnull
        public Builder setOnTimeout(@Nonnull Consumer<PickStageTimeoutEvent> onTimeout) {
            this.onTimeout = onTimeout;
            return this;
        }

        @Nullable
        public Long getPickingUser() {
            return pickingUser;
        }

        @Nullable
        public Ruleset getRuleset() {
            return ruleset;
        }

        @Nullable
        public Message getStart() {
            return start;
        }

        @Nonnull
        public List<Integer> getBannedStageIds() {
            return bannedStageIds;
        }

        @Nonnull
        public BiConsumer<PickStageResult, ButtonClickEvent> getOnResult() {
            return onResult;
        }

        @Nonnull
        public Consumer<PickStageTimeoutEvent> getOnTimeout() {
            return onTimeout;
        }

        @Nonnull
        @Override
        public PickStageMenu build() {
            preBuild();

            if (pickingUser == null) throw new IllegalStateException("PickingUser must be set");
            if (ruleset == null) throw new IllegalStateException("Ruleset must be set");
            if (start == null) throw new IllegalStateException("Start must be set");

            // We know nonnullability because preBuild
            //noinspection ConstantConditions
            return new PickStageMenu(getWaiter(), pickingUser, getTimeout(), getUnit(), ruleset, bannedStageIds, onResult, start, onTimeout);
        }
    }
}
