package com.github.gpluscb.toni.command.components;

import com.github.gpluscb.toni.util.Constants;
import com.github.gpluscb.toni.util.MiscUtil;
import com.github.gpluscb.toni.util.discord.ChannelChoiceWaiter;
import com.github.gpluscb.toni.util.discord.menu.ActionMenu;
import com.github.gpluscb.toni.smashset.Character;
import com.github.gpluscb.toni.smashset.CharacterTree;
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
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class BlindPickMenu extends ActionMenu {
    private static final Logger log = LogManager.getLogger(BlindPickMenu.class);

    @Nonnull
    private final Settings settings;

    public BlindPickMenu(@Nonnull Settings settings) {
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
        ChannelChoiceWaiter.WaitChoiceHandle handle = settings.waiter().waitForDMChoice(
                settings.users(),
                true,
                this::verifyChoice,
                choices -> settings.onResult().accept(new BlindPickResult(choices)),
                getActionMenuSettings().timeout(), getActionMenuSettings().unit(),
                choices -> settings.onTimeout().accept(new BlindPickTimeoutEvent(choices))
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
        Message message = event.getMessage();
        String choice = message.getContentRaw();

        Character character = settings.characters().stream().filter(c -> c.altNames().contains(choice.toLowerCase())).findAny().orElse(null);
        if (character == null) message.reply("I don't know that character.").queue();
        else message.reply("Accepted!").queue();

        return Optional.ofNullable(character);
    }

    @Nonnull
    public Settings getBlindPickMenuSettings() {
        return settings;
    }

    private abstract class BlindPickMenuStateInfo extends MenuStateInfo {
        @Nonnull
        public Settings getBlindPickMenuSettings() {
            return BlindPickMenu.this.getBlindPickMenuSettings();
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

    public record Settings(@Nonnull ActionMenu.Settings actionMenuSettings, @Nonnull ChannelChoiceWaiter waiter,
                           @Nonnull List<Long> users, @Nonnull Message start, @Nonnull List<Character> characters,
                           @Nonnull Consumer<BlindPickResult> onResult,
                           @Nonnull Consumer<BlindPickTimeoutEvent> onTimeout, @Nonnull Runnable onFailedInit) {
        @Nonnull
        public static final Consumer<BlindPickResult> DEFAULT_ON_RESULT = MiscUtil.emptyConsumer();
        @Nonnull
        public static final Consumer<BlindPickTimeoutEvent> DEFAULT_ON_TIMEOUT = MiscUtil.emptyConsumer();
        @Nonnull
        public static final Runnable DEFAULT_ON_FAILED_INIT = Constants.EMPTY_RUNNABLE;

        public static class Builder {
            @Nullable
            private ActionMenu.Settings actionMenuSettings;
            @Nullable
            private ChannelChoiceWaiter channelWaiter;
            @Nonnull
            private List<Long> users = new ArrayList<>();
            @Nullable
            private Message start;
            @Nullable
            private List<Character> characters;
            @Nonnull
            private Consumer<BlindPickResult> onResult = DEFAULT_ON_RESULT;
            @Nonnull
            private Consumer<BlindPickTimeoutEvent> onTimeout = DEFAULT_ON_TIMEOUT;
            @Nonnull
            private Runnable onFailedInit = DEFAULT_ON_FAILED_INIT;

            @Nonnull
            public Builder setActionMenuSettings(@Nullable ActionMenu.Settings actionMenuSettings) {
                this.actionMenuSettings = actionMenuSettings;
                return this;
            }

            @Nonnull
            public Builder setChannelWaiter(@Nonnull ChannelChoiceWaiter channelWaiter) {
                this.channelWaiter = channelWaiter;
                return this;
            }

            @Nonnull
            public Builder addUsers(@Nonnull long... users) {
                Arrays.stream(users).forEach(this.users::add);
                return this;
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

            @Nonnull
            public Settings build() {
                if (actionMenuSettings == null) throw new IllegalStateException("ActionMenuSettings must be set");
                if (channelWaiter == null) throw new IllegalStateException("ChannelWaiter must be set");
                if (start == null) throw new IllegalStateException("Start must be set");
                if (characters == null) throw new IllegalStateException("Characters must be set");

                return new Settings(actionMenuSettings, channelWaiter, users, start, characters, onResult, onTimeout, onFailedInit);
            }
        }
    }
}
