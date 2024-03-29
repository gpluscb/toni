package com.github.gpluscb.toni.command.menu;

import com.github.gpluscb.toni.menu.ActionMenu;
import com.github.gpluscb.toni.menu.TwoUsersChoicesActionMenu;
import com.github.gpluscb.toni.smashset.Ruleset;
import com.github.gpluscb.toni.util.MiscUtil;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BanPickStagesMenu extends TwoUsersChoicesActionMenu {
    @Nonnull
    private final Settings settings;

    // PickStageMenu if there are no bans in this ruleset
    @Nonnull
    private final ActionMenu underlying;

    @Nullable
    private BanStagesMenu.BanResult banResult;

    public BanPickStagesMenu(@Nonnull Settings settings) {
        super(settings.twoUsersChoicesActionMenuSettings());
        this.settings = settings;

        if (settings.ruleset().stageBans() == 0) {
            underlying = createPickMenu(Collections.emptySet());
            return;
        }

        BanStagesMenu.Settings.Builder banUnderlyingBuilder = new BanStagesMenu.Settings.Builder()
                .setActionMenuSettings(getActionMenuSettings())
                .setBanningUser(getTwoUsersChoicesActionMenuSettings().user1())
                .setRuleset(settings.ruleset())
                .setBanMessageProducer(settings.banMessageProducer())
                .setDsrIllegalStages(settings.dsrIllegalStages())
                .setOnTimeout(settings.onBanTimeout())
                .setOnBan(settings.onBan())
                .setOnResult(this::onBanResult);

        underlying = new BanStagesMenu(banUnderlyingBuilder.build());
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

    private synchronized void onBanResult(@Nonnull BanStagesMenu.BanResult result, @Nonnull ButtonInteractionEvent e) {
        e.deferEdit().queue();

        settings.onBanResult().accept(result, e);

        banResult = result;

        PickStageMenu pickUnderlying = createPickMenu(Stream.concat(result.getBannedStageIds().stream(), result.getBanStagesMenuSettings().dsrIllegalStages().stream())
                .collect(Collectors.toSet()));

        pickUnderlying.display(e.getMessage());
    }

    @Nonnull
    private PickStageMenu createPickMenu(@Nonnull Set<Integer> bannedStageIds) {
        return new PickStageMenu(new PickStageMenu.Settings.Builder()
                .setActionMenuSettings(new ActionMenu.Settings.Builder()
                        .setWaiter(getActionMenuSettings().waiter())
                        .setTimeout(settings.pickStageTimeout(), settings.pickStageUnit())
                        .build())
                .setPickingUser(getTwoUsersChoicesActionMenuSettings().user2())
                .setRuleset(settings.ruleset())
                .setBannedStageIds(bannedStageIds)
                .setStart(settings.pickStageStart())
                .setOnResult(this::onPickResult)
                .setOnTimeout(settings.onPickTimeout())
                .build());
    }

    private synchronized void onPickResult(@Nonnull PickStageMenu.PickStageResult pickResult, @Nonnull ButtonInteractionEvent e) {
        settings.onPickResult().accept(pickResult, e);

        // banResult will be set at this point
        //noinspection ConstantConditions
        settings.onResult().accept(new BanPickStagesResult(banResult, pickResult), e);
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
    public Settings getBanPickStagesMenuSettings() {
        return settings;
    }

    public class BanPickStagesResult extends TwoUsersMenuStateInfo {
        @Nonnull
        private final BanStagesMenu.BanResult banResult;
        @Nonnull
        private final PickStageMenu.PickStageResult pickResult;

        public BanPickStagesResult(@Nonnull BanStagesMenu.BanResult banResult, @Nonnull PickStageMenu.PickStageResult pickResult) {
            this.banResult = banResult;
            this.pickResult = pickResult;
        }

        @Nonnull
        public Settings getBanPickStagesMenuSettings() {
            return BanPickStagesMenu.this.getBanPickStagesMenuSettings();
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

    public record Settings(@Nonnull TwoUsersChoicesActionMenu.Settings twoUsersChoicesActionMenuSettings,
                           @Nonnull Ruleset ruleset, @Nonnull Set<Integer> dsrIllegalStages,
                           @Nonnull BiConsumer<BanStagesMenu.StageBan, ButtonInteractionEvent> onBan,
                           @Nonnull Function<BanStagesMenu.UpcomingBanInfo, MessageCreateData> banMessageProducer,
                           @Nonnull BiConsumer<BanStagesMenu.BanResult, ButtonInteractionEvent> onBanResult,
                           @Nonnull Consumer<BanStagesMenu.BanStagesTimeoutEvent> onBanTimeout, long pickStageTimeout,
                           @Nonnull TimeUnit pickStageUnit, @Nonnull MessageCreateData pickStageStart,
                           @Nonnull BiConsumer<PickStageMenu.PickStageResult, ButtonInteractionEvent> onPickResult,
                           @Nonnull Consumer<PickStageMenu.PickStageTimeoutEvent> onPickTimeout,
                           @Nonnull BiConsumer<BanPickStagesResult, ButtonInteractionEvent> onResult) {
        @Nonnull
        public static final Function<BanStagesMenu.UpcomingBanInfo, MessageCreateData> DEFAULT_BAN_MESSAGE_PRODUCER = BanStagesMenu.Settings.DEFAULT_BAN_MESSAGE_PRODUCER;
        @Nonnull
        public static final BiConsumer<BanStagesMenu.StageBan, ButtonInteractionEvent> DEFAULT_ON_BAN = BanStagesMenu.Settings.DEFAULT_ON_BAN;
        @Nonnull
        public static final BiConsumer<BanStagesMenu.BanResult, ButtonInteractionEvent> DEFAULT_ON_BAN_RESULT = BanStagesMenu.Settings.DEFAULT_ON_RESULT;
        @Nonnull
        public static final Consumer<BanStagesMenu.BanStagesTimeoutEvent> DEFAULT_ON_BAN_TIMEOUT = BanStagesMenu.Settings.DEFAULT_ON_TIMEOUT;
        public static final long DEFAULT_PICK_STAGE_TIMEOUT = 5;
        @Nonnull
        public static final TimeUnit DEFAULT_PICK_STAGE_UNIT = TimeUnit.MINUTES;
        @Nonnull
        public static final BiConsumer<PickStageMenu.PickStageResult, ButtonInteractionEvent> DEFAULT_ON_PICK_RESULT = PickStageMenu.Settings.DEFAULT_ON_RESULT;
        @Nonnull
        public static final Consumer<PickStageMenu.PickStageTimeoutEvent> DEFAULT_ON_PICK_TIMEOUT = PickStageMenu.Settings.DEFAULT_ON_TIMEOUT;
        @Nonnull
        public static final BiConsumer<BanPickStagesResult, ButtonInteractionEvent> DEFAULT_ON_RESULT = MiscUtil.emptyBiConsumer();

        public static class Builder {
            @Nullable
            private TwoUsersChoicesActionMenu.Settings twoUsersChoicesActionMenuSettings;
            @Nullable
            private Ruleset ruleset;
            @Nonnull
            private Set<Integer> dsrIllegalStages = new HashSet<>();
            @Nonnull
            private Function<BanStagesMenu.UpcomingBanInfo, MessageCreateData> banMessageProducer = DEFAULT_BAN_MESSAGE_PRODUCER;
            @Nonnull
            private BiConsumer<BanStagesMenu.StageBan, ButtonInteractionEvent> onBan = DEFAULT_ON_BAN;
            @Nonnull
            private BiConsumer<BanStagesMenu.BanResult, ButtonInteractionEvent> onBanResult = DEFAULT_ON_BAN_RESULT;
            @Nonnull
            private Consumer<BanStagesMenu.BanStagesTimeoutEvent> onBanTimeout = DEFAULT_ON_BAN_TIMEOUT;
            private long pickStageTimeout = DEFAULT_PICK_STAGE_TIMEOUT;
            @Nonnull
            private TimeUnit pickStageUnit = DEFAULT_PICK_STAGE_UNIT;
            @Nullable
            private MessageCreateData pickStageStart;
            @Nonnull
            private BiConsumer<PickStageMenu.PickStageResult, ButtonInteractionEvent> onPickResult = DEFAULT_ON_PICK_RESULT;
            @Nonnull
            private Consumer<PickStageMenu.PickStageTimeoutEvent> onPickTimeout = DEFAULT_ON_PICK_TIMEOUT;
            @Nonnull
            private BiConsumer<BanPickStagesResult, ButtonInteractionEvent> onResult = DEFAULT_ON_RESULT;

            /**
             * user1 is banning user, user2 is picking user
             * timeout is ban timeout
             */
            @Nonnull
            public Builder setTwoUsersChoicesActionMenuSettings(@Nullable TwoUsersChoicesActionMenu.Settings twoUsersChoicesActionMenuSettings) {
                this.twoUsersChoicesActionMenuSettings = twoUsersChoicesActionMenuSettings;
                return this;
            }

            @Nonnull
            public Builder setRuleset(@Nullable Ruleset ruleset) {
                this.ruleset = ruleset;
                return this;
            }

            @Nonnull
            public Builder setDsrIllegalStages(@Nonnull Set<Integer> dsrIllegalStages) {
                this.dsrIllegalStages = dsrIllegalStages;
                return this;
            }

            @Nonnull
            public Builder setBanMessageProducer(@Nonnull Function<BanStagesMenu.UpcomingBanInfo, MessageCreateData> banMessageProducer) {
                this.banMessageProducer = banMessageProducer;
                return this;
            }

            @Nonnull
            public Builder setOnBan(@Nonnull BiConsumer<BanStagesMenu.StageBan, ButtonInteractionEvent> onBan) {
                this.onBan = onBan;
                return this;
            }

            @Nonnull
            public Builder setOnBanResult(@Nonnull BiConsumer<BanStagesMenu.BanResult, ButtonInteractionEvent> onBanResult) {
                this.onBanResult = onBanResult;
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
            public Builder setPickStageStart(@Nullable MessageCreateData pickStageStart) {
                this.pickStageStart = pickStageStart;
                return this;
            }

            @Nonnull
            public Builder setOnPickResult(@Nonnull BiConsumer<PickStageMenu.PickStageResult, ButtonInteractionEvent> onPickResult) {
                this.onPickResult = onPickResult;
                return this;
            }

            @Nonnull
            public Builder setOnPickTimeout(@Nonnull Consumer<PickStageMenu.PickStageTimeoutEvent> onPickTimeout) {
                this.onPickTimeout = onPickTimeout;
                return this;
            }

            @Nonnull
            public Builder setOnResult(@Nonnull BiConsumer<BanPickStagesResult, ButtonInteractionEvent> onResult) {
                this.onResult = onResult;
                return this;
            }

            @Nonnull
            public Settings build() {
                if (twoUsersChoicesActionMenuSettings == null)
                    throw new IllegalStateException("TwoUsersChoicesActionMenuSettings must be set");
                if (ruleset == null) throw new IllegalStateException("Ruleset must be set");
                if (pickStageStart == null) throw new IllegalStateException("PickStageStart must be set");

                return new Settings(twoUsersChoicesActionMenuSettings, ruleset, dsrIllegalStages, onBan, banMessageProducer, onBanResult, onBanTimeout,
                        pickStageTimeout, pickStageUnit, pickStageStart, onPickResult, onPickTimeout,
                        onResult);
            }
        }
    }
}
