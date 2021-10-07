package com.github.gpluscb.toni.command.components;

import com.github.gpluscb.toni.util.OneOfTwo;
import com.github.gpluscb.toni.util.discord.ChannelChoiceWaiter;
import com.github.gpluscb.toni.util.discord.TwoUsersChoicesActionMenu;
import com.github.gpluscb.toni.util.smash.Character;
import com.github.gpluscb.toni.util.smash.Ruleset;
import com.github.gpluscb.toni.util.smash.SmashSet;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class SmashSetMenu extends TwoUsersChoicesActionMenu {
    @Nonnull
    private final ChannelChoiceWaiter channelWaiter;

    @Nonnull
    private final Ruleset ruleset;
    @Nonnull
    private final List<Character> characters;

    @Nullable
    private final RPSInfo rpsInfo;

    private final long strikeTimeout;
    @Nonnull
    private final TimeUnit strikeUnit;

    private final long doubleBlindTimeout;
    @Nonnull
    private final TimeUnit doubleBlindUnit;

    private final long reportGameTimeout;
    @Nonnull
    private final TimeUnit reportGameUnit;
    @Nonnull
    private final String user1Display;
    @Nonnull
    private final String user2Display;

    private final long banTimeout;
    @Nonnull
    private final TimeUnit banUnit;
    private final long pickStageTimeout;
    @Nonnull
    private final TimeUnit pickStageUnit;

    private final long winnerCharPickTimeout;
    @Nonnull
    private final TimeUnit winnerCharPickUnit;

    private final long loserCharCounterpickTimeout;
    @Nonnull
    private final TimeUnit loserCharCounterpickUnit;

    @Nonnull
    private final TwoUsersChoicesActionMenu startUnderlying;

    @Nonnull
    private final SmashSet set;
    @Nullable
    private SmashSet.SetState state;

    public SmashSetMenu(@Nonnull ChannelChoiceWaiter channelWaiter, long user1, long user2, @Nonnull Ruleset ruleset, int firstToWhatScore,
                        @Nullable RPSInfo rpsInfo,
                        long strikeTimeout, @Nonnull TimeUnit strikeUnit) {
        super(channelWaiter.getEventWaiter(), user1, user2, strikeTimeout, strikeUnit);

        this.channelWaiter = channelWaiter;

        this.ruleset = ruleset;
        this.rpsInfo = rpsInfo;

        set = new SmashSet(ruleset, firstToWhatScore, rpsInfo != null);

        // TODO: Potentially doubleblind first

        if (rpsInfo != null) {
            startUnderlying = new RPSAndStrikeStagesMenu.Builder()
                    .setWaiter(getWaiter())
                    .setUsers(user1, user2)
                    .setStart(rpsInfo.getStart())
                    .setRuleset(ruleset)
                    .setRpsTimeout(rpsInfo.getTimeout(), rpsInfo.getUnit())
                    .setStrikeTimeout(strikeTimeout, strikeUnit)
                    .setStrikeFirstChoiceTimeout(rpsInfo.getStrikeFirstChoiceTimeout(), rpsInfo.getStrikeFirstChoiceUnit())
                    .setOnRPSTimeout(this::onRPSTimeout)
                    .setOnStrikeFirstChoice(this::onStrikeFirstChoice)
                    .setOnStrikeFirstTimeout(this::onStrikeFirstTimeout)
                    .setOnUserStrikes(this::onUserStrikes)
                    .setOnStrikeTimeout(this::onStrikeTimeout)
                    .build();
        } else {

        }
    }

    @Nonnull
    private RPSAndStrikeStagesMenu createRPSAndStrikeStagesMenu() {
        return new RPSAndStrikeStagesMenu.Builder()
                .setWaiter(getWaiter())
                .setUsers(getUser1(), getUser2())
                .setStart(rpsInfo.getStart())
                .setRuleset(ruleset)
                .setRpsTimeout(rpsInfo.getTimeout(), rpsInfo.getUnit())
                .setStrikeTimeout(strikeTimeout, strikeUnit)
                .setStrikeFirstChoiceTimeout(rpsInfo.getStrikeFirstChoiceTimeout(), rpsInfo.getStrikeFirstChoiceUnit())
                .setOnRPSTimeout(this::onRPSTimeout)
                .setOnStrikeFirstChoice(this::onStrikeFirstChoice)
                .setOnStrikeFirstTimeout(this::onStrikeFirstTimeout)
                .setOnUserStrikes(this::onUserStrikes)
                .setOnStrikeTimeout(this::onStrikeTimeout)
                .build();
    }

    @Nonnull
    private BlindPickMenu createDoubleBlindMenu() {
        Message start = new MessageBuilder().build(); // TODO

        return new BlindPickMenu.Builder()
                .setChannelWaiter(channelWaiter)
                .setUsers(Arrays.asList(getUser1(), getUser2()))
                .setStart(start)
                .setTimeout(doubleBlindTimeout, doubleBlindUnit)
                .setCharacters(characters)
                .setOnResult(this::onDoubleBlindResult)
                .setOnTimeout(this::onDobleBlindTimeout)
                .setOnFailedInit(this::onFailedDoubleBlindInit)
                .build();
    }

    @Nonnull
    private ReportGameMenu createReportGameMenu() {
        Message start = new MessageBuilder().build(); // TODO

        return new ReportGameMenu.Builder()
                .setWaiter(getWaiter())
                .setUsers(getUser1(), getUser2())
                .setUsersDisplay(user1Display, user2Display)
                .setStart(start)
                .setTimeout(reportGameTimeout, reportGameUnit)
                .setOnResult(this::onReportGameResult)
                .setOnTimeout(this::onReportGameTimeout)
                .build();
    }

    @Nonnull
    private BanPickStagesMenu createBanPickStagesMenu(long banningUser, @Nonnull List<Integer> dsrIllegalStages) {
        // TODO: Get info from args out of set??

        long pickingUser = banningUser == getUser1() ? getUser2() : getUser1();

        return new BanPickStagesMenu.Builder()
                .setWaiter(getWaiter())
                .setUsers(banningUser, pickingUser)
                .setRuleset(ruleset)
                .setDsrIllegalStages(dsrIllegalStages)
                .setBanTimeout(banTimeout, banUnit)
                .setPickTimeout(pickStageTimeout, pickStageUnit)
                .setOnBanResult(this::onBanResult)
                .setOnBanTimeout(this::onBanTimeout)
                .setOnPickResult(this::onStagePickResult)
                .setOnPickTimeout(this::onStagePickTimeout)
                .build();
    }

    @Nonnull
    private CharPickMenu createWinnerCharPickMenu(long winner, long channelId) {
        // TODO: Get info from args out of set??

        Message start = new MessageBuilder().build(); // TODO

        return new CharPickMenu.Builder()
                .setChannelWaiter(channelWaiter)
                .setUser(winner)
                .setChannelId(channelId) // TODO: Maybe this will be changed later???
                .setCharacters(characters)
                .setStart(start)
                .setTimeout(winnerCharPickTimeout, winnerCharPickUnit)
                .setOnResult(this::onWinnerCharPickResult)
                .setOnTimeout(this::onWinnerCharPickTimeout)
                .setOnFailedInit(this::onFailedWinnerCharPickInit)
                .build();
    }

    @Nonnull
    private CharPickMenu createLoserCharCounterpickMenu(long loser, long channelId) {
        // TODO: Get info from args out of set??

        Message start = new MessageBuilder().build(); // TODO

        return new CharPickMenu.Builder()
                .setChannelWaiter(channelWaiter)
                .setUser(loser)
                .setChannelId(channelId) // TODO: Maybe this will be changed later???
                .setCharacters(characters)
                .setStart(start)
                .setTimeout(loserCharCounterpickTimeout, loserCharCounterpickUnit)
                .setOnResult(this::onLoserCharCounterpickResult)
                .setOnTimeout(this::onLoserCharCounterpickTimeout)
                .setOnFailedInit(this::onFailedLoserCharCounterpickInit)
                .build();
    }

    private synchronized void onStrikeFirstChoice(@Nonnull RPSAndStrikeStagesMenu.StrikeFirstChoiceResult result, @Nonnull ButtonClickEvent event) {
        SmashSet.Player winner = result.getUserMakingChoice() == getUser1() ? SmashSet.Player.PLAYER1 : SmashSet.Player.PLAYER2;
        SmashSet.Player firstStriker = result.getFirstStriker() == getUser1() ? SmashSet.Player.PLAYER1 : SmashSet.Player.PLAYER2;

        // At this point it will be displayed => not null
        //noinspection ConstantConditions
        state = ((SmashSet.SetRPSState) state).completeRPS(winner, firstStriker);
    }

    private synchronized void onUserStrikes(@Nonnull StrikeStagesMenu.UserStrikesInfo info, @Nonnull ButtonClickEvent event) {
        // At this point it will be displayed => not null
        @SuppressWarnings("ConstantConditions")
        OneOfTwo<SmashSet.SetDoubleBlindState, SmashSet.SetInGameState> newState = ((SmashSet.SetStarterStrikingState) state)
                .strikeStages(info.getStruckStagesIds());

        if (newState == null) return;
        // We have completed stage striking

        state = newState.map(doubleBlind -> doubleBlind, inGame -> inGame);

        event.deferEdit().queue();

        newState.map(
                doubleBlind -> createDoubleBlindMenu(),
                inGame -> createReportGameMenu()
        ).display(event.getMessage());
    }

    private synchronized void onDoubleBlindResult(@Nonnull BlindPickMenu.BlindPickResult result) {
        // Will always be found
        @SuppressWarnings("OptionalGetWithoutIsPresent")
        Character user1Choice = result.getPicks().stream()
                .filter(choice -> choice.getUserId() == getUser1())
                .map(ChannelChoiceWaiter.UserChoiceInfo::getChoice)
                .findAny()
                .get();

        // Will always be found
        @SuppressWarnings("OptionalGetWithoutIsPresent")
        Character user2Choice = result.getPicks().stream()
                .filter(choice -> choice.getUserId() == getUser2())
                .map(ChannelChoiceWaiter.UserChoiceInfo::getChoice)
                .findAny()
                .get();

        // At this point it will be displayed => not null
        @SuppressWarnings("ConstantConditions")
        OneOfTwo<SmashSet.SetRPSState, SmashSet.SetInGameState> newState = ((SmashSet.SetDoubleBlindState) state)
                .completeDoubleBlind(user1Choice, user2Choice);

        state = newState.map(rps -> rps, inGame -> inGame);

        newState.map(
                rps -> createRPSAndStrikeStagesMenu(),
                inGame -> createReportGameMenu()
        ).display(event.getMessage());
    }

    private synchronized void onReportGameResult(@Nonnull ReportGameMenu.ReportGameResult result, @Nonnull ButtonClickEvent event) {
        SmashSet.Player winner = result.getWinner() == getUser1() ? SmashSet.Player.PLAYER1 : SmashSet.Player.PLAYER2;

        // At this point it will be displayed => not null
        @SuppressWarnings("ConstantConditions")
        OneOfTwo<OneOfTwo<SmashSet.SetWinnerStageBanState, SmashSet.SetWinnerCharPickState>, SmashSet.SetCompletedState> newState =
                ((SmashSet.SetInGameState) state).completeGame(winner);

        state = newState.map(notComplete -> notComplete.map(ban -> ban, chars -> chars), complete -> complete);

        newState.onT(notComplete -> {
            event.deferEdit().queue();

            notComplete.map(
                    stageBan -> createBanPickStagesMenu(result.getWinner(), stageBan.getDSRIllegalStages()),
                    charPick -> createWinnerCharPickMenu(result.getWinner(), event.getChannel().getIdLong())
            ).display(event.getMessage());
        }).onU(completed -> {
            // TODO
            event.editMessage("Wowee you completed the match!!").queue();
        });
    }

    private synchronized void onBanResult(@Nonnull BanStagesMenu.BanResult result, @Nonnull ButtonClickEvent event) {
        // At this point it will be displayed => not null
        //noinspection ConstantConditions
        state = ((SmashSet.SetWinnerStageBanState) state).banStages(result.getBannedStageIds()); // TODO: Make this stuff a set over in the menu
    }

    private synchronized void onStagePickResult(@Nonnull PickStageMenu.PickStageResult result, @Nonnull ButtonClickEvent event) {
        // At this point it will be displayed => not null
        @SuppressWarnings("ConstantConditions")
        OneOfTwo<SmashSet.SetWinnerCharPickState, SmashSet.SetInGameState> newState = ((SmashSet.SetLoserStageCounterpickState) state)
                .pickStage(result.getPickedStageId());

        event.deferEdit().queue();

        state = newState.map(charPick -> charPick, inGame -> inGame);

        newState.map(
                charPick -> createWinnerCharPickMenu(winner, event.getChannel().getIdLong()),
                inGame -> createReportGameMenu()
        ).display(event.getMessage());
    }

    private synchronized void onWinnerCharPickResult(@Nonnull CharPickMenu.CharPickResult result) {
        // At this point it will be displayed => not null
        //noinspection ConstantConditions
        state = ((SmashSet.SetWinnerCharPickState) state).pickCharacter(result.getPickedCharacter());

        createLoserCharCounterpickMenu(loser, event.getChannel().getIdLong()).display(event.getMessage());
    }

    private synchronized void onLoserCharCounterpickResult(@Nonnull CharPickMenu.CharPickResult result) {
        // At this point it will be displayed => not null
        @SuppressWarnings("ConstantConditions")
        OneOfTwo<SmashSet.SetWinnerStageBanState, SmashSet.SetInGameState> newState = ((SmashSet.SetLoserCharCounterpickState) state)
                .pickCharacter(result.getPickedCharacter());

        state = newState.map(ban -> ban, inGame -> inGame);

        newState.map(
                stageBan -> createBanPickStagesMenu(winner, dsrIllegalStages),
                inGame -> createReportGameMenu()
        ).display(event.getMessage());
    }

    public static class RPSInfo {
        private final long timeout;
        @Nonnull
        private final TimeUnit unit;
        private final Message start;
        private final long strikeFirstChoiceTimeout;
        @Nonnull
        private final TimeUnit strikeFirstChoiceUnit;

        public RPSInfo(long timeout, @Nonnull TimeUnit unit, Message start, long strikeFirstChoiceTimeout, @Nonnull TimeUnit strikeFirstChoiceUnit) {
            this.timeout = timeout;
            this.unit = unit;
            this.start = start;
            this.strikeFirstChoiceTimeout = strikeFirstChoiceTimeout;
            this.strikeFirstChoiceUnit = strikeFirstChoiceUnit;
        }

        public long getTimeout() {
            return timeout;
        }

        @Nonnull
        public TimeUnit getUnit() {
            return unit;
        }

        public Message getStart() {
            return start;
        }

        public long getStrikeFirstChoiceTimeout() {
            return strikeFirstChoiceTimeout;
        }

        @Nonnull
        public TimeUnit getStrikeFirstChoiceUnit() {
            return strikeFirstChoiceUnit;
        }
    }
}
