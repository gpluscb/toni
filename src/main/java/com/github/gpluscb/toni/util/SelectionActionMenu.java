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
                this::checkSelection,
                this::handleSelection,
                timeout, unit, FailLogger.logFail(() -> { // This is the only thing that will be executed on not JDA-WS thread. So exceptions may get swallowed
                    MessageChannel channel = jda.getTextChannelById(channelId);
                    if (channel == null) channel = jda.getPrivateChannelById(channelId);
                    timeoutAction.accept(channel, messageId);
                    if (channel == null) log.warn("MessageChannel for timeoutAction not in cache for timeoutAction");
                }));
    }

    private boolean checkSelection(@Nonnull SelectionMenuEvent e) {
        return e.getComponentId().equals(id) && isValidUser(e.getUser().getIdLong());
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
}
