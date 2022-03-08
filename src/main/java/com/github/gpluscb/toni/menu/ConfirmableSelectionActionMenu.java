package com.github.gpluscb.toni.menu;

import com.github.gpluscb.toni.util.OneOfTwo;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.SelectionMenuEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Button;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class ConfirmableSelectionActionMenu<T> extends ActionMenu {
    private static final Logger log = LogManager.getLogger(ConfirmableSelectionActionMenu.class);

    @Nonnull
    private final Settings<T> settings;

    @Nonnull
    private final SelectionActionMenu selectionUnderlying;
    @Nonnull
    private final ButtonActionMenu buttonUnderlying;

    @Nullable
    private T currentSelection;
    private boolean isCancelled;

    public ConfirmableSelectionActionMenu(@Nonnull Settings<T> settings) {
        super(settings.actionMenuSettings());

        this.settings = settings;

        currentSelection = null;

        SelectionActionMenu.Settings.Builder selectionUnderlyingBuilder = new SelectionActionMenu.Settings.Builder()
                .setActionMenuSettings(settings.actionMenuSettings())
                .addUsers(settings.user())
                .setStart(new MessageBuilder().build()) // Start must be set but will be ignored (tech debt yay!!)
                .setOnTimeout(this::onSelectionTimeout);

        for (ChoiceOption<T> choiceOption : settings.choices())
            selectionUnderlyingBuilder.registerOption(choiceOption.option(), (info, e) ->
                    onOptionChoice(choiceOption.associatedChoice, info, e));

        selectionUnderlying = new SelectionActionMenu(selectionUnderlyingBuilder.build());
        buttonUnderlying = new ButtonActionMenu(new ButtonActionMenu.Settings.Builder()
                .setActionMenuSettings(settings.actionMenuSettings())
                .addUsers(settings.user())
                .setStart(new MessageBuilder().build()) // Start must be set but will be ignored
                .setDeletionButton(null)
                .registerButton(settings.submitButton(), this::onSubmit)
                .setOnTimeout(this::onSubmitTimeout)
                .build());

        isCancelled = false;
    }

    @Override
    public void display(@Nonnull MessageChannel channel) {
        channel.sendMessage(settings.start())
                .setActionRows(getComponents())
                .queue(this::start);
    }

    @Override
    public void display(@Nonnull MessageChannel channel, long messageId) {
        channel.editMessageById(messageId, settings.start())
                .setActionRows(getComponents())
                .queue(this::start);
    }

    @Override
    public void displayReplying(@Nonnull MessageChannel channel, long messageId) {
        channel.sendMessage(settings.start())
                .referenceById(messageId)
                .setActionRows(getComponents())
                .queue(this::start);
    }

    @Override
    public void displaySlashReplying(@Nonnull SlashCommandEvent event) {
        event.reply(settings.start())
                .addActionRows(getComponents())
                .flatMap(InteractionHook::retrieveOriginal)
                .queue(this::start);
    }

    @Override
    public void displayDeferredReplying(@Nonnull InteractionHook hook) {
        hook.sendMessage(settings.start())
                .addActionRows(getComponents())
                .queue(this::start);
    }

    @Nonnull
    @Override
    public List<ActionRow> getComponents() {
        return Stream.concat(selectionUnderlying.getComponents().stream(), buttonUnderlying.getComponents().stream()).toList();
    }

    @Override
    public void start(@Nonnull Message message) {
        selectionUnderlying.start(message);
        buttonUnderlying.start(message);
        setMessageInfo(message);
    }

    @Nonnull
    private synchronized MenuAction onOptionChoice(@Nonnull T choice, @Nonnull SelectionActionMenu.SelectionInfo info, @Nonnull SelectionMenuEvent event) {
        // Cancel SelectionActionMenu if already submitted
        // The choice shouldn't happen because the menu will already be removed
        // tho it kinda is a race condition.
        // If this doesn't happen, we'll time out eventually, so it's not a leak
        if (isCancelled) return MenuAction.CANCEL;

        currentSelection = choice;

        settings.onOptionChoice().accept(new OptionChoiceInfo(info), event);

        return MenuAction.CONTINUE;
    }

    @Nonnull
    private synchronized MenuAction onSubmit(@Nonnull ButtonClickEvent event) {
        isCancelled = true;

        settings.onConfirmation().accept(new ConfirmationInfo(), event);

        return MenuAction.CANCEL;
    }

    private synchronized void onSelectionTimeout(@Nonnull SelectionActionMenu.SelectionMenuTimeoutEvent timeout) {
        isCancelled = true;

        settings.onTimeout().accept(new ConfirmationInfoTimeoutEvent(OneOfTwo.ofT(timeout)));
    }

    private synchronized void onSubmitTimeout(@Nonnull ButtonActionMenu.ButtonActionMenuTimeoutEvent timeout) {
        isCancelled = true;

        settings.onTimeout().accept(new ConfirmationInfoTimeoutEvent(OneOfTwo.ofU(timeout)));
    }

    private abstract class ConfirmableSelectionInfo extends MenuStateInfo {
        @Nullable
        public T getCurrentSelection() {
            return currentSelection;
        }

        public boolean isCancelled() {
            return isCancelled;
        }
    }

    public class OptionChoiceInfo extends ConfirmableSelectionInfo {
        @Nonnull
        private final SelectionActionMenu.SelectionInfo info;

        public OptionChoiceInfo(@Nonnull SelectionActionMenu.SelectionInfo info) {
            this.info = info;
        }

        @Nonnull
        public SelectionActionMenu.SelectionInfo getInfo() {
            return info;
        }
    }

    public class ConfirmationInfo extends ConfirmableSelectionInfo {
    }

    public class ConfirmationInfoTimeoutEvent extends ConfirmableSelectionInfo {
        @Nonnull
        private final OneOfTwo<SelectionActionMenu.SelectionMenuTimeoutEvent, ButtonActionMenu.ButtonActionMenuTimeoutEvent> underlyingTimeout;

        public ConfirmationInfoTimeoutEvent(@Nonnull OneOfTwo<SelectionActionMenu.SelectionMenuTimeoutEvent, ButtonActionMenu.ButtonActionMenuTimeoutEvent> underlyingTimeout) {
            this.underlyingTimeout = underlyingTimeout;
        }

        @Nonnull
        public OneOfTwo<SelectionActionMenu.SelectionMenuTimeoutEvent, ButtonActionMenu.ButtonActionMenuTimeoutEvent> getUnderlyingTimeout() {
            return underlyingTimeout;
        }
    }

    public record ChoiceOption<T>(@Nonnull SelectOption option, @Nonnull T associatedChoice) {
    }

    public record Settings<T>(@Nonnull ActionMenu.Settings actionMenuSettings, @Nonnull Message start,
                              long user, @Nonnull Button submitButton,
                              @Nonnull List<ChoiceOption<T>> choices,
                              @Nonnull BiConsumer<ConfirmableSelectionActionMenu<T>.OptionChoiceInfo, SelectionMenuEvent> onOptionChoice,
                              @Nonnull BiConsumer<ConfirmableSelectionActionMenu<T>.ConfirmationInfo, ButtonClickEvent> onConfirmation,
                              @Nonnull Consumer<ConfirmableSelectionActionMenu<T>.ConfirmationInfoTimeoutEvent> onTimeout) {
        @Nonnull
        public static final Button DEFAULT_SUBMIT_BUTTON = Button.primary("submit", "Submit");

        public static <T> void DEFAULT_ON_OPTION_CHOICE(ConfirmableSelectionActionMenu<T>.OptionChoiceInfo choiceInfo, SelectionMenuEvent event) {
        }

        public static <T> void DEFAULT_ON_CONFIRMATION(ConfirmableSelectionActionMenu<T>.ConfirmationInfo submitInfo, ButtonClickEvent event) {
        }

        public static <T> void DEFAULT_ON_TIMEOUT(ConfirmableSelectionActionMenu<T>.ConfirmationInfoTimeoutEvent timeout) {
            MessageChannel channel = timeout.getChannel();
            long id = timeout.getMessageId();
            if (channel == null) {
                log.warn("MessageChannel for timeoutAction not in cache");
                return;
            }
            if (channel instanceof TextChannel textChannel) {
                if (!textChannel.getGuild().getSelfMember().hasPermission(textChannel, Permission.MESSAGE_HISTORY))
                    return;
            }

            channel.retrieveMessageById(id)
                    .flatMap(m -> m.editMessage(m).setActionRows())
                    .queue(null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_MESSAGE));
        }

        public static class Builder<T> {
            @Nullable
            private ActionMenu.Settings actionMenuSettings;
            @Nullable
            private Message start;
            @Nullable
            private Long user;
            @Nonnull
            private Button submitButton = DEFAULT_SUBMIT_BUTTON;
            @Nonnull
            private List<ChoiceOption<T>> choices = new ArrayList<>();
            @Nonnull
            private BiConsumer<ConfirmableSelectionActionMenu<T>.OptionChoiceInfo, SelectionMenuEvent> onOptionChoice = Settings::DEFAULT_ON_OPTION_CHOICE;
            @Nonnull
            private BiConsumer<ConfirmableSelectionActionMenu<T>.ConfirmationInfo, ButtonClickEvent> onConfirmation = Settings::DEFAULT_ON_CONFIRMATION;
            @Nonnull
            private Consumer<ConfirmableSelectionActionMenu<T>.ConfirmationInfoTimeoutEvent> onTimeout = Settings::DEFAULT_ON_TIMEOUT;

            @Nonnull
            public Builder<T> setActionMenuSettings(@Nonnull ActionMenu.Settings actionMenuSettings) {
                this.actionMenuSettings = actionMenuSettings;
                return this;
            }

            @Nonnull
            public Builder<T> setStart(@Nonnull Message start) {
                this.start = start;
                return this;
            }

            @Nonnull
            public Builder<T> setUser(long user) {
                this.user = user;
                return this;
            }

            @Nonnull
            public Builder<T> setSubmitButton(@Nonnull Button submitButton) {
                this.submitButton = submitButton;
                return this;
            }

            @Nonnull
            public Builder<T> setChoices(@Nonnull List<ChoiceOption<T>> choices) {
                this.choices = choices;
                return this;
            }

            @Nonnull
            public Builder<T> registerChoice(@Nonnull ChoiceOption<T> choice) {
                choices.add(choice);
                return this;
            }

            @Nonnull
            public Builder<T> registerChoice(@Nonnull SelectOption option, @Nonnull T associatedChoice) {
                return registerChoice(new ChoiceOption<>(option, associatedChoice));
            }

            @Nonnull
            public Builder<T> setOnOptionChoice(@Nonnull BiConsumer<ConfirmableSelectionActionMenu<T>.OptionChoiceInfo, SelectionMenuEvent> onOptionChoice) {
                this.onOptionChoice = onOptionChoice;
                return this;
            }

            @Nonnull
            public Builder<T> setOnConfirmation(@Nonnull BiConsumer<ConfirmableSelectionActionMenu<T>.ConfirmationInfo, ButtonClickEvent> onConfirmation) {
                this.onConfirmation = onConfirmation;
                return this;
            }

            @Nonnull
            public Builder<T> setOnTimeout(@Nonnull Consumer<ConfirmableSelectionActionMenu<T>.ConfirmationInfoTimeoutEvent> onTimeout) {
                this.onTimeout = onTimeout;
                return this;
            }

            @Nonnull
            public Settings<T> build() {
                if (actionMenuSettings == null) throw new IllegalStateException("ActionMenuSettings must be set");
                if (start == null) throw new IllegalStateException("Start must be set");
                if (user == null) throw new IllegalStateException("User must be set");

                return new Settings<>(actionMenuSettings, start, user, submitButton, choices, onOptionChoice, onConfirmation, onTimeout);
            }
        }
    }
}
