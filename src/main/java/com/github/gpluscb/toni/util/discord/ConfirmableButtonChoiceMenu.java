package com.github.gpluscb.toni.util.discord;

import com.github.gpluscb.toni.util.MiscUtil;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Button;
import net.dv8tion.jda.api.interactions.components.ComponentLayout;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

public class ConfirmableButtonChoiceMenu<T> extends ActionMenu {
    private static final Logger log = LogManager.getLogger(ConfirmableButtonChoiceMenu.class);

    @Nonnull
    private final ButtonActionMenu underlying;
    private final int minChoices;
    private final int maxChoices;
    @Nonnull
    private final Button confirmButton;
    @Nonnull
    private final Button resetButton;
    @Nonnull
    private final BiFunction<ChoiceInfo, ButtonClickEvent, Message> choiceMessageProvider;
    @Nonnull
    private final BiFunction<ConfirmableButtonChoiceInfo, ButtonClickEvent, Message> resetMessageProvider;
    @Nonnull
    private final BiConsumer<ConfirmableButtonChoiceInfo, ButtonClickEvent> onChoicesConfirmed;
    @Nonnull
    private final BiConsumer<ConfirmableButtonChoiceInfo, ButtonActionMenu.ButtonActionMenuTimeoutEvent> onTimeout;

    @Nonnull
    private final List<T> currentChoices;

    public ConfirmableButtonChoiceMenu(@Nonnull EventWaiter waiter, long user, long timeout, @Nonnull TimeUnit unit, @Nonnull List<ChoiceButton<T>> choiceButtons, @Nonnull Message start,
                                       @Nonnull Button confirmButton, @Nonnull Button resetButton, int minChoices, int maxChoices, @Nonnull BiFunction<ChoiceInfo, ButtonClickEvent, Message> choiceMessageProvider, @Nonnull BiFunction<ConfirmableButtonChoiceInfo, ButtonClickEvent, Message> resetMessageProvider, @Nonnull BiConsumer<ConfirmableButtonChoiceInfo, ButtonClickEvent> onChoicesConfirmed, @Nonnull BiConsumer<ConfirmableButtonChoiceInfo, ButtonActionMenu.ButtonActionMenuTimeoutEvent> onTimeout) {
        super(waiter, timeout, unit);

        if (minChoices > maxChoices)
            throw new IllegalArgumentException("minChoices must be less than or equal to maxChoices.");

        this.minChoices = minChoices;
        this.maxChoices = maxChoices;
        this.confirmButton = confirmButton;
        this.resetButton = resetButton;
        this.choiceMessageProvider = choiceMessageProvider;
        this.resetMessageProvider = resetMessageProvider;
        this.onChoicesConfirmed = onChoicesConfirmed;
        this.onTimeout = onTimeout;
        currentChoices = new ArrayList<>();

        ButtonActionMenu.Builder underlyingBuilder = new ButtonActionMenu.Builder()
                .setTimeout(timeout, unit)
                .addUsers(user)
                .setStart(start)
                .setDeletionButton(null)
                .setTimeoutAction(this::onTimeout);

        for (ChoiceButton<T> choiceButton : choiceButtons)
            underlyingBuilder.registerButton(choiceButton.getButton(), e -> onChoice(choiceButton.getAssociatedChoice(), e));

        underlyingBuilder.registerButton(confirmButton, this::onConfirm);
        underlyingBuilder.registerButton(resetButton, this::onReset);

        underlying = underlyingBuilder.build();
    }

    @Override
    public void display(@Nonnull MessageChannel channel) {
        underlying.display(channel);
    }

    @Override
    public void display(MessageChannel channel, long messageId) {
        underlying.display(channel, messageId);
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
    private synchronized ButtonActionMenu.MenuAction onChoice(@Nullable T choice, @Nonnull ButtonClickEvent event) {
        if (choice == null) {
            log.warn("Invalid choice chosen - id: {}", event.getComponentId());

            event.reply("This choice is not valid. Choose another one.")
                    .setEphemeral(true)
                    .queue();

            return ButtonActionMenu.MenuAction.CONTINUE;
        }

        if (currentChoices.size() == maxChoices) {
            event.reply("You have already chosen the maximum amount of choices. " +
                            "Use the \"Change choice\" button to change your choice.")
                    .setEphemeral(true)
                    .queue();

            return ButtonActionMenu.MenuAction.CONTINUE;
        }

        currentChoices.add(choice);
        Message message = choiceMessageProvider.apply(new ChoiceInfo(choice), event);

        List<ActionRow> actionRows = MiscUtil.disabledButtonActionRows(event);

        // Activate reset
        // resetButton will not be a link button, otherwise build of underlying would have failed
        //noinspection ConstantConditions
        ComponentLayout.updateComponent(actionRows, resetButton.getId(), resetButton.asEnabled());

        // (De)Activate confirm
        boolean canConfirm = currentChoices.size() >= minChoices && currentChoices.size() <= maxChoices;
        // confirmButton will not be a link button, otherwise build of underlying would have failed
        //noinspection ConstantConditions
        ComponentLayout.updateComponent(actionRows, confirmButton.getId(), confirmButton.withDisabled(!canConfirm));

        event.editMessage(message)
                .setActionRows(actionRows)
                .queue();

        return ButtonActionMenu.MenuAction.CONTINUE;
    }

    @Nonnull
    private synchronized ButtonActionMenu.MenuAction onConfirm(@Nonnull ButtonClickEvent event) {
        if (currentChoices.size() < minChoices || currentChoices.size() > maxChoices) {
            log.warn("Confirm was activated with illegal number of choices.");

            event.reply(String.format("You must select between %d and %d choices before confirming.", minChoices, maxChoices))
                    .setEphemeral(true)
                    .queue();

            return ButtonActionMenu.MenuAction.CONTINUE;
        }

        onChoicesConfirmed.accept(new ConfirmableButtonChoiceInfo(), event);

        return ButtonActionMenu.MenuAction.CANCEL;
    }

    @Nonnull
    private synchronized ButtonActionMenu.MenuAction onReset(@Nonnull ButtonClickEvent event) {
        currentChoices.clear();

        Message message = resetMessageProvider.apply(new ConfirmableButtonChoiceInfo(), event);

        List<ActionRow> actionRows = underlying.getInitialActionRows();

        event.editMessage(message)
                .setActionRows(actionRows)
                .queue();

        return ButtonActionMenu.MenuAction.CONTINUE;
    }

    private synchronized void onTimeout(@Nonnull ButtonActionMenu.ButtonActionMenuTimeoutEvent timeout) {
        onTimeout.accept(new ConfirmableButtonChoiceTimeoutEvent(), timeout);
    }

    public class ConfirmableButtonChoiceInfo extends MenuStateInfo {
        @Nonnull
        public List<T> getChoices() {
            return currentChoices;
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

    public class ConfirmableButtonChoiceTimeoutEvent extends ConfirmableButtonChoiceInfo {
    }

    public static class ChoiceButton<T> {
        @Nonnull
        private final Button button;
        @Nullable
        private final T associatedChoice;

        public ChoiceButton(@Nonnull Button button, @Nullable T associatedChoice) {
            this.button = button;
            this.associatedChoice = associatedChoice;
        }

        @Nonnull
        public Button getButton() {
            return button;
        }

        /**
         * @return null if this button should not be choosable
         */
        @Nullable
        public T getAssociatedChoice() {
            return associatedChoice;
        }
    }
}
