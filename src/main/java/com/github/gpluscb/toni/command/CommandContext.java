package com.github.gpluscb.toni.command;

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

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class CommandContext implements ICommandContext<Event, RestAction<?>> {
    @Nonnull
    private final OneOfTwo<MessageCommandContext, SlashCommandContext> context;

    public CommandContext(@Nonnull OneOfTwo<MessageCommandContext, SlashCommandContext> context) {
        this.context = context;
    }

    @Nonnull
    public static CommandContext fromMessageReceivedEvent(@Nonnull MessageReceivedEvent e) {
        return new CommandContext(OneOfTwo.ofT(new MessageCommandContext(e)));
    }

    @Nonnull
    public static CommandContext fromSlashCommandEvent(@Nonnull SlashCommandEvent e) {
        return new CommandContext(OneOfTwo.ofU(new SlashCommandContext(e)));
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
    public RestAction<?> reply(@Nonnull Message message) {
        return context.map(ctx -> ctx.reply(message), ctx -> ctx.reply(message));
    }

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

    @Override
    public String toString() {
        return "CommandContext{" +
                "context=" + context +
                '}';
    }
}
