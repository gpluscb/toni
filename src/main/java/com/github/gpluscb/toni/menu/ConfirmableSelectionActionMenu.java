package com.github.gpluscb.toni.menu;

import com.github.gpluscb.toni.util.OneOfTwo;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.SelectionMenuEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Button;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class ConfirmableSelectionActionMenu<T> extends ActionMenu {
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
                .setUsers(settings.users())
                .setStart(new MessageBuilder().build()) // Start must be set but will be ignored (tech debt yay!!)
                .setOnTimeout(this::onSelectionTimeout);

        for (ChoiceOption<T> choiceOption : settings.choices())
            selectionUnderlyingBuilder.registerOption(choiceOption.option(), (info, e) ->
                    onOptionChoice(choiceOption.associatedChoice, info, e));

        selectionUnderlying = new SelectionActionMenu(selectionUnderlyingBuilder.build());
        buttonUnderlying = new ButtonActionMenu(new ButtonActionMenu.Settings.Builder()
                .setActionMenuSettings(settings.actionMenuSettings())
                .setUsers(settings.users())
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

    private void onSelectionTimeout(@Nonnull SelectionActionMenu.SelectionMenuTimeoutEvent timeout) {
        isCancelled = true;

        settings.onTimeout().accept(new ConfirmationInfoTimeoutEvent(OneOfTwo.ofT(timeout)));
    }

    private void onSubmitTimeout(@Nonnull ButtonActionMenu.ButtonActionMenuTimeoutEvent timeout) {
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
                              @Nonnull Set<Long> users, @Nonnull Button submitButton,
                              @Nonnull List<ChoiceOption<T>> choices,
                              @Nonnull BiConsumer<ConfirmableSelectionActionMenu<T>.OptionChoiceInfo, SelectionMenuEvent> onOptionChoice,
                              @Nonnull BiConsumer<ConfirmableSelectionActionMenu<T>.ConfirmationInfo, ButtonClickEvent> onConfirmation,
                              @Nonnull Consumer<ConfirmableSelectionActionMenu<T>.ConfirmationInfoTimeoutEvent> onTimeout) {
    }
}
