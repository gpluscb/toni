package com.github.gpluscb.toni.command;

import com.github.gpluscb.toni.Config;
import com.github.gpluscb.toni.util.StringTokenizer;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.requests.restaction.MessageAction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageCommandContext implements ICommandContext<MessageReceivedEvent, MessageAction> {
    private static final Logger log = LogManager.getLogger(MessageCommandContext.class);

    // TODO: This infrastructure doesn't allow for more/custom prefixes or multiple word long commands
    // 0 is prefix, 1 is command
    private static final int ARG_OFFSET = 2;

    // TODO: Less ugly or something
    private static final StringTokenizer TOKENIZER = new StringTokenizer('"', '`');

    @Nonnull
    private final MessageReceivedEvent event;
    @Nonnull
    private final StringTokenizer.TokenList tokens;

    @Nonnull
    private final Config config;

    public MessageCommandContext(@Nonnull MessageReceivedEvent event, @Nonnull Config config) {
        this.event = event;
        tokens = TOKENIZER.tokenize(event.getMessage().getContentRaw());
        this.config = config;
    }

    /**
     * Assumes perms
     * Except it works around missing MESSAGE_HISTORY
     */
    @Nonnull
    @CheckReturnValue
    @Override
    public MessageAction reply(@Nonnull Message message) {
        String content = message.getContentRaw();
        log.debug("Reply: {}", content.isEmpty() ? message.getEmbeds() : content);

        MessageReceivedEvent e = getEvent();
        if (e.isFromGuild() && !e.getGuild().getSelfMember().hasPermission(e.getGuildChannel(), Permission.MESSAGE_HISTORY))
            return e.getGuildChannel().sendMessage(message);
        else
            return getMessage().reply(message);
    }

    @Nonnull
    public List<String> getArgs() {
        List<String> tokens = this.tokens.getTokens();
        return tokens.subList(ARG_OFFSET, tokens.size());
    }

    public int getArgNum() {
        return tokens.getTokenNum() - ARG_OFFSET;
    }

    @Nonnull
    public String getArg(int index) {
        return tokens.getToken(index + ARG_OFFSET);
    }

    /**
     * @param end exclusive
     */
    @Nonnull
    public String getArgsRange(int start, int end) {
        return tokens.getTokenRange(start + ARG_OFFSET, end + ARG_OFFSET);
    }

    @Nonnull
    public String getArgsFrom(int start) {
        return tokens.getTokensFrom(start + ARG_OFFSET);
    }

    @Nullable
    public User getUserMentionArg(int index) {
        String arg = getArg(index);

        Pattern userPattern = Message.MentionType.USER.getPattern();
        Matcher matcher = userPattern.matcher(arg);
        if (!matcher.matches()) return null;

        // First group is id
        String id = matcher.group(1);

        // Can't rely on cache, so this list is the "cache" we work with
        List<User> mentionedUsers = getMessage().getMentionedUsers();

        User mention = mentionedUsers.stream().filter(user -> user.getId().equals(id)).findAny().orElse(null);
        if (mention == null)
            log.info("Arg fits user mention pattern, not found in mentionedUsers, arg: {}, id: {}, content: {}, mentionedUsers: {}", arg, id, getContent(), mentionedUsers);

        return mention;
    }

    @Nullable
    public Member getMemberMentionArg(int index) {
        String arg = getArg(index);

        Pattern memberPattern = Message.MentionType.USER.getPattern();
        Matcher matcher = memberPattern.matcher(arg);
        if (!matcher.matches()) return null;

        // First group is id
        String id = matcher.group(1);

        // Can't rely on cache, so this list is the "cache" we work with
        List<Member> mentionedMembers = getMessage().getMentionedMembers();

        return mentionedMembers.stream().filter(member -> member.getId().equals(id)).findAny().orElse(null);
    }

    @Nullable
    public Role getRoleMentionArg(int index) {
        String arg = getArg(index);

        Pattern rolePattern = Message.MentionType.ROLE.getPattern();
        Matcher matcher = rolePattern.matcher(arg);
        if (!matcher.matches()) return null;

        // First group is id
        String id = matcher.group(1);

        List<Role> mentionedRoles = getMessage().getMentionedRoles();

        Role mention = mentionedRoles.stream().filter(role -> role.getId().equals(id)).findAny().orElse(null);
        if (mention == null)
            log.info("Arg fits role mention pattern, not found in mentionedRoles, arg: {}, id: {}, content: {}, mentionedRoles: {}", arg, id, getContent(), mentionedRoles);

        return mention;
    }

    @Nullable
    public TextChannel getChannelMentionArg(int index) {
        String arg = getArg(index);

        Pattern channelPattern = Message.MentionType.CHANNEL.getPattern();
        Matcher matcher = channelPattern.matcher(arg);
        if (!matcher.matches()) return null;

        // First group is id
        String id = matcher.group(1);

        // Working with mentioned channels cache because it's smaller
        List<TextChannel> mentionedChannels = getMessage().getMentionedChannels();

        TextChannel mention = mentionedChannels.stream().filter(channel -> channel.getId().equals(id)).findAny().orElse(null);
        if (mention == null)
            log.info("Arg fits channel mention pattern, not found in mentionedChannels, arg: {}, id: {}, content: {}, mentionedChannels: {}", arg, id, getContent(), mentionedChannels);

        return mention;
    }

    @Nonnull
    public String getInvokedName() {
        return tokens.getToken(1);
    }

    @Nonnull
    public String getInvokedPrefix() {
        return tokens.getToken(0);
    }

    @Nonnull
    public StringTokenizer.TokenList getTokens() {
        return tokens;
    }

    @Nonnull
    @Override
    public MessageReceivedEvent getEvent() {
        return event;
    }

    @Nonnull
    @Override
    public JDA getJDA() {
        return event.getJDA();
    }

    @Nonnull
    public Message getMessage() {
        return event.getMessage();
    }

    @Nonnull
    public String getContent() {
        return event.getMessage().getContentRaw();
    }

    @Nonnull
    @Override
    public User getUser() {
        return event.getAuthor();
    }

    @Nullable
    @Override
    public Member getMember() {
        return event.getMember();
    }

    @Override
    @Nonnull
    public Config getConfig() {
        return config;
    }

    @Nonnull
    public MessageChannel getChannel() {
        return event.getChannel();
    }

    @Override
    public String toString() {
        return "CommandContext{" +
                "event=" + event +
                ", tokens=" + tokens +
                '}';
    }
}
