package com.github.gpluscb.toni.util.discord;

import com.github.gpluscb.toni.util.PairNonnull;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.interactions.components.Button;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class ConfirmableButtonChoiceMenu<T> extends ActionMenu {
    @Nonnull
    private final ButtonActionMenu underlying;

    public ConfirmableButtonChoiceMenu(@Nonnull EventWaiter waiter, @Nonnull Set<Long> users, long timeout, @Nonnull TimeUnit unit, @Nonnull List<ChoiceButton<T>> choiceButtons, @Nonnull Message start,
                                       @Nonnull Button confirmButton, @Nonnull Button resetButton) {
        super(waiter, timeout, unit);

        ButtonActionMenu.Builder underlyingBuilder = new ButtonActionMenu.Builder()
                .setTimeout(timeout, unit)
                .setUsers(users)
                .setStart(start);


        for (ChoiceButton<T> choiceButton : choiceButtons) {

        }
    }

    public static class ChoiceButton<T> {
        @Nonnull
        private final Button button;
        @Nonnull
        private final T associatedChoice;

        public ChoiceButton(@Nonnull Button button, @Nonnull T associatedChoice) {
            this.button = button;
            this.associatedChoice = associatedChoice;
        }

        @Nonnull
        public Button getButton() {
            return button;
        }

        @Nonnull
        public T getAssociatedChoice() {
            return associatedChoice;
        }
    }
}
