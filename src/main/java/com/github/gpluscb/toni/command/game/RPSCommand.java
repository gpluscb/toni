package com.github.gpluscb.toni.command.game;

import com.github.gpluscb.toni.command.Command;
import com.github.gpluscb.toni.command.CommandContext;
import com.github.gpluscb.toni.command.components.RPSComponent;
import com.github.gpluscb.toni.util.FailLogger;
import com.github.gpluscb.toni.util.MiscUtil;
import com.github.gpluscb.toni.util.OneOfTwo;
import com.github.gpluscb.toni.util.PairNonnull;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class RPSCommand implements Command {
    private static final Logger log = LogManager.getLogger(RPSCommand.class);

    @Nonnull
    private final RPSComponent component;

    public RPSCommand(@Nonnull RPSComponent component) {
        this.component = component;
    }

    @Override
    public void execute(@Nonnull CommandContext ctx) {
        OneOfTwo<PairNonnull<Long, Long>, MiscUtil.TwoUserArgsErrorType> argResult = MiscUtil.getTwoUserArgs(ctx);

        MiscUtil.TwoUserArgsErrorType error = argResult.getU().orElse(null);
        if (error != null) {
            String reply;
            switch (error) {
                case WRONG_NUMBER_ARGS:
                    reply = "You must mention either one or two users.";
                    break;
                case NOT_USER_MENTION_ARG:
                    reply = "Arguments must be user mentions.";
                    break;
                case BOT_USER:
                    reply = "This command doesn't support bot or webhook users.";
                    break;
                case USER_1_EQUALS_USER_2:
                    reply = "I can't have someone rps with themselves, what would that even look like?";
                    break;
                default:
                    throw new IllegalStateException("Non exhaustive switch over error");
            }

            ctx.reply(reply).queue();
        }

        PairNonnull<Long, Long> users = argResult.getTOrThrow();
        long user1 = users.getT();
        long user2 = users.getU();

        String user1Mention = MiscUtil.mentionUser(user1);
        String user2Mention = MiscUtil.mentionUser(user2);

        Message start = new MessageBuilder(String.format("Alrighty! %s and %s, please click on the button of your choice now. " +
                "You have three (3) minutes!", user1Mention, user2Mention))
                .mentionUsers(user1, user2)
                .build();

        component.sendRPSReplying(ctx.getMessage(), start, user1, user2).whenComplete(FailLogger.logFail((pair, timeout) -> {
            if (timeout != null) {
                if (!(timeout instanceof RPSComponent.RPSTimeoutException)) {
                    log.error("Failed RPS completion not RPSTimeoutException", timeout);
                    // TODO: Tell the user?
                    return;
                }

                RPSComponent.RPSTimeoutException rpsTimeout = (RPSComponent.RPSTimeoutException) timeout;
                MessageChannel channel = rpsTimeout.getChannel();
                RPSComponent.RPS choice1 = rpsTimeout.getChoiceA();
                RPSComponent.RPS choice2 = rpsTimeout.getChoiceB();
                long messageId = rpsTimeout.getMessageId();

                if (channel == null) return;

                // TODO: Variable naming
                StringBuilder lazyIdiots = new StringBuilder();
                if (choice1 == null) {
                    lazyIdiots.append(MiscUtil.mentionUser(user1));
                    if (choice2 == null) lazyIdiots.append(" and ");
                }
                if (choice2 == null) lazyIdiots.append(MiscUtil.mentionUser(user2));

                channel.editMessageById(messageId, String.format("The three (3) minutes are done. Not all of you have given me your choice. Shame on you, %s!", lazyIdiots))
                        .mentionUsers(user1, user2)
                        .setActionRows()
                        .queue();
                return;
            }

            RPSComponent.RPSResult result = pair.getT();
            ButtonClickEvent e = pair.getU();

            String outcome;
            switch (result.getWinner()) {
                case Tie:
                    outcome = "It's a tie!";
                    break;
                case A:
                    outcome = String.format("%s won!", user1Mention);
                    break;
                case B:
                    outcome = String.format("%s won!", user2Mention);
                    break;
                default:
                    throw new IllegalStateException("Not all results covered");
            }

            e.reply(String.format("It has been decided! %s chose %s, and %s chose %s. That means %s",
                    user1Mention, result.getChoiceA().getDisplayName(), user2Mention, result.getChoiceB().getDisplayName(), outcome))
                    .mentionUsers(user1, user2)
                    .queue();
        }));
    }

    @Nonnull
    @Override
    public String[] getAliases() {
        return new String[]{"rps", "rockpaperscissors"};
    }

    @Nullable
    @Override
    public String getShortHelp() {
        return "Helps you play rock paper scissors. Usage: `rps [PLAYER 1] <PLAYER 2>`";
    }

    @Nullable
    @Override
    public String getDetailedHelp() {
        return "`rps [PLAYER 1 (default: message author)] <PLAYER 2>`\n" +
                "Helps you play the world famous game of [rock paper scissors](https://en.wikipedia.org/wiki/Rock_paper_scissors). " +
                "Aliases: `rockpaperscissors`, `rps`";
    }
}
