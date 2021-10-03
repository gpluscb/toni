package com.github.gpluscb.toni.command.components;

import com.github.gpluscb.toni.util.discord.ActionMenu;
import com.github.gpluscb.toni.util.discord.DMChoiceWaiter;
import com.github.gpluscb.toni.util.smash.Character;
import com.github.gpluscb.toni.util.smash.CharacterTree;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.events.message.priv.PrivateMessageReceivedEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class BlindPickMenu extends ActionMenu {
    @Nonnull
    private final DMChoiceWaiter dmWaiter;
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

    public BlindPickMenu(@Nonnull DMChoiceWaiter dmWaiter, @Nonnull List<Long> users, long timeout, @Nonnull TimeUnit unit, @Nonnull Message start, @Nonnull List<Character> characters, @Nonnull Consumer<BlindPickResult> onResult, @Nonnull Consumer<BlindPickTimeoutEvent> onTimeout, @Nonnull Runnable onFailedInit) {
        super(dmWaiter.getEventWaiter(), timeout, unit);
        this.dmWaiter = dmWaiter;
        this.users = users;
        this.start = start;
        this.characters = characters;
        this.onResult = onResult;
        this.onTimeout = onTimeout;
        this.onFailedInit = onFailedInit;
    }

    @Override
    public void display(@Nonnull MessageChannel channel) {
        if (initWaiter()) channel.sendMessage(start).queue();
    }

    @Override
    public void display(@Nonnull Message message) {
        if (initWaiter()) message.editMessage(start).queue();
    }

    @Override
    public void displayReplying(@Nonnull MessageChannel channel, long messageId) {
        // TODO: MESSAGE_HISTORY
        if (initWaiter()) channel.sendMessage(start).referenceById(messageId).queue();
    }

    @Override
    public void displaySlashReplying(@Nonnull SlashCommandEvent event) {
        if (initWaiter()) event.reply(start).queue();
    }

    @Override
    public void displayDeferredReplying(@Nonnull InteractionHook hook) {
        if (initWaiter()) hook.sendMessage(start).queue();
    }

    private boolean initWaiter() {
        boolean success = dmWaiter.waitForDMChoice(
                users,
                true,
                this::verifyChoice,
                map -> onResult.accept(new BlindPickResult(map)),
                getTimeout(), getUnit(),
                map -> onTimeout.accept(new BlindPickTimeoutEvent(map))
        );

        if (!success) onFailedInit.run();
        return success;
    }

    @Nonnull
    private Optional<Character> verifyChoice(@Nonnull PrivateMessageReceivedEvent event) {
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
        private final Map<Long, Character> picks;

        public BlindPickResult(@Nonnull Map<Long, Character> picks) {
            this.picks = picks;
        }

        @Nonnull
        public Map<Long, Character> getPicks() {
            return picks;
        }
    }

    public class BlindPickTimeoutEvent extends BlindPickMenuStateInfo {
        @Nonnull
        private final Map<Long, Character> picksSoFar;

        public BlindPickTimeoutEvent(@Nonnull Map<Long, Character> picksSoFar) {
            this.picksSoFar = picksSoFar;
        }

        @Nonnull
        public Map<Long, Character> getPicksSoFar() {
            return picksSoFar;
        }
    }

    public static class Builder extends ActionMenu.Builder<Builder, BlindPickMenu> {
        @Nullable
        private DMChoiceWaiter dmWaiter;
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
        public Builder setDmWaiter(@Nonnull DMChoiceWaiter dmWaiter) {
            this.dmWaiter = dmWaiter;
            return setWaiter(dmWaiter.getEventWaiter());
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
        public DMChoiceWaiter getDmWaiter() {
            return dmWaiter;
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
            if (dmWaiter == null) throw new IllegalStateException("DMWaiter must be set");
            if (start == null) throw new IllegalStateException("Start must be set");
            if (characters == null) throw new IllegalStateException("Characters must be set");

            return new BlindPickMenu(dmWaiter, users, getTimeout(), getUnit(), start, characters, onResult, onTimeout, onFailedInit);
        }
    }
}
