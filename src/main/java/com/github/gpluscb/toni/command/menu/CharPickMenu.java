package com.github.gpluscb.toni.command.menu;

import com.github.gpluscb.toni.util.Constants;
import com.github.gpluscb.toni.util.MiscUtil;
import com.github.gpluscb.toni.util.discord.ChannelChoiceWaiter;
import com.github.gpluscb.toni.menu.ActionMenu;
import com.github.gpluscb.toni.smashset.Character;
import com.github.gpluscb.toni.smashset.CharacterTree;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.MessageReference;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.RestAction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class CharPickMenu extends ActionMenu {
    private static final Logger log = LogManager.getLogger(CharPickMenu.class);

    @Nonnull
    private final Settings settings;

    public CharPickMenu(@Nonnull Settings settings) {
        super(settings.actionMenuSettings());
        this.settings = settings;
    }

    @Override
    public void display(@Nonnull MessageChannel channel) {
        initWithMessageAction(channel.sendMessage(settings.start()));
    }

    @Override
    public void display(@Nonnull MessageChannel channel, long messageId) {
        initWithMessageAction(channel.editMessageById(messageId, settings.start()));
    }

    @Override
    public void displayReplying(@Nonnull MessageChannel channel, long messageId) {
        // TODO: MESSAGE_HISTORY
        initWithMessageAction(channel.sendMessage(settings.start()).referenceById(messageId));
    }

    @Override
    public void displaySlashReplying(@Nonnull SlashCommandEvent event) {
        initWithMessageAction(event.reply(settings.start()).flatMap(InteractionHook::retrieveOriginal));
    }

    @Override
    public void displayDeferredReplying(@Nonnull InteractionHook hook) {
        initWithMessageAction(hook.sendMessage(settings.start()));
    }

    private void initWithMessageAction(@Nonnull RestAction<Message> action) {
        ChannelChoiceWaiter.WaitChoiceHandle handle = settings.waiter().waitForChoice(
                Collections.singletonList(settings.user()),
                settings.channelId(),
                true,
                this::verifyChoice,
                this::onResult,
                getActionMenuSettings().timeout(), getActionMenuSettings().unit(),
                this::onTimeout
        );

        if (handle == null) {
            settings.onFailedInit().run();
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
        long botMessage = getMessageId();

        Message message = event.getMessage();
        MessageReference reference = message.getMessageReference();
        // Only allow replies
        if (reference == null || reference.getMessageIdLong() != botMessage)
            return Optional.empty();

        String choice = message.getContentRaw();

        Character character = settings.characters().stream().filter(c -> c.altNames().contains(choice.toLowerCase())).findAny().orElse(null);
        if (character == null) message.reply("I don't know that character.").queue();
        else settings.onChoice().accept(character);

        return Optional.ofNullable(character);
    }

    private void onResult(@Nonnull List<ChannelChoiceWaiter.UserChoiceInfo<Character>> choices) {
        // Since we're done here it's nonnull time
        //noinspection ConstantConditions
        settings.onResult().accept(new CharPickResult(choices.get(0).getChoice()));
    }

    private void onTimeout(@Nonnull List<ChannelChoiceWaiter.UserChoiceInfo<Character>> choices) {
        settings.onTimeout().accept(new CharPickTimeoutEvent());
    }

    @Nonnull
    public Settings getCharPickMenuSettings() {
        return settings;
    }

    private abstract class CharPickMenuInfo extends MenuStateInfo {
        @Nonnull
        public Settings getCharPickMenuSettings() {
            return CharPickMenu.this.getCharPickMenuSettings();
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

    public record Settings(@Nonnull ActionMenu.Settings actionMenuSettings, @Nonnull ChannelChoiceWaiter waiter,
                           long user, long channelId, @Nonnull Message start, @Nonnull List<Character> characters,
                           @Nonnull Consumer<Character> onChoice, @Nonnull Consumer<CharPickResult> onResult,
                           @Nonnull Consumer<CharPickTimeoutEvent> onTimeout, @Nonnull Runnable onFailedInit) {
        @Nonnull
        public static final Consumer<Character> DEFAULT_ON_CHOICE = MiscUtil.emptyConsumer();
        @Nonnull
        public static final Consumer<CharPickResult> DEFAULT_ON_RESULT = MiscUtil.emptyConsumer();
        @Nonnull
        public static final Consumer<CharPickTimeoutEvent> DEFAULT_ON_TIMEOUT = MiscUtil.emptyConsumer();
        @Nonnull
        public static final Runnable DEFAULT_ON_FAILED_INIT = Constants.EMPTY_RUNNABLE;

        public static class Builder {
            @Nullable
            private ActionMenu.Settings actionMenuSettings;
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
            private Consumer<Character> onChoice = DEFAULT_ON_CHOICE;
            @Nonnull
            private Consumer<CharPickResult> onResult = DEFAULT_ON_RESULT;
            @Nonnull
            private Consumer<CharPickTimeoutEvent> onTimeout = DEFAULT_ON_TIMEOUT;
            @Nonnull
            private Runnable onFailedInit = DEFAULT_ON_FAILED_INIT;

            @Nonnull
            public Builder setActionMenuSettings(@Nonnull ActionMenu.Settings actionMenuSettings) {
                this.actionMenuSettings = actionMenuSettings;
                return this;
            }

            @Nonnull
            public Builder setChannelWaiter(@Nonnull ChannelChoiceWaiter channelWaiter) {
                this.channelWaiter = channelWaiter;
                return this;
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
            public Builder setOnChoice(@Nonnull Consumer<Character> onChoice) {
                this.onChoice = onChoice;
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

            @Nonnull
            public Settings build() {
                if (actionMenuSettings == null) throw new IllegalStateException("ActionMenuSettings must be set");
                if (channelWaiter == null) throw new IllegalStateException("ChannelWaiter must be set");
                if (user == null) throw new IllegalStateException("User must be set");
                if (channelId == null) throw new IllegalStateException("ChannelId must be set");
                if (start == null) throw new IllegalStateException("Start must be set");
                if (characters == null) throw new IllegalStateException("Characters must be set");

                return new Settings(actionMenuSettings, channelWaiter, user, channelId, start, characters, onChoice, onResult, onTimeout, onFailedInit);
            }
        }
    }
}
