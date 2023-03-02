package com.github.gpluscb.toni.command;

import com.github.gpluscb.toni.Config;
import com.github.gpluscb.toni.util.discord.ReplyAction;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@SuppressWarnings("ClassCanBeRecord")
public class CommandContext {
    private static final Logger log = LogManager.getLogger(CommandContext.class);

    @Nonnull
    private final SlashCommandInteractionEvent event;

    @Nonnull
    private final Config config;

    public CommandContext(@Nonnull SlashCommandInteractionEvent event, @Nonnull Config config) {
        this.event = event;
        this.config = config;
    }

    @Nonnull
    public SlashCommandInteractionEvent getEvent() {
        return event;
    }

    @Nonnull
    @CheckReturnValue
    public ReplyAction<?, ?> reply(@Nonnull String message) {
        return reply(new MessageCreateBuilder().setContent(message).build());
    }

    @Nonnull
    @CheckReturnValue
    public ReplyAction<?, ?> reply(@Nonnull MessageEmbed embed) {
        return reply(new MessageCreateBuilder().setEmbeds(embed).build());
    }

    @Nonnull
    @CheckReturnValue
    public ReplyAction<?, ?> reply(@Nonnull MessageCreateData message) {
        String content = message.getContent();
        log.debug("Reply: {}", content.isEmpty() ? message.getEmbeds() : content);

        return event.isAcknowledged() ?
                new ReplyAction<>(event.getHook().sendMessage(message))
                : new ReplyAction<>(event.reply(message));
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean memberHasBotAdminPermission() {
        return getUser().getIdLong() == getConfig().devId();
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean memberHasManageChannelsPermission() {
        Member member = getMember();
        if (member == null) throw new IllegalStateException("This event is not from a guild.");
        return member.hasPermission(Permission.MANAGE_CHANNEL);
    }

    @Nonnull
    public JDA getJDA() {
        return event.getJDA();
    }

    @Nonnull
    public User getUser() {
        return event.getUser();
    }

    @Nullable
    public Member getMember() {
        return event.getMember();
    }

    @Nonnull
    public MessageChannel getChannel() {
        return event.getChannel();
    }

    @Nonnull
    public Config getConfig() {
        return config;
    }

    @Nullable
    public OptionMapping getOption(@Nonnull String name) {
        return event.getOption(name);
    }

    @Nonnull
    public String getName() {
        return event.getName();
    }

    @Nonnull
    public OptionMapping getOptionNonNull(@Nonnull String name) {
        OptionMapping ret = event.getOption(name);
        if (ret == null) {
            log.error("OptionMapping is null for option: {}, event: {}", name, event);
            throw new IllegalArgumentException("OptionMapping is null");
        }
        return ret;
    }

    @Override
    public String toString() {
        return "SlashCommandContext{" +
                "event=" + event +
                ", command=" + event.getFullCommandName() +
                ", options=" + event.getOptions() +
                '}';
    }
}
