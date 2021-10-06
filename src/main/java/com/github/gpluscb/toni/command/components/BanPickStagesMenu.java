package com.github.gpluscb.toni.command.components;

import com.github.gpluscb.toni.util.MiscUtil;
import com.github.gpluscb.toni.util.discord.TwoUsersChoicesActionMenu;
import com.github.gpluscb.toni.util.smash.Ruleset;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BanPickStagesMenu extends TwoUsersChoicesActionMenu {
    @Nonnull
    private final BanStagesMenu banUnderlying;

    private final long pickStageTimeout;
    @Nonnull
    private final TimeUnit pickStageUnit;
    @Nonnull
    private final BiConsumer<PickStageMenu.PickStageResult, ButtonClickEvent> onPickResult;
    @Nonnull
    private final Consumer<PickStageMenu.PickStageTimeoutEvent> onPickTimeout;

    @Nonnull
    private final BiConsumer<BanPickStagesResult, ButtonClickEvent> onResult;

    @Nullable
    private BanStagesMenu.BanResult banResult;

    public BanPickStagesMenu(@Nonnull EventWaiter waiter, long banningUser, long counterpickingUser, long banTimeout, @Nonnull TimeUnit banUnit, @Nonnull Ruleset ruleset, @Nonnull List<Integer> dsrIllegalStages, @Nonnull BiConsumer<BanStagesMenu.StageBan, ButtonClickEvent> onBan, @Nonnull Consumer<BanStagesMenu.BanStagesTimeoutEvent> onBanTimeout,
                             long pickStageTimeout, @Nonnull TimeUnit pickStageUnit, @Nonnull BiConsumer<PickStageMenu.PickStageResult, ButtonClickEvent> onPickResult, @Nonnull Consumer<PickStageMenu.PickStageTimeoutEvent> onPickTimeout,
                             @Nonnull BiConsumer<BanPickStagesResult, ButtonClickEvent> onResult) {
        super(waiter, banningUser, counterpickingUser, banTimeout, banUnit);

        BanStagesMenu.Builder banUnderlyingBuilder = new BanStagesMenu.Builder()
                .setWaiter(waiter)
                .setBanningUser(banningUser)
                .setRuleset(ruleset)
                .setDsrIllegalStages(dsrIllegalStages)
                .setTimeout(banTimeout, banUnit)
                .setOnTimeout(onBanTimeout)
                .setOnBan(onBan)
                .setOnResult(this::onBanResult);

        // TODO: What if no bans??

        banUnderlying = banUnderlyingBuilder.build();

        this.pickStageTimeout = pickStageTimeout;
        this.pickStageUnit = pickStageUnit;
        this.onPickResult = onPickResult;
        this.onPickTimeout = onPickTimeout;

        this.onResult = onResult;
    }

    @Override
    public void display(@Nonnull MessageChannel channel) {
        banUnderlying.display(channel);
    }

    @Override
    public void display(@Nonnull Message message) {
        banUnderlying.display(message);
    }

    @Override
    public void displayReplying(@Nonnull MessageChannel channel, long messageId) {
        banUnderlying.displayReplying(channel, messageId);
    }

    @Override
    public void displaySlashReplying(@Nonnull SlashCommandEvent event) {
        banUnderlying.displaySlashReplying(event);
    }

    @Override
    public void displayDeferredReplying(@Nonnull InteractionHook hook) {
        banUnderlying.displayDeferredReplying(hook);
    }

    private synchronized void onBanResult(@Nonnull BanStagesMenu.BanResult result, @Nonnull ButtonClickEvent e) {
        banResult = result;

        long user1 = getUser1();
        long user2 = getUser2();

        Message start = new MessageBuilder(String.format("%s, since %s has chosen their bans, you can now pick one stage from the remaining stages.",
                MiscUtil.mentionUser(user1),
                MiscUtil.mentionUser(user2)))
                .mentionUsers(user1, user2)
                .build();

        PickStageMenu pickUnderlying = new PickStageMenu.Builder()
                .setWaiter(getWaiter())
                .setPickingUser(getUser2())
                .setRuleset(result.getRuleset())
                .setTimeout(pickStageTimeout, pickStageUnit)
                .setBannedStageIds(Stream.concat(result.getBannedStageIds().stream(), result.getDsrIllegalStages().stream()).collect(Collectors.toList()))
                .setStart(start)
                .setOnResult(this::onPickResult)
                .setOnTimeout(onPickTimeout)
                .build();

        // TODO: needs ack?
        pickUnderlying.display(e.getMessage());
    }

    private void onPickResult(@Nonnull PickStageMenu.PickStageResult pickResult, @Nonnull ButtonClickEvent e) {
        onPickResult.accept(pickResult, e);

        // banResult will be set at this point
        //noinspection ConstantConditions
        onResult.accept(new BanPickStagesResult(banResult, pickResult), e);
    }

    public class BanPickStagesResult extends MenuStateInfo {
        @Nonnull
        private final BanStagesMenu.BanResult banResult;
        @Nonnull
        private final PickStageMenu.PickStageResult pickResult;

        public BanPickStagesResult(@Nonnull BanStagesMenu.BanResult banResult, @Nonnull PickStageMenu.PickStageResult pickResult) {
            this.banResult = banResult;
            this.pickResult = pickResult;
        }

        @Nonnull
        public BanStagesMenu.BanResult getBanResult() {
            return banResult;
        }

        @Nonnull
        public PickStageMenu.PickStageResult getPickResult() {
            return pickResult;
        }
    }

    public static class Builder extends TwoUsersChoicesActionMenu.Builder<Builder, BanPickStagesMenu> {
        @Nullable
        private Ruleset ruleset;
        @Nonnull
        private List<Integer> dsrIllegalStages;
        @Nonnull
        private BiConsumer<BanStagesMenu.StageBan, ButtonClickEvent> onBan;
        @Nonnull
        private Consumer<BanStagesMenu.BanStagesTimeoutEvent> onBanTimeout;
        private long pickStageTimeout;
        @Nonnull
        private TimeUnit pickStageUnit;
        @Nonnull
        private BiConsumer<PickStageMenu.PickStageResult, ButtonClickEvent> onPickResult;
        @Nonnull
        private Consumer<PickStageMenu.PickStageTimeoutEvent> onPickTimeout;
        @Nonnull
        private BiConsumer<BanPickStagesResult, ButtonClickEvent> onResult;

        public Builder() {
            super(Builder.class);

            dsrIllegalStages = new ArrayList<>();
            onBan = (ban, e) -> {
            };
            onBanTimeout = timeout -> {
            };
            pickStageTimeout = 5;
            pickStageUnit = TimeUnit.MINUTES;
            onPickResult = (result, e) -> {
            };
            onPickTimeout = timeout -> {
            };
            onResult = (result, e) -> {
            };
        }

        // Exactly like super, just the parameter names are more clear
        @Nonnull
        @Override
        public Builder setUsers(long banningUser, long pickingUser) {
            return super.setUsers(banningUser, pickingUser);
        }

        @Nonnull
        public Builder setBanTimeout(long banTimeout, @Nonnull TimeUnit banUnit) {
            return setTimeout(banTimeout, banUnit);
        }

        @Nonnull
        public Builder setRuleset(@Nullable Ruleset ruleset) {
            this.ruleset = ruleset;
            return this;
        }

        @Nonnull
        public Builder setDsrIllegalStages(@Nonnull List<Integer> dsrIllegalStages) {
            this.dsrIllegalStages = dsrIllegalStages;
            return this;
        }

        @Nonnull
        public Builder setOnBan(@Nonnull BiConsumer<BanStagesMenu.StageBan, ButtonClickEvent> onBan) {
            this.onBan = onBan;
            return this;
        }

        @Nonnull
        public Builder setOnBanTimeout(@Nonnull Consumer<BanStagesMenu.BanStagesTimeoutEvent> onBanTimeout) {
            this.onBanTimeout = onBanTimeout;
            return this;
        }

        @Nonnull
        public Builder setPickTimeout(long pickStageTimeout, @Nonnull TimeUnit pickStageUnit) {
            this.pickStageTimeout = pickStageTimeout;
            this.pickStageUnit = pickStageUnit;
            return this;
        }

        @Nonnull
        public Builder setOnPickResult(@Nonnull BiConsumer<PickStageMenu.PickStageResult, ButtonClickEvent> onPickResult) {
            this.onPickResult = onPickResult;
            return this;
        }

        @Nonnull
        public Builder setOnPickTimeout(@Nonnull Consumer<PickStageMenu.PickStageTimeoutEvent> onPickTimeout) {
            this.onPickTimeout = onPickTimeout;
            return this;
        }

        @Nonnull
        public Builder setOnResult(@Nonnull BiConsumer<BanPickStagesResult, ButtonClickEvent> onResult) {
            this.onResult = onResult;
            return this;
        }

        public long getBanTimeout() {
            return getTimeout();
        }

        @Nonnull
        public TimeUnit getBanUnit() {
            return getUnit();
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
        public BiConsumer<BanStagesMenu.StageBan, ButtonClickEvent> getOnBan() {
            return onBan;
        }

        @Nonnull
        public Consumer<BanStagesMenu.BanStagesTimeoutEvent> getOnBanTimeout() {
            return onBanTimeout;
        }

        public long getPickStageTimeout() {
            return pickStageTimeout;
        }

        @Nonnull
        public TimeUnit getPickStageUnit() {
            return pickStageUnit;
        }

        @Nonnull
        public BiConsumer<PickStageMenu.PickStageResult, ButtonClickEvent> getOnPickResult() {
            return onPickResult;
        }

        @Nonnull
        public Consumer<PickStageMenu.PickStageTimeoutEvent> getOnPickTimeout() {
            return onPickTimeout;
        }

        @Nonnull
        public BiConsumer<BanPickStagesResult, ButtonClickEvent> getOnResult() {
            return onResult;
        }

        @Nonnull
        @Override
        public BanPickStagesMenu build() {
            preBuild();

            if (ruleset == null) throw new IllegalStateException("Ruleset must be set");

            // We know the nonullability because preBuild
            //noinspection ConstantConditions
            return new BanPickStagesMenu(getWaiter(), getUser1(), getUser2(), getTimeout(), getUnit(), ruleset, dsrIllegalStages, onBan, onBanTimeout,
                    pickStageTimeout, pickStageUnit, onPickResult, onPickTimeout,
                    onResult);
        }
    }
}
