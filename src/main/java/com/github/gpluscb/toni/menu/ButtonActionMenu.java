package com.github.gpluscb.toni.menu;

import com.github.gpluscb.toni.util.Constants;
import com.github.gpluscb.toni.util.FailLogger;
import com.github.gpluscb.toni.util.MiscUtil;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.interactions.components.Component;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import net.dv8tion.jda.api.utils.messages.MessageRequest;

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
    private final Map<String, Function<ButtonInteractionEvent, MenuAction>> buttonActions;

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
        init(channel.editMessageById(messageId, MessageEditData.fromCreateData(settings.start())));
    }

    @Override
    public void displaySlashReplying(@Nonnull SlashCommandInteractionEvent e) {
        e.reply(settings.start()).addComponents(initialActionRows).flatMap(InteractionHook::retrieveOriginal).queue(this::start);
    }

    @Override
    public void displayDeferredReplying(@Nonnull InteractionHook hook) {
        init(hook.sendMessage(settings.start()));
    }

    @Override
    public void displayReplying(@Nonnull MessageChannel channel, long messageId) {
        boolean hasPerms = true;
        if (channel instanceof TextChannel textChannel) {
            hasPerms = textChannel.getGuild().getSelfMember().hasPermission(textChannel, Permission.MESSAGE_HISTORY);
        }

        MessageCreateData start = settings.start();
        if (hasPerms)
            init(channel.sendMessage(start).setMessageReference(messageId));
        else
            init(channel.sendMessage(start));
    }

    @Nonnull
    @Override
    public List<ActionRow> getComponents() {
        return initialActionRows;
    }

    @Override
    public void start(@Nonnull Message message) {
        setMessageInfo(message);
        awaitEvents();
    }

    private <T extends MessageRequest<R>, R extends RestAction<Message> & MessageRequest<R>> void init(@Nonnull T messageAction) {
        messageAction.setComponents(initialActionRows).queue(this::start);
    }

    private void awaitEvents() {
        getActionMenuSettings().waiter().waitForEvent(
                ButtonInteractionEvent.class,
                e -> {
                    if (checkButtonClick(e, getMessageId())) {
                        // Stop awaiting on CANCEL
                        return handleButtonClick(e) == MenuAction.CANCEL;
                    }
                    // Return false to endlessly keep awaiting until timeout
                    return false;
                },
                MiscUtil.emptyConsumer(),
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

    private boolean checkButtonClick(@Nonnull ButtonInteractionEvent e, long messageId) {
        if (e.getMessageIdLong() != messageId) return false;

        String buttonId = e.getComponentId();
        Button deletionButton = settings.deletionButton();

        if (!buttonActions.containsKey(buttonId) &&
                !(deletionButton != null && buttonId.equals(deletionButton.getId()))) {
            return false;
        }

        if (!isValidUser(e.getUser().getIdLong())) {
            e.reply("You cannot use this interaction.").setEphemeral(true).queue();
            return false;
        }

        return true;
    }

    @Nonnull
    private MenuAction handleButtonClick(@Nonnull ButtonInteractionEvent e) {
        String buttonId = e.getComponentId();
        Button deletionButton = settings.deletionButton();
        if (deletionButton != null && buttonId.equals(deletionButton.getId())) {
            e.getMessage().delete().queue();
            return MenuAction.CANCEL;
        }

        return buttonActions.get(buttonId).apply(e);
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
                                   @Nonnull Function<ButtonInteractionEvent, MenuAction> onClick,
                                   boolean displayInitially) {
        public RegisteredButton(@Nonnull Button button, @Nonnull Function<ButtonInteractionEvent, MenuAction> onClick, boolean displayInitially) {
            this.button = button;
            this.onClick = onClick;
            this.displayInitially = displayInitially;
        }
    }

    public record Settings(@Nonnull ActionMenu.Settings settings, @Nonnull Set<Long> users,
                           @Nonnull List<RegisteredButton> buttons, @Nonnull MessageCreateData start,
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
                    .flatMap(m -> m.editMessage(MessageEditData.fromMessage(m)).setComponents())
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
            private MessageCreateData start;
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
            public Builder registerButton(@Nonnull Button button, @Nonnull Function<ButtonInteractionEvent, MenuAction> function) {
                return registerButton(button, function, true);
            }

            @Nonnull
            public Builder registerButton(@Nonnull Button button, @Nonnull Function<ButtonInteractionEvent, MenuAction> function, boolean displayInitially) {
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
            public Builder setStart(@Nonnull MessageCreateData start) {
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
