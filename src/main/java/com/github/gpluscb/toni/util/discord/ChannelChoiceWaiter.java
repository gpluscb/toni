package com.github.gpluscb.toni.util.discord;

import com.github.gpluscb.toni.util.FailLogger;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ChannelChoiceWaiter {
    @Nonnull
    private final EventWaiter waiter;

    @Nonnull
    private final Map<WaitChoiceHandle, WaitingElement<?>> activeElements;

    public ChannelChoiceWaiter(@Nonnull EventWaiter waiter) {
        this.waiter = waiter;
        activeElements = new HashMap<>();
    }

    @Nullable
    @CheckReturnValue
    public <T> WaitChoiceHandle waitForChoice(@Nonnull List<Long> participants, long channelId, boolean ignoreDoubleChoice, @Nonnull Function<MessageReceivedEvent, Optional<T>> verifyChoice, @Nonnull Consumer<List<UserChoiceInfo<T>>> onChoicesDone, long timeout, @Nonnull TimeUnit unit, @Nonnull Consumer<List<UserChoiceInfo<T>>> timeoutAction) {
        synchronized (activeElements) {
            List<Long> activeUsers = getActiveUsers(channelId);
            if (participants.stream().anyMatch(activeUsers::contains)) return null;

            List<UserChoiceInfo<T>> choices = participants.stream()
                    .map(id -> new UserChoiceInfo<T>(id, channelId))
                    .collect(Collectors.toList());

            return putElement(choices, ignoreDoubleChoice, verifyChoice, onChoicesDone, timeout, unit, timeoutAction);
        }
    }

    @Nullable
    @CheckReturnValue
    public <T> WaitChoiceHandle waitForDMChoice(@Nonnull List<Long> participants, boolean ignoreDoubleChoice, @Nonnull Function<MessageReceivedEvent, Optional<T>> verifyChoice, @Nonnull Consumer<List<UserChoiceInfo<T>>> onChoicesDone, long timeout, @Nonnull TimeUnit unit, @Nonnull Consumer<List<UserChoiceInfo<T>>> timeoutAction) {
        synchronized (activeElements) {
            List<Long> activeDMUsers = getActiveDMUsers();
            if (participants.stream().anyMatch(activeDMUsers::contains)) return null;

            List<UserChoiceInfo<T>> choices = participants.stream()
                    .map(id -> new UserChoiceInfo<T>(id, id))
                    .collect(Collectors.toList());

            return putElement(choices, ignoreDoubleChoice, verifyChoice, onChoicesDone, timeout, unit, timeoutAction);
        }
    }

    @Nonnull
    private <T> WaitChoiceHandle putElement(@Nonnull List<UserChoiceInfo<T>> choices, boolean ignoreDoubleChoice, @Nonnull Function<MessageReceivedEvent, Optional<T>> verifyChoice, @Nonnull Consumer<List<UserChoiceInfo<T>>> onChoicesDone, long timeout, @Nonnull TimeUnit unit, @Nonnull Consumer<List<UserChoiceInfo<T>>> timeoutAction) {
        WaitingElement<T> element = new WaitingElement<>(choices, ignoreDoubleChoice, timeout, unit, verifyChoice, onChoicesDone, timeoutAction);
        WaitChoiceHandle handle = new WaitChoiceHandle();

        synchronized (activeElements) {
            activeElements.put(handle, element);
        }

        return handle;
    }

    public void startListening(@Nonnull WaitChoiceHandle handle) {
        handle.setListening();
        synchronized (activeElements) {
            WaitingElement<?> element = activeElements.get(handle);

            if (element == null) throw new IllegalStateException("Active handle was not present in activeElements");

            waiter.waitForEvent(MessageReceivedEvent.class,
                    element::attemptChoice,
                    e -> removeElement(handle),
                    element.getTimeout(), element.getUnit(), FailLogger.logFail(() -> { // This is the only thing that will be executed on not JDA-WS thread. So exceptions may get swallowed
                        element.onTimeout();
                        removeElement(handle);
                    }));
        }
    }

    public void cancel(@Nonnull WaitChoiceHandle handle) {
        handle.setCancelled();
        synchronized (activeElements) {
            if (activeElements.remove(handle) == null)
                throw new IllegalStateException("Handle was not present in activeElements");
        }
    }

    private void removeElement(@Nonnull WaitChoiceHandle handle) {
        synchronized (activeElements) {
            activeElements.remove(handle);
        }
    }

    @Nonnull
    public List<Long> getActiveUsers(long channelId) {
        synchronized (activeElements) {
            return activeElements.values().stream()
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
            return activeElements.values().stream()
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
        private final long timeout;
        @Nonnull
        private final TimeUnit unit;
        @Nonnull
        private final Function<MessageReceivedEvent, Optional<T>> verifyChoice;
        @Nonnull
        private final Consumer<List<UserChoiceInfo<T>>> onChoicesDone;
        @Nonnull
        private final Consumer<List<UserChoiceInfo<T>>> timeoutAction;

        public WaitingElement(@Nonnull List<UserChoiceInfo<T>> choices, boolean ignoreDoubleChoices, long timeout, @Nonnull TimeUnit unit, @Nonnull Function<MessageReceivedEvent, Optional<T>> verifyChoice, @Nonnull Consumer<List<UserChoiceInfo<T>>> onChoicesDone, @Nonnull Consumer<List<UserChoiceInfo<T>>> timeoutAction) {
            this.choices = choices;
            this.ignoreDoubleChoices = ignoreDoubleChoices;
            this.timeout = timeout;
            this.unit = unit;
            this.verifyChoice = verifyChoice;
            this.onChoicesDone = onChoicesDone;
            this.timeoutAction = timeoutAction;
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

        private void onTimeout() {
            timeoutAction.accept(choices);
        }

        @Nonnull
        public List<UserChoiceInfo<T>> getChoices() {
            return choices;
        }

        public long getTimeout() {
            return timeout;
        }

        @Nonnull
        public TimeUnit getUnit() {
            return unit;
        }
    }

    public class WaitChoiceHandle {
        private boolean alreadyListening;
        private boolean cancelled;

        private WaitChoiceHandle() {
        }

        public void startListening() {
            ChannelChoiceWaiter.this.startListening(this);
        }

        public void cancel() {
            ChannelChoiceWaiter.this.cancel(this);
        }

        private synchronized void setListening() {
            if (alreadyListening) throw new IllegalStateException("Handle was already listening");
            if (cancelled) throw new IllegalStateException("Handle was cancelled");
            alreadyListening = true;
        }

        private synchronized void setCancelled() {
            if (alreadyListening) throw new IllegalStateException("Handle was already listening");
            if (cancelled) throw new IllegalStateException("Handle was already cancelled");
            cancelled = true;
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
