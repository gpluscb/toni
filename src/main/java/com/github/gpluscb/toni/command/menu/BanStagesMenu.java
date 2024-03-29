package com.github.gpluscb.toni.command.menu;

import com.github.gpluscb.toni.menu.ActionMenu;
import com.github.gpluscb.toni.menu.ButtonActionMenu;
import com.github.gpluscb.toni.smashset.Ruleset;
import com.github.gpluscb.toni.smashset.Stage;
import com.github.gpluscb.toni.util.MiscUtil;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
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
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static net.dv8tion.jda.api.interactions.components.buttons.Button.LABEL_MAX_LENGTH;

public class BanStagesMenu extends ActionMenu {
    private static final Logger log = LogManager.getLogger(BanStagesMenu.class);

    @Nonnull
    private final Settings settings;

    @Nonnull
    private final ButtonActionMenu underlying;

    @Nonnull
    private final Set<Integer> bannedStageIds;

    public BanStagesMenu(@Nonnull Settings settings) {
        super(settings.actionMenuSettings());
        this.settings = settings;

        bannedStageIds = new HashSet<>();

        if (settings.ruleset().stageBans() == 0)
            throw new IllegalArgumentException("The ruleset must have at least one ban.");

        MessageCreateData start = settings.banMessageProducer().apply(new UpcomingBanInfo());

        ButtonActionMenu.Settings.Builder underlyingBuilder = new ButtonActionMenu.Settings.Builder()
                .setActionMenuSettings(getActionMenuSettings())
                .setStart(start)
                .addUsers(settings.banningUser())
                .setDeletionButton(null)
                .setOnTimeout(this::onTimeout);

        settings.ruleset().getStagesStream().forEach(stage -> {
            int id = stage.stageId();
            Button stageButton = Button.secondary(String.valueOf(id), StringUtils.abbreviate(stage.name(), LABEL_MAX_LENGTH))
                    .withEmoji(Emoji.fromCustom("a", stage.stageEmoteId(), false)) // a as placeholder because it may not be empty
                    .withDisabled(settings.dsrIllegalStages().contains(id));

            underlyingBuilder.registerButton(stageButton, e -> onBan(id, e));
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
    private synchronized MenuAction onBan(int stageId, @Nonnull ButtonInteractionEvent e) {
        if (bannedStageIds.contains(stageId)) {
            log.warn("Stage was banned twice: {}", stageId);
            e.reply("I have recorded that you banned this stage already earlier.").setEphemeral(true).queue();
            return MenuAction.CONTINUE;
        }

        if (settings.dsrIllegalStages().contains(stageId)) {
            log.warn("DSR illegal stage was banned: {}", stageId);
            e.reply("You shouldn't have been able to ban this stage, " +
                    "because DSR rules already prevent your opponent from picking this stage.").setEphemeral(true).queue();
            return MenuAction.CONTINUE;
        }

        bannedStageIds.add(stageId);
        settings.onBan().accept(new StageBan(stageId), e);

        if (bannedStageIds.size() == settings.ruleset().stageBans()) {
            // Our job here is done
            settings.onResult().accept(new BanResult(), e);
            return MenuAction.CANCEL;
        }

        // More stages are to be banned
        List<ActionRow> actionRows = MiscUtil.disabledButtonActionRows(e);

        MessageCreateData newMessage = settings.banMessageProducer().apply(new UpcomingBanInfo());

        e.editMessage(MessageEditData.fromCreateData(newMessage)).setComponents(actionRows).queue();

        return MenuAction.CONTINUE;
    }

    private synchronized void onTimeout(@Nonnull ButtonActionMenu.ButtonActionMenuTimeoutEvent timeout) {
        settings.onTimeout().accept(new BanStagesTimeoutEvent());
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
    public Settings getBanStagesMenuSettings() {
        return settings;
    }

    private abstract class BanStagesInfo extends MenuStateInfo {
        @Nonnull
        public Settings getBanStagesMenuSettings() {
            return BanStagesMenu.this.getBanStagesMenuSettings();
        }

        @Nonnull
        public Set<Integer> getBannedStageIds() {
            return bannedStageIds;
        }

        @Nonnull
        public List<Stage> getBannedStages() {
            // For each banned stage there is a stage with that id in the ruleset
            //noinspection OptionalGetWithoutIsPresent
            return bannedStageIds.stream()
                    .map(id -> settings.ruleset().getStagesStream().filter(stage -> stage.stageId() == id).findAny().get())
                    .toList();
        }

        @Nonnull
        public Set<Integer> getRemainingStageIds() {
            return settings.ruleset().getStagesStream()
                    .map(Stage::stageId)
                    .filter(Predicate.not(bannedStageIds::contains))
                    .filter(Predicate.not(settings.dsrIllegalStages()::contains))
                    .collect(Collectors.toSet());
        }

        @Nonnull
        public List<Stage> getRemainingStages() {
            return settings.ruleset().getStagesStream()
                    .filter(stage -> !bannedStageIds.contains(stage.stageId()))
                    .filter(stage -> !settings.dsrIllegalStages().contains(stage.stageId()))
                    .toList();
        }
    }

    public class UpcomingBanInfo extends BanStagesInfo {
        public int getStagesToBan() {
            return settings.ruleset().stageBans() - bannedStageIds.size();
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
            return settings.ruleset().getStagesStream()
                    .filter(stage -> stage.stageId() == bannedStageId)
                    .findAny()
                    .get();
        }
    }

    public class BanResult extends BanStagesInfo {
    }

    public class BanStagesTimeoutEvent extends BanStagesInfo {
    }

    public record Settings(@Nonnull ActionMenu.Settings actionMenuSettings, long banningUser, @Nonnull Ruleset ruleset,
                           @Nonnull Set<Integer> dsrIllegalStages,
                           @Nonnull Function<UpcomingBanInfo, MessageCreateData> banMessageProducer,
                           @Nonnull BiConsumer<StageBan, ButtonInteractionEvent> onBan,
                           @Nonnull BiConsumer<BanResult, ButtonInteractionEvent> onResult,
                           @Nonnull Consumer<BanStagesTimeoutEvent> onTimeout) {
        @Nonnull
        public static final Function<UpcomingBanInfo, MessageCreateData> DEFAULT_BAN_MESSAGE_PRODUCER = info -> {
            long banningUser = info.getBanStagesMenuSettings().banningUser();
            int stagesToBan = info.getStagesToBan();

            if (info.getBannedStageIds().isEmpty()) {
                return new MessageCreateBuilder()
                        .setContent(String.format("%s, it is your turn to ban stages. Please ban %d stage%s from the list below.",
                                MiscUtil.mentionUser(banningUser),
                                stagesToBan,
                                stagesToBan == 1 ? "" : "s"))
                        .mentionUsers(banningUser)
                        .build();
            } else {
                return new MessageCreateBuilder()
                        .setContent(String.format("%s, please ban %d more stage%s from the list below.",
                                MiscUtil.mentionUser(banningUser),
                                stagesToBan,
                                stagesToBan == 1 ? "" : "s"))
                        .mentionUsers(banningUser)
                        .build();
            }
        };
        @Nonnull
        public static final BiConsumer<StageBan, ButtonInteractionEvent> DEFAULT_ON_BAN = MiscUtil.emptyBiConsumer();
        @Nonnull
        public static final BiConsumer<BanResult, ButtonInteractionEvent> DEFAULT_ON_RESULT = MiscUtil.emptyBiConsumer();
        @Nonnull
        public static final Consumer<BanStagesTimeoutEvent> DEFAULT_ON_TIMEOUT = MiscUtil.emptyConsumer();

        public static class Builder {
            @Nullable
            private ActionMenu.Settings actionMenuSettings;
            @Nullable
            private Long banningUser;
            @Nullable
            private Ruleset ruleset;
            @Nonnull
            private Set<Integer> dsrIllegalStages = new HashSet<>();
            @Nonnull
            private Function<UpcomingBanInfo, MessageCreateData> banMessageProducer = DEFAULT_BAN_MESSAGE_PRODUCER;
            @Nonnull
            private BiConsumer<StageBan, ButtonInteractionEvent> onBan = DEFAULT_ON_BAN;
            @Nonnull
            private BiConsumer<BanResult, ButtonInteractionEvent> onResult = DEFAULT_ON_RESULT;
            @Nonnull
            private Consumer<BanStagesTimeoutEvent> onTimeout = DEFAULT_ON_TIMEOUT;

            @Nonnull
            public Builder setActionMenuSettings(@Nullable ActionMenu.Settings actionMenuSettings) {
                this.actionMenuSettings = actionMenuSettings;
                return this;
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
            public Builder setDsrIllegalStages(@Nonnull Set<Integer> dsrIllegalStages) {
                this.dsrIllegalStages = dsrIllegalStages;
                return this;
            }

            @Nonnull
            public Builder setBanMessageProducer(@Nonnull Function<UpcomingBanInfo, MessageCreateData> banMessageProducer) {
                this.banMessageProducer = banMessageProducer;
                return this;
            }

            @Nonnull
            public Builder setOnBan(@Nonnull BiConsumer<StageBan, ButtonInteractionEvent> onBan) {
                this.onBan = onBan;
                return this;
            }

            @Nonnull
            public Builder setOnResult(@Nonnull BiConsumer<BanResult, ButtonInteractionEvent> onResult) {
                this.onResult = onResult;
                return this;
            }

            @Nonnull
            public Builder setOnTimeout(@Nonnull Consumer<BanStagesTimeoutEvent> onTimeout) {
                this.onTimeout = onTimeout;
                return this;
            }

            @Nonnull
            public Settings build() {
                if (actionMenuSettings == null) throw new IllegalStateException("ActionMenuSettings must be set");
                if (banningUser == null) throw new IllegalStateException("BanningUser must be set");
                if (ruleset == null) throw new IllegalStateException("Ruleset must be set");

                return new Settings(actionMenuSettings, banningUser, ruleset, dsrIllegalStages, banMessageProducer, onBan, onResult, onTimeout);
            }
        }
    }
}
