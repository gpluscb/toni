package com.github.gpluscb.toni.util.discord;

import com.github.gpluscb.toni.util.FailLogger;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ChannelChoiceWaiter {
    @Nonnull
    private final EventWaiter waiter;

    @Nonnull
    private final List<WaitingElement<?>> activeElements;

    public ChannelChoiceWaiter(@Nonnull EventWaiter waiter) {
        this.waiter = waiter;
        activeElements = new ArrayList<>();
    }

    public <T> boolean waitForChoice(@Nonnull List<Long> participants, long channelId, boolean ignoreDoubleChoice, @Nonnull Function<MessageReceivedEvent, Optional<T>> verifyChoice, @Nonnull Consumer<List<UserChoiceInfo<T>>> onChoicesDone, long timeout, @Nullable TimeUnit unit, @Nullable Consumer<List<UserChoiceInfo<T>>> timeoutAction) {
        synchronized (activeElements) {
            List<Long> activeUsers = getActiveUsers(channelId);
            if (participants.stream().anyMatch(activeUsers::contains)) return false;

            List<UserChoiceInfo<T>> choices = participants.stream()
                    .map(id -> new UserChoiceInfo<T>(id, channelId))
                    .collect(Collectors.toList());

            return initWaiter(choices, ignoreDoubleChoice, verifyChoice, onChoicesDone, timeout, unit, timeoutAction);
        }
    }

    public <T> boolean waitForDMChoice(@Nonnull List<Long> participants, boolean ignoreDoubleChoice, @Nonnull Function<MessageReceivedEvent, Optional<T>> verifyChoice, @Nonnull Consumer<List<UserChoiceInfo<T>>> onChoicesDone, long timeout, @Nullable TimeUnit unit, @Nullable Consumer<List<UserChoiceInfo<T>>> timeoutAction) {
        synchronized (activeElements) {
            List<Long> activeDMUsers = getActiveDMUsers();
            if (participants.stream().anyMatch(activeDMUsers::contains)) return false;

            List<UserChoiceInfo<T>> choices = participants.stream()
                    .map(id -> new UserChoiceInfo<T>(id, id))
                    .collect(Collectors.toList());

            return initWaiter(choices, ignoreDoubleChoice, verifyChoice, onChoicesDone, timeout, unit, timeoutAction);
        }
    }

    private <T> boolean initWaiter(@Nonnull List<UserChoiceInfo<T>> choices, boolean ignoreDoubleChoice, @Nonnull Function<MessageReceivedEvent, Optional<T>> verifyChoice, @Nonnull Consumer<List<UserChoiceInfo<T>>> onChoicesDone, long timeout, @Nullable TimeUnit unit, @Nullable Consumer<List<UserChoiceInfo<T>>> timeoutAction) {
        WaitingElement<T> element;

        synchronized (activeElements) {
            element = new WaitingElement<>(choices, ignoreDoubleChoice, verifyChoice, onChoicesDone);

            activeElements.add(element);
        }

        waiter.waitForEvent(MessageReceivedEvent.class,
                element::attemptChoice,
                e -> removeElement(element),
                timeout, unit, FailLogger.logFail(() -> { // This is the only thing that will be executed on not JDA-WS thread. So exceptions may get swallowed
                    if (timeoutAction != null) timeoutAction.accept(element.getChoices());
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
    public List<Long> getActiveUsers(long channelId) {
        synchronized (activeElements) {
            return activeElements.stream()
                    .map(WaitingElement::getChoices)
                    .flatMap(List::stream)
                    .filter(choice -> choice.getChannelId() == channelId)
                    .map(UserChoiceInfo::getUserId)
                    .collect(Collectors.toList());
        }
    }

    @Nonnull
    public List<Long> getActiveDMUsers() {
        synchronized (activeElements) {
            return activeElements.stream()
                    .map(WaitingElement::getChoices)
                    .flatMap(List::stream)
                    .filter(choice -> choice.getChannelId() == choice.getUserId())
                    .map(UserChoiceInfo::getUserId)
                    .collect(Collectors.toList());
        }
    }

    @Nonnull
    public EventWaiter getEventWaiter() {
        return waiter;
    }

    private static class WaitingElement<T> {
        @Nonnull
        private final List<UserChoiceInfo<T>> choices;
        private final boolean ignoreDoubleChoices;
        @Nonnull
        private final Function<MessageReceivedEvent, Optional<T>> verifyChoice;
        @Nonnull
        private final Consumer<List<UserChoiceInfo<T>>> onChoicesDone;

        public WaitingElement(@Nonnull List<UserChoiceInfo<T>> choices, boolean ignoreDoubleChoices, @Nonnull Function<MessageReceivedEvent, Optional<T>> verifyChoice, @Nonnull Consumer<List<UserChoiceInfo<T>>> onChoicesDone) {
            this.choices = choices;
            this.ignoreDoubleChoices = ignoreDoubleChoices;
            this.verifyChoice = verifyChoice;
            this.onChoicesDone = onChoicesDone;
        }

        /**
         * @return true if done
         */
        public boolean attemptChoice(@Nonnull MessageReceivedEvent event) {
            long userId = event.getAuthor().getIdLong();
            long channelId = event.getChannel().getIdLong();

            synchronized (choices) {
                UserChoiceInfo<T> userChoice = findUserChoiceById(userId);
                if (userChoice == null) return false;
                if (channelId != userChoice.getChannelId()) return false;
                if (!isActive(userChoice)) return false;

                Optional<T> choice = verifyChoice.apply(event);
                if (choice.isEmpty()) return false;

                userChoice.setChoice(choice.get());

                if (!isDone()) return false;

                onChoicesDone.accept(choices);
                return true;
            }
        }

        private boolean isDone() {
            synchronized (choices) {
                return choices.stream()
                        .map(UserChoiceInfo::getChoice)
                        .allMatch(Objects::nonNull);
            }
        }

        /**
         * @return false if user does not exist in here
         */
        private boolean isActive(long userId) {
            synchronized (choices) {
                UserChoiceInfo<T> userChoice = findUserChoiceById(userId);

                if (userChoice == null) return false;

                return isActive(userChoice);
            }
        }

        /**
         * helper
         */
        private boolean isActive(@Nonnull UserChoiceInfo<T> userChoice) {
            return userChoice.getChoice() != null || !ignoreDoubleChoices;
        }

        @Nullable
        private UserChoiceInfo<T> findUserChoiceById(long id) {
            synchronized (choices) {
                return choices.stream()
                        .filter(info -> info.getUserId() == id)
                        .findAny()
                        .orElse(null);
            }
        }

        @Nonnull
        public List<UserChoiceInfo<T>> getChoices() {
            return choices;
        }

    }

    public static class UserChoiceInfo<T> {
        private final long userId;
        private final long channelId;
        @Nullable
        private T choice;

        public UserChoiceInfo(long userId, long channelId) {
            this.userId = userId;
            this.channelId = channelId;
        }

        private void setChoice(@Nullable T choice) {
            this.choice = choice;
        }

        public long getUserId() {
            return userId;
        }

        public long getChannelId() {
            return channelId;
        }

        @Nullable
        public T getChoice() {
            return choice;
        }
    }
}
