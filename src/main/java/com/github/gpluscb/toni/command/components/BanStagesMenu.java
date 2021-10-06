package com.github.gpluscb.toni.command.components;

import com.github.gpluscb.toni.util.MiscUtil;
import com.github.gpluscb.toni.util.OneOfTwo;
import com.github.gpluscb.toni.util.discord.ActionMenu;
import com.github.gpluscb.toni.util.discord.ButtonActionMenu;
import com.github.gpluscb.toni.util.smash.Ruleset;
import com.github.gpluscb.toni.util.smash.Stage;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Button;
import net.dv8tion.jda.api.interactions.components.Component;
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
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static net.dv8tion.jda.api.interactions.components.Button.LABEL_MAX_LENGTH;

public class BanStagesMenu extends ActionMenu {
    private static final Logger log = LogManager.getLogger(BanStagesMenu.class);

    private final long banningUser;
    @Nonnull
    private final Ruleset ruleset;
    @Nonnull
    private final List<Integer> dsrIllegalStages;
    @Nonnull
    private final BiConsumer<StageBan, ButtonClickEvent> onBan;
    @Nonnull
    private final BiConsumer<BanResult, ButtonClickEvent> onResult;
    @Nonnull
    private final Consumer<BanStagesTimeoutEvent> onTimeout;
    @Nonnull
    private final ButtonActionMenu underlying;

    @Nonnull
    private final List<Integer> bannedStageIds;

