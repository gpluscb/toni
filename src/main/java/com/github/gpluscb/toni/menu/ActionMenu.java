package com.github.gpluscb.toni.menu;

import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.requests.restaction.MessageAction;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.TimeUnit;

public abstract class ActionMenu {
    @Nonnull
    private final Settings settings;

    @Nullable
    private JDA jda;
    private long messageId;
    private long channelId;

    public ActionMenu(@Nonnull Settings settings) {
        this.settings = settings;
    }

    protected void setMessageInfo(@Nonnull Message message) {
        this.jda = message.getJDA();
        this.messageId = message.getIdLong();
        this.channelId = message.getChannel().getIdLong();
    }

    // final to prevent accidentally overriding. getJDA and stuff can be overridden
    @Nonnull
    public final Settings getActionMenuSettings() {
        return settings;
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

    public abstract void display(@Nonnull MessageChannel channel, long messageId);

    public void displayReplying(@Nonnull Message reference) {
        displayReplying(reference.getChannel(), reference.getIdLong());
    }

    public abstract void displayReplying(@Nonnull MessageChannel channel, long messageId);

    public abstract void displaySlashReplying(@Nonnull SlashCommandInteractionEvent event);

    public abstract void displayDeferredReplying(@Nonnull InteractionHook hook);

    @Nonnull
    public abstract List<ActionRow> getComponents();

    public abstract void start(@Nonnull Message message);

    public abstract class MenuStateInfo {
        @Nonnull
        public Settings getActionMenuSettings() {
            return ActionMenu.this.getActionMenuSettings();
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

    public record Settings(@Nonnull EventWaiter waiter, long timeout, @Nonnull TimeUnit unit) {
        public static final long DEFAULT_TIMEOUT = 20;
        @Nonnull
        public static final TimeUnit DEFAULT_UNIT = TimeUnit.MINUTES;

        public static class Builder {
            @Nullable
            private EventWaiter waiter;
            private long timeout = DEFAULT_TIMEOUT;
            @Nonnull
            private TimeUnit unit = DEFAULT_UNIT;

            @Nonnull
            public Builder setWaiter(@Nullable EventWaiter waiter) {
                this.waiter = waiter;
                return this;
            }

            @Nonnull
            public Builder setTimeout(long timeout, @Nonnull TimeUnit unit) {
                this.timeout = timeout;
                this.unit = unit;
                return this;
            }

            @Nonnull
            public Settings build() {
                if (waiter == null) throw new IllegalStateException("Waiter must be set");
                return new Settings(waiter, timeout, unit);
            }
        }
    }

    public enum MenuAction {
        CONTINUE,
        CANCEL
    }
}
