package com.github.gpluscb.toni.util.discord;

import com.github.gpluscb.toni.util.Constants;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

public class EmbedUtil {
    public static final String GG_ICON_URL = "https://images.squarespace-cdn.com/content/v1/5a9f03823917eedc1f3d70a8/1532389053471-VAS050M2RWB844686YC4/ke17ZwdGBToddI8pDm48kI2QMvs45oEqGNkHyq2-PDuoCXeSvxnTEQmG4uwOsdIceAoHiyRoc52GMN5_2H8Wp398LIBXMf3VXjZWpujXlt99wLTlXFoVAPff3oiife71cO0v_IXC4WMiKO6Ds5TAIw/favicon.ico";
    public static final String UFD_ICON_URL = "https://ultimateframedata.com/ogimage.png";
    public static final String SMASHDATA_ICON_URL = "https://pbs.twimg.com/profile_images/1225945446226350080/3V1FENbf_400x400.jpg";


    @Nonnull
    public static EmbedBuilder getPrepared() {
        return applyColor(applyTimestamp(new EmbedBuilder()));
    }

    @Nonnull
    public static EmbedBuilder getPreparedAuthor(@Nullable Member member, @Nonnull User author) {
        return applyAuthor(getPrepared(), member, author);
    }

    @Nonnull
    public static EmbedBuilder getPreparedGG(@Nullable Member member, @Nonnull User author) {
        return applySmashGGFooter(getPreparedAuthor(member, author));
    }

    @Nonnull
    public static EmbedBuilder getPreparedUFD(@Nullable Member member, @Nonnull User author) {
        return applyUFDFooter(getPreparedAuthor(member, author));
    }

    @Nonnull
    public static EmbedBuilder getPreparedSmashdata(@Nullable Member member, @Nonnull User author) {
        return applySmashdataFooter(getPreparedAuthor(member, author));
    }

    @Nonnull
    public static EmbedBuilder applyAuthor(@Nonnull EmbedBuilder builder, @Nullable Member member, @Nonnull User author) {
        return builder.setAuthor(member == null ? author.getName() : member.getEffectiveName(), null, author.getEffectiveAvatarUrl());
    }

    @Nonnull
    public static EmbedBuilder applyTimestamp(@Nonnull EmbedBuilder builder) {
        return builder.setTimestamp(Instant.now());
    }

    @Nonnull
    public static EmbedBuilder applyColor(@Nonnull EmbedBuilder builder) {
        return builder.setColor(Constants.BRAND_COLOR);
    }

    @Nonnull
    public static EmbedBuilder applySmashGGFooter(@Nonnull EmbedBuilder builder) {
        return builder.setFooter("Data from the smash.gg GraphQL API (https://developer.smash.gg)", GG_ICON_URL);
    }

    @Nonnull
    public static EmbedBuilder applyUFDFooter(@Nonnull EmbedBuilder builder) {
        return builder.setFooter("Data from the https://ultimateframedata.com website", UFD_ICON_URL);
    }

    @Nonnull
    public static EmbedBuilder applySmashdataFooter(@Nonnull EmbedBuilder builder) {
        return builder.setFooter("Data from the https://smashdata.gg database", SMASHDATA_ICON_URL);
    }

    @Nonnull
    public static String parseInlineFields(@Nonnull List<InlineField> fields) {
        // Modified from JDA-Butlers help command code: https://github.com/Almighty-Alpaca/JDA-Butler/blob/fc8abbb80088f0b83d881fc754e58f0d170dbf2d/bot/src/main/java/com/almightyalpaca/discord/jdabutler/commands/commands/HelpCommand.java#L30-L32
        // JDA-Butlers code is published under the Apache license: https://github.com/Almighty-Alpaca/JDA-Butler/blob/fc8abbb80088f0b83d881fc754e58f0d170dbf2d/LICENSE
        int maxSize = fields.stream().mapToInt(field -> field.name().length() + 1).max().orElse(0);

        return fields.stream().map(field -> String.format("`%s`| %s", StringUtils.rightPad(field.name(), maxSize, "·"), field.value())).collect(Collectors.joining("\n"));
    }

    public record InlineField(@Nonnull String name, @Nonnull String value) {
    }
}
