package com.github.gpluscb.toni.menu;

import com.github.gpluscb.toni.util.MiscUtil;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.LayoutComponent;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;

public class ConfirmableButtonChoiceMenu<T> extends ActionMenu {
    private static final Logger log = LogManager.getLogger(ConfirmableButtonChoiceMenu.class);

    @Nonnull
    private final Settings<T> settings;

    @Nonnull
    private final ButtonActionMenu underlying;

    @Nonnull
    private final List<T> currentChoices;

    public ConfirmableButtonChoiceMenu(@Nonnull Settings<T> settings) {
        super(settings.actionMenuSettings());

        this.settings = settings;

        if (settings.minChoices() > settings.maxChoices())
            throw new IllegalArgumentException("minChoices must be less than or equal to maxChoices.");

        currentChoices = new ArrayList<>();

        ButtonActionMenu.Settings.Builder underlyingSettingsBuilder = new ButtonActionMenu.Settings.Builder()
                .setActionMenuSettings(settings.actionMenuSettings())
                .addUsers(settings.user())
                .setStart(settings.start())
                .setDeletionButton(null)
                .setOnTimeout(this::onTimeout);

        for (ChoiceButton<T> choiceButton : settings.choiceButtons())
            underlyingSettingsBuilder.registerButton(choiceButton.button(), e -> onChoice(choiceButton.associatedChoice(), e));

        underlyingSettingsBuilder.registerButton(settings.confirmButton(), this::onConfirm);
        underlyingSettingsBuilder.registerButton(settings.resetButton(), this::onReset);

        underlying = new ButtonActionMenu(underlyingSettingsBuilder.build());
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
    private synchronized MenuAction onChoice(@Nullable T choice, @Nonnull ButtonInteractionEvent event) {
        if (choice == null) {
            log.warn("Invalid choice chosen - id: {}", event.getComponentId());

            event.reply("This choice is not valid. Choose another one.")
                    .setEphemeral(true)
                    .queue();

            return MenuAction.CONTINUE;
        }

        if (currentChoices.size() == settings.maxChoices()) {
            event.reply("You have already chosen the maximum amount of choices. " +
                            "Use the \"Change choice\" button to change your choice.")
                    .setEphemeral(true)
                    .queue();

            return MenuAction.CONTINUE;
        }

        currentChoices.add(choice);
        MessageEditData message = settings.choiceMessageProvider().apply(new ChoiceInfo(choice), event);

        List<ActionRow> actionRows = MiscUtil.disabledButtonActionRows(event);

        // Activate reset
        // resetButton will not be a link button, otherwise build of underlying would have failed
        //noinspection ConstantConditions
        LayoutComponent.updateComponent(actionRows, settings.resetButton().getId(), settings.resetButton().asEnabled());

        // (De)Activate confirm
        boolean canConfirm = currentChoices.size() >= settings.minChoices() && currentChoices.size() <= settings.maxChoices();
        // confirmButton will not be a link button, otherwise build of underlying would have failed
        //noinspection ConstantConditions
        LayoutComponent.updateComponent(actionRows, settings.confirmButton().getId(), settings.confirmButton().withDisabled(!canConfirm));

        event.editMessage(message)
                .setComponents(actionRows)
                .queue();

        return MenuAction.CONTINUE;
    }

    @Nonnull
    private synchronized MenuAction onConfirm(@Nonnull ButtonInteractionEvent event) {
        if (currentChoices.size() < settings.minChoices() || currentChoices.size() > settings.maxChoices()) {
            log.warn("Confirm was activated with illegal number of choices.");

            event.reply(String.format("You must select between %d and %d choices before confirming.", settings.minChoices(), settings.maxChoices()))
                    .setEphemeral(true)
                    .queue();

            return MenuAction.CONTINUE;
        }

        settings.onChoicesConfirmed().accept(new ConfirmInfo(), event);

        return MenuAction.CANCEL;
    }

    @Nonnull
    private synchronized MenuAction onReset(@Nonnull ButtonInteractionEvent event) {
        currentChoices.clear();

        MessageEditData message = settings.resetMessageProvider().apply(new ResetInfo(), event);

        List<ActionRow> actionRows = underlying.getInitialActionRows();

        event.editMessage(message)
                .setComponents(actionRows)
                .queue();

        return MenuAction.CONTINUE;
    }

    private synchronized void onTimeout(@Nonnull ButtonActionMenu.ButtonActionMenuTimeoutEvent timeout) {
        settings.onTimeout().accept(new ConfirmableButtonChoiceTimeoutEvent(timeout));
    }

    @Nonnull
    public Settings<T> getConfirmableButtonChoiceMenuSettings() {
        return settings;
    }

    @Nonnull
    public ButtonActionMenu.Settings getUnderlyingButtonActionMenuSettings() {
        return underlying.getButtonActionMenuSettings();
    }

    @Nonnull
    @Override
    public JDA getJDA() {
        return underlying.getJDA();
    }

    @Override
    public long getChannelId() {
        return underlying.getChannelId();
    }

    @Override
    public long getMessageId() {
        return underlying.getMessageId();
    }

    private abstract class ConfirmableButtonChoiceInfo extends MenuStateInfo {
        @Nonnull
        public List<T> getChoices() {
            return currentChoices;
        }

        @Nonnull
        public Settings<T> getConfirmableButtonChoiceMenuSettings() {
            return ConfirmableButtonChoiceMenu.this.getConfirmableButtonChoiceMenuSettings();
        }

        @Nonnull
        public ButtonActionMenu.Settings getUnderlyingButtonActionMenuSeconds() {
            return ConfirmableButtonChoiceMenu.this.getUnderlyingButtonActionMenuSettings();
        }
    }

    public class ChoiceInfo extends ConfirmableButtonChoiceInfo {
        @Nonnull
        private final T choice;

        public ChoiceInfo(@Nonnull T choice) {
            this.choice = choice;
        }

        @Nonnull
        public T getChoice() {
            return choice;
        }
    }

    public class ResetInfo extends ConfirmableButtonChoiceInfo {
    }

    public class ConfirmInfo extends ConfirmableButtonChoiceInfo {
    }

    public class ConfirmableButtonChoiceTimeoutEvent extends ConfirmableButtonChoiceInfo {
        @Nonnull
        private final ButtonActionMenu.ButtonActionMenuTimeoutEvent underlyingTimeout;

        public ConfirmableButtonChoiceTimeoutEvent(@Nonnull ButtonActionMenu.ButtonActionMenuTimeoutEvent underlyingTimeout) {
            this.underlyingTimeout = underlyingTimeout;
        }

        @Nonnull
        public ButtonActionMenu.ButtonActionMenuTimeoutEvent getUnderlyingTimeout() {
            return underlyingTimeout;
        }
    }

    public record ChoiceButton<T>(@Nonnull Button button,
                                  @Nullable T associatedChoice) {
    }

    public record Settings<T>(@Nonnull ActionMenu.Settings actionMenuSettings, long user,
                              @Nonnull List<ChoiceButton<T>> choiceButtons, @Nonnull MessageCreateData start,
                              @Nonnull Button confirmButton, @Nonnull Button resetButton,
                              int minChoices, int maxChoices,
                              @Nonnull BiFunction<ConfirmableButtonChoiceMenu<? super T>.ChoiceInfo, ButtonInteractionEvent, MessageEditData> choiceMessageProvider,
                              @Nonnull BiFunction<ConfirmableButtonChoiceMenu<? super T>.ResetInfo, ButtonInteractionEvent, MessageEditData> resetMessageProvider,
                              @Nonnull BiConsumer<ConfirmableButtonChoiceMenu<? super T>.ConfirmInfo, ButtonInteractionEvent> onChoicesConfirmed,
                              @Nonnull Consumer<ConfirmableButtonChoiceMenu<? super T>.ConfirmableButtonChoiceTimeoutEvent> onTimeout) {
        public static class Builder<T> {
            @Nullable
            private ActionMenu.Settings actionMenuSettings;
            @Nullable
            private Long user;
            @Nonnull
            private List<ChoiceButton<T>> choiceButtons = new ArrayList<>();
            @Nullable
            private MessageCreateData start;
            @Nullable
            private Button confirmButton;
            @Nullable
            private Button resetButton;
            @Nullable
            private Integer minChoices;
            @Nullable
            private Integer maxChoices;
            @Nullable
            private BiFunction<ConfirmableButtonChoiceMenu<? super T>.ChoiceInfo, ButtonInteractionEvent, MessageEditData> choiceMessageProvider;
            @Nullable
            private BiFunction<ConfirmableButtonChoiceMenu<? super T>.ResetInfo, ButtonInteractionEvent, MessageEditData> resetMessageProvider;
            @Nullable
            private BiConsumer<ConfirmableButtonChoiceMenu<? super T>.ConfirmInfo, ButtonInteractionEvent> onChoicesConfirmed;
            @Nullable
            private Consumer<ConfirmableButtonChoiceMenu<? super T>.ConfirmableButtonChoiceTimeoutEvent> onTimeout;

            @Nonnull
            public Builder<T> setActionMenuSettings(@Nullable ActionMenu.Settings actionMenuSettings) {
                this.actionMenuSettings = actionMenuSettings;
                return this;
            }

            @Nonnull
            public Builder<T> setUser(long user) {
                this.user = user;
                return this;
            }

            @Nonnull
            public Builder<T> registerChoiceButton(@Nonnull Button button, @Nullable T associatedChoice) {
                return registerChoiceButton(new ChoiceButton<>(button, associatedChoice));
            }

            @Nonnull
            public Builder<T> registerChoiceButton(@Nonnull ChoiceButton<T> choiceButton) {
                choiceButtons.add(choiceButton);
                return this;
            }

            @Nonnull
            public Builder<T> setChoiceButtons(@Nonnull List<ChoiceButton<T>> choiceButtons) {
                this.choiceButtons = choiceButtons;
                return this;
            }

            @Nonnull
            public Builder<T> setStart(@Nullable MessageCreateData start) {
                this.start = start;
                return this;
            }

            @Nonnull
            public Builder<T> setConfirmButton(@Nullable Button confirmButton) {
                this.confirmButton = confirmButton;
                return this;
            }

            @Nonnull
            public Builder<T> setResetButton(@Nullable Button resetButton) {
                this.resetButton = resetButton;
                return this;
            }

            @Nonnull
            public Builder<T> setChoicesRange(int minChoices, int maxChoices) {
                this.minChoices = minChoices;
                this.maxChoices = maxChoices;
                return this;
            }

            @Nonnull
            public Builder<T> setChoiceMessageProvider(@Nullable BiFunction<ConfirmableButtonChoiceMenu<? super T>.ChoiceInfo, ButtonInteractionEvent, MessageEditData> choiceMessageProvider) {
                this.choiceMessageProvider = choiceMessageProvider;
                return this;
            }

            @Nonnull
            public Builder<T> setResetMessageProvider(@Nullable BiFunction<ConfirmableButtonChoiceMenu<? super T>.ResetInfo, ButtonInteractionEvent, MessageEditData> resetMessageProvider) {
                this.resetMessageProvider = resetMessageProvider;
                return this;
            }

            @Nonnull
            public Builder<T> setOnChoicesConfirmed(@Nullable BiConsumer<ConfirmableButtonChoiceMenu<? super T>.ConfirmInfo, ButtonInteractionEvent> onChoicesConfirmed) {
                this.onChoicesConfirmed = onChoicesConfirmed;
                return this;
            }

            @Nonnull
            public Builder<T> setOnTimeout(@Nullable Consumer<ConfirmableButtonChoiceMenu<? super T>.ConfirmableButtonChoiceTimeoutEvent> onTimeout) {
                this.onTimeout = onTimeout;
                return this;
            }

            public Settings<T> build() {
                if (actionMenuSettings == null) throw new IllegalStateException("ActionMenuSettings must be set");
                if (user == null) throw new IllegalStateException("User must be set");
                if (start == null) throw new IllegalStateException("Start must be set");
                if (confirmButton == null) throw new IllegalStateException("ConfirmButton must be set");
                if (resetButton == null) throw new IllegalStateException("ResetButton must be set");
                if (minChoices == null || maxChoices == null)
                    throw new IllegalStateException("choiceRange must be set");
                if (choiceMessageProvider == null) throw new IllegalStateException("ChoiceMessageProvider must be set");
                if (resetMessageProvider == null) throw new IllegalStateException("ResetMessageProvider must be set");
                if (onChoicesConfirmed == null) throw new IllegalStateException("OnChoicesConfirmed must be set");
                if (onTimeout == null) throw new IllegalStateException("OnTimeout must be set");

                return new Settings<>(actionMenuSettings, user, choiceButtons, start, confirmButton, resetButton,
                        minChoices, maxChoices, choiceMessageProvider, resetMessageProvider, onChoicesConfirmed, onTimeout);
            }
        }
    }
}
