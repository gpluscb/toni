package com.github.gpluscb.toni.util;

import net.dv8tion.jda.api.Permission;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.regex.Matcher;

public class MiscUtil {
    private static final Logger log = LogManager.getLogger(MiscUtil.class);

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
