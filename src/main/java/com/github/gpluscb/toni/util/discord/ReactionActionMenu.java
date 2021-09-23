package com.github.gpluscb.toni.util.discord;

import com.github.gpluscb.toni.util.Constants;
import com.github.gpluscb.toni.util.FailLogger;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * We override the validation to only allow specific users in users.
 */
public class ReactionActionMenu extends Menu {
    private static final Logger log = LogManager.getLogger(ReactionActionMenu.class);

    @Nonnull
    private final Set<Long> users;

    @Nonnull
    private final Map<String, Function<MessageReactionAddEvent, Message>> buttonActions;
    @Nonnull
    private final Message start;
    /**
     * Inner is null until we're ready
     */
    @Nonnull
    private final AtomicReference<Long> botId;
    @Nullable
    private final String deletionButton;
    @Nonnull
    private final BiConsumer<MessageChannel, Long> timeoutAction;

    public ReactionActionMenu(@Nonnull EventWaiter waiter, @Nonnull Set<Long> users, long timeout, @Nonnull TimeUnit unit, @Nonnull Map<String, Function<MessageReactionAddEvent, Message>> buttonActions, @Nonnull Message start, @Nullable String deletionButton, @Nonnull BiConsumer<MessageChannel, Long> timeoutAction) {
        super(waiter, Collections.emptySet(), Collections.emptySet(), timeout, unit);
        this.users = users;
        this.buttonActions = buttonActions;
        this.start = start;
        botId = new AtomicReference<>(null);
        this.deletionButton = deletionButton;
        this.timeoutAction = timeoutAction;
    }

    @Override
    public void display(@Nonnull MessageChannel channel) {
        channel.sendMessage(start).queue(this::init);
    }

    public void displayReplying(Message reference) {
        displayReplying(reference.getChannel(), reference.getIdLong());
    }

    public void displayReplying(@Nonnull MessageChannel channel, long messageId) {
        boolean hasPerms = true;
        if (channel instanceof TextChannel) {
            TextChannel textChannel = (TextChannel) channel;
            hasPerms = textChannel.getGuild().getSelfMember().hasPermission(textChannel, Permission.MESSAGE_HISTORY);
        }

        if (hasPerms)
            channel.sendMessage(start).referenceById(messageId).queue(this::init);
        else
            channel.sendMessage(start).queue(this::init);
    }

    @Override
    public void display(@Nonnull Message message) {
        message.editMessage(start).queue(this::init);
    }

    private void init(@Nonnull Message message) {
        botId.set(message.getAuthor().getIdLong());

        if (!message.isFromGuild() || message.getGuild().getSelfMember().hasPermission(message.getTextChannel(), Permission.MESSAGE_ADD_REACTION, Permission.MESSAGE_HISTORY)) {
            buttonActions.keySet().stream().map(message::addReaction).forEach(RestAction::queue);
            if (deletionButton != null) message.addReaction(deletionButton).queue();
        }

        awaitEvents(message);
    }

    private void awaitEvents(@Nonnull Message message) {
        awaitEvents(message.getJDA(), message.getIdLong(), message.getChannel().getIdLong());
    }

    private void awaitEvents(@Nonnull JDA jda, long messageId, long channelId) {
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
        Long botIdLong = botId.get();
        // If null, we're not ready yet
        return botIdLong != null && botIdLong != user && (users.isEmpty() || users.contains(user));
    }

    private boolean checkReaction(@Nonnull MessageReactionAddEvent e, long messageId) {
        if (e.getMessageIdLong() != messageId) return false;
        if (!isValidUser(e.getUserIdLong())) return false;

        String reactionName = e.getReactionEmote().getName();
        return buttonActions.containsKey(reactionName) || reactionName.equals(deletionButton);
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

        Message edited = buttonActions.get(reactionName).apply(e);
        if (edited != null) channel.editMessageById(messageId, edited).queue(this::awaitEvents);
        else awaitEvents(e.getJDA(), messageId, channel.getIdLong());
    }

    public static class Builder extends Menu.Builder<Builder, ReactionActionMenu> {
        @Nonnull
        private final Set<Long> users;
        @Nonnull
        private final Map<String, Function<MessageReactionAddEvent, Message>> buttonActions;
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
         * <p>
         * If the user list ends up empty, everyone can use it
         */
        @Nonnull
        public Builder addUsers(Long... users) {
            this.users.addAll(Arrays.asList(users));
            return this;
        }

        /**
         * @param action If action returns null, the message is not edited
         * @throws IllegalArgumentException if reaction is already registered
         */
        @Nonnull
        public synchronized Builder registerButton(@Nonnull String reaction, @Nonnull Function<MessageReactionAddEvent, Message> action) {
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
        public synchronized ReactionActionMenu build() {
            if (waiter == null) throw new IllegalStateException("Waiter must be set");
            if (start == null) throw new IllegalStateException("Start must be set");
            if (!super.users.isEmpty())
                throw new IllegalStateException("You likely tried to use addUsers(User...). Use addUsers(Long...) instead.");

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

            return new ReactionActionMenu(waiter, users, timeout, unit, buttonActions, start, deletionButton, timeoutAction);
        }
    }
}
