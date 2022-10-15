package com.github.gpluscb.toni.util.discord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.IMentionable;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.exceptions.RateLimitedException;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.utils.AllowedMentions;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class ReplyAction<T, R extends RestAction<T> & AllowedMentions<R>> implements RestAction<T>, AllowedMentions<R> {
    @Nonnull private final R action;

    public ReplyAction(@Nonnull R action) {
        this.action = action;
    }

    @Nonnull
    @Override
    public JDA getJDA() {
        return action.getJDA();
    }

    @Nonnull
    @Override
    public RestAction<T> setCheck(@Nullable BooleanSupplier checks) {
        return action.setCheck(checks);
    }

    @Override
    public void queue(@Nullable Consumer<? super T> success, @Nullable Consumer<? super Throwable> failure) {
        action.queue(success, failure);
    }

    @Override
    public T complete(boolean shouldQueue) throws RateLimitedException {
        return action.complete(shouldQueue);
    }

    @Nonnull
    @Override
    public CompletableFuture<T> submit(boolean shouldQueue) {
        return action.submit(shouldQueue);
    }

    @Nonnull
    @Override
    public R mentionRepliedUser(boolean mention) {
        return action.mentionRepliedUser(mention);
    }

    @Nonnull
    @Override
    public R allowedMentions(@Nullable Collection<Message.MentionType> allowedMentions) {
        return action.allowedMentions(allowedMentions);
    }

    @Nonnull
    @Override
    public R mention(@Nonnull IMentionable... mentions) {
        return action.mention(mentions);
    }

    @Nonnull
    @Override
    public R mentionUsers(@Nonnull String... userIds) {
        return action.mentionUsers(userIds);
    }

    @Nonnull
    @Override
    public R mentionRoles(@Nonnull String... roleIds) {
        return action.mentionRoles(roleIds);
    }
}
