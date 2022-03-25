package com.github.gpluscb.toni.menu;

import com.github.gpluscb.toni.util.FailLogger;
import com.github.gpluscb.toni.util.MiscUtil;
import com.github.gpluscb.toni.util.PairNonnull;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.SelectMenuInteractionEvent;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.selections.SelectMenu;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.restaction.MessageAction;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class SelectionActionMenu extends ActionMenu {
    @Nonnull
    private final Settings settings;

    @Nonnull
    private final Map<String, BiFunction<SelectionInfo, SelectMenuInteractionEvent, MenuAction>> selectionActions;

    public SelectionActionMenu(@Nonnull Settings settings) {
        super(settings.actionMenuSettings());

        this.settings = settings;

        this.selectionActions = settings.selectionActions()
                .stream()
                .map(pair -> pair.mapT(SelectOption::getValue))
                .collect(Collectors.toMap(PairNonnull::getT, PairNonnull::getU));
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
    public void displaySlashReplying(@Nonnull SlashCommandInteractionEvent event) {
        SelectMenu selectionMenu = initSelectionMenu();
        event.reply(settings.start()).addActionRow(selectionMenu).flatMap(InteractionHook::retrieveOriginal).queue(this::start);
    }

    @Override
    public void displayDeferredReplying(@Nonnull InteractionHook hook) {
        SelectMenu selectionMenu = initSelectionMenu();
        hook.sendMessage(settings.start()).addActionRow(selectionMenu).queue(this::start);
    }

    @Override
    public void displayReplying(@Nonnull MessageChannel channel, long messageId) {
        boolean hasPerms = true;
        if (channel instanceof TextChannel textChannel) {
            hasPerms = textChannel.getGuild().getSelfMember().hasPermission(textChannel, Permission.MESSAGE_HISTORY);
        }

        if (hasPerms)
            init(channel.sendMessage(settings.start()).referenceById(messageId));
        else
            init(channel.sendMessage(settings.start()));
    }

    @Nonnull
    @Override
    public List<ActionRow> getComponents() {
        return Collections.singletonList(ActionRow.of(SelectMenu.create(settings.id()).addOptions(getInitialSelectOptions()).build()));
    }

    @Override
    public void start(@Nonnull Message message) {
        setMessageInfo(message);
        awaitEvents();
    }

    private void init(@Nonnull MessageAction messageAction) {
        SelectMenu selectionMenu = SelectMenu.create(settings.id()).addOptions(getInitialSelectOptions()).build();
        messageAction.setActionRow(selectionMenu).queue(this::start);
    }


    @Nonnull
    private SelectMenu initSelectionMenu() {
        return SelectMenu.create(settings.id()).addOptions(getInitialSelectOptions()).build();
    }

    private void awaitEvents() {
        getActionMenuSettings().waiter().waitForEvent(SelectMenuInteractionEvent.class,
                e -> checkSelection(e, getMessageId()),
                this::handleSelection,
                getActionMenuSettings().timeout(), getActionMenuSettings().unit(), FailLogger.logFail(() -> { // This is the only thing that will be executed on not JDA-WS thread. So exceptions may get swallowed
                    settings.onTimeout().accept(new SelectionMenuTimeoutEvent());
                }));
    }

    private boolean checkSelection(@Nonnull SelectMenuInteractionEvent e, long messageId) {
        return e.getMessageIdLong() == messageId
                && e.getComponentId().equals(settings.id())
                && isValidUser(e.getUser().getIdLong());
    }

    private void handleSelection(@Nonnull SelectMenuInteractionEvent e) {
        // We require exactly one selection.
        String value = e.getValues().get(0);
        MenuAction action = selectionActions.get(value).apply(new SelectionInfo(), e);

        if (action == MenuAction.CONTINUE) awaitEvents();
    }

    private boolean isValidUser(long user) {
        return settings.users().isEmpty() || settings.users().contains(user);
    }

    @Nonnull
    public List<SelectOption> getInitialSelectOptions() {
        return settings.selectionActions().stream()
                .map(PairNonnull::getT)
                .toList();
    }

    @Nonnull
    public Settings getSelectionActionMenuSettings() {
        return settings;
    }

    private abstract class SelectionStateInfo extends MenuStateInfo {
        @Nonnull
        public List<SelectOption> getInitialSelectOptions() {
            return SelectionActionMenu.this.getInitialSelectOptions();
        }

        @Nonnull
        public Settings getSelectionActionMenuSettings() {
            return SelectionActionMenu.this.getSelectionActionMenuSettings();
        }
    }

    public class SelectionInfo extends SelectionStateInfo {
    }

    public class SelectionMenuTimeoutEvent extends SelectionStateInfo {
    }

    public record Settings(@Nonnull ActionMenu.Settings actionMenuSettings, @Nonnull Set<Long> users,
                           @Nonnull List<PairNonnull<SelectOption, BiFunction<SelectionInfo, SelectMenuInteractionEvent, MenuAction>>> selectionActions,
                           @Nonnull Message start, @Nonnull String id,
                           @Nonnull Consumer<SelectionMenuTimeoutEvent> onTimeout) {
        @Nonnull
        public static final Supplier<String> DEFAULT_ID_GENERATOR = () -> MiscUtil.randomString(5);
        @Nonnull
        public static final Consumer<SelectionMenuTimeoutEvent> DEFAULT_ON_TIMEOUT = timeout -> {
            MessageChannel channel = timeout.getChannel();
            if (channel == null) return;
            if (channel instanceof TextChannel textChannel) {
                if (!textChannel.getGuild().getSelfMember().hasPermission(textChannel, Permission.MESSAGE_HISTORY))
                    return;
            }

            channel.retrieveMessageById(timeout.getMessageId())
                    .flatMap(m -> m.editMessage(m).setActionRows())
                    .queue(null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_MESSAGE));
        };

        public static class Builder {
            @Nullable
            private ActionMenu.Settings actionMenuSettings;
            @Nonnull
            private Set<Long> users = new HashSet<>();
            @Nonnull
            private final List<PairNonnull<SelectOption, BiFunction<SelectionInfo, SelectMenuInteractionEvent, MenuAction>>> selectionActions = new ArrayList<>();
            @Nullable
            private Message start;
            @Nonnull
            private String id = DEFAULT_ID_GENERATOR.get();
            @Nonnull
            private Consumer<SelectionMenuTimeoutEvent> onTimeout = DEFAULT_ON_TIMEOUT;

            public Builder setActionMenuSettings(@Nonnull ActionMenu.Settings actionMenuSettings) {
                this.actionMenuSettings = actionMenuSettings;
                return this;
            }

            @Nonnull
            public Builder addUsers(Long... users) {
                this.users.addAll(Arrays.asList(users));
                return this;
            }

            @Nonnull
            public Builder setUsers(@Nonnull Set<Long> users) {
                this.users = users;
                return this;
            }

            /**
             * @param action If action returns null, the message is not edited
             * @throws IllegalArgumentException if option is already registered
             */
            @Nonnull
            public Builder registerOption(@Nonnull SelectOption option, @Nonnull BiFunction<SelectionInfo, SelectMenuInteractionEvent, MenuAction> action) {
                if (selectionActions.stream().map(PairNonnull::getT).anyMatch(option::equals))
                    throw new IllegalArgumentException("Option already registered");
                selectionActions.add(new PairNonnull<>(option, action));
                return this;
            }

            @Nonnull
            public Builder setStart(@Nonnull Message start) {
                this.start = start;
                return this;
            }

            /**
             * Default: Random 5-character String
             */
            @Nonnull
            public Builder setId(@Nonnull String id) {
                this.id = id;
                return this;
            }

            /**
             * MessageChannel may be null on timeout in weird cases
             */
            @Nonnull
            public Builder setOnTimeout(@Nonnull Consumer<SelectionMenuTimeoutEvent> onTimeout) {
                this.onTimeout = onTimeout;
                return this;
            }

            @Nonnull
            public synchronized Settings build() {
                if (actionMenuSettings == null) throw new IllegalArgumentException("ActionMenuSettings must be set");
                if (start == null) throw new IllegalStateException("Start must be set");

                return new Settings(actionMenuSettings, users, selectionActions, start, id, onTimeout);
            }
        }
    }
}
