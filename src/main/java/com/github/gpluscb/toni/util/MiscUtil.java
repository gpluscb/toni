package com.github.gpluscb.toni.util;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.LayoutComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.RestAction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MiscUtil {
    private static final Logger log = LogManager.getLogger(MiscUtil.class);

    private static final Pattern durationPatternPrimary = Pattern.compile("(?:(\\d+):)?(\\d+):(\\d+)");
    private static final Pattern durationPatternAlternate = Pattern.compile("(?:(\\d+)h)? *(?:(\\d+)m(?:in)?)? *(?:(\\d+)s(?:ec)?)?");

    @Nonnull
    public static String randomString(int length) {
        // Source: https://www.baeldung.com/java-random-string lol
        int leftLimit = 97; // letter 'a'
        int rightLimit = 122; // letter 'z'
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        return rng.ints(leftLimit, rightLimit + 1)
                .limit(length)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }

    /**
     * @return Null if this is not a digit emoji
     */
    @Nullable
    public static Byte getNumberFromEmoji(@Nonnull String unicode) {
        try {
            Matcher matcher = Constants.NUMBER_EMOJI_PATTERN.matcher(unicode);
            return matcher.matches() ? Byte.parseByte(matcher.group(1)) : null;
        } catch (NumberFormatException e) {
            log.catching(e);
            return null;
        }
    }

    @Nonnull
    public static <T> Consumer<T> emptyConsumer() {
        return t -> {
        };
    }

    @Nonnull
    public static <T, U> BiConsumer<T, U> emptyBiConsumer() {
        return (t, u) -> {
        };
    }

    @Nonnull
    public static String capitalizeFirst(@Nonnull String string) {
        return string.substring(0, 1).toUpperCase() + string.substring(1);
    }

    @Nullable
    public static Boolean boolFromString(@Nonnull String string) {
        return switch (string.toLowerCase()) {
            case "0", "false", "no", "n" -> false;
            case "1", "true", "yes", "y" -> true;
            default -> null;
        };
    }

    @Nonnull
    public static <T> List<List<T>> splitList(@Nonnull List<T> toSplit, int maxLength) {
        List<List<T>> ret = new ArrayList<>();
        List<T> currentList = new ArrayList<>();

        for (T elem : toSplit) {
            currentList.add(elem);
            if (currentList.size() >= maxLength) {
                ret.add(currentList);
                currentList = new ArrayList<>();
            }

        }

        if (!currentList.isEmpty()) ret.add(currentList);

        return ret;
    }

    @Nonnull
    @CheckReturnValue
    public static RestAction<Void> clearReactionsOrRemoveOwnReactions(@Nonnull MessageChannel channel, long messageId, @Nonnull String... reactions) {
        if (channel instanceof TextChannel textChannel) {
            if (textChannel.getGuild().getSelfMember().hasPermission(textChannel, Permission.MESSAGE_MANAGE))
                return textChannel.clearReactionsById(messageId);
        }

        return RestAction.allOf(
                Arrays.stream(reactions).map(r -> channel.removeReactionById(messageId, r))
                        .toList()
        ).map(v -> null);
    }

    @Nonnull
    public static List<ActionRow> disabledButtonActionRows(@Nonnull ButtonInteractionEvent e) {
        if (e.getMessage().isEphemeral()) throw new IllegalStateException("Message may not be ephemeral");

        List<ActionRow> actionRows = new ArrayList<>(e.getMessage().getActionRows());
        Button button = e.getButton();

        // Update the actionRows to disable the current button

        if (!LayoutComponent.updateComponent(actionRows, e.getComponentId(), button.asDisabled()))
            log.warn("Updating button as disabled failed: actionRows: {}, componentId: {}", actionRows, e.getComponentId());

        return actionRows;
    }

    @Nonnull
    public static String mentionEmote(long id) {
        return String.format("<:a:%d>", id);
    }

    @Nonnull
    public static String mentionUser(long id) {
        return String.format("<@%d>", id);
    }

    @Nonnull
    public static String mentionRole(long id) {
        return String.format("<@&%d>", id);
    }

    @Nonnull
    public static String mentionChannel(long id) {
        return String.format("<#%d>", id);
    }

    /**
     * @return Non-negative duration
     */
    @Nullable
    public static Duration parseDuration(@Nonnull String input) {
        if (input.isEmpty()) return null;

        Matcher matcher = durationPatternPrimary.matcher(input);
        if (!matcher.matches()) matcher = durationPatternAlternate.matcher(input);
        if (!matcher.matches()) return null;

        String matchedHours = matcher.group(1);
        String matchedMinutes = matcher.group(2);
        String matchedSeconds = matcher.group(3);

        try {
            // Since input is not empty, at least one of these should not be null
            long hours = matchedHours == null ? 0 : Long.parseLong(matchedHours);
            long minutes = matchedMinutes == null ? 0 : Long.parseLong(matchedMinutes);
            long seconds = matchedSeconds == null ? 0 : Long.parseLong(matchedSeconds);

            return Duration.ofHours(hours)
                    .plusMinutes(minutes)
                    .plusSeconds(seconds);
        } catch (NumberFormatException e) {
            log.warn("NumberFormatException when parsing duration: is it out of bounds for long? input: {}, error: {}", input, e);
            return null;
        }
    }

    @Nonnull
    public static String durationToString(@Nonnull Duration duration) {
        StringBuilder builder = new StringBuilder();

        long hours = duration.toHours();
        duration = duration.minusHours(hours);
        long minutes = duration.toMinutes();
        duration = duration.minusMinutes(minutes);
        long seconds = duration.getSeconds();

        if (hours > 0) builder.append(hours).append('h');
        if (minutes > 0) {
            if (builder.length() > 0) builder.append(' ');
            builder.append(minutes).append('m');
        }
        if (seconds > 0) {
            if (builder.length() > 0) builder.append(' ');
            builder.append(seconds).append('s');
        }

        return builder.toString();
    }

    // TODO: I don't think this is necessary, getName should just do that for me
    @Nonnull
    public static String getPermName(@Nonnull Permission perm) {
        return switch (perm) {
            case MESSAGE_EMBED_LINKS -> "Embed Links";
            case MESSAGE_HISTORY -> "Read Message History";
            default -> perm.getName();
        };
    }
}
