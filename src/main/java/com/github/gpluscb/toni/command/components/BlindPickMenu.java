package com.github.gpluscb.toni.command.components;

import com.github.gpluscb.toni.util.discord.menu.ActionMenu;
import com.github.gpluscb.toni.util.discord.ChannelChoiceWaiter;
import com.github.gpluscb.toni.util.smash.Character;
import com.github.gpluscb.toni.util.smash.CharacterTree;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.RestAction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class BlindPickMenu extends ActionMenu {
    private static final Logger log = LogManager.getLogger(BlindPickMenu.class);

    @Nonnull
    private final ChannelChoiceWaiter waiter;
    @Nonnull
    private final List<Long> users;
    @Nonnull
    private final Message start;
    @Nonnull
    private final List<Character> characters;
    @Nonnull
    private final Consumer<BlindPickResult> onResult;
    @Nonnull
    private final Consumer<BlindPickTimeoutEvent> onTimeout;
    @Nonnull
    private final Runnable onFailedInit;

    public BlindPickMenu(@Nonnull ChannelChoiceWaiter waiter, @Nonnull List<Long> users, long timeout, @Nonnull TimeUnit unit, @Nonnull Message start, @Nonnull List<Character> characters, @Nonnull Consumer<BlindPickResult> onResult, @Nonnull Consumer<BlindPickTimeoutEvent> onTimeout, @Nonnull Runnable onFailedInit) {
        super(waiter.getEventWaiter(), timeout, unit);
        this.waiter = waiter;
        this.users = users;
        this.start = start;
        this.characters = characters;
        this.onResult = onResult;
        this.onTimeout = onTimeout;
        this.onFailedInit = onFailedInit;
    }

    @Override
    public void display(@Nonnull MessageChannel channel) {
        initWithMessageAction(channel.sendMessage(start));
    }

    @Override
    public void display(@Nonnull MessageChannel channel, long messageId) {
        initWithMessageAction(channel.editMessageById(messageId, start));
    }

    @Override
    public void displayReplying(@Nonnull MessageChannel channel, long messageId) {
        // TODO: MESSAGE_HISTORY
        initWithMessageAction(channel.sendMessage(start).referenceById(messageId));
    }

    @Override
    public void displaySlashReplying(@Nonnull SlashCommandEvent event) {
        initWithMessageAction(event.reply(start).flatMap(InteractionHook::retrieveOriginal));
    }

    @Override
    public void displayDeferredReplying(@Nonnull InteractionHook hook) {
        initWithMessageAction(hook.sendMessage(start));
    }

    private void initWithMessageAction(@Nonnull RestAction<Message> action) {
        ChannelChoiceWaiter.WaitChoiceHandle handle = waiter.waitForDMChoice(
                users,
                true,
                this::verifyChoice,
                choices -> onResult.accept(new BlindPickResult(choices)),
                getTimeout(), getUnit(),
                choices -> onTimeout.accept(new BlindPickTimeoutEvent(choices))
        );

        if (handle == null) {
            onFailedInit.run();
            return;
        }

        action.queue(message -> {
            setMessageInfo(message);
            handle.startListening();
        }, t -> {
            log.catching(t);
            handle.cancel();
        });
    }

    @Nonnull
    private Optional<Character> verifyChoice(@Nonnull MessageReceivedEvent event) {
        Message message = event.getMessage();
        String choice = message.getContentRaw();

        Character character = characters.stream().filter(c -> c.getAltNames().contains(choice.toLowerCase())).findAny().orElse(null);
        if (character == null) message.reply("I don't know that character.").queue();
        else message.reply("Accepted!").queue();

        return Optional.ofNullable(character);
    }

    private abstract class BlindPickMenuStateInfo extends MenuStateInfo {
        @Nonnull
        public List<Long> getUsers() {
            return users;
        }
    }

    public class BlindPickResult extends BlindPickMenuStateInfo {
        @Nonnull
        private final List<ChannelChoiceWaiter.UserChoiceInfo<Character>> picks;

        public BlindPickResult(@Nonnull List<ChannelChoiceWaiter.UserChoiceInfo<Character>> picks) {
            this.picks = picks;
        }

        @Nonnull
        public List<ChannelChoiceWaiter.UserChoiceInfo<Character>> getPicks() {
            return picks;
        }
    }

    public class BlindPickTimeoutEvent extends BlindPickMenuStateInfo {
        @Nonnull
        private final List<ChannelChoiceWaiter.UserChoiceInfo<Character>> picksSoFar;

        public BlindPickTimeoutEvent(@Nonnull List<ChannelChoiceWaiter.UserChoiceInfo<Character>> picksSoFar) {
            this.picksSoFar = picksSoFar;
        }

        @Nonnull
        public List<ChannelChoiceWaiter.UserChoiceInfo<Character>> getPicksSoFar() {
            return picksSoFar;
        }
    }

    public static class Builder extends ActionMenu.Builder<Builder, BlindPickMenu> {
        @Nullable
        private ChannelChoiceWaiter channelWaiter;
        @Nonnull
        private List<Long> users;
        @Nullable
        private Message start;
        @Nullable
        private List<Character> characters;
        @Nonnull
        private Consumer<BlindPickResult> onResult;
        @Nonnull
        private Consumer<BlindPickTimeoutEvent> onTimeout;
        @Nonnull
        private Runnable onFailedInit;

        public Builder() {
            super(Builder.class);

            users = new ArrayList<>();
            onResult = result -> {
            };
            onTimeout = timeout -> {
            };
            onFailedInit = () -> {
            };
        }

        @Nonnull
        public Builder setChannelWaiter(@Nonnull ChannelChoiceWaiter channelWaiter) {
            this.channelWaiter = channelWaiter;
            return setWaiter(channelWaiter.getEventWaiter());
        }

        @Nonnull
        public Builder setUsers(@Nonnull List<Long> users) {
            this.users = users;
            return this;
        }

        @Nonnull
        public Builder setStart(@Nonnull Message start) {
            this.start = start;
            return this;
        }

        @Nonnull
        public Builder setCharacters(@Nonnull List<Character> characters) {
            this.characters = characters;
            return this;
        }

        @Nonnull
        public Builder setCharacterTree(@Nonnull CharacterTree characterTree) {
            characters = characterTree.getAllCharacters();
            return this;
        }

        @Nonnull
        public Builder setOnResult(@Nonnull Consumer<BlindPickResult> onResult) {
            this.onResult = onResult;
            return this;
        }

        @Nonnull
        public Builder setOnTimeout(@Nonnull Consumer<BlindPickTimeoutEvent> onTimeout) {
            this.onTimeout = onTimeout;
            return this;
        }

        @Nonnull
        public Builder setOnFailedInit(@Nonnull Runnable onFailedInit) {
            this.onFailedInit = onFailedInit;
            return this;
        }

        @Nullable
        public ChannelChoiceWaiter getChannelWaiter() {
            return channelWaiter;
        }

        @Nonnull
        public List<Long> getUsers() {
            return users;
        }

        @Nullable
        public Message getStart() {
            return start;
        }

        @Nullable
        public List<Character> getCharacters() {
            return characters;
        }

        @Nonnull
        public Consumer<BlindPickResult> getOnResult() {
            return onResult;
        }

        @Nonnull
        public Consumer<BlindPickTimeoutEvent> getOnTimeout() {
            return onTimeout;
        }

        @Nonnull
        public Runnable getOnFailedInit() {
            return onFailedInit;
        }

        @Nonnull
        @Override
        public BlindPickMenu build() {
            preBuild();

            if (channelWaiter == null) throw new IllegalStateException("ChannelWaiter must be set");
            if (start == null) throw new IllegalStateException("Start must be set");
            if (characters == null) throw new IllegalStateException("Characters must be set");

            return new BlindPickMenu(channelWaiter, users, getTimeout(), getUnit(), start, characters, onResult, onTimeout, onFailedInit);
        }
    }
}
