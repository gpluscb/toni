package com.github.gpluscb.toni.util;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.requests.RestAction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Duration;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class MiscUtil {
    private static final Logger log = LogManager.getLogger(MiscUtil.class);

    private static final Pattern durationPatternPrimary = Pattern.compile("(?:(\\d+):)?(\\d+):(\\d+)");
    private static final Pattern durationPatternAlternate = Pattern.compile("(?:(\\d+)h)? *(?:(\\d+)m(?:in)?)? *(?:(\\d+)s(?:ec)?)?");

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
    public static String capitalizeFirst(@Nonnull String string) {
        return string.substring(0, 1).toUpperCase() + string.substring(1);
    }

    @Nullable
    public static Boolean boolFromString(@Nonnull String string) {
        switch (string.toLowerCase()) {
            case "0":
            case "false":
                return false;
            case "1":
            case "true":
                return true;
            default:
                return null;
        }
    }

    @Nonnull
    @CheckReturnValue
    public static RestAction<Void> clearReactionsOrRemoveOwnReactions(@Nonnull MessageChannel channel, long messageId, @Nonnull String... reactions) {
        if (channel instanceof TextChannel) {
            TextChannel textChannel = (TextChannel) channel;
            if (textChannel.getGuild().getSelfMember().hasPermission(textChannel, Permission.MESSAGE_MANAGE))
                return textChannel.clearReactionsById(messageId);
        }

        return RestAction.allOf(
                Arrays.stream(reactions).map(r -> channel.removeReactionById(messageId, r))
                        .collect(Collectors.toList())
        ).map(v -> null);
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

    @Nonnull
    public static String getPermName(@Nonnull Permission perm) {
        switch (perm) {
            case MESSAGE_EMBED_LINKS:
                return "Embed Links";
            default:
                return perm.getName();
        }
    }
}
