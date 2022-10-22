package com.github.gpluscb.toni.menu;

import com.github.gpluscb.toni.util.Constants;
import com.github.gpluscb.toni.util.FailLogger;
import com.github.gpluscb.toni.util.MiscUtil;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import com.jagrosh.jdautilities.menu.Menu;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
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
    private final Map<Emoji, Function<MessageReactionAddEvent, MessageEditData>> buttonActions;
    @Nonnull
    private final MessageCreateData start;
    /**
     * Inner is null until we're ready
     */
    @Nonnull
    private final AtomicReference<Long> botId;
    @Nullable
    private final Emoji deletionButton;
    @Nonnull
    private final BiConsumer<MessageChannel, Long> timeoutAction;

    public ReactionActionMenu(@Nonnull EventWaiter waiter, @Nonnull Set<Long> users, long timeout, @Nonnull TimeUnit unit, @Nonnull Map<Emoji, Function<MessageReactionAddEvent, MessageEditData>> buttonActions, @Nonnull MessageCreateData start, @Nullable Emoji deletionButton, @Nonnull BiConsumer<MessageChannel, Long> timeoutAction) {
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

    /**
     * Needs MESSAGE_HISTORY perms
     */
    public void displaySlashCommandReplying(@Nonnull SlashCommandInteractionEvent e) {
        e.reply(start).flatMap(InteractionHook::retrieveOriginal).queue(this::init);
    }

    public void displaySlashCommandDeferred(@Nonnull SlashCommandInteractionEvent e) {
        e.getHook().sendMessage(start).queue(this::init);
    }

    public void displayReplying(Message reference) {
        displayReplying(reference.getChannel(), reference.getIdLong());
    }

    public void displayReplying(@Nonnull MessageChannel channel, long messageId) {
        boolean hasPerms = true;
        if (channel instanceof TextChannel textChannel) {
            hasPerms = textChannel.getGuild().getSelfMember().hasPermission(textChannel, Permission.MESSAGE_HISTORY);
        }

        if (hasPerms)
            channel.sendMessage(start).setMessageReference(messageId).queue(this::init);
        else
            channel.sendMessage(start).queue(this::init);
    }

    @Override
    public void display(@Nonnull Message message) {
        message.editMessage(MessageEditData.fromCreateData(start)).queue(this::init);
    }

    private void init(@Nonnull Message message) {
        botId.set(message.getAuthor().getIdLong());

        if (!message.isFromGuild() || message.getGuild().getSelfMember().hasPermission(message.getGuildChannel(), Permission.MESSAGE_ADD_REACTION, Permission.MESSAGE_HISTORY)) {
            buttonActions.keySet().stream().map(message::addReaction).forEach(RestAction::queue);
            if (deletionButton != null) message.addReaction(deletionButton).queue();
        }

        awaitEvents(message);
    }

    private void awaitEvents(@Nonnull Message message) {
        JDA jda = message.getJDA();
        long messageId = message.getIdLong();
        long channelId = message.getChannel().getIdLong();

        waiter.waitForEvent(MessageReactionAddEvent.class,
                e -> {
                    if (checkReaction(e, messageId)) {
                        // Stop awaiting on CANCEL
                        return handleMessageReactionAdd(e) == ActionMenu.MenuAction.CANCEL;
                    }
                    // Return false to endlessly keep awaiting until timeout
                    return false;
                },
                MiscUtil.emptyConsumer(),
                timeout, unit, FailLogger.logFail(() -> { // This is the only thing that will be executed on not JDA-WS thread. So exceptions may get swallowed
                    MessageChannel channel = jda.getChannelById(MessageChannel.class, channelId);

                    timeoutAction.accept(channel, messageId);
                    if (channel == null) log.warn("MessageChannel for onTimeout not in cache for onTimeout");
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

        Emoji reaction = e.getEmoji();
        return buttonActions.containsKey(reaction) || reaction.equals(deletionButton);
    }

    @Nonnull
    private ActionMenu.MenuAction handleMessageReactionAdd(@Nonnull MessageReactionAddEvent e) {
        Emoji reaction = e.getEmoji();

        long messageId = e.getMessageIdLong();
        MessageChannel channel = e.getChannel();
        if (reaction.equals(deletionButton)) {
            channel.deleteMessageById(e.getMessageId()).queue();
            return ActionMenu.MenuAction.CANCEL;
        }

        if (e.isFromGuild() && e.getGuild().getSelfMember().hasPermission(e.getGuildChannel(), Permission.MESSAGE_MANAGE)) {
            User user = e.getUser();
            if (user != null) e.getReaction().removeReaction(user).queue();
            else log.warn("User was null despite event being from guild. Not removing reaction");
        }

        MessageEditData edited = buttonActions.get(reaction).apply(e);
        if (edited != null) channel.editMessageById(messageId, edited).queue();

        return ActionMenu.MenuAction.CONTINUE;
    }

    public static class Builder extends Menu.Builder<Builder, ReactionActionMenu> {
        @Nonnull
        private final Set<Long> users;
        @Nonnull
        private final Map<Emoji, Function<MessageReactionAddEvent, MessageEditData>> buttonActions;
        @Nullable
        private MessageCreateData start;
        @Nullable
        private Emoji deletionButton;
        @Nullable
        private BiConsumer<MessageChannel, Long> timeoutAction;

        /**
         * Default timeout of 20 minutes
         */
        public Builder() {
            users = new HashSet<>();
            buttonActions = new LinkedHashMap<>(); // Preserve order
            deletionButton = Emoji.fromUnicode(Constants.CROSS_MARK);
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
        public synchronized Builder registerButton(@Nonnull Emoji reaction, @Nonnull Function<MessageReactionAddEvent, MessageEditData> action) {
            if (buttonActions.containsKey(reaction)) throw new IllegalArgumentException("Reaction already registered");
            buttonActions.put(reaction, action);
            return this;
        }

        @Nonnull
        public Builder setStart(@Nullable MessageCreateData start) {
            this.start = start;
            return this;
        }

        /**
         * Default: {@link Constants#CROSS_MARK}
         * {@code null} is none, not default
         */
        @Nonnull
        public Builder setDeletionButton(@Nullable Emoji deletionButton) {
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
                    if (channel instanceof TextChannel textChannel) {
                        if (textChannel.getGuild().getSelfMember().hasPermission(textChannel, Permission.MESSAGE_MANAGE))
                            textChannel.clearReactionsById(id).queue();
                    } else {
                        for (Emoji button : buttonActions.keySet()) channel.removeReactionById(id, button).queue();
                        if (deletionButton != null) channel.removeReactionById(id, deletionButton).queue();
                    }
                };
            }

            return new ReactionActionMenu(waiter, users, timeout, unit, buttonActions, start, deletionButton, timeoutAction);
        }
    }
}
