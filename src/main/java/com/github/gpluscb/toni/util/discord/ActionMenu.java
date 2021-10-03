package com.github.gpluscb.toni.util.discord;

import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.TimeUnit;

public abstract class ActionMenu {
    @Nonnull
    private final EventWaiter waiter;
    private final long timeout;
    @Nonnull
    private final TimeUnit unit;

    public ActionMenu(@Nonnull EventWaiter waiter, long timeout, @Nonnull TimeUnit unit) {
        this.waiter = waiter;
        this.timeout = timeout;
        this.unit = unit;
    }

    @Nonnull
    public EventWaiter getWaiter() {
        return waiter;
    }

    public long getTimeout() {
        return timeout;
    }

    @Nonnull
    public TimeUnit getUnit() {
        return unit;
    }

    /**
     * Displays this Menu in a {@link net.dv8tion.jda.api.entities.MessageChannel MessageChannel}.
     *
     * @param channel The MessageChannel to display this Menu in
     */
    public abstract void display(@Nonnull MessageChannel channel);

    /**
     * Displays this Menu as a designated {@link net.dv8tion.jda.api.entities.Message Message}.
     * <br>The Message provided must be one sent by the bot! Trying to provided a Message
     * authored by another {@link net.dv8tion.jda.api.entities.User User} will prevent the
     * Menu from being displayed!
     *
     * @param message The Message to display this Menu as
     */
    public abstract void display(@Nonnull Message message);

    public void displayReplying(@Nonnull Message reference) {
        displayReplying(reference.getChannel(), reference.getIdLong());
    }

    public abstract void displayReplying(@Nonnull MessageChannel channel, long messageId);

    public abstract void displaySlashReplying(@Nonnull SlashCommandEvent event);

    public abstract void displayDeferredReplying(@Nonnull InteractionHook hook);

    public abstract class MenuStateInfo {
        public EventWaiter getWaiter() {
            return ActionMenu.this.getWaiter();
        }

        public long getTimeout() {
            return ActionMenu.this.getTimeout();
        }

        @Nonnull
        public TimeUnit getUnit() {
            return ActionMenu.this.getUnit();
        }
    }

    public interface MenuTimeoutEvent {
        @Nullable
        MessageChannel getChannel();

        long getMessageId();
    }

    @SuppressWarnings("unchecked")
    public static abstract class Builder<T extends Builder<T, V>, V extends ActionMenu> {
        @Nullable
        private EventWaiter waiter;
        private long timeout;
        @Nonnull
        private TimeUnit unit;

        public Builder(@Nonnull Class<T> clazz) {
            if (!this.getClass().equals(clazz)) throw new IllegalArgumentException("T must be the own class");

            timeout = 20;
            unit = TimeUnit.MINUTES;
        }

        @Nonnull
        public T setWaiter(@Nonnull EventWaiter waiter) {
            this.waiter = waiter;
            return (T) this;
        }

        @Nonnull
        public T setTimeout(long timeout, @Nonnull TimeUnit unit) {
            this.timeout = timeout;
            this.unit = unit;
            return (T) this;
        }

        @Nullable
        public EventWaiter getWaiter() {
            return waiter;
        }

        public long getTimeout() {
            return timeout;
        }

        @Nonnull
        public TimeUnit getUnit() {
            return unit;
        }

        protected void preBuild() {
            if (waiter == null) throw new IllegalStateException("Waiter must be set");
        }

        /**
         * call preBuild as the first thing in here. It does checks.
         */
        @Nonnull
        public abstract V build();
    }
}