    public BanStagesMenu(@Nonnull EventWaiter waiter, long banningUser, long timeout, @Nonnull TimeUnit unit, @Nonnull Ruleset ruleset, @Nonnull List<Integer> dsrIllegalStages, @Nonnull BiConsumer<StageBan, ButtonClickEvent> onBan, @Nonnull BiConsumer<BanResult, ButtonClickEvent> onResult, @Nonnull Consumer<BanStagesTimeoutEvent> onTimeout) {
        super(waiter, timeout, unit);

        this.banningUser = banningUser;
        this.ruleset = ruleset;
        this.dsrIllegalStages = dsrIllegalStages;
        this.onBan = onBan;
        this.onResult = onResult;
        this.onTimeout = onTimeout;

        bannedStageIds = new ArrayList<>();

        // TODO: What if no bans??
        int banAmount = ruleset.getStageBans();
        Message start = new MessageBuilder(String.format("%s, it is your turn to ban stages. Please ban %d stage%s from the list below.",
                MiscUtil.mentionUser(banningUser),
                banAmount,
                banAmount == 1 ? "" : "s"))
                .mentionUsers(banningUser)
                .build();

        ButtonActionMenu.Builder underlyingBuilder = new ButtonActionMenu.Builder()
                .setWaiter(waiter)
                .setStart(start)
                .addUsers(banningUser)
                .setDeletionButton(null)
                .setTimeout(timeout, unit)
                .setTimeoutAction(this::onTimeout);

        ruleset.getStagesStream().forEach(stage -> {
            int id = stage.getStageId();
            Button stageButton = Button.secondary(String.valueOf(id), StringUtils.abbreviate(stage.getName(), LABEL_MAX_LENGTH))
                    .withDisabled(dsrIllegalStages.contains(id));

            underlyingBuilder.registerButton(stageButton, e -> onBan(id, e));
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

    private synchronized OneOfTwo<Message, ButtonActionMenu.MenuAction> onBan(int stageId, @Nonnull ButtonClickEvent e) {
        if (bannedStageIds.contains(stageId)) {
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

        bannedStageIds.add(stageId);
        onBan.accept(new StageBan(stageId), e);

        if (bannedStageIds.size() == ruleset.getStageBans()) {
            // Our job here is done
            onResult.accept(new BanResult(), e);
            return OneOfTwo.ofU(ButtonActionMenu.MenuAction.CANCEL);
        }

        // More stages are to be banned
        int stagesToBan = ruleset.getStageBans() - bannedStageIds.size();
        MessageBuilder builder = new MessageBuilder();
        builder.appendFormat("%s, please ban %d more stage%s from the list below.",
                        MiscUtil.mentionUser(banningUser),
                        stagesToBan,
                        stagesToBan == 1 ? "" : "s")
                .mentionUsers(banningUser);

        List<Button> buttonsToAdd = ruleset.getStagesStream()
                .map(stage -> Button.secondary(
                                String.valueOf(stage.getStageId()),
                                StringUtils.abbreviate(stage.getName(), LABEL_MAX_LENGTH)
                        ).withDisabled(bannedStageIds.contains(stage.getStageId()))
                ).collect(Collectors.toList());

        // Multiple ActionRows in case of > 5 buttons
        List<ActionRow> actionRows = MiscUtil.splitList(buttonsToAdd, Component.Type.BUTTON.getMaxPerRow())
                .stream()
                .map(ActionRow::of)
                .collect(Collectors.toList());
        builder.setActionRows(actionRows);

        return OneOfTwo.ofT(builder.build());
    }

    private synchronized void onTimeout(@Nullable MessageChannel channel, long messageId) {
        onTimeout.accept(new BanStagesTimeoutEvent(channel, messageId));
    }

    private abstract class BanStagesInfo extends MenuStateInfo {
        public long getBanningUser() {
            return banningUser;
        }

        @Nonnull
        public List<Integer> getBannedStageIds() {
            return bannedStageIds;
        }

        @Nonnull
        public List<Integer> getDsrIllegalStages() {
            return dsrIllegalStages;
        }

        @Nonnull
        public Ruleset getRuleset() {
            return ruleset;
        }

        @Nonnull
        public List<Stage> getBannedStages() {
            // For each banned stage there is a stage with that id in the ruleset
            //noinspection OptionalGetWithoutIsPresent
            return bannedStageIds.stream()
                    .map(id -> ruleset.getStagesStream().filter(stage -> stage.getStageId() == id).findAny().get())
                    .collect(Collectors.toList());
        }

        @Nonnull
        public List<Integer> getRemainingStageIds() {
            return ruleset.getStagesStream()
                    .map(Stage::getStageId)
                    .filter(Predicate.not(bannedStageIds::contains))
                    .filter(Predicate.not(dsrIllegalStages::contains))
                    .collect(Collectors.toList());
        }

        @Nonnull
        public List<Stage> getRemainingStages() {
            return ruleset.getStagesStream()
                    .filter(stage -> !bannedStageIds.contains(stage.getStageId()))
                    .filter(stage -> !dsrIllegalStages.contains(stage.getStageId()))
                    .collect(Collectors.toList());
        }
    }

    public class StageBan extends BanStagesInfo {
        private final int bannedStageId;

        public StageBan(int bannedStageId) {
            this.bannedStageId = bannedStageId;
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
    }

    public class BanResult extends BanStagesInfo {
    }

    public class BanStagesTimeoutEvent extends BanStagesInfo implements MenuTimeoutEvent {
        @Nullable
        private final MessageChannel channel;
        private final long messageId;

        public BanStagesTimeoutEvent(@Nullable MessageChannel channel, long messageId) {
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

    public static class Builder extends ActionMenu.Builder<Builder, BanStagesMenu> {
        @Nullable
        private Long banningUser;
        @Nullable
        private Ruleset ruleset;
        @Nonnull
        private List<Integer> dsrIllegalStages;
        @Nonnull
        private BiConsumer<StageBan, ButtonClickEvent> onBan;
        @Nonnull
        private BiConsumer<BanResult, ButtonClickEvent> onResult;
        @Nonnull
        private Consumer<BanStagesTimeoutEvent> onTimeout;

        public Builder() {
            super(Builder.class);

            dsrIllegalStages = new ArrayList<>();
            onBan = (ban, e) -> {
            };
            onResult = (result, e) -> {
            };
            onTimeout = timeout -> {
            };
        }

        @Nonnull
        public Builder setBanningUser(long banningUser) {
            this.banningUser = banningUser;
            return this;
        }

        @Nonnull
        public Builder setRuleset(@Nonnull Ruleset ruleset) {
            this.ruleset = ruleset;
            return this;
        }

        @Nonnull
        public Builder setDsrIllegalStages(@Nonnull List<Integer> dsrIllegalStages) {
            this.dsrIllegalStages = dsrIllegalStages;
            return this;
        }

        @Nonnull
        public Builder setOnBan(@Nonnull BiConsumer<StageBan, ButtonClickEvent> onBan) {
            this.onBan = onBan;
            return this;
        }

        @Nonnull
        public Builder setOnResult(@Nonnull BiConsumer<BanResult, ButtonClickEvent> onResult) {
            this.onResult = onResult;
            return this;
        }

        @Nonnull
        public Builder setOnTimeout(@Nonnull Consumer<BanStagesTimeoutEvent> onTimeout) {
            this.onTimeout = onTimeout;
            return this;
        }

        @Nullable
        public Long getBanningUser() {
            return banningUser;
        }

        @Nullable
        public Ruleset getRuleset() {
            return ruleset;
        }

        @Nonnull
        public List<Integer> getDsrIllegalStages() {
            return dsrIllegalStages;
        }

        @Nonnull
        public BiConsumer<StageBan, ButtonClickEvent> getOnBan() {
            return onBan;
        }

        @Nonnull
        public BiConsumer<BanResult, ButtonClickEvent> getOnResult() {
            return onResult;
        }

        @Nonnull
        public Consumer<BanStagesTimeoutEvent> getOnTimeout() {
            return onTimeout;
        }

        @Nonnull
        @Override
        public BanStagesMenu build() {
            preBuild();

            if (banningUser == null) throw new IllegalStateException("BanningUser must be set");
            if (ruleset == null) throw new IllegalStateException("Ruleset must be set");

            // We know it's not null because preBuild
            //noinspection ConstantConditions
            return new BanStagesMenu(getWaiter(), banningUser, getTimeout(), getUnit(), ruleset, dsrIllegalStages, onBan, onResult, onTimeout);
        }
    }
}
