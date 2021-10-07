package com.github.gpluscb.toni.command.components;

import com.github.gpluscb.toni.util.discord.ActionMenu;
import com.github.gpluscb.toni.util.discord.ChannelChoiceWaiter;
import com.github.gpluscb.toni.util.smash.Character;
import com.github.gpluscb.toni.util.smash.CharacterTree;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class CharPickMenu extends ActionMenu {
    @Nonnull
    private final ChannelChoiceWaiter waiter;
    private final long user;
    private final long channelId;
    @Nonnull
    private final Message start;
    @Nonnull
    private final List<Character> characters;
    @Nonnull
    private final Consumer<CharPickResult> onResult;
    @Nonnull
    private final Consumer<CharPickTimeoutEvent> onTimeout;
    @Nonnull
    private final Runnable onFailedInit;

    public CharPickMenu(@Nonnull ChannelChoiceWaiter waiter, long timeout, @Nonnull TimeUnit unit, long user, long channelId, @Nonnull Message start, @Nonnull List<Character> characters, @Nonnull Consumer<CharPickResult> onResult, @Nonnull Consumer<CharPickTimeoutEvent> onTimeout, @Nonnull Runnable onFailedInit) {
        super(waiter.getEventWaiter(), timeout, unit);
        this.waiter = waiter;
        this.user = user;
        this.channelId = channelId;
        this.start = start;
        this.characters = characters;
        this.onResult = onResult;
        this.onTimeout = onTimeout;
        this.onFailedInit = onFailedInit;
    }

    @Override
    public void display(@Nonnull MessageChannel channel) {
        if (initWaiter(channelId)) channel.sendMessage(start).queue();
    }

    @Override
    public void display(@Nonnull Message message) {
        if (initWaiter(channelId)) message.editMessage(start).queue();
    }

    @Override
    public void displayReplying(@Nonnull MessageChannel channel, long messageId) {
        // TODO: MESSAGE_HISTORY
        if (initWaiter(channelId)) channel.sendMessage(start).referenceById(messageId).queue();
    }

    @Override
    public void displaySlashReplying(@Nonnull SlashCommandEvent event) {
        if (initWaiter(channelId)) event.reply(start).queue();
    }

    @Override
    public void displayDeferredReplying(@Nonnull InteractionHook hook) {
        if (initWaiter(channelId)) hook.sendMessage(start).queue();
    }

    private boolean initWaiter(long channelId) {
        boolean success = waiter.waitForChoice(
                Collections.singletonList(user),
                channelId,
                true,
                this::verifyChoice,
                this::onResult,
                getTimeout(), getUnit(),
                this::onTimeout
        );

        if (!success) onFailedInit.run();
        return success;
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

    private void onResult(@Nonnull List<ChannelChoiceWaiter.UserChoiceInfo<Character>> choices) {
        // Since we're done here it's nonnull time
        //noinspection ConstantConditions
        onResult.accept(new CharPickResult(choices.get(0).getChoice()));
    }

    private void onTimeout(@Nonnull List<ChannelChoiceWaiter.UserChoiceInfo<Character>> choices) {
        onTimeout.accept(new CharPickTimeoutEvent());
    }

    private abstract class CharPickMenuInfo extends MenuStateInfo {
        @Nonnull
        public ChannelChoiceWaiter getChannelWaiter() {
            return waiter;
        }

        public long getUser() {
            return user;
        }

        public long getChannelId() {
            return channelId;
        }

        @Nonnull
        public List<Character> getCharacters() {
            return characters;
        }
    }

    public class CharPickResult extends CharPickMenuInfo {
        @Nonnull
        private final Character pickedCharacter;

        public CharPickResult(@Nonnull Character pickedCharacter) {
            this.pickedCharacter = pickedCharacter;
        }

        @Nonnull
        public Character getPickedCharacter() {
            return pickedCharacter;
        }
    }

    public class CharPickTimeoutEvent extends CharPickMenuInfo {
    }

    public static class Builder extends ActionMenu.Builder<Builder, CharPickMenu> {
        @Nullable
        private ChannelChoiceWaiter channelWaiter;
        @Nullable
        private Long user;
        @Nullable
        private Long channelId;
        @Nullable
        private Message start;
        @Nullable
        private List<Character> characters;
        @Nonnull
        private Consumer<CharPickResult> onResult;
        @Nonnull
        private Consumer<CharPickTimeoutEvent> onTimeout;
        @Nonnull
        private Runnable onFailedInit;

        public Builder() {
            super(Builder.class);

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
        public Builder setUser(long user) {
            this.user = user;
            return this;
        }

        @Nonnull
        public Builder setChannelId(long channelId) {
            this.channelId = channelId;
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
        public Builder setOnResult(@Nonnull Consumer<CharPickResult> onResult) {
            this.onResult = onResult;
            return this;
        }

        @Nonnull
        public Builder setOnTimeout(@Nonnull Consumer<CharPickTimeoutEvent> onTimeout) {
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

        @Nullable
        public Long getUser() {
            return user;
        }

        @Nullable
        public Long getChannelId() {
            return channelId;
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
        public Consumer<CharPickResult> getOnResult() {
            return onResult;
        }

        @Nonnull
        public Consumer<CharPickTimeoutEvent> getOnTimeout() {
            return onTimeout;
        }

        @Nonnull
        public Runnable getOnFailedInit() {
            return onFailedInit;
        }

        @Nonnull
        @Override
        public CharPickMenu build() {
            preBuild();

            if (channelWaiter == null) throw new IllegalStateException("ChannelWaiter must be set");
            if (user == null) throw new IllegalStateException("User must be set");
            if (channelId == null) throw new IllegalStateException("ChannelId must be set");
            if (start == null) throw new IllegalStateException("Start must be set");
            if (characters == null) throw new IllegalStateException("Characters must be set");

            return new CharPickMenu(channelWaiter, getTimeout(), getUnit(), user, channelId, start, characters, onResult, onTimeout, onFailedInit);
        }
    }
}
