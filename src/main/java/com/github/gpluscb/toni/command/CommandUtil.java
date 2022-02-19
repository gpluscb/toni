package com.github.gpluscb.toni.command;

import com.github.gpluscb.toni.util.OneOfTwo;
import net.dv8tion.jda.api.entities.User;

import javax.annotation.Nonnull;

public class CommandUtil {
    public enum TwoUserArgsErrorType {
        WRONG_NUMBER_ARGS,
        NOT_USER_MENTION_ARG,
        BOT_USER,
        USER_1_EQUALS_USER_2,
    }

    public record OneOrTwoUserArgs(@Nonnull User user1User,
                                   @Nonnull User user2User, boolean twoArgumentsGiven) {
        public long getUser1() {
            return user1User.getIdLong();
        }

        public long getUser2() {
            return user2User.getIdLong();
        }
    }

    /**
     * Only useful if those one or two user mentions are <b>all</b> the arguments.
     * If there is one argument given user 2 will default to the author.
     */
    // TODO: This needs an overhaul, probably should have List of users with minUsers and maxUsers and defaultAuthorIfMin or sth like that
    @Nonnull
    public static OneOfTwo<OneOrTwoUserArgs, TwoUserArgsErrorType> getTwoUserArgs(@Nonnull MessageCommandContext ctx, boolean allowMoreArgs) {
        int argNum = ctx.getArgNum();
        if (ctx.getArgNum() < 1 || (!allowMoreArgs && ctx.getArgNum() > 2))
            return OneOfTwo.ofU(TwoUserArgsErrorType.WRONG_NUMBER_ARGS);

        User user1User = ctx.getUserMentionArg(0);
        boolean twoArgumentsGiven = argNum >= 2;
        User user2User = twoArgumentsGiven ? ctx.getUserMentionArg(1) : ctx.getUser();
        if (user2User == null && allowMoreArgs) {
            user2User = ctx.getUser();
            // TODO: Better naming
            twoArgumentsGiven = false;
        }
        if (user1User == null || user2User == null)
            return OneOfTwo.ofU(TwoUserArgsErrorType.NOT_USER_MENTION_ARG);


        if (user1User.isBot() || user2User.isBot())
            return OneOfTwo.ofU(TwoUserArgsErrorType.BOT_USER);

        if (user1User.getIdLong() == user2User.getIdLong())
            return OneOfTwo.ofU(TwoUserArgsErrorType.USER_1_EQUALS_USER_2);

        return OneOfTwo.ofT(new OneOrTwoUserArgs(user1User, user2User, twoArgumentsGiven));
    }
}
