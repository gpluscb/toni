package com.github.gpluscb.toni.command.components;

import com.github.gpluscb.toni.util.OneOfTwo;
import com.github.gpluscb.toni.util.discord.ButtonActionMenu;
import com.github.gpluscb.toni.util.discord.TwoUsersChoicesActionMenu;
import com.github.gpluscb.toni.util.smash.Ruleset;
import com.github.gpluscb.toni.util.smash.Stage;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
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

public class BanPickStagesMenu extends TwoUsersChoicesActionMenu {
    private static final Logger log = LogManager.getLogger(BanPickStagesMenu.class);

    @Nonnull
    private final Ruleset ruleset;
    @Nonnull
    private final List<Integer> dsrIllegalStages;
    @Nonnull
    private final BiConsumer<StageBan, ButtonClickEvent> onBan;
    @Nonnull
    private final BiConsumer<BanStagesMenu.BanResult, ButtonClickEvent> onBanResult;
    @Nonnull
    private final Consumer<BanStagesTimeoutEvent> onBanTimeout;
    @Nonnull
    private final ButtonActionMenu underlying;

    private final long pickStageTimeout;
    @Nonnull
    private final TimeUnit pickStageUnit;
    @Nonnull
    private final Consumer<PickStageTimeoutEvent> onPickTimeout;

    @Nonnull
    private final List<Integer> bannedStages;

    public BanPickStagesMenu(@Nonnull EventWaiter waiter, long banningUser, long counterpickingUser, long timeout, @Nonnull TimeUnit unit, @Nonnull Ruleset ruleset, @Nonnull List<Integer> dsrIllegalStages, @Nonnull BiConsumer<StageBan, ButtonClickEvent> onBan, @Nonnull Consumer<BanStagesTimeoutEvent> onBanTimeout,
                             long pickStageTimeout, @Nonnull TimeUnit pickStageUnit, @Nonnull Consumer<PickStageTimeoutEvent> onPickTimeout) {
        super(waiter, banningUser, counterpickingUser, timeout, unit);

        this.ruleset = ruleset;
        this.dsrIllegalStages = dsrIllegalStages;
        this.onBan = onBan;
        this.onBanTimeout = onBanTimeout;

        bannedStages = new ArrayList<>();

        ButtonActionMenu.Builder underlyingBuilder = new ButtonActionMenu.Builder()
                .setWaiter(waiter)
                .addUsers(banningUser, counterpickingUser)
                .setDeletionButton(null)
                .setTimeout(timeout, unit)
                .setTimeoutAction(this::onTimeout);

        ruleset.getStagesStream().forEach(stage -> {
            int id = stage.getStageId();
            Button stageButton = Button.secondary(String.valueOf(id), StringUtils.abbreviate(stage.getName(), LABEL_MAX_LENGTH));
            if (dsrIllegalStages.contains(id)) stageButton = stageButton.asDisabled();

            underlyingBuilder.registerButton(stageButton, e -> onBan(id, e));
        });

        // TODO: What if no bans??

        underlying = underlyingBuilder.build();

        this.pickStageTimeout = pickStageTimeout;
        this.pickStageUnit = pickStageUnit;
        this.onPickTimeout = onPickTimeout;
    }

    private synchronized OneOfTwo<Message, ButtonActionMenu.MenuAction> onBan(int stageId, @Nonnull ButtonClickEvent e) {
        if (e.getUser().getIdLong() == getUser2()) {
            e.reply("It is not your turn to ban stages right now. You can pick a counterpick stage later.").setEphemeral(true).queue();
            return OneOfTwo.ofU(ButtonActionMenu.MenuAction.NOTHING);
        }

        if (bannedStages.contains(stageId)) {
            log.warn("Stage was banned twice: {}", stageId);
            e.reply("I have recorded that you banned this stage already earlier.").setEphemeral(true).queue();
            return OneOfTwo.ofU(ButtonActionMenu.MenuAction.NOTHING);
        }

        if (dsrIllegalStages.contains(stageId)) {
            log.warn("DSR illegal stage was banned: {}", stageId);
            e.reply("You shouldn't have been able to ban this stage, " +
                    "because DSR rules already prevent your opponent from picking this stage.").setEphemeral(true).queue();
            return OneOfTwo.ofU(ButtonActionMenu.MenuAction.NOTHING);
        }

        bannedStages.add(stageId);
        onBan.accept(new StageBan(getUser1(), getUser2(), stageId, ruleset), e);
        if (bannedStages.size() == ruleset.getStageBans()) {
            on

            ButtonActionMenu pickUnderlying = new ButtonActionMenu.Builder()
        }
    }

    private synchronized void onTimeout(@Nullable MessageChannel channel, long messageId) {
        onBanTimeout.accept(new BanStagesTimeoutEvent(getUser1(), getUser2(), bannedStages, ruleset, channel, messageId));
    }

