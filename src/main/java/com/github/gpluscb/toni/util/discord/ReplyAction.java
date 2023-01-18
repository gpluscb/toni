package com.github.gpluscb.toni.util.discord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.IMentionable;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.exceptions.RateLimitedException;
import net.dv8tion.jda.api.interactions.components.LayoutComponent;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.utils.AttachedFile;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageRequest;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class ReplyAction<T, R extends RestAction<T> & MessageRequest<R>> implements RestAction<T>, MessageRequest<R> {
    @Nonnull
    private final R action;

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
    public R setContent(@Nullable String content) {
        return action.setContent(content);
    }

    @Nonnull
    @Override
    public R setEmbeds(@Nonnull Collection<? extends MessageEmbed> embeds) {
        return action.setEmbeds(embeds);
    }

    @Nonnull
    @Override
    public R setComponents(@Nonnull Collection<? extends LayoutComponent> components) {
        return action.setComponents(components);
    }

    @Nonnull
    @Override
    public R setSuppressEmbeds(boolean suppress) {
        return action.setSuppressEmbeds(suppress);
    }

    @Nonnull
    @Override
    public R setFiles(@Nullable Collection<? extends FileUpload> files) {
        return action.setFiles(files);
    }

    @Nonnull
    @Override
    public R mentionRepliedUser(boolean mention) {
        return action.mentionRepliedUser(mention);
    }

    @Nonnull
    @Override
    public R setAllowedMentions(@Nullable Collection<Message.MentionType> allowedMentions) {
        return action.setAllowedMentions(allowedMentions);
    }

    @Nonnull
    @Override
    public R mention(@Nonnull Collection<? extends IMentionable> mentions) {
        return action.mention(mentions);
    }

    @Nonnull
    @Override
    public R mentionUsers(@Nonnull Collection<String> userIds) {
        return action.mentionUsers(userIds);
    }

    @Nonnull
    @Override
    public R mentionRoles(@Nonnull Collection<String> roleIds) {
        return action.mentionRoles(roleIds);
    }

    @Nonnull
    @Override
    public R applyMessage(@Nonnull Message message) {
        return action.applyMessage(message);
    }

    @Nonnull
    @Override
    public String getContent() {
        return action.getContent();
    }

    @Nonnull
    @Override
    public List<MessageEmbed> getEmbeds() {
        return action.getEmbeds();
    }

    @Nonnull
    @Override
    public List<LayoutComponent> getComponents() {
        return action.getComponents();
    }

    @Nonnull
    @Override
    public List<? extends AttachedFile> getAttachments() {
        return action.getAttachments();
    }

    @Override
    public boolean isSuppressEmbeds() {
        return action.isSuppressEmbeds();
    }

    @Nonnull
    @Override
    public Set<String> getMentionedUsers() {
        return action.getMentionedUsers();
    }

    @Nonnull
    @Override
    public Set<String> getMentionedRoles() {
        return action.getMentionedRoles();
    }

    @Nonnull
    @Override
    public EnumSet<Message.MentionType> getAllowedMentions() {
        return action.getAllowedMentions();
    }

    @Override
    public boolean isMentionRepliedUser() {
        return action.isMentionRepliedUser();
    }

    @Nonnull
    @Override
    public RestAction<T> deadline(long timestamp) {
        return action.deadline(timestamp);
    }
}
