package com.github.gpluscb.toni.util;

import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import net.dv8tion.jda.api.events.message.priv.PrivateMessageReceivedEvent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DMChoiceWaiter {
    @Nonnull
    private final EventWaiter waiter;

    @Nonnull
    private final List<WaitingElement<?>> activeElements;

    public DMChoiceWaiter(@Nonnull EventWaiter waiter) {
        this.waiter = waiter;
        activeElements = new ArrayList<>();
    }

    public <T> boolean waitForDMChoice(@Nonnull List<Long> participants, boolean ignoreDoubleChoice, @Nonnull Function<PrivateMessageReceivedEvent, Optional<T>> verifyChoice, @Nonnull Consumer<Map<Long, T>> onChoicesDone, long timeout, @Nullable TimeUnit unit, @Nullable Consumer<Map<Long, T>> timeoutAction) {
        WaitingElement<T> element;

        synchronized (activeElements) {
            List<Long> activeUsers = getActiveUsers();
            if (participants.stream().anyMatch(activeUsers::contains)) return false;

            element = new WaitingElement<>(participants, ignoreDoubleChoice, verifyChoice, onChoicesDone);

            activeElements.add(element);
        }

        waiter.waitForEvent(PrivateMessageReceivedEvent.class,
                e -> element.attemptChoice(e.getAuthor().getIdLong(), e),
                e -> removeElement(element),
                timeout, unit, FailLogger.logFail(() -> { // This is the only thing that will be executed on not JDA-WS thread. So exceptions may get swallowed
                    if (timeoutAction != null) timeoutAction.accept(element.getIncompleteResults());
                    removeElement(element);
                }));

        return true;
    }

    private void removeElement(@Nonnull WaitingElement<?> element) {
        synchronized (activeElements) {
            activeElements.remove(element);
        }
    }

    @Nonnull
    public List<Long> getActiveUsers() {
        synchronized (activeElements) {
            return activeElements.stream().map(WaitingElement::getActiveUsers).flatMap(List::stream).collect(Collectors.toList());
        }
    }

    // TODO: Have active users cached? Could speed thing up a little. For each command invocation in private messages, getActiveUsers is checked
    private static class WaitingElement<T> {
        @Nonnull
        final Map<Long, Optional<T>> choices;
        final boolean ignoreDoubleChoice;
        @Nonnull
        final Function<PrivateMessageReceivedEvent, Optional<T>> verifyChoice;
        @Nonnull
        final Consumer<Map<Long, T>> onChoicesDone;

        public WaitingElement(@Nonnull List<Long> participants, boolean ignoreDoubleChoice, @Nonnull Function<PrivateMessageReceivedEvent, Optional<T>> verifyChoice, @Nonnull Consumer<Map<Long, T>> onChoicesDone) {
            this.choices = participants.stream().collect(Collectors.toMap(id -> id, id -> Optional.empty()));
            this.ignoreDoubleChoice = ignoreDoubleChoice;
            this.verifyChoice = verifyChoice;
            this.onChoicesDone = onChoicesDone;
        }

        /**
         * @return true if done
         */
        boolean attemptChoice(long id, @Nonnull PrivateMessageReceivedEvent event) {
            synchronized (choices) {
                if (!isActive(id)) return false;

                Optional<T> choice = verifyChoice.apply(event);
                if (choice.isEmpty()) return false;

                choices.put(id, choice);

                Map<Long, T> result = getResultIfDone();
                if (result == null) return false;

                onChoicesDone.accept(result);
                return true;
            }
        }

        /**
         * @return null if not done
         */
        @Nullable
        Map<Long, T> getResultIfDone() {
            synchronized (choices) {
                Map<Long, T> validResults = getIncompleteResults();

                return validResults.size() == choices.size() ? validResults : null;
            }
        }

        @Nonnull
        Map<Long, T> getIncompleteResults() {
            return choices.entrySet().stream()
                    .filter(entry -> entry.getValue().isPresent())
                    .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().get()));
        }

        @Nonnull
        List<Long> getActiveUsers() {
            synchronized (choices) {
                return choices.entrySet().stream()
                        .filter(entry -> isActive(entry.getValue().orElse(null)))
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toList());
            }
        }

        /**
         * @return false if user does not exist in here
         */
        boolean isActive(long id) {
            synchronized (choices) {
                Optional<T> choice = choices.get(id);
                //noinspection OptionalAssignedToNull
                if (choice == null) return false;

                T nullableChoice = choice.orElse(null);

                return isActive(nullableChoice);
            }
        }

        /**
         * helper
         */
        private boolean isActive(@Nullable T choice) {
            return choice == null || !ignoreDoubleChoice;
        }
    }
}