    public static class StageBan {
        private final long banningUser;
        private final long counterpickingUser;
        private final int bannedStageId;
        @Nonnull
        private final Ruleset ruleset;

        public StageBan(long banningUser, long counterpickingUser, int bannedStageId, @Nonnull Ruleset ruleset) {
            this.banningUser = banningUser;
            this.counterpickingUser = counterpickingUser;
            this.bannedStageId = bannedStageId;
            this.ruleset = ruleset;
        }

        public long getBanningUser() {
            return banningUser;
        }

        public long getCounterpickingUser() {
            return counterpickingUser;
        }

        public int getBannedStageId() {
            return bannedStageId;
        }

        @Nonnull
        public Stage getBannedStage() {
            // Should be present if everything is ok
            //noinspection OptionalGetWithoutIsPresent
            return ruleset.getStagesStream()
                    .filter(stage -> stage.getStageId() == bannedStageId)
                    .findAny()
                    .get();
        }

        @Nonnull
        public Ruleset getRuleset() {
            return ruleset;
        }
    }

    public static class BanPickStagesResult {
        private final long banningUser;
        private final long counterpickingUser;
        @Nonnull
        private final List<Integer> bannedStageIds;
        @Nonnull
        private final Ruleset ruleset;
        private final int pickedStageId;

        public BanPickStagesResult(long banningUser, long counterpickingUser, @Nonnull List<Integer> bannedStageIds, @Nonnull Ruleset ruleset, int pickedStageId) {
            this.banningUser = banningUser;
            this.counterpickingUser = counterpickingUser;
            this.bannedStageIds = bannedStageIds;
            this.ruleset = ruleset;
            this.pickedStageId = pickedStageId;
        }

        public long getBanningUser() {
            return banningUser;
        }

        public long getCounterpickingUser() {
            return counterpickingUser;
        }

        @Nonnull
        public List<Integer> getBannedStageIds() {
            return bannedStageIds;
        }

        @Nonnull
        public Ruleset getRuleset() {
            return ruleset;
        }

        public int getPickedStageId() {
            return pickedStageId;
        }

        @Nonnull
        public Stage getPickedStage() {
            // If this is not present we messed up somewhere
            //noinspection OptionalGetWithoutIsPresent
            return ruleset.getStagesStream()
                    .filter(stage -> stage.getStageId() == pickedStageId)
                    .findAny()
                    .get();
        }
    }

    public static class BanStagesTimeoutEvent {
        private final long banningUser;
        private final long counterpickingUser;
        @Nonnull
        private final List<Integer> bannedStageIdsSoFar;
        @Nonnull
        private final Ruleset ruleset;
        @Nullable
        private final MessageChannel channel;
        private final long messageId;

        public BanStagesTimeoutEvent(long banningUser, long counterpickingUser, @Nonnull List<Integer> bannedStageIdsSoFar, @Nonnull Ruleset ruleset, @Nullable MessageChannel channel, long messageId) {
            this.banningUser = banningUser;
            this.counterpickingUser = counterpickingUser;
            this.bannedStageIdsSoFar = bannedStageIdsSoFar;
            this.ruleset = ruleset;
            this.channel = channel;
            this.messageId = messageId;
        }

        public long getBanningUser() {
            return banningUser;
        }

        public long getCounterpickingUser() {
            return counterpickingUser;
        }

        @Nonnull
        public List<Integer> getBannedStageIdsSoFar() {
            return bannedStageIdsSoFar;
        }

        @Nonnull
        public Ruleset getRuleset() {
            return ruleset;
        }

        @Nullable
        public MessageChannel getChannel() {
            return channel;
        }

        public long getMessageId() {
            return messageId;
        }
    }

    public static class PickStageTimeoutEvent {
        private final long banningUser;
        private final long counterpickingUser;
        @Nonnull
        private final List<Integer> bannedStageIds;
        @Nonnull
        private final Ruleset ruleset;
        @Nullable
        private final MessageChannel channel;
        private final long messageId;

        public PickStageTimeoutEvent(long banningUser, long counterpickingUser, @Nonnull List<Integer> bannedStageIds, @Nonnull Ruleset ruleset, @Nullable MessageChannel channel, long messageId) {
            this.banningUser = banningUser;
            this.counterpickingUser = counterpickingUser;
            this.bannedStageIds = bannedStageIds;
            this.ruleset = ruleset;
            this.channel = channel;
            this.messageId = messageId;
        }

        public long getBanningUser() {
            return banningUser;
        }

        public long getCounterpickingUser() {
            return counterpickingUser;
        }

        @Nonnull
        public List<Integer> getBannedStageIds() {
            return bannedStageIds;
        }

        @Nonnull
        public Ruleset getRuleset() {
            return ruleset;
        }

        @Nullable
        public MessageChannel getChannel() {
            return channel;
        }

        public long getMessageId() {
            return messageId;
        }
    }
}
