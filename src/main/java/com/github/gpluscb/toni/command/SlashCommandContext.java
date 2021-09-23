package com.github.gpluscb.toni.command;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyAction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class SlashCommandContext implements ICommandContext<SlashCommandEvent, ReplyAction> {
    private static final Logger log = LogManager.getLogger(SlashCommandContext.class);

    @Nonnull
    private final SlashCommandEvent event;

    public SlashCommandContext(@Nonnull SlashCommandEvent event) {
        this.event = event;
    }

    @Nonnull
    @Override
    public SlashCommandEvent getEvent() {
        return event;
    }

    @Nonnull
    @CheckReturnValue
    @Override
    public ReplyAction reply(@Nonnull Message message) {
        String content = message.getContentRaw();
        log.debug("Reply: {}", content.isEmpty() ? message.getEmbeds() : content);

        return event.reply(message);
    }

    @Nonnull
    @Override
    public JDA getJDA() {
        return event.getJDA();
    }

    @Nonnull
    @Override
    public User getUser() {
        return event.getUser();
    }

    @Nullable
    @Override
    public Member getMember() {
        return event.getMember();
    }

    @Nonnull
    @Override
    public MessageChannel getChannel() {
        return event.getChannel();
    }

    @Override
    public String toString() {
        return "SlashCommandContext{" +
                "event=" + event +
                '}';
    }
}
