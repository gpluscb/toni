package com.github.gpluscb.toni.util.discord;

import com.github.gpluscb.toni.util.Constants;
import com.github.gpluscb.toni.util.FailLogger;
import com.github.gpluscb.toni.util.MiscUtil;
import com.github.gpluscb.toni.util.OneOfTwo;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Button;
import net.dv8tion.jda.api.interactions.components.ButtonStyle;
import net.dv8tion.jda.api.interactions.components.Component;
import net.dv8tion.jda.api.requests.ErrorResponse;
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
import java.util.stream.Stream;

/**
 * We override the validation to only allow specific users in users.
 */
public class ButtonActionMenu extends ActionMenu {
    private static final Logger log = LogManager.getLogger(ButtonActionMenu.class);

    @Nonnull
    private final Set<Long> users;

    @Nonnull
    private final List<ActionRow> actionRows;
    @Nonnull
    private final Map<String, Function<ButtonClickEvent, OneOfTwo<Message, MenuAction>>> buttonActions;
    @Nonnull
    private final Message start;
    @Nullable
    private final Button deletionButton;
    @Nonnull
    private final BiConsumer<MessageChannel, Long> timeoutAction;

    public ButtonActionMenu(@Nonnull EventWaiter waiter, @Nonnull Set<Long> users, long timeout, @Nonnull TimeUnit unit, @Nonnull List<RegisteredButton> buttons, @Nonnull Message start, @Nullable Button deletionButton, @Nonnull BiConsumer<MessageChannel, Long> timeoutAction) {
        super(waiter, timeout, unit);
        this.users = users;

        Stream<Button> buttonsStream = buttons.stream()
                .filter(RegisteredButton::isDisplayInitially)
                .map(RegisteredButton::getButton);

        if (deletionButton != null) buttonsStream = Stream.concat(buttonsStream, Stream.of(deletionButton));

        List<Button> buttonsToAdd = buttonsStream.collect(Collectors.toList());

        if (buttonsToAdd.stream().anyMatch(button -> button.getStyle() == ButtonStyle.LINK))
            throw new IllegalStateException("Buttons may not be link buttons");

        // Multiple ActionRows in case of > 5 buttons
        List<List<Button>> splitButtonsToAdd = MiscUtil.splitList(buttonsToAdd, Component.Type.BUTTON.getMaxPerRow());
        actionRows = splitButtonsToAdd.stream().map(ActionRow::of).collect(Collectors.toList());

        // e.getKey.getId() cannot return null here since we don't allow link buttons
        this.buttonActions = buttons
                .stream()
                .collect(Collectors.toMap(reg -> reg.getButton().getId(), RegisteredButton::getOnClick));

        this.start = start;
        this.deletionButton = deletionButton;
        this.timeoutAction = timeoutAction;
    }

    @Override
    public void display(@Nonnull MessageChannel channel) {
        init(channel.sendMessage(start));
    }

    @Override
    public void display(@Nonnull MessageChannel channel, long messageId) {
        init(channel.editMessageById(messageId, start));
    }

    @Override
    public void displaySlashReplying(@Nonnull SlashCommandEvent e) {
        e.reply(start).addActionRows(actionRows).flatMap(InteractionHook::retrieveOriginal).queue(this::awaitEvents);
    }

    @Override
    public void displayDeferredReplying(@Nonnull InteractionHook hook) {
        hook.sendMessage(start).addActionRows(actionRows).queue(this::awaitEvents);
    }

    @Override
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

    private void init(@Nonnull MessageAction messageAction) {
        messageAction.setActionRows(actionRows).queue(this::awaitEvents);
    }

    private void awaitEvents(@Nonnull Message message) {
        awaitEvents(message.getJDA(), message.getIdLong(), message.getChannel().getIdLong());
    }

    private void awaitEvents(@Nonnull JDA jda, long messageId, long channelId) {
        getWaiter().waitForEvent(ButtonClickEvent.class,
                e -> checkButtonClick(e, messageId),
                this::handleButtonClick,
                getTimeout(), getUnit(), FailLogger.logFail(() -> { // This is the only thing that will be executed on not JDA-WS thread. So exceptions may get swallowed
                    MessageChannel channel = jda.getTextChannelById(channelId);
                    if (channel == null) channel = jda.getPrivateChannelById(channelId);
                    timeoutAction.accept(channel, messageId);
                    if (channel == null) log.warn("MessageChannel for timeoutAction not in cache for timeoutAction");
                }));
    }

    private boolean isValidUser(long user) {
        return users.isEmpty() || users.contains(user);
    }

    private boolean checkButtonClick(@Nonnull ButtonClickEvent e, long messageId) {
        if (e.getMessageIdLong() != messageId) return false;
        if (!isValidUser(e.getUser().getIdLong())) return false;

        String buttonId = e.getComponentId();
        return buttonActions.containsKey(buttonId) ||
                (deletionButton != null && buttonId.equals(deletionButton.getId()));
    }

    private void handleButtonClick(@Nonnull ButtonClickEvent e) {
        String buttonId = e.getComponentId();

        long messageId = e.getMessageIdLong();
        MessageChannel channel = e.getChannel();
        if (deletionButton != null && buttonId.equals(deletionButton.getId())) {
            channel.deleteMessageById(e.getMessageId()).queue();
            return;
        }

        OneOfTwo<Message, MenuAction> action = buttonActions.get(buttonId).apply(e);
        action
                .onT(edited -> channel.editMessageById(messageId, edited).queue(this::awaitEvents))
                .onU(otherAction -> {
                    switch (otherAction) {
                        case NOTHING:
                            awaitEvents(e.getJDA(), messageId, channel.getIdLong());
                            break;
                        case CANCEL:
                            break;
                        default:
                            throw new IllegalStateException("Non exhaustive switch over MenuAction");
                    }
                });
    }

    public static class RegisteredButton {
        @Nonnull
        private final Button button;
        @Nonnull
        private final Function<ButtonClickEvent, OneOfTwo<Message, MenuAction>> onClick;
        private final boolean displayInitially;

        public RegisteredButton(@Nonnull Button button, @Nonnull Function<ButtonClickEvent, OneOfTwo<Message, MenuAction>> onClick, boolean displayInitially) {
            this.button = button;
            this.onClick = onClick;
            this.displayInitially = displayInitially;
        }

        @Nonnull
        public Button getButton() {
            return button;
        }

        @Nonnull
        public Function<ButtonClickEvent, OneOfTwo<Message, MenuAction>> getOnClick() {
            return onClick;
        }

        public boolean isDisplayInitially() {
            return displayInitially;
        }
    }

    public enum MenuAction {
        NOTHING,
        /**
         * Does not remove the buttons
         */
        CANCEL
    }

    public static class Builder extends ActionMenu.Builder<Builder, ButtonActionMenu> {
        @Nonnull
        private final Set<Long> users;
        @Nonnull
        private final List<RegisteredButton> buttons;
        @Nullable
        private Message start;
        @Nullable
        private Button deletionButton;
        @Nullable
        private BiConsumer<MessageChannel, Long> timeoutAction;

        /**
         * Default timeout of 20 minutes
         */
        public Builder() {
            super(Builder.class);

            users = new HashSet<>();
            buttons = new ArrayList<>();
            deletionButton = Button.danger("delete", Constants.CROSS_MARK);
            setTimeout(20, TimeUnit.MINUTES);
        }

        /**
         * If the user list ends up empty, everyone can use it
         */
        @Nonnull
        public Builder addUsers(long... users) {
            Arrays.stream(users).forEach(this.users::add);
            return this;
        }

        @Nonnull
        public synchronized Builder registerButton(@Nonnull Button button, @Nonnull Function<ButtonClickEvent, OneOfTwo<Message, MenuAction>> action) {
            return registerButton(button, action, true);
        }

        @Nonnull
        public synchronized Builder registerButton(@Nonnull Button button, @Nonnull Function<ButtonClickEvent, OneOfTwo<Message, MenuAction>> action, boolean displayInitially) {
            return registerButton(new RegisteredButton(button, action, displayInitially));
        }

        @Nonnull
        public synchronized Builder registerButton(@Nonnull RegisteredButton reg) {
            buttons.add(reg);
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
        public Builder setDeletionButton(@Nullable Button deletionButton) {
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

        @Nonnull
        public Set<Long> getUsers() {
            return users;
        }

        @Nonnull
        public List<RegisteredButton> getButtons() {
            return buttons;
        }

        @Nullable
        public Message getStart() {
            return start;
        }

        @Nullable
        public Button getDeletionButton() {
            return deletionButton;
        }

        @Nullable
        public BiConsumer<MessageChannel, Long> getTimeoutAction() {
            return timeoutAction;
        }

        /**
         * @throws IllegalStateException if waiter or start is not set, or if super.users contains stuff to prevent accidents
         */
        @Nonnull
        @Override
        public synchronized ButtonActionMenu build() {
            preBuild();
            if (start == null) throw new IllegalStateException("Start must be set");

            if (timeoutAction == null) {
                timeoutAction = (channel, id) -> {
                    if (channel == null) return;
                    if (channel instanceof TextChannel) {
                        TextChannel textChannel = (TextChannel) channel;
                        if (!textChannel.getGuild().getSelfMember().hasPermission(textChannel, Permission.MESSAGE_HISTORY))
                            return;
                    }

                    channel.retrieveMessageById(id)
                            .flatMap(m -> m.editMessage(m).setActionRows())
                            .queue(null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_MESSAGE));
                };
            }

            // preBuild checks for null on waiter
            //noinspection ConstantConditions
            return new ButtonActionMenu(getWaiter(), users, getTimeout(), getUnit(), buttons, start, deletionButton, timeoutAction);
        }
    }
}
