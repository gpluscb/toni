package com.github.gpluscb.toni.util;

import com.github.gpluscb.toni.command.MessageCommandContext;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Button;
import net.dv8tion.jda.api.interactions.components.ComponentLayout;
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
import java.util.function.BiConsumer;
import java.util.function.Consumer;
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
    public static <T> Consumer<T> emptyConsumer() {
        return t -> {
        };
    }

    @Nonnull
    public static <T, U> BiConsumer<T, U> emptyBiConsumer() {
        return (t, u) -> {
        };
    }

    public enum TwoUserArgsErrorType {
        WRONG_NUMBER_ARGS,
        NOT_USER_MENTION_ARG,
        BOT_USER,
        USER_1_EQUALS_USER_2,
    }

    public record OneOrTwoUserArgs(@Nonnull User user1User,
                                   @Nonnull User user2User, boolean twoArgumentsGiven) {
        public long getUser1() {
            return user1User.getIdLong();
        }

        public long getUser2() {
            return user2User.getIdLong();
        }
    }

    /**
     * Only useful if those one or two user mentions are <b>all</b> the arguments.
     * If there is one argument given user 2 will default to the author.
     */
    // TODO: This needs an overhaul, probably should have List of users with minUsers and maxUsers and defaultAuthorIfMin or sth like that
    @Nonnull
    public static OneOfTwo<OneOrTwoUserArgs, TwoUserArgsErrorType> getTwoUserArgs(@Nonnull MessageCommandContext ctx, boolean allowMoreArgs) {
        int argNum = ctx.getArgNum();
        if (ctx.getArgNum() < 1 || (!allowMoreArgs && ctx.getArgNum() > 2))
            return OneOfTwo.ofU(TwoUserArgsErrorType.WRONG_NUMBER_ARGS);

        User user1User = ctx.getUserMentionArg(0);
        boolean twoArgumentsGiven = argNum >= 2;
        User user2User = twoArgumentsGiven ? ctx.getUserMentionArg(1) : ctx.getUser();
        if (user2User == null && allowMoreArgs) {
            user2User = ctx.getUser();
            // TODO: Better naming
            twoArgumentsGiven = false;
        }
        if (user1User == null || user2User == null)
            return OneOfTwo.ofU(TwoUserArgsErrorType.NOT_USER_MENTION_ARG);


        if (user1User.isBot() || user2User.isBot())
            return OneOfTwo.ofU(TwoUserArgsErrorType.BOT_USER);

        if (user1User.getIdLong() == user2User.getIdLong())
            return OneOfTwo.ofU(TwoUserArgsErrorType.USER_1_EQUALS_USER_2);

        return OneOfTwo.ofT(new OneOrTwoUserArgs(user1User, user2User, twoArgumentsGiven));
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
    public static List<ActionRow> disabledButtonActionRows(@Nonnull ButtonClickEvent e) {
        if (e.getMessage().isEphemeral()) throw new IllegalStateException("Message may not be ephemeral");

        List<ActionRow> actionRows = new ArrayList<>(e.getMessage().getActionRows());
        Button button = e.getButton();

        // Update the actionRows to disable the current button

        // not ephemeral => button not null
        //noinspection ConstantConditions
        if (!ComponentLayout.updateComponent(actionRows, e.getComponentId(), button.asDisabled()))
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
