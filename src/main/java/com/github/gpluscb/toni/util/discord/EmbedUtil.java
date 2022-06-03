package com.github.gpluscb.toni.util.discord;

import com.github.gpluscb.toni.smashset.Ruleset;
import com.github.gpluscb.toni.smashset.Stage;
import com.github.gpluscb.toni.util.Constants;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class EmbedUtil {
    public static final String GG_ICON_URL = "https://cdn.discordapp.com/attachments/756494920772091924/982380357255065660/start.gg_Icon_Large_RGB.png";
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
        return applyStartGGFooter(getPreparedAuthor(member, author));
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
    public static EmbedBuilder applyStartGGFooter(@Nonnull EmbedBuilder builder) {
        return builder.setFooter("Data from the start.gg GraphQL API (https://developer.start.gg)", GG_ICON_URL);
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
    public static EmbedBuilder applyRuleset(@Nonnull EmbedBuilder builder, @Nonnull Ruleset ruleset) {
        builder.setTitle(String.format("Ruleset %d: %s", ruleset.rulesetId(), ruleset.name()), ruleset.url());
        builder.appendDescription(ruleset.shortDescription())
                .appendDescription("\n\n");
        List<EmbedUtil.InlineField> fields = new ArrayList<>();
        List<Stage> starters = ruleset.starters();
        // Guaranteed to have at least one starter
        fields.add(new EmbedUtil.InlineField("Starters", starters.get(0).getDisplayName()));
        for (int i = 1; i < starters.size(); i++)
            fields.add(new EmbedUtil.InlineField("", starters.get(i).getDisplayName()));
        List<Stage> counterpicks = ruleset.counterpicks();
        fields.add(new EmbedUtil.InlineField("Counterpicks", counterpicks.isEmpty() ? "None" : counterpicks.get(0).getDisplayName()));
        for (int i = 1; i < counterpicks.size(); i++)
            fields.add(new EmbedUtil.InlineField("", counterpicks.get(i).getDisplayName()));
        String dsrSsbwikiUrl = ruleset.dsrMode().getSsbwikiUrl();
        fields.add(new EmbedUtil.InlineField("DSR Mode",
                String.format("%s%s", ruleset.dsrMode().displayName(),
                        dsrSsbwikiUrl == null ? "" : String.format(" ([SmashWiki](%s))", dsrSsbwikiUrl))));
        fields.add(new EmbedUtil.InlineField("Bans", String.valueOf(ruleset.stageBans())));
        int[] starterStrikePattern = ruleset.starterStrikePattern();
        String strikePattern = starterStrikePattern.length == 0 ? "No Strikes"
                : Arrays.stream(starterStrikePattern)
                .mapToObj(String::valueOf)
                .collect(Collectors.joining("-"));
        fields.add(new EmbedUtil.InlineField("Starter Strike Pattern", strikePattern));
        fields.add(new EmbedUtil.InlineField("Character Blind Pick", String.format("**%s** Stage Striking", ruleset.blindPickBeforeStage() ? "Before" : "After")));
        fields.add(new EmbedUtil.InlineField("Character Reveal", String.format("**%s** Stage Bans", ruleset.stageBeforeCharacter() ? "After" : "Before")));
        builder.appendDescription(EmbedUtil.parseInlineFields(fields));
        return builder;
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
