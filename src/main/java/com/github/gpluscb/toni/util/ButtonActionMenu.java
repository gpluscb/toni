package com.github.gpluscb.toni.util;

import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import com.jagrosh.jdautilities.menu.Menu;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.requests.RestAction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * We override the validation to only allow specific users in users.
 */
public class ButtonActionMenu extends Menu {
    private static final Logger log = LogManager.getLogger(ButtonActionMenu.class);

    @Nonnull
    private final Set<Long> users;

    @Nonnull
    private final Map<String, Supplier<Message>> buttonActions;
    @Nonnull
    private final Message start;
    @Nullable
    private final String deletionButton;
    @Nonnull
    private final BiConsumer<MessageChannel, Long> timeoutAction;

    public ButtonActionMenu(@Nonnull EventWaiter waiter, @Nonnull Set<Long> users, long timeout, @Nonnull TimeUnit unit, @Nonnull Map<String, Supplier<Message>> buttonActions, @Nonnull Message start, @Nullable String deletionButton, @Nonnull BiConsumer<MessageChannel, Long> timeoutAction) {
        super(waiter, Collections.emptySet(), Collections.emptySet(), timeout, unit);
        this.users = users;
        this.buttonActions = buttonActions;
        this.start = start;
        this.deletionButton = deletionButton;
        this.timeoutAction = timeoutAction;
    }

    @Override
    public void display(MessageChannel channel) {
        channel.sendMessage(start).queue(this::init);
    }

    public void displayReplying(Message reference) {
        reference.reply(start).queue(this::init);
    }

    @Override
    public void display(Message message) {
        message.editMessage(start).queue(this::init);
    }

    private void init(@Nonnull Message message) {
        if (!message.isFromGuild() || message.getGuild().getSelfMember().hasPermission(message.getTextChannel(), Permission.MESSAGE_ADD_REACTION)) {
            buttonActions.keySet().forEach(e -> message.addReaction(e).queue());
            buttonActions.keySet().stream().map(message::addReaction).forEach(RestAction::queue);
            if (deletionButton != null) message.addReaction(deletionButton).queue();
        }

        awaitEvents(message);
    }

    private void awaitEvents(@Nonnull Message message) {
        long messageId = message.getIdLong();
        long channelId = message.getChannel().getIdLong();
        JDA jda = message.getJDA();
        waiter.waitForEvent(MessageReactionAddEvent.class,
                e -> checkReaction(e, messageId),
                this::handleMessageReactionAdd,
                timeout, unit, FailLogger.logFail(() -> { // This is the only thing that will be executed on not JDA-WS thread. So exceptions may get swallowed
                    MessageChannel channel = jda.getTextChannelById(channelId);
                    if (channel == null) channel = jda.getPrivateChannelById(channelId);
                    timeoutAction.accept(channel, messageId);
                    if (channel == null) log.warn("MessageChannel for timeoutAction not in cache for timeoutAction");
                }));
    }

    private boolean isValidUser(long user) {
        return users.contains(user);
    }

    private boolean checkReaction(@Nonnull MessageReactionAddEvent e, long messageId) {
        if (e.getMessageIdLong() != messageId) return false;

        String reactionName = e.getReactionEmote().getName();
        return (buttonActions.containsKey(reactionName) || reactionName.equals(deletionButton)) && isValidUser(e.getUserIdLong());
    }

    private void handleMessageReactionAdd(@Nonnull MessageReactionAddEvent e) {
        String reactionName = e.getReactionEmote().getName();

        long messageId = e.getMessageIdLong();
        MessageChannel channel = e.getChannel();
        if (reactionName.equals(deletionButton)) {
            channel.deleteMessageById(e.getMessageId()).queue();
            return;
        }

        if (e.isFromGuild() && e.getGuild().getSelfMember().hasPermission(e.getTextChannel(), Permission.MESSAGE_MANAGE)) {
            User user = e.getUser();
            if (user != null) e.getReaction().removeReaction(user).queue();
            else log.warn("User was null despite event being from guild. Not removing reaction");
        }

        channel.editMessageById(messageId, buttonActions.get(reactionName).get()).queue(this::awaitEvents);
    }

    public static class Builder extends Menu.Builder<Builder, ButtonActionMenu> {
        @Nonnull
        private final Set<Long> users;
        @Nonnull
        private final Map<String, Supplier<Message>> buttonActions;
        @Nullable
        private Message start;
        @Nullable
        private String deletionButton;
        @Nullable
        private BiConsumer<MessageChannel, Long> timeoutAction;

        /**
         * Default timeout of 20 minutes
         */
        public Builder() {
            users = new HashSet<>();
            buttonActions = new LinkedHashMap<>(); // Preserve order
            deletionButton = Constants.CROSS_MARK;
            setTimeout(20, TimeUnit.MINUTES);
        }

        /**
         * USE THIS METHOD INSTEAD
         */
        @Nonnull
        public Builder addUsers(Long... users) {
            this.users.addAll(Arrays.asList(users));
            return this;
        }

        /**
         * @throws IllegalArgumentException if reaction is already registered
         */
        @Nonnull
        public synchronized Builder registerButton(@Nonnull String reaction, @Nonnull Supplier<Message> action) {
            if (buttonActions.containsKey(reaction)) throw new IllegalArgumentException("Reaction already registered");
            buttonActions.put(reaction, action);
            return this;
        }

        @Nonnull
        public Builder setStart(@Nullable Message start) {
            this.start = start;
            return this;
        }

        /**
         * Default: {@link Constants#CROSS_MARK}
         * {@code null} is none, not default
         */
        @Nonnull
        public Builder setDeletionButton(@Nullable String deletionButton) {
            this.deletionButton = deletionButton;
            return this;
        }

        /**
         * MessageChannel may be null on timeout in weird cases
         * <p>
         * Default: look at source lol it's too long for docs: {@link #build()}
         */
        @Nonnull
        public Builder setTimeoutAction(@Nullable BiConsumer<MessageChannel, Long> timeoutAction) {
            this.timeoutAction = timeoutAction;
            return this;
        }

        /**
         * @throws IllegalStateException if waiter or start is not set, or if super.users contains stuff to prevent accidents
         */
        @Nonnull
        @Override
        public synchronized ButtonActionMenu build() {
            if (waiter == null) throw new IllegalStateException("Waiter must be set");
            if (start == null) throw new IllegalStateException("Start must be set");
            if (!super.users.isEmpty())
                throw new IllegalStateException("You likely tried to use addUsers(User...). User addUsers(Long...) instead.");

            if (timeoutAction == null) {
                timeoutAction = (channel, id) -> {
                    if (channel == null) return;
                    if (channel instanceof TextChannel) {
                        TextChannel textChannel = (TextChannel) channel;
                        if (textChannel.getGuild().getSelfMember().hasPermission(textChannel, Permission.MESSAGE_MANAGE))
                            textChannel.clearReactionsById(id).queue();
                    } else {
                        for (String button : buttonActions.keySet()) channel.removeReactionById(id, button).queue();
                        if (deletionButton != null) channel.removeReactionById(id, deletionButton).queue();
                    }
                };
            }

            return new ButtonActionMenu(waiter, users, timeout, unit, buttonActions, start, deletionButton, timeoutAction);
        }
    }
}
