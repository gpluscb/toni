package com.github.gpluscb.toni.command;

import com.github.gpluscb.toni.Config;
import com.github.gpluscb.toni.util.OneOfTwo;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.Event;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.utils.AllowedMentions;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class CommandContext<T extends RestAction<?> & AllowedMentions<T>> implements ICommandContext<Event, T> {
    @Nonnull
    private final OneOfTwo<MessageCommandContext, SlashCommandContext> context;

    private CommandContext(@Nonnull OneOfTwo<MessageCommandContext, SlashCommandContext> context) {
        this.context = context;
    }

    @Nonnull
    public static CommandContext<?> fromMessageReceivedEvent(@Nonnull MessageReceivedEvent e, @Nonnull Config config) {
        return new CommandContext<>(OneOfTwo.ofT(new MessageCommandContext(e, config)));
    }

    @Nonnull
    public static CommandContext<?> fromSlashCommandEvent(@Nonnull SlashCommandEvent e, @Nonnull Config config) {
        return new CommandContext<>(OneOfTwo.ofU(new SlashCommandContext(e, config)));
    }

    @Nonnull
    public OneOfTwo<MessageCommandContext, SlashCommandContext> getContext() {
        return context;
    }

    @Nullable
    public MessageCommandContext getMessageCommandContext() {
        return context.getT().orElse(null);
    }

    @Nullable
    public SlashCommandContext getSlashCommandContext() {
        return context.getU().orElse(null);
    }

    @Nonnull
    @CheckReturnValue
    @Override
    public T reply(@Nonnull Message message) {
        // TODO: Uhhhhhhhh how do we actually do this right???
        return (T) context.map(ctx -> ctx.reply(message), ctx -> ctx.reply(message));
    }

    @Nonnull
    @Override
    public Event getEvent() {
        return context.map(MessageCommandContext::getEvent, SlashCommandContext::getEvent);
    }

    @Nonnull
    @Override
    public JDA getJDA() {
        return context.map(MessageCommandContext::getJDA, SlashCommandContext::getJDA);
    }

    @Nonnull
    @Override
    public User getUser() {
        return context.map(MessageCommandContext::getUser, SlashCommandContext::getUser);
    }

    @Nullable
    @Override
    public Member getMember() {
        return context.map(MessageCommandContext::getMember, SlashCommandContext::getMember);
    }

    @Nonnull
    @Override
    public MessageChannel getChannel() {
        return context.map(MessageCommandContext::getChannel, SlashCommandContext::getChannel);
    }

    @Nonnull
    @Override
    public Config getConfig() {
        return context.map(MessageCommandContext::getConfig, SlashCommandContext::getConfig);
    }

    @Override
    public String toString() {
        return "CommandContext{" +
                "context=" + context +
                '}';
    }
}
