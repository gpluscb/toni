package com.github.gpluscb.toni.command.menu;

import com.github.gpluscb.toni.command.modal.CharPickModalHandler;
import com.github.gpluscb.toni.menu.ActionMenu;
import com.github.gpluscb.toni.menu.ButtonActionMenu;
import com.github.gpluscb.toni.smashset.Character;
import com.github.gpluscb.toni.smashset.CharacterTree;
import com.github.gpluscb.toni.util.MiscUtil;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class BlindPickMenu extends ActionMenu {
    @Nonnull
    private final Settings settings;

    @Nonnull
    private final CharPickModalHandler modalHandler;
    @Nonnull
    private final ButtonActionMenu buttonMenu;

    @Nonnull
    private final HashMap<Long, Character> choices;
    @Nonnull
    private final AtomicBoolean finished;

    public BlindPickMenu(@Nonnull Settings settings) {
        super(settings.actionMenuSettings());
        this.settings = settings;

        modalHandler = new CharPickModalHandler(settings.waiter());
        buttonMenu = new ButtonActionMenu(new ButtonActionMenu.Settings.Builder()
                .setActionMenuSettings(settings.actionMenuSettings())
                .setStart(settings.start())
                .setOnTimeout(this::onTimeout)
                .setUsers(settings.users())
                .registerButton(Button.primary("select", "Select Character"), this::onButtonClick)
                .setDeletionButton(null)
                .build());

        finished = new AtomicBoolean(false);
        choices = new HashMap<>();
    }

    @Override
    public void display(@Nonnull MessageChannel channel) {
        buttonMenu.display(channel);
    }

    @Override
    public void display(@Nonnull MessageChannel channel, long messageId) {
        buttonMenu.display(channel, messageId);
    }

    @Override
    public void displayReplying(@Nonnull MessageChannel channel, long messageId) {
        buttonMenu.displayReplying(channel, messageId);
    }

    @Override
    public void displaySlashReplying(@Nonnull SlashCommandInteractionEvent event) {
        buttonMenu.displaySlashReplying(event);
    }

    @Override
    public void displayDeferredReplying(@Nonnull InteractionHook hook) {
        buttonMenu.displayDeferredReplying(hook);
    }

    @Override
    public void start(@Nonnull Message message) {
        buttonMenu.start(message);
    }

    @Nonnull
    @Override
    public List<ActionRow> getComponents() {
        return buttonMenu.getComponents();
    }

    @Nonnull
    private MenuAction onButtonClick(@Nonnull ButtonInteractionEvent event) {
        if (finished.get()) return MenuAction.CANCEL;
        modalHandler.replyWith(event.getInteraction(), this::onChoice).queue();
        return MenuAction.CONTINUE;
    }

    private void onChoice(@Nonnull String choice, @Nonnull ModalInteractionEvent event) {
        if (finished.get()) {
            event.reply("All characters have already been selected and published, or this interaction timed out." +
                            " So your choice wasn't recorded")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        Character characterChoice = settings.characters().stream()
                .filter(c -> c.altNames().contains(choice.toLowerCase()))
                .findAny()
                .orElse(null);

        if (characterChoice == null) {
            // TODO: Maybe block while another modal is open?
            event.reply("I don't recognise that character. Please try again.").setEphemeral(true).queue();
            return;
        }

        boolean firstChoice = choices.put(event.getUser().getIdLong(), characterChoice) == null;

        if (settings.users().stream().allMatch(choices::containsKey)) {
            finished.set(true);

            settings.onResult().accept(new BlindPickResult(), event);

            return;
        }

        event.reply(String.format("I have %s your choice.", firstChoice ? "noted" : "updated"))
                .setEphemeral(true)
                .queue();
    }

    public void onTimeout(@Nonnull ButtonActionMenu.ButtonActionMenuTimeoutEvent timeout) {
        if (finished.getAndSet(true)) return;

        settings.onTimeout().accept(new BlindPickTimeoutEvent());
    }

    @Nonnull
    public Settings getBlindPickMenuSettings() {
        return settings;
    }

    @Nonnull
    @Override
    public JDA getJDA() {
        return buttonMenu.getJDA();
    }

    @Override
    public long getChannelId() {
        return buttonMenu.getChannelId();
    }

    @Override
    public long getMessageId() {
        return buttonMenu.getMessageId();
    }

    private abstract class BlindPickMenuStateInfo extends MenuStateInfo {
        @Nonnull
        public Settings getBlindPickMenuSettings() {
            return BlindPickMenu.this.getBlindPickMenuSettings();
        }

        @Nonnull
        public HashMap<Long, Character> getChoices() {
            return choices;
        }
    }

    public class BlindPickResult extends BlindPickMenuStateInfo {
    }

    public class BlindPickTimeoutEvent extends BlindPickMenuStateInfo {
    }

    public record Settings(@Nonnull ActionMenu.Settings actionMenuSettings, @Nonnull EventWaiter waiter,
                           @Nonnull Set<Long> users, @Nonnull Message start, @Nonnull List<Character> characters,
                           @Nonnull BiConsumer<BlindPickResult, ModalInteractionEvent> onResult,
                           @Nonnull Consumer<BlindPickTimeoutEvent> onTimeout) {
        @Nonnull
        public static final BiConsumer<BlindPickResult, ModalInteractionEvent> DEFAULT_ON_RESULT = MiscUtil.emptyBiConsumer();
        @Nonnull
        public static final Consumer<BlindPickTimeoutEvent> DEFAULT_ON_TIMEOUT = MiscUtil.emptyConsumer();

        public static class Builder {
            @Nullable
            private ActionMenu.Settings actionMenuSettings;
            @Nullable
            private EventWaiter waiter;
            @Nonnull
            private Set<Long> users = new HashSet<>();
            @Nullable
            private Message start;
            @Nullable
            private List<Character> characters;
            @Nonnull
            private BiConsumer<BlindPickResult, ModalInteractionEvent> onResult = DEFAULT_ON_RESULT;
            @Nonnull
            private Consumer<BlindPickTimeoutEvent> onTimeout = DEFAULT_ON_TIMEOUT;

            @Nonnull
            public Builder setActionMenuSettings(@Nullable ActionMenu.Settings actionMenuSettings) {
                this.actionMenuSettings = actionMenuSettings;
                return this;
            }

            @Nonnull
            public Builder setWaiter(@Nonnull EventWaiter waiter) {
                this.waiter = waiter;
                return this;
            }

            @Nonnull
            public Builder addUsers(@Nonnull long... users) {
                Arrays.stream(users).forEach(this.users::add);
                return this;
            }

            @Nonnull
            public Builder setUsers(@Nonnull Set<Long> users) {
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
            public Builder setOnResult(@Nonnull BiConsumer<BlindPickResult, ModalInteractionEvent> onResult) {
                this.onResult = onResult;
                return this;
            }

            @Nonnull
            public Builder setOnTimeout(@Nonnull Consumer<BlindPickTimeoutEvent> onTimeout) {
                this.onTimeout = onTimeout;
                return this;
            }

            @Nonnull
            public Settings build() {
                if (actionMenuSettings == null) throw new IllegalStateException("ActionMenuSettings must be set");
                if (waiter == null) throw new IllegalStateException("Waiter must be set");
                if (start == null) throw new IllegalStateException("Start must be set");
                if (characters == null) throw new IllegalStateException("Characters must be set");

                return new Settings(actionMenuSettings, waiter, users, start, characters, onResult, onTimeout);
            }
        }
    }
}
