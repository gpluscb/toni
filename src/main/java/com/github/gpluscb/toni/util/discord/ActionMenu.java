package com.github.gpluscb.toni.util.discord;

import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import net.dv8tion.jda.api.JDA;
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

    @Nullable
    private JDA jda;
    private long messageId;
    private long channelId;

    public ActionMenu(@Nonnull EventWaiter waiter, long timeout, @Nonnull TimeUnit unit) {
        this.waiter = waiter;
        this.timeout = timeout;
        this.unit = unit;
    }

    protected void setMessageInfo(@Nonnull Message message) {
        this.jda = message.getJDA();
        this.messageId = message.getIdLong();
        this.channelId = message.getChannel().getIdLong();
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

    @Nonnull
    public JDA getJDA() {
        if (jda == null) throw new IllegalStateException("Message info has not been initialized yet");
        return jda;
    }

    public long getMessageId() {
        if (jda == null) throw new IllegalStateException("Message info has not been initialized yet");
        return messageId;
    }

    public long getChannelId() {
        if (jda == null) throw new IllegalStateException("Message info has not been initialized yet");
        return channelId;
    }

    @Nullable
    public MessageChannel getChannel() {
        // TODO: Overriding that and having unused data here is kinda ugly, maybe an interface?
        // Shadowing so we can override and not error
        long channelId = getChannelId();
        JDA jda = getJDA();

        MessageChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) channel = jda.getPrivateChannelById(channelId);

        return channel;
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
    public void display(@Nonnull Message message) {
        display(message.getChannel(), message.getIdLong());
    }

    public abstract void display(MessageChannel channel, long messageId);

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

        @Nonnull
        public JDA getJDA() {
            return ActionMenu.this.getJDA();
        }

        public long getChannelId() {
            return ActionMenu.this.getChannelId();
        }

        public long getMessageId() {
            return ActionMenu.this.getMessageId();
        }

        @Nullable
        public MessageChannel getChannel() {
            return ActionMenu.this.getChannel();
        }
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