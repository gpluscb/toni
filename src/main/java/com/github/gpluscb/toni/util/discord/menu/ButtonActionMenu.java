package com.github.gpluscb.toni.util.discord.menu;

import com.github.gpluscb.toni.util.Constants;
import com.github.gpluscb.toni.util.FailLogger;
import com.github.gpluscb.toni.util.MiscUtil;
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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ButtonActionMenu extends ActionMenu {
    @Nonnull
    private final Settings settings;

    @Nonnull
    private final List<ActionRow> initialActionRows;
    @Nonnull
    private final Map<String, Function<ButtonClickEvent, MenuAction>> buttonActions;

    public ButtonActionMenu(@Nonnull Settings settings) {
        super(settings.settings());

        this.settings = settings;

        List<RegisteredButton> buttons = settings.buttons();
        Stream<Button> buttonsStream = buttons.stream()
                .filter(RegisteredButton::displayInitially)
                .map(RegisteredButton::button);

        Button deletionButton = settings.deletionButton();
        if (deletionButton != null) buttonsStream = Stream.concat(buttonsStream, Stream.of(deletionButton));

        List<Button> buttonsToAdd = buttonsStream.toList();

        if (buttonsToAdd.stream().anyMatch(button -> button.getStyle() == ButtonStyle.LINK))
            throw new IllegalStateException("Buttons may not be link buttons");

        // Multiple ActionRows in case of > 5 buttons
        List<List<Button>> splitButtonsToAdd = MiscUtil.splitList(buttonsToAdd, Component.Type.BUTTON.getMaxPerRow());
        initialActionRows = splitButtonsToAdd.stream().map(ActionRow::of).toList();

        // e.getKey.getId() cannot return null here since we don't allow link buttons
        this.buttonActions = buttons
                .stream()
                .collect(Collectors.toMap(reg -> reg.button().getId(), RegisteredButton::onClick));
    }

    @Override
    public void display(@Nonnull MessageChannel channel) {
        init(channel.sendMessage(settings.start()));
    }

    @Override
    public void display(@Nonnull MessageChannel channel, long messageId) {
        init(channel.editMessageById(messageId, settings.start()));
    }

    @Override
    public void displaySlashReplying(@Nonnull SlashCommandEvent e) {
        e.reply(settings.start()).addActionRows(initialActionRows).flatMap(InteractionHook::retrieveOriginal).queue(this::initWithMessage);
    }

    @Override
    public void displayDeferredReplying(@Nonnull InteractionHook hook) {
        hook.sendMessage(settings.start()).addActionRows(initialActionRows).queue(this::initWithMessage);
    }

    @Override
    public void displayReplying(@Nonnull MessageChannel channel, long messageId) {
        boolean hasPerms = true;
        if (channel instanceof TextChannel textChannel) {
            hasPerms = textChannel.getGuild().getSelfMember().hasPermission(textChannel, Permission.MESSAGE_HISTORY);
        }

        Message start = settings.start();
        if (hasPerms)
            init(channel.sendMessage(start).referenceById(messageId));
        else
            init(channel.sendMessage(start));
    }

    private void init(@Nonnull MessageAction messageAction) {
        messageAction.setActionRows(initialActionRows).queue(this::initWithMessage);
    }

    private void initWithMessage(@Nonnull Message message) {
        setMessageInfo(message);
        awaitEvents();
    }

    private void awaitEvents() {
        getActionMenuSettings().waiter().waitForEvent(
                ButtonClickEvent.class,
                e -> checkButtonClick(e, getMessageId()),
                this::handleButtonClick,
                getActionMenuSettings().timeout(),
                getActionMenuSettings().unit(),
                FailLogger.logFail(() -> { // This is the only thing that will be executed on not JDA-WS thread. So exceptions may get swallowed
                    settings.onTimeout().accept(new ButtonActionMenuTimeoutEvent());
                })
        );
    }

    private boolean isValidUser(long user) {
        Set<Long> users = settings.users();
        return users.isEmpty() || users.contains(user);
    }

    private boolean checkButtonClick(@Nonnull ButtonClickEvent e, long messageId) {
        if (e.getMessageIdLong() != messageId) return false;
        if (!isValidUser(e.getUser().getIdLong())) return false;

        String buttonId = e.getComponentId();
        Button deletionButton = settings.deletionButton();
        return buttonActions.containsKey(buttonId) ||
                (deletionButton != null && buttonId.equals(deletionButton.getId()));
    }

    private void handleButtonClick(@Nonnull ButtonClickEvent e) {
        String buttonId = e.getComponentId();
        Button deletionButton = settings.deletionButton();
        if (deletionButton != null && buttonId.equals(deletionButton.getId())) {
            e.getMessage().delete().queue();
            return;
        }

        MenuAction action = buttonActions.get(buttonId).apply(e);

        if (action == MenuAction.CONTINUE) awaitEvents();
    }

    @Nonnull
    public List<ActionRow> getInitialActionRows() {
        return initialActionRows;
    }

    @Nonnull
    public Settings getButtonActionMenuSettings() {
        return settings;
    }

    public class ButtonActionMenuTimeoutEvent extends MenuStateInfo {
        @Nonnull
        public Settings getButtonActionMenuSettings() {
            return settings;
        }
    }

    public record RegisteredButton(@Nonnull Button button,
                                   @Nonnull Function<ButtonClickEvent, MenuAction> onClick,
                                   boolean displayInitially) {
        public RegisteredButton(@Nonnull Button button, @Nonnull Function<ButtonClickEvent, MenuAction> onClick, boolean displayInitially) {
            this.button = button;
            this.onClick = onClick;
            this.displayInitially = displayInitially;
        }
    }

    public record Settings(@Nonnull ActionMenu.Settings settings, @Nonnull Set<Long> users,
                           @Nonnull List<RegisteredButton> buttons, @Nonnull Message start,
                           @Nullable Button deletionButton,
                           @Nonnull Consumer<ButtonActionMenuTimeoutEvent> onTimeout) {
        @Nonnull
        public static final Button DEFAULT_DELETION_BUTTON = Button.danger("delete", Constants.CROSS_MARK);
        @Nonnull
        public static final Consumer<ButtonActionMenuTimeoutEvent> DEFAULT_ON_TIMEOUT = event -> {
            MessageChannel channel = event.getChannel();
            if (channel == null) return;
            if (channel instanceof TextChannel textChannel) {
                if (!textChannel.getGuild().getSelfMember().hasPermission(textChannel, Permission.MESSAGE_HISTORY))
                    return;
            }

            channel.retrieveMessageById(event.getMessageId())
                    .flatMap(m -> m.editMessage(m).setActionRows())
                    .queue(null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_MESSAGE));
        };

        public static class Builder {
            @Nullable
            private ActionMenu.Settings actionMenuSettings;
            @Nonnull
            private Set<Long> users = new HashSet<>();
            @Nonnull
            private List<RegisteredButton> buttons = new ArrayList<>();
            @Nullable
            private Message start;
            @Nullable
            private Button deletionButton = DEFAULT_DELETION_BUTTON;
            @Nonnull
            private Consumer<ButtonActionMenuTimeoutEvent> onTimeout = DEFAULT_ON_TIMEOUT;

            @Nonnull
            public Builder setActionMenuSettings(@Nullable ActionMenu.Settings actionMenuSettings) {
                this.actionMenuSettings = actionMenuSettings;
                return this;
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
            public Builder setUsers(@Nonnull Set<Long> users) {
                this.users = users;
                return this;
            }

            @Nonnull
            public Builder registerButton(@Nonnull Button button, @Nonnull Function<ButtonClickEvent, MenuAction> function) {
                return registerButton(button, function, true);
            }

            @Nonnull
            public Builder registerButton(@Nonnull Button button, @Nonnull Function<ButtonClickEvent, MenuAction> function, boolean displayInitially) {
                return registerButton(new RegisteredButton(button, function, displayInitially));
            }

            @Nonnull
            public Builder registerButton(@Nonnull RegisteredButton button) {
                buttons.add(button);
                return this;
            }

            @Nonnull
            public Builder setButtons(@Nonnull List<RegisteredButton> buttons) {
                this.buttons = buttons;
                return this;
            }

            @Nonnull
            public Builder setStart(@Nonnull Message start) {
                this.start = start;
                return this;
            }

            @Nonnull
            public Builder setDeletionButton(@Nullable Button deletionButton) {
                this.deletionButton = deletionButton;
                return this;
            }

            @Nonnull
            public Builder setOnTimeout(@Nonnull Consumer<ButtonActionMenuTimeoutEvent> onTimeout) {
                this.onTimeout = onTimeout;
                return this;
            }

            @Nonnull
            public Settings build() {
                if (actionMenuSettings == null) throw new IllegalStateException("ActionMenuSettings must be set");
                if (start == null) throw new IllegalStateException("Start must be set");

                return new Settings(actionMenuSettings, users, buttons, start, deletionButton, onTimeout);
            }
        }
    }
}
