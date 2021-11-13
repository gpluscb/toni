package com.github.gpluscb.toni.util;

import javax.annotation.Nonnull;
import java.awt.*;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

public class Constants {
    @Nonnull
    public static final Color BRAND_COLOR = new Color(0xDC22B4);
    @Nonnull
    public static final ZoneId TIME_ZONE = ZoneId.of("UTC");
    @Nonnull
    public static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.RFC_1123_DATE_TIME;
    /**
     * Group 1 captures the digit
     */
    @Nonnull
    public static final Pattern NUMBER_EMOJI_PATTERN = Pattern.compile("(\\d)\uFE0F?\u20E3");

    @Nonnull
    public static final String ARROW_UPWARD = "\uD83D\uDD3C";
    @Nonnull
    public static final String ARROW_DOWNWARD = "\uD83D\uDD3D";
    @Nonnull
    public static final String ARROW_FORWARD = "\u25B6\uFE0F";
    @Nonnull
    public static final String ARROW_BACKWARD = "\u25c0\uFE0F";
    @Nonnull
    public static final String ARROW_DOUBLE_FORWARD = "\u23E9";
    @Nonnull
    public static final String ARROW_DOUBLE_BACKWARD = "\u23EA";
    @Nonnull
    public static final String FRAME = "\uD83C\uDF9E";
    @Nonnull
    public static final String CROSS_MARK = "\u274C";
    @Nonnull
    public static final String CHECK_MARK = "\u2705";
    @Nonnull
    public static final String FENCER = "\uD83E\uDD3A";
    @Nonnull
    public static final String ROCK = "\uD83E\uDEA8";
    @Nonnull
    public static final String PAPER = "\uD83D\uDCC4";
    @Nonnull
    public static final String SCISSORS = "\u2702\uFE0F";
    @Nonnull
    public static final String ONE = "1\u20E3";
    @Nonnull
    public static final String TWO = "2\u20E3";
    @Nonnull
    public static final Runnable EMPTY_RUNNABLE = () -> {
    };
}
