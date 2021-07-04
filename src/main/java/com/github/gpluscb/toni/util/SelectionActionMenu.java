package com.github.gpluscb.toni.util;

import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import com.jagrosh.jdautilities.menu.Menu;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.interaction.SelectionMenuEvent;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.SelectionMenu;
import net.dv8tion.jda.api.requests.restaction.MessageAction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * We override the validation to only allow specific users in users.
 */
public class SelectionActionMenu extends Menu {
    private static final Logger log = LogManager.getLogger(SelectionActionMenu.class);

    @Nonnull
    private final Set<Long> users;

    @Nonnull
    private final Set<SelectOption> optionsToAdd;
    @Nonnull
    private final Map<String, Function<SelectionMenuEvent, Message>> selectionActions;
    @Nonnull
    private final Message start;
    @Nonnull
    private final String id;
    @Nonnull
    private final BiConsumer<MessageChannel, Long> timeoutAction;

    public SelectionActionMenu(@Nonnull EventWaiter waiter, @Nonnull Set<Long> users, long timeout, @Nonnull TimeUnit unit, @Nonnull Map<SelectOption, Function<SelectionMenuEvent, Message>> selectionActions, @Nonnull Message start, @Nonnull String id, @Nonnull BiConsumer<MessageChannel, Long> timeoutAction) {
        super(waiter, Collections.emptySet(), Collections.emptySet(), timeout, unit);
        this.users = users;

        optionsToAdd = selectionActions.keySet();

        this.selectionActions = selectionActions
                .entrySet()
                .stream()
                .map(e -> new PairNonnull<>(e.getKey().getValue(), e.getValue()))
                .collect(Collectors.toMap(PairNonnull::getT, PairNonnull::getU));

        this.start = start;
        this.id = id;
        this.timeoutAction = timeoutAction;
    }

    @Override
    public void display(@Nonnull MessageChannel channel) {
        init(channel.sendMessage(start));
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
            init(channel.sendMessage(start).referenceById(messageId));
        else
            init(channel.sendMessage(start));
    }

    @Override
    public void display(@Nonnull Message message) {
        init(message.editMessage(start));
    }

    private void init(@Nonnull MessageAction messageAction) {
        SelectionMenu selectionMenu = SelectionMenu.create(id).addOptions(optionsToAdd).build();
        messageAction.setActionRow(selectionMenu).queue(this::awaitEvents);
    }

    private void awaitEvents(@Nonnull Message message) {
        awaitEvents(message.getJDA(), message.getIdLong(), message.getChannel().getIdLong());
    }

    private void awaitEvents(@Nonnull JDA jda, long messageId, long channelId) {
        waiter.waitForEvent(SelectionMenuEvent.class,
                e -> checkSelection(e, messageId),
                this::handleSelection,
                timeout, unit, FailLogger.logFail(() -> { // This is the only thing that will be executed on not JDA-WS thread. So exceptions may get swallowed
                    MessageChannel channel = jda.getTextChannelById(channelId);
                    if (channel == null) channel = jda.getPrivateChannelById(channelId);
                    timeoutAction.accept(channel, messageId);
                    if (channel == null) log.warn("MessageChannel for timeoutAction not in cache for timeoutAction");
                }));
    }

    private boolean checkSelection(@Nonnull SelectionMenuEvent e, long messageId) {
        return e.getMessageIdLong() == messageId
                && e.getComponentId().equals(id)
                && isValidUser(e.getUser().getIdLong());
    }

    private void handleSelection(@Nonnull SelectionMenuEvent e) {
        // We require exactly one selection.
        String value = e.getValues().get(0);
        Message edited = selectionActions.get(value).apply(e);

        long messageId = e.getMessageIdLong();
        MessageChannel channel = e.getChannel();

        if (edited != null) channel.editMessageById(messageId, edited).queue(this::awaitEvents);
        else awaitEvents(e.getJDA(), messageId, channel.getIdLong());
    }

    private boolean isValidUser(long user) {
        return users.isEmpty() || users.contains(user);
    }

    public static class Builder extends Menu.Builder<SelectionActionMenu.Builder, SelectionActionMenu> {
        @Nonnull
        private final Set<Long> users;
        @Nonnull
        private final Map<SelectOption, Function<SelectionMenuEvent, Message>> selectionActions;
        @Nullable
        private Message start;
        @Nullable
        private String id;
        @Nullable
        private BiConsumer<MessageChannel, Long> timeoutAction;

        /**
         * Default timeout of 20 minutes
         */
        public Builder() {
            users = new HashSet<>();
            selectionActions = new LinkedHashMap<>(); // Preserve order
            setTimeout(20, TimeUnit.MINUTES);
        }

        /**
         * USE THIS METHOD INSTEAD
         * <p>
         * If the user list ends up empty, everyone can use it
         */
        @Nonnull
        public SelectionActionMenu.Builder addUsers(Long... users) {
            this.users.addAll(Arrays.asList(users));
            return this;
        }

        /**
         * @param action If action returns null, the message is not edited
         * @throws IllegalArgumentException if option is already registered
         */
        @Nonnull
        public synchronized SelectionActionMenu.Builder registerOption(@Nonnull SelectOption option, @Nonnull Function<SelectionMenuEvent, Message> action) {
            if (selectionActions.containsKey(option)) throw new IllegalArgumentException("Option already registered");
            selectionActions.put(option, action);
            return this;
        }

        @Nonnull
        public SelectionActionMenu.Builder setStart(@Nullable Message start) {
            this.start = start;
            return this;
        }

        /**
         * Default: Random 5-character String
         */
        @Nonnull
        public SelectionActionMenu.Builder setId(@Nullable String id) {
            this.id = id;
            return this;
        }

        /**
         * MessageChannel may be null on timeout in weird cases
         * <p>
         * Default: look at source lol it's too long for docs: {@link #build()}
         */
        @Nonnull
        public SelectionActionMenu.Builder setTimeoutAction(@Nullable BiConsumer<MessageChannel, Long> timeoutAction) {
            this.timeoutAction = timeoutAction;
            return this;
        }

        /**
         * @throws IllegalStateException if waiter or start is not set, or if super.users contains stuff to prevent accidents
         */
        @Nonnull
        @Override
        public synchronized SelectionActionMenu build() {
            if (waiter == null) throw new IllegalStateException("Waiter must be set");
            if (start == null) throw new IllegalStateException("Start must be set");
            if (!super.users.isEmpty())
                throw new IllegalStateException("You likely tried to use addUsers(User...). Use addUsers(Long...) instead.");

            if (timeoutAction == null) {
                timeoutAction = (channel, id) -> {
                    if (channel == null) return;

                    channel.retrieveMessageById(id)
                            .flatMap(m -> m.editMessage(m).setActionRows())
                            .queue();
                };
            }

            if (id == null) {
                // Source: https://www.baeldung.com/java-random-string lol
                int leftLimit = 97; // letter 'a'
                int rightLimit = 122; // letter 'z'
                int targetStringLength = 5;
                Random random = new Random();

                String generatedString = random.ints(leftLimit, rightLimit + 1)
                        .limit(targetStringLength)
                        .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                        .toString();

                System.out.println(generatedString);
            }

            return new SelectionActionMenu(waiter, users, timeout, unit, selectionActions, start, id, timeoutAction);
        }
    }
}
