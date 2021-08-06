package com.github.gpluscb.toni.command.components;

import com.github.gpluscb.toni.util.DMChoiceWaiter;
import com.github.gpluscb.toni.util.smash.Character;
import com.github.gpluscb.toni.util.smash.CharacterTree;
import net.dv8tion.jda.api.entities.Message;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class BlindPickComponent {
    @Nonnull
    private final DMChoiceWaiter waiter;
    @Nonnull
    private final List<Character> characters;

    public BlindPickComponent(@Nonnull DMChoiceWaiter waiter, @Nonnull CharacterTree characterTree) {
        this.waiter = waiter;
        characters = characterTree.getAllCharacters();
    }

    /**
     * @return Null if the DMChoiceWaiter is busy
     */
    @Nullable
    public CompletableFuture<Map<Long, Character>> initiateBlindPick(List<Long> users) {
        CompletableFuture<Map<Long, Character>> blindPickResult = new CompletableFuture<>();

        boolean worked = waiter.waitForDMChoice(users, true, e -> {
                    Message message = e.getMessage();
                    String choice = message.getContentRaw();

                    Character character = characters.stream().filter(c -> c.getAltNames().contains(choice.toLowerCase())).findAny().orElse(null);
                    if (character == null) message.reply("I don't know that character.").queue();
                    else message.reply("Accepted!").queue();

                    return Optional.ofNullable(character);
                },
                blindPickResult::complete,
                3, TimeUnit.MINUTES, map -> blindPickResult.completeExceptionally(new BlindPickTimeoutException(map)));

        if (!worked) {
            // I believe this prevents a leak?
            blindPickResult.complete(null);
            return null;
        }

        return blindPickResult;
    }

    public static class BlindPickTimeoutException extends Exception {
        @Nonnull
        private final Map<Long, Character> picksSoFar;

        public BlindPickTimeoutException(@Nonnull Map<Long, Character> picksSoFar) {
            this.picksSoFar = picksSoFar;
        }

        public BlindPickTimeoutException(String message, @Nonnull Map<Long, Character> picksSoFar) {
            super(message);
            this.picksSoFar = picksSoFar;
        }

        public BlindPickTimeoutException(String message, Throwable cause, @Nonnull Map<Long, Character> picksSoFar) {
            super(message, cause);
            this.picksSoFar = picksSoFar;
        }

        public BlindPickTimeoutException(Throwable cause, @Nonnull Map<Long, Character> picksSoFar) {
            super(cause);
            this.picksSoFar = picksSoFar;
        }

        public BlindPickTimeoutException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace, @Nonnull Map<Long, Character> picksSoFar) {
            super(message, cause, enableSuppression, writableStackTrace);
            this.picksSoFar = picksSoFar;
        }

        @Nonnull
        public Map<Long, Character> getPicksSoFar() {
            return picksSoFar;
        }
    }
}
