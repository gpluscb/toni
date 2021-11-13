package com.github.gpluscb.toni.command;

import com.github.gpluscb.toni.Config;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.requests.RestAction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@SuppressWarnings("ClassCanBeRecord")
public class SlashCommandContext implements ICommandContext<SlashCommandEvent, RestAction<?>> {
    private static final Logger log = LogManager.getLogger(SlashCommandContext.class);

    @Nonnull
    private final SlashCommandEvent event;

    @Nonnull
    private final Config config;

    public SlashCommandContext(@Nonnull SlashCommandEvent event, @Nonnull Config config) {
        this.event = event;
        this.config = config;
    }

    @Nonnull
    @Override
    public SlashCommandEvent getEvent() {
        return event;
    }

    @Nonnull
    @CheckReturnValue
    @Override
    public RestAction<?> reply(@Nonnull Message message) {
        String content = message.getContentRaw();
        log.debug("Reply: {}", content.isEmpty() ? message.getEmbeds() : content);

        return event.isAcknowledged() ?
                event.getHook().sendMessage(message)
                : event.reply(message);
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
                ", command=" + event.getCommandPath() +
                ", options=" + event.getOptions() +
                '}';
    }
}
