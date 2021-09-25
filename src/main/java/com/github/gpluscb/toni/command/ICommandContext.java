package com.github.gpluscb.toni.command;

import com.github.gpluscb.toni.Config;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.Event;
import net.dv8tion.jda.api.requests.RestAction;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public interface ICommandContext<EVENT extends Event, ACTION extends RestAction<?>> {
    @Nonnull
    @CheckReturnValue
    ACTION reply(@Nonnull Message message);

    @Nonnull
    @CheckReturnValue
    default ACTION reply(@Nonnull String message) {
        return reply(new MessageBuilder(message).build());
    }

    @Nonnull
    @CheckReturnValue
    default ACTION reply(@Nonnull MessageEmbed embed) {
        return reply(new MessageBuilder().setEmbeds(embed).build());
    }

    EVENT getEvent();

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    default boolean memberHasBotAdminPermission() {
        return getUser().getIdLong() == getConfig().getDevId();
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    default boolean memberHasManageChannelsPermission() {
        Member member = getMember();
        if (member == null) throw new IllegalStateException("This event is not from a guild.");
        return member.hasPermission(Permission.MANAGE_CHANNEL);
    }

    @Nonnull
    JDA getJDA();

    @Nonnull
    User getUser();

    @Nullable
    Member getMember();

    @Nonnull
    MessageChannel getChannel();

    @Nonnull
    Config getConfig();
}
