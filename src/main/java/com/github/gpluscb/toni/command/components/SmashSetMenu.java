package com.github.gpluscb.toni.command.components;

import com.github.gpluscb.toni.util.Constants;
import com.github.gpluscb.toni.util.OneOfTwo;
import com.github.gpluscb.toni.util.discord.ActionMenu;
import com.github.gpluscb.toni.util.discord.ChannelChoiceWaiter;
import com.github.gpluscb.toni.util.discord.EmbedUtil;
import com.github.gpluscb.toni.util.discord.TwoUsersChoicesActionMenu;
import com.github.gpluscb.toni.util.smash.Character;
import com.github.gpluscb.toni.util.smash.*;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

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
    @Nonnull
    private final Consumer<SmashSetStrikeTimeoutEvent> onStrikeTimeout;

    private final long doubleBlindTimeout;
    @Nonnull
    private final TimeUnit doubleBlindUnit;
    @Nonnull
    private final Consumer<SmashSetDoubleBlindTimeoutEvent> onDoubleBlindTimeout;
    @Nonnull
    private final Consumer<SmashSetStateInfo> onDoubleBlindFailedInit;

    private final long reportGameTimeout;
    @Nonnull
    private final TimeUnit reportGameUnit;
    @Nonnull
    private final String user1Display;
    @Nonnull
    private final String user2Display;
    @Nonnull
    private final Consumer<SmashSetReportGameTimeoutEvent> onReportGameTimeout;

    private final long banTimeout;
    @Nonnull
    private final TimeUnit banUnit;
    @Nonnull
    private final Consumer<SmashSetBanTimeoutEvent> onBanTimeout;
    private final long pickStageTimeout;
    @Nonnull
    private final TimeUnit pickStageUnit;
    @Nonnull
    private final Consumer<SmashSetPickStageTimeoutEvent> onPickStageTimeout;

    private final long winnerCharPickTimeout;
    @Nonnull
    private final TimeUnit winnerCharPickUnit;
    @Nonnull
    private final Consumer<SmashSetCharPickTimeoutEvent> onWinnerCharPickTimeout;
    @Nonnull
    private final Consumer<SmashSetStateInfo> onWinnerCharPickFailedInit;

    private final long loserCharCounterpickTimeout;
    @Nonnull
    private final TimeUnit loserCharCounterpickUnit;
    @Nonnull
    private final Consumer<SmashSetCharPickTimeoutEvent> onLoserCharCounterpickTimeout;
    @Nonnull
    private final Consumer<SmashSetStateInfo> onLoserCharCounterpickFailedInit;

    @Nonnull
    private final Consumer<SmashSetStateInfo> onMessageChannelNotInCache;

    @Nonnull
    private final BiConsumer<SmashSetResult, ButtonClickEvent> onResult;

    @Nonnull
    private final ActionMenu startUnderlying;

    @Nonnull
    private final SmashSet set;
    @Nullable
    private SmashSet.SetState state;

    public SmashSetMenu(@Nonnull ChannelChoiceWaiter channelWaiter, long user1, long user2, @Nonnull Ruleset ruleset, @Nonnull List<Character> characters, int firstToWhatScore,
                        @Nullable RPSInfo rpsInfo,
                        long strikeTimeout, @Nonnull TimeUnit strikeUnit, @Nonnull Consumer<SmashSetStrikeTimeoutEvent> onStrikeTimeout,
                        long doubleBlindTimeout, @Nonnull TimeUnit doubleBlindUnit, @Nonnull Consumer<SmashSetDoubleBlindTimeoutEvent> onDoubleBlindTimeout, @Nonnull Consumer<SmashSetStateInfo> onDoubleBlindFailedInit,
                        long reportGameTimeout, @Nonnull TimeUnit reportGameUnit, @Nonnull String user1Display, @Nonnull String user2Display, @Nonnull Consumer<SmashSetReportGameTimeoutEvent> onReportGameTimeout,
                        long banTimeout, @Nonnull TimeUnit banUnit, @Nonnull Consumer<SmashSetBanTimeoutEvent> onBanTimeout,
                        long pickStageTimeout, @Nonnull TimeUnit pickStageUnit, @Nonnull Consumer<SmashSetPickStageTimeoutEvent> onPickStageTimeout,
                        long winnerCharPickTimeout, @Nonnull TimeUnit winnerCharPickUnit, @Nonnull Consumer<SmashSetCharPickTimeoutEvent> onWinnerCharPickTimeout, @Nonnull Consumer<SmashSetStateInfo> onWinnerCharPickFailedInit,
                        long loserCharCounterpickTimeout, @Nonnull TimeUnit loserCharCounterpickUnit, @Nonnull Consumer<SmashSetCharPickTimeoutEvent> onLoserCharCounterpickTimeout, @Nonnull Consumer<SmashSetStateInfo> onLoserCharCounterpickFailedInit,
                        @Nonnull Consumer<SmashSetStateInfo> onMessageChannelNotInCache, @Nonnull BiConsumer<SmashSetResult, ButtonClickEvent> onResult) {
        super(channelWaiter.getEventWaiter(), user1, user2, strikeTimeout, strikeUnit);

        this.channelWaiter = channelWaiter;

        this.ruleset = ruleset;
        this.characters = characters;
        this.rpsInfo = rpsInfo;
        this.strikeTimeout = strikeTimeout;
        this.strikeUnit = strikeUnit;
        this.onStrikeTimeout = onStrikeTimeout;
        this.doubleBlindTimeout = doubleBlindTimeout;
        this.doubleBlindUnit = doubleBlindUnit;
        this.onDoubleBlindTimeout = onDoubleBlindTimeout;
        this.onDoubleBlindFailedInit = onDoubleBlindFailedInit;
        this.reportGameTimeout = reportGameTimeout;
        this.reportGameUnit = reportGameUnit;
        this.user1Display = user1Display;
        this.user2Display = user2Display;
        this.onReportGameTimeout = onReportGameTimeout;
        this.banTimeout = banTimeout;
        this.banUnit = banUnit;
        this.onBanTimeout = onBanTimeout;
        this.pickStageTimeout = pickStageTimeout;
        this.pickStageUnit = pickStageUnit;
        this.onPickStageTimeout = onPickStageTimeout;
        this.winnerCharPickTimeout = winnerCharPickTimeout;
        this.winnerCharPickUnit = winnerCharPickUnit;
        this.onWinnerCharPickTimeout = onWinnerCharPickTimeout;
        this.onWinnerCharPickFailedInit = onWinnerCharPickFailedInit;
        this.loserCharCounterpickTimeout = loserCharCounterpickTimeout;
        this.loserCharCounterpickUnit = loserCharCounterpickUnit;
        this.onLoserCharCounterpickTimeout = onLoserCharCounterpickTimeout;
        this.onLoserCharCounterpickFailedInit = onLoserCharCounterpickFailedInit;
        this.onMessageChannelNotInCache = onMessageChannelNotInCache;
        this.onResult = onResult;

        set = new SmashSet(ruleset, firstToWhatScore, rpsInfo != null);

        if (ruleset.isBlindPickBeforeStage()) {
            // Manually set game number because we haven't tarted set yet
            Message start = new MessageBuilder(prepareEmbed("Double Blind Pick", 1)
                    .setDescription("Alright, we start by doing a double blind pick. Please DM me the character you will play in the first game now.")
                    .build())
                    .build();

            startUnderlying = createDoubleBlindMenu(start);
        } else if (rpsInfo != null) {
            // Manually set game number because we haven't tarted set yet
            Message start = new MessageBuilder(prepareEmbed("RPS", 1)
                    .setDescription("Alright, we start by playing RPS for who gets to strike first. Please choose what you pick below.")
                    .build())
                    .build();

            startUnderlying = createRPSAndStrikeStagesMenu(start, null, null, "Let's strike stages then.");
        } else {
            startUnderlying = createStrikeStagesMenu((String) null);
        }
    }

    @Override
    public void display(@Nonnull MessageChannel channel) {
        initSet();
        startUnderlying.display(channel);
    }

    @Override
    public void display(MessageChannel channel, long messageId) {
        initSet();
        startUnderlying.display(channel, messageId);
    }

    @Override
    public void displayReplying(@Nonnull MessageChannel channel, long messageId) {
        initSet();
        startUnderlying.displayReplying(channel, messageId);
    }

    @Override
    public void displaySlashReplying(@Nonnull SlashCommandEvent event) {
        initSet();
        startUnderlying.displaySlashReplying(event);
    }

    @Override
    public void displayDeferredReplying(@Nonnull InteractionHook hook) {
        initSet();
        startUnderlying.displayDeferredReplying(hook);
    }

    private void initSet() {
        if (rpsInfo == null) {
            SmashSet.Player firstStriker = ThreadLocalRandom.current().nextBoolean() ?
                    SmashSet.Player.PLAYER1
                    : SmashSet.Player.PLAYER2;

            state = set.startSetNoRPS(firstStriker).map(doubleBlind -> doubleBlind, strike -> strike);
        } else {
            state = set.startSetWithRPS().map(doubleBlind -> doubleBlind, rps -> rps);
        }
    }

    @Nonnull
    private Message createRPSTieMessage(@Nonnull RPSMenu.RPSResult rpsResult, @Nullable String messagePrepend) {
        return new MessageBuilder(prepareEmbed("RPS")
                .setDescription(String.format("%s" +
                                "The RPS was a tie (both of you chose %s), so please try that again.",
                        messagePrepend == null ? "" : String.format("%s%n", messagePrepend),
                        rpsResult.getChoice1().getDisplayName()))
                .build())
                .build();
    }

    @Nonnull
    private Message createStrikeFirstMessage(@Nonnull RPSMenu.RPSResult rpsResult, @Nullable String messagePrepend) {
        // This will be a decisive result
        //noinspection ConstantConditions
        return new MessageBuilder(prepareEmbed("RPS")
                .setDescription(String.format("%s" +
                                "**%s** chose %s, and **%s** chose %s in the rps, meaning **%s** won the RPS.%n" +
                                "**%s**, you can now decide if you want to strike first (%s), or second (%s) now.",
                        messagePrepend == null ? "" : String.format("%s%n", messagePrepend),
                        user1Display,
                        rpsResult.getChoice1().getDisplayName(),
                        user2Display,
                        rpsResult.getChoice2().getDisplayName(),
                        displayFromUser(rpsResult.getWinnerId()),
                        displayFromUser(rpsResult.getWinnerId()),
                        Constants.ONE,
                        Constants.TWO))
                .build())
                .build();
    }

    @Nonnull
    private Message createStrikeMessage(@Nonnull StrikeStagesMenu.UpcomingStrikeInfo info, @Nullable String messagePrepend) {
        long currentStriker = info.getCurrentStriker();
        int stagesToStrike = info.getStagesToStrike();

        EmbedBuilder builder = prepareEmbed("Stage Striking");
        if (messagePrepend != null) builder.appendDescription(messagePrepend).appendDescription("\n");

        if (info.isFirstStrike()) {
            builder.appendDescription(String.format("**%s**, you start by striking %d stage%s.",
                    displayFromUser(currentStriker),
                    stagesToStrike,
                    stagesToStrike == 1 ? "" : "s"));
        } else {
            builder.appendDescription(String.format("**%s**, please strike %d stage%s from the list below.",
                    displayFromUser(currentStriker),
                    stagesToStrike,
                    stagesToStrike == 1 ? "" : "s"));
        }

        return new MessageBuilder(builder.build()).build();
    }

    @Nonnull
    private Message createBanMessage(@Nonnull BanStagesMenu.UpcomingBanInfo info, @Nullable String messagePrepend) {
        int stagesToBan = info.getStagesToBan();

        return new MessageBuilder(prepareEmbed("Stage Ban / Counterpick")
                .setDescription(String.format("%s" +
                                "**%s**, please ban %d stage%s from the list below.",
                        messagePrepend == null ? "" : String.format("%s%n", messagePrepend),
                        displayFromUser(info.getBanningUser()),
                        stagesToBan,
                        stagesToBan == 1 ? "" : "s"))
                .build())
                .build();
    }

    @Nonnull
    private RPSAndStrikeStagesMenu createRPSAndStrikeStagesMenu(@Nonnull Message start, @Nullable String rpsTieMessagePrepend, @Nullable String strikeFirstMessagePrepend, @Nonnull String strikeMessagePrepend) {
        return createRPSAndStrikeStagesMenu(start,
                (rpsResult, event) -> createRPSTieMessage(rpsResult, rpsTieMessagePrepend),
                (rpsResult, event) -> createStrikeFirstMessage(rpsResult, strikeFirstMessagePrepend),
                info -> createStrikeMessage(info, strikeMessagePrepend));
    }

    @Nonnull
    private RPSAndStrikeStagesMenu createRPSAndStrikeStagesMenu(@Nonnull Message start, @Nonnull BiFunction<RPSMenu.RPSResult, ButtonClickEvent, Message> rpsTieMessageProvider, @Nonnull BiFunction<RPSMenu.RPSResult, ButtonClickEvent, Message> strikeFirstMessageProvider, @Nonnull Function<StrikeStagesMenu.UpcomingStrikeInfo, Message> strikeMessageProducer) {
        // Will only be called if we do rps
        //noinspection ConstantConditions
        return new RPSAndStrikeStagesMenu.Builder()
                .setWaiter(getWaiter())
                .setUsers(getUser1(), getUser2())
                .setStart(start)
                .setRuleset(ruleset)
                .setRpsTimeout(rpsInfo.getTimeout(), rpsInfo.getUnit())
                .setStrikeTimeout(strikeTimeout, strikeUnit)
                .setStrikeFirstChoiceTimeout(rpsInfo.getStrikeFirstChoiceTimeout(), rpsInfo.getStrikeFirstChoiceUnit())
                .setStrikeFirstMessageProvider(strikeFirstMessageProvider)
                .setRpsTieMessageProvider(rpsTieMessageProvider)
                .setStrikeMessageProducer(strikeMessageProducer)
                .setOnRPSTimeout(this::onRPSTimeout)
                .setOnStrikeFirstChoice(this::onStrikeFirstChoice)
                .setOnStrikeFirstTimeout(this::onFirstChoiceTimeout)
                .setOnUserStrikes(this::onUserStrikes)
                .setOnStrikeTimeout(this::onStrikeTimeout)
                .build();
    }

    @Nonnull
    private StrikeStagesMenu createStrikeStagesMenu(@Nullable String messagePrepend) {
        return createStrikeStagesMenu(info -> createStrikeMessage(info, messagePrepend));
    }

    @Nonnull
    private StrikeStagesMenu createStrikeStagesMenu(@Nonnull Function<StrikeStagesMenu.UpcomingStrikeInfo, Message> strikeMessageProducer) {
        return new StrikeStagesMenu.Builder()
                .setWaiter(getWaiter())
                .setUsers(getUser1(), getUser2())
                .setRuleset(ruleset)
                .setStrikeMessageProducer(strikeMessageProducer)
                .setTimeout(strikeTimeout, strikeUnit)
                .setOnTimeout(this::onStrikeTimeout)
                .setOnUserStrikes(this::onUserStrikes)
                .build();
    }

    @Nonnull
    private BlindPickMenu createDoubleBlindMenu(@Nonnull Message start) {
        return new BlindPickMenu.Builder()
                .setChannelWaiter(channelWaiter)
                .setUsers(Arrays.asList(getUser1(), getUser2()))
                .setStart(start)
                .setTimeout(doubleBlindTimeout, doubleBlindUnit)
                .setCharacters(characters)
                .setOnResult(this::onDoubleBlindResult)
                .setOnTimeout(this::onDoubleBlindTimeout)
                .setOnFailedInit(this::onDoubleBlindFailedInit)
                .build();
    }

    @Nonnull
    private ReportGameMenu createReportGameMenu() {
        SmashSet.SetInGameState inGameState = ((SmashSet.SetInGameState) state);
        // At this point it will be displayed => not null
        @SuppressWarnings("ConstantConditions")
        SmashSet.GameData game = inGameState.getGame();

        Character user1Char = game.getPlayer1Char();
        Character user2Char = game.getPlayer2Char();

        // Stage is already chosen here
        @SuppressWarnings("ConstantConditions")
        Stage stage = ruleset.getStageAtIdx(game.getStageIdx());

        // Characters are already chosen here
        @SuppressWarnings("ConstantConditions")
        Message start = new MessageBuilder(prepareEmbed("Game Reporting")
                .setDescription(String.format("You will play your next game on %s. " +
                                "**%s** will play as %s, and **%s** will play as %s.%n" +
                                "You can start the game now, report the winner here once you're done.",
                        stage.getDisplayName(),
                        user1Display,
                        user1Char.getDisplayName(),
                        user2Display,
                        user2Char.getDisplayName()))
                .build())
                .build();

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
    private BanPickStagesMenu createBanPickStagesMenu(@Nullable String banMessagePrepend, @Nonnull Message pickStageStart) {
        return createBanPickStagesMenu(info -> createBanMessage(info, banMessagePrepend), pickStageStart);
    }

    @Nonnull
    private BanPickStagesMenu createBanPickStagesMenu(@Nonnull Function<BanStagesMenu.UpcomingBanInfo, Message> banMessageProducer, @Nonnull Message pickStageStart) {
        SmashSet.SetWinnerStageBanState banState = ((SmashSet.SetWinnerStageBanState) state);
        // At this point it will be displayed => not null
        @SuppressWarnings("ConstantConditions")
        long banningUser = userFromPlayer(banState.getPrevWinner());
        long pickingUser = userFromPlayer(banState.getPrevLoser());

        return new BanPickStagesMenu.Builder()
                .setWaiter(getWaiter())
                .setUsers(banningUser, pickingUser)
                .setRuleset(ruleset)
                .setDsrIllegalStages(banState.getDSRIllegalStageIndizes())
                .setBanMessageProducer(banMessageProducer)
                .setPickStageStart(pickStageStart)
                .setBanTimeout(banTimeout, banUnit)
                .setPickTimeout(pickStageTimeout, pickStageUnit)
                .setOnBanResult(this::onBanResult)
                .setOnBanTimeout(this::onBanTimeout)
                .setOnPickResult(this::onPickStageResult)
                .setOnPickTimeout(this::onPickStageTimeout)
                .build();
    }

    @Nonnull
    private CharPickMenu createWinnerCharPickMenu(@Nonnull Message start) {
        // At this point it will be displayed => not null
        @SuppressWarnings("ConstantConditions")
        long winner = userFromPlayer(((SmashSet.SetWinnerCharPickState) state).getPrevWinner());

        return new CharPickMenu.Builder()
                .setChannelWaiter(channelWaiter)
                .setUser(winner)
                .setChannelId(getChannelId())
                .setCharacters(characters)
                .setStart(start)
                .setTimeout(winnerCharPickTimeout, winnerCharPickUnit)
                .setOnResult(this::onWinnerCharPickResult)
                .setOnTimeout(this::onWinnerCharPickTimeout)
                .setOnFailedInit(this::onWinnerCharPickFailedInit)
                .build();
    }

    @Nonnull
    private CharPickMenu createLoserCharCounterpickMenu(@Nonnull Message start) {
        // At this point it will be displayed => not null
        @SuppressWarnings("ConstantConditions")
        long loser = userFromPlayer(((SmashSet.SetLoserCharCounterpickState) state).getPrevLoser());

        return new CharPickMenu.Builder()
                .setChannelWaiter(channelWaiter)
                .setUser(loser)
                .setChannelId(getChannelId())
                .setCharacters(characters)
                .setStart(start)
                .setTimeout(loserCharCounterpickTimeout, loserCharCounterpickUnit)
                .setOnResult(this::onLoserCharCounterpickResult)
                .setOnTimeout(this::onLoserCharCounterpickTimeout)
                .setOnFailedInit(this::onLoserCharCounterpickFailedInit)
                .build();
    }

    private synchronized void onStrikeFirstChoice(@Nonnull RPSAndStrikeStagesMenu.StrikeFirstChoiceResult result, @Nonnull ButtonClickEvent event) {
        SmashSet.Player winner = playerFromUser(result.getUserMakingChoice());
        SmashSet.Player firstStriker = playerFromUser(result.getFirstStriker());

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

        Stage remainingStage = info.getRemainingStages().get(0);

        event.editMessageEmbeds(prepareEmbed("Stage Striking")
                        .setDescription(String.format("Stage Striking: You struck to %s", remainingStage.getDisplayName()))
                        .build())
                .setActionRows()
                .queue();

        newState.map(
                doubleBlind -> {
                    Message start = new MessageBuilder(prepareEmbed("Double Blind Pick")
                            .setDescription(String.format("You have struck to %s, " +
                                            "so now we'll determine the characters you play by doing a double blind pick.%n" +
                                            "Please DM me the character you'll play next game.",
                                    remainingStage.getDisplayName()))
                            .build())
                            .build();

                    return createDoubleBlindMenu(start);
                },
                inGame -> createReportGameMenu()
        ).displayReplying(event.getMessage());
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
        OneOfTwo<OneOfTwo<SmashSet.SetRPSState, SmashSet.SetStarterStrikingState>, SmashSet.SetInGameState> newState = ((SmashSet.SetDoubleBlindState) state)
                .completeDoubleBlind(user1Choice, user2Choice);

        state = newState.map(notInGame -> notInGame.map(rps -> rps, striking -> striking), inGame -> inGame);

        MessageChannel channel = result.getChannel();
        if (channel == null) {
            onMessageChannelNotInCache.accept(new SmashSetStateInfo());
            return;
        }

        long messageId = result.getMessageId();

        channel.editMessageEmbedsById(messageId, prepareEmbed("Double Blind Pick")
                        .setDescription(String.format("**%s** chose %s and **%s** chose %s.",
                                user1Display,
                                user1Choice.getDisplayName(),
                                user2Display,
                                user2Choice.getDisplayName()))
                        .build())
                .queue();

        newState.map(
                notInGame -> {
                    String messagePrepend = String.format("The characters are decided. **%s** plays %s, and **%s** plays %s next game.",
                            user1Display,
                            user1Choice.getDisplayName(),
                            user2Display,
                            user2Choice.getDisplayName());

                    return notInGame.map(
                            rps -> {
                                Message start = new MessageBuilder(prepareEmbed("RPS")
                                        .setDescription(String.format("The characters are decided. **%s** plays %s, and **%s** plays %s next game.%n" +
                                                        "Now we'll play a game of RPS to determine who will strike first.",
                                                user1Display,
                                                user1Choice.getDisplayName(),
                                                user2Display,
                                                user2Choice.getDisplayName()))
                                        .build())
                                        .build();

                                return createRPSAndStrikeStagesMenu(start,
                                        messagePrepend,
                                        messagePrepend,
                                        String.format("%s So let's strike stages now.", messagePrepend));
                            },
                            strike -> createStrikeStagesMenu(messagePrepend)
                    );
                },
                inGame -> createReportGameMenu()
        ).displayReplying(channel, messageId);
    }

    private synchronized void onReportGameResult(@Nonnull ReportGameMenu.ReportGameResult result, @Nonnull ButtonClickEvent event) {
        SmashSet.Player winner = playerFromUser(result.getWinner());

        // At this point it will be displayed => not null
        @SuppressWarnings("ConstantConditions")
        OneOfTwo<OneOfTwo<SmashSet.SetWinnerStageBanState, SmashSet.SetWinnerCharPickState>, SmashSet.SetCompletedState> newState =
                ((SmashSet.SetInGameState) state).completeGame(winner);

        state = newState.map(notComplete -> notComplete.map(ban -> ban, chars -> chars), complete -> complete);

        List<SmashSet.GameData> games = set.getGames();
        long user1Score = games.stream().filter(game -> game.getWinner() == SmashSet.Player.PLAYER1).count();
        long user2Score = games.stream().filter(game -> game.getWinner() == SmashSet.Player.PLAYER2).count();

        // -1 to not count the game we just started with completeGame()
        event.editMessageEmbeds(prepareEmbed("Game Reporting", games.size() - 1)
                        .setDescription(String.format("**%s** won, and **%s** lost this game. The current score of this best of %d set is:%n" +
                                        "`%s -- %d to %d -- %s`.",
                                displayFromUser(result.getWinner()),
                                displayFromUser(result.getLoser()),
                                set.getBestOfWhatScore(),
                                user1Display,
                                user1Score,
                                user2Score,
                                user2Display))
                        .build())
                .setActionRows()
                .queue();

        newState.onT(notComplete -> notComplete.map(
                stageBan -> {
                    Message pickStagesStart = new MessageBuilder(prepareEmbed("Stage Ban / Counterpick")
                            .setDescription(String.format("It has been determined that **%s** won, and **%s** lost the last game and the stages have been banned.%n" +
                                            "So %s, please counterpick one of the remaining stages.",
                                    displayFromUser(result.getWinner()),
                                    displayFromUser(result.getLoser()),
                                    displayFromUser(result.getLoser()))
                            ).build())
                            .build();

                    String banMessagePrepend = String.format("It has been determined that **%s** won, and **%s** lost the last game. " +
                                    "Now, **%s**, you get to ban stages.",
                            displayFromUser(result.getWinner()),
                            displayFromUser(result.getLoser()),
                            displayFromUser(result.getWinner()));

                    return createBanPickStagesMenu(banMessagePrepend, pickStagesStart);
                },
                charPick -> {
                    Message start = new MessageBuilder(prepareEmbed("Winner Character Pick")
                            .setDescription(String.format("It has been determined that **%s** won, and **%s** lost the last game. " +
                                            "Now, %s, you have to pick the character you will play next game first.%n" +
                                            "Please type the character you'll play in this channel.",
                                    displayFromUser(result.getWinner()),
                                    displayFromUser(result.getLoser()),
                                    displayFromUser(result.getWinner())))
                            .build())
                            .build();

                    return createWinnerCharPickMenu(start);
                }
        ).displayReplying(event.getMessage())).onU(completed -> this.onResult(event));
    }

    private synchronized void onBanResult(@Nonnull BanStagesMenu.BanResult result, @Nonnull ButtonClickEvent event) {
        // At this point it will be displayed => not null
        //noinspection ConstantConditions
        state = ((SmashSet.SetWinnerStageBanState) state).banStages(result.getBannedStageIds());
    }

    private synchronized void onPickStageResult(@Nonnull PickStageMenu.PickStageResult result, @Nonnull ButtonClickEvent event) {
        // At this point it will be displayed => not null
        @SuppressWarnings("ConstantConditions")
        OneOfTwo<SmashSet.SetWinnerCharPickState, SmashSet.SetInGameState> newState = ((SmashSet.SetLoserStageCounterpickState) state)
                .pickStage(result.getPickedStageId());

        state = newState.map(charPick -> charPick, inGame -> inGame);

        event.editMessageEmbeds(prepareEmbed("Stage Ban / Counterpick")
                        .setDescription(String.format("You will play the next game on %s.", result.getPickedStage().getDisplayName()))
                        .build())
                .setActionRows()
                .queue();

        newState.map(
                charPick -> {
                    long prevWinner = userFromPlayer(charPick.getPrevWinner());

                    Message start = new MessageBuilder(prepareEmbed("Winner Character Pick")
                            .setDescription(String.format("You will play the next game on %s. " +
                                            "Now the winner of the previous game will have to pick the character.%n" +
                                            "**%s**, please type the character you'll play next game in this channel.",
                                    result.getPickedStage().getDisplayName(),
                                    displayFromUser(prevWinner)))
                            .build())
                            .build();

                    return createWinnerCharPickMenu(start);
                },
                inGame -> createReportGameMenu()
        ).displayReplying(event.getMessage());
    }

    private synchronized void onWinnerCharPickResult(@Nonnull CharPickMenu.CharPickResult result) {
        // At this point it will be displayed => not null
        //noinspection ConstantConditions
        SmashSet.SetLoserCharCounterpickState newState = ((SmashSet.SetWinnerCharPickState) state).pickCharacter(result.getPickedCharacter());
        state = newState;

        MessageChannel channel = result.getChannel();
        if (channel == null) {
            onMessageChannelNotInCache.accept(new SmashSetStateInfo());
            return;
        }

        long messageId = result.getMessageId();

        channel.editMessageEmbedsById(messageId, prepareEmbed("Winner Character Pick")
                        .setDescription(String.format("**%s** picked %s.",
                                displayFromUser(result.getUser()),
                                result.getPickedCharacter().getDisplayName()))
                        .build())
                .queue();

        long prevLoser = userFromPlayer(newState.getPrevLoser());

        Message start = new MessageBuilder(prepareEmbed("Loser Character Counterpick")
                .setDescription(String.format("**%s** chose %s as their character, so now the loser of the previous game has to state their character.%n" +
                                "**%s**, please type the character you will use next game in this channel.",
                        displayFromUser(result.getUser()),
                        result.getPickedCharacter().getDisplayName(),
                        displayFromUser(prevLoser)))
                .build())
                .build();

        createLoserCharCounterpickMenu(start).displayReplying(channel, messageId);
    }

    private synchronized void onLoserCharCounterpickResult(@Nonnull CharPickMenu.CharPickResult result) {
        // At this point it will be displayed => not null
        @SuppressWarnings("ConstantConditions")
        OneOfTwo<SmashSet.SetWinnerStageBanState, SmashSet.SetInGameState> newState = ((SmashSet.SetLoserCharCounterpickState) state)
                .pickCharacter(result.getPickedCharacter());

        state = newState.map(ban -> ban, inGame -> inGame);

        MessageChannel channel = result.getChannel();
        if (channel == null) {
            onMessageChannelNotInCache.accept(new SmashSetStateInfo());
            return;
        }

        long messageId = result.getMessageId();

        channel.editMessageEmbedsById(messageId, prepareEmbed("Loser Character Counterpick")
                        .setDescription(String.format("**%s** counterpicked %s.",
                                displayFromUser(result.getUser()),
                                result.getPickedCharacter().getDisplayName()))
                        .build())
                .queue();

        newState.map(
                stageBan -> {
                    Character user1Char = stageBan.getGame().getPlayer1Char();
                    Character user2Char = stageBan.getGame().getPlayer2Char();
                    long prevLoser = userFromPlayer(stageBan.getPrevLoser());

                    // Characters will have been determined at this point
                    @SuppressWarnings("ConstantConditions")
                    String banMessagePrepend = String.format("**%s** picked %s and **%s** picked %s. Now, onto stage banning.",
                            user1Display,
                            user1Char.getDisplayName(),
                            user2Display,
                            user2Char.getDisplayName());

                    Message pickStageStart = new MessageBuilder(prepareEmbed("Stage Ban / Counterpick")
                            .setDescription(String.format("**%s** picked %s, **%s** picked %s, and the stages have been banned.%n" +
                                            "%s, please counterpick a stage from the list below now.",
                                    user1Display,
                                    user1Char.getDisplayName(),
                                    user2Display,
                                    user2Char.getDisplayName(),
                                    displayFromUser(prevLoser)))
                            .build())
                            .build();

                    return createBanPickStagesMenu(banMessagePrepend, pickStageStart);
                },
                inGame -> createReportGameMenu()
        ).displayReplying(channel, messageId);
    }

    private void onResult(@Nonnull ButtonClickEvent event) {
        onResult.accept(new SmashSetResult(), event);
    }

    private void onRPSTimeout(@Nonnull RPSMenu.RPSTimeoutEvent event) {
        // If this is called this is nonnull
        //noinspection ConstantConditions
        rpsInfo.getOnRPSTimeout().accept(new SmashSetRPSTimeoutEvent(event));
    }

    private void onFirstChoiceTimeout(@Nonnull RPSAndStrikeStagesMenu.StrikeFirstChoiceTimeoutEvent event) {
        // If this is called this is nonnull
        //noinspection ConstantConditions
        rpsInfo.getOnFirstChoiceTimeout().accept(new SmashSetStrikeFirstChoiceTimeoutEvent(event));
    }

    private void onStrikeTimeout(@Nonnull StrikeStagesMenu.StrikeStagesTimeoutEvent event) {
        onStrikeTimeout.accept(new SmashSetStrikeTimeoutEvent(event));
    }

    private void onDoubleBlindTimeout(@Nonnull BlindPickMenu.BlindPickTimeoutEvent event) {
        onDoubleBlindTimeout.accept(new SmashSetDoubleBlindTimeoutEvent(event));
    }

    private void onDoubleBlindFailedInit() {
        onDoubleBlindFailedInit.accept(new SmashSetStateInfo());
    }

    private void onReportGameTimeout(@Nonnull ReportGameMenu.ReportGameTimeoutEvent event) {
        onReportGameTimeout.accept(new SmashSetReportGameTimeoutEvent(event));
    }

    private void onBanTimeout(@Nonnull BanStagesMenu.BanStagesTimeoutEvent event) {
        onBanTimeout.accept(new SmashSetBanTimeoutEvent(event));
    }

    private void onPickStageTimeout(@Nonnull PickStageMenu.PickStageTimeoutEvent event) {
        onPickStageTimeout.accept(new SmashSetPickStageTimeoutEvent(event));
    }

    private void onWinnerCharPickTimeout(@Nonnull CharPickMenu.CharPickTimeoutEvent event) {
        onWinnerCharPickTimeout.accept(new SmashSetCharPickTimeoutEvent(event));
    }

    private void onWinnerCharPickFailedInit() {
        onWinnerCharPickFailedInit.accept(new SmashSetStateInfo());
    }

    private void onLoserCharCounterpickTimeout(@Nonnull CharPickMenu.CharPickTimeoutEvent event) {
        onLoserCharCounterpickTimeout.accept(new SmashSetCharPickTimeoutEvent(event));
    }

    private void onLoserCharCounterpickFailedInit() {
        onLoserCharCounterpickFailedInit.accept(new SmashSetStateInfo());
    }

    private long userFromPlayer(@Nonnull SmashSet.Player player) {
        return player == SmashSet.Player.PLAYER1 ? getUser1() : getUser2();
    }

    @Nonnull
    private SmashSet.Player playerFromUser(long user) {
        return user == getUser1() ? SmashSet.Player.PLAYER1 : SmashSet.Player.PLAYER2;
    }

    @Nonnull
    private String displayFromUser(long user) {
        return user == getUser1() ? user1Display : user2Display;
    }

    @Nonnull
    private EmbedBuilder prepareEmbed(@Nonnull String title) {
        return prepareEmbed(title, set.getGames().size());
    }

    @Nonnull
    private EmbedBuilder prepareEmbed(@Nonnull String title, int gameNumber) {
        return EmbedUtil.getPrepared()
                .setTitle(String.format("%s vs %s | Game %d | %s",
                        user1Display,
                        user2Display,
                        gameNumber,
                        title))
                .setFooter(String.format("%s vs %s", user1Display, user2Display));
    }

    @Nonnull
    @Override
    public JDA getJDA() {
        return startUnderlying.getJDA();
    }

    @Override
    public long getMessageId() {
        return startUnderlying.getMessageId();
    }

    @Override
    public long getChannelId() {
        return startUnderlying.getChannelId();
    }

    public class SmashSetStateInfo extends TwoUsersMenuStateInfo {
        @Nonnull
        public ChannelChoiceWaiter getChannelWaiter() {
            return channelWaiter;
        }

        @Nonnull
        public Ruleset getRuleset() {
            return ruleset;
        }

        @Nonnull
        public List<Character> getCharacters() {
            return characters;
        }

        public long getStrikeTimeout() {
            return strikeTimeout;
        }

        @Nonnull
        public TimeUnit getStrikeUnit() {
            return strikeUnit;
        }

        public long getDoubleBlindTimeout() {
            return doubleBlindTimeout;
        }

        @Nonnull
        public TimeUnit getDoubleBlindUnit() {
            return doubleBlindUnit;
        }

        public long getReportGameTimeout() {
            return reportGameTimeout;
        }

        @Nonnull
        public TimeUnit getReportGameUnit() {
            return reportGameUnit;
        }

        @Nonnull
        public String getUser1Display() {
            return user1Display;
        }

        @Nonnull
        public String getUser2Display() {
            return user2Display;
        }

        public long getBanTimeout() {
            return banTimeout;
        }

        @Nonnull
        public TimeUnit getBanUnit() {
            return banUnit;
        }

        public long getPickStageTimeout() {
            return pickStageTimeout;
        }

        @Nonnull
        public TimeUnit getPickStageUnit() {
            return pickStageUnit;
        }

        public long getWinnerCharPickTimeout() {
            return winnerCharPickTimeout;
        }

        @Nonnull
        public TimeUnit getWinnerCharPickUnit() {
            return winnerCharPickUnit;
        }

        public long getLoserCharCounterpickTimeout() {
            return loserCharCounterpickTimeout;
        }

        @Nonnull
        public TimeUnit getLoserCharCounterpickUnit() {
            return loserCharCounterpickUnit;
        }

        @Nonnull
        public SmashSet getSet() {
            return set;
        }

        @Nonnull
        public SmashSet.SetState getState() {
            // Whenever this is constructed, display will already have been called
            //noinspection ConstantConditions
            return state;
        }
    }

    public class SmashSetResult extends SmashSetStateInfo {
    }

    public class SmashSetStrikeFirstChoiceTimeoutEvent extends SmashSetStateInfo {
        @Nonnull
        private final RPSAndStrikeStagesMenu.StrikeFirstChoiceTimeoutEvent timeoutEvent;

        public SmashSetStrikeFirstChoiceTimeoutEvent(@Nonnull RPSAndStrikeStagesMenu.StrikeFirstChoiceTimeoutEvent timeoutEvent) {
            this.timeoutEvent = timeoutEvent;
        }

        @Nonnull
        public RPSAndStrikeStagesMenu.StrikeFirstChoiceTimeoutEvent getTimeoutEvent() {
            return timeoutEvent;
        }
    }

    public class SmashSetRPSTimeoutEvent extends SmashSetStateInfo {
        @Nonnull
        private final RPSMenu.RPSTimeoutEvent timeoutEvent;

        public SmashSetRPSTimeoutEvent(@Nonnull RPSMenu.RPSTimeoutEvent timeoutEvent) {
            this.timeoutEvent = timeoutEvent;
        }

        @Nonnull
        public RPSMenu.RPSTimeoutEvent getTimeoutEvent() {
            return timeoutEvent;
        }
    }

    public class SmashSetStrikeTimeoutEvent extends SmashSetStateInfo {
        @Nonnull
        private final StrikeStagesMenu.StrikeStagesTimeoutEvent timeoutEvent;

        public SmashSetStrikeTimeoutEvent(@Nonnull StrikeStagesMenu.StrikeStagesTimeoutEvent timeoutEvent) {
            this.timeoutEvent = timeoutEvent;
        }

        @Nonnull
        public StrikeStagesMenu.StrikeStagesTimeoutEvent getTimeoutEvent() {
            return timeoutEvent;
        }
    }

    public class SmashSetDoubleBlindTimeoutEvent extends SmashSetStateInfo {
        @Nonnull
        private final BlindPickMenu.BlindPickTimeoutEvent timeoutEvent;

        public SmashSetDoubleBlindTimeoutEvent(@Nonnull BlindPickMenu.BlindPickTimeoutEvent timeoutEvent) {
            this.timeoutEvent = timeoutEvent;
        }

        @Nonnull
        public BlindPickMenu.BlindPickTimeoutEvent getTimeoutEvent() {
            return timeoutEvent;
        }
    }

    public class SmashSetReportGameTimeoutEvent extends SmashSetStateInfo {
        @Nonnull
        private final ReportGameMenu.ReportGameTimeoutEvent timeoutEvent;

        public SmashSetReportGameTimeoutEvent(@Nonnull ReportGameMenu.ReportGameTimeoutEvent timeoutEvent) {
            this.timeoutEvent = timeoutEvent;
        }

        @Nonnull
        public ReportGameMenu.ReportGameTimeoutEvent getTimeoutEvent() {
            return timeoutEvent;
        }
    }

    public class SmashSetBanTimeoutEvent extends SmashSetStateInfo {
        @Nonnull
        private final BanStagesMenu.BanStagesTimeoutEvent timeoutEvent;

        public SmashSetBanTimeoutEvent(@Nonnull BanStagesMenu.BanStagesTimeoutEvent timeoutEvent) {
            this.timeoutEvent = timeoutEvent;
        }

        @Nonnull
        public BanStagesMenu.BanStagesTimeoutEvent getTimeoutEvent() {
            return timeoutEvent;
        }
    }

    public class SmashSetPickStageTimeoutEvent extends SmashSetStateInfo {
        @Nonnull
        private final PickStageMenu.PickStageTimeoutEvent timeoutEvent;

        public SmashSetPickStageTimeoutEvent(@Nonnull PickStageMenu.PickStageTimeoutEvent timeoutEvent) {
            this.timeoutEvent = timeoutEvent;
        }

        @Nonnull
        public PickStageMenu.PickStageTimeoutEvent getTimeoutEvent() {
            return timeoutEvent;
        }
    }

    public class SmashSetCharPickTimeoutEvent extends SmashSetStateInfo {
        @Nonnull
        private final CharPickMenu.CharPickTimeoutEvent timeoutEvent;

        public SmashSetCharPickTimeoutEvent(@Nonnull CharPickMenu.CharPickTimeoutEvent timeoutEvent) {
            this.timeoutEvent = timeoutEvent;
        }

        @Nonnull
        public CharPickMenu.CharPickTimeoutEvent getTimeoutEvent() {
            return timeoutEvent;
        }
    }

    public static class RPSInfo {
        private final long timeout;
        @Nonnull
        private final TimeUnit unit;
        private final long strikeFirstChoiceTimeout;
        @Nonnull
        private final TimeUnit strikeFirstChoiceUnit;
        @Nonnull
        private final Consumer<SmashSetStrikeFirstChoiceTimeoutEvent> onFirstChoiceTimeout;
        @Nonnull
        private final Consumer<SmashSetRPSTimeoutEvent> onRPSTimeout;

        public RPSInfo(long timeout, @Nonnull TimeUnit unit, long strikeFirstChoiceTimeout, @Nonnull TimeUnit strikeFirstChoiceUnit, @Nonnull Consumer<SmashSetStrikeFirstChoiceTimeoutEvent> onFirstChoiceTimeout, @Nonnull Consumer<SmashSetRPSTimeoutEvent> onRPSTimeout) {
            this.timeout = timeout;
            this.unit = unit;
            this.strikeFirstChoiceTimeout = strikeFirstChoiceTimeout;
            this.strikeFirstChoiceUnit = strikeFirstChoiceUnit;
            this.onFirstChoiceTimeout = onFirstChoiceTimeout;
            this.onRPSTimeout = onRPSTimeout;
        }

        public long getTimeout() {
            return timeout;
        }

        @Nonnull
        public TimeUnit getUnit() {
            return unit;
        }

        public long getStrikeFirstChoiceTimeout() {
            return strikeFirstChoiceTimeout;
        }

        @Nonnull
        public TimeUnit getStrikeFirstChoiceUnit() {
            return strikeFirstChoiceUnit;
        }

        @Nonnull
        public Consumer<SmashSetStrikeFirstChoiceTimeoutEvent> getOnFirstChoiceTimeout() {
            return onFirstChoiceTimeout;
        }

        @Nonnull
        public Consumer<SmashSetRPSTimeoutEvent> getOnRPSTimeout() {
            return onRPSTimeout;
        }
    }

    public static class Builder extends TwoUsersChoicesActionMenu.Builder<Builder, SmashSetMenu> {
        @Nullable
        private ChannelChoiceWaiter channelWaiter;

        @Nullable
        private Ruleset ruleset;
        @Nullable
        private List<Character> characters;
        @Nullable
        private Integer firstToWhatScore;

        @Nullable
        private RPSInfo rpsInfo;

        @Nonnull
        private Consumer<SmashSetStrikeTimeoutEvent> onStrikeTimeout;

        private long doubleBlindTimeout;
        @Nonnull
        private TimeUnit doubleBlindUnit;
        @Nonnull
        private Consumer<SmashSetDoubleBlindTimeoutEvent> onDoubleBlindTimeout;
        @Nonnull
        private Consumer<SmashSetStateInfo> onDoubleBlindFailedInit;

        private long reportGameTimeout;
        @Nonnull
        private TimeUnit reportGameUnit;
        @Nullable
        private String user1Display;
        @Nullable
        private String user2Display;
        @Nonnull
        private Consumer<SmashSetReportGameTimeoutEvent> onReportGameTimeout;

        private long banTimeout;
        @Nonnull
        private TimeUnit banUnit;
        @Nonnull
        private Consumer<SmashSetBanTimeoutEvent> onBanTimeout;
        private long pickStageTimeout;
        @Nonnull
        private TimeUnit pickStageUnit;
        @Nonnull
        private Consumer<SmashSetPickStageTimeoutEvent> onPickStageTimeout;

        private long winnerCharPickTimeout;
        @Nonnull
        private TimeUnit winnerCharPickUnit;
        @Nonnull
        private Consumer<SmashSetCharPickTimeoutEvent> onWinnerCharPickTimeout;
        @Nonnull
        private Consumer<SmashSetStateInfo> onWinnerCharPickFailedInit;

        private long loserCharCounterpickTimeout;
        @Nonnull
        private TimeUnit loserCharCounterpickUnit;
        @Nonnull
        private Consumer<SmashSetCharPickTimeoutEvent> onLoserCharCounterpickTimeout;
        @Nonnull
        private Consumer<SmashSetStateInfo> onLoserCharCounterpickFailedInit;

        @Nonnull
        private Consumer<SmashSetStateInfo> onMessageChannelNotInCache;

        @Nonnull
        private BiConsumer<SmashSetResult, ButtonClickEvent> onResult;

        public Builder() {
            super(Builder.class);

            onStrikeTimeout = timeout -> {
            };

            doubleBlindTimeout = 5;
            doubleBlindUnit = TimeUnit.MINUTES;
            onDoubleBlindTimeout = timeout -> {
            };
            onDoubleBlindFailedInit = info -> {
            };

            reportGameTimeout = 40;
            reportGameUnit = TimeUnit.MINUTES;
            onReportGameTimeout = timeout -> {
            };

            banTimeout = 5;
            banUnit = TimeUnit.MINUTES;
            onBanTimeout = timeout -> {
            };
            pickStageTimeout = 5;
            pickStageUnit = TimeUnit.MINUTES;
            onPickStageTimeout = timeout -> {
            };

            winnerCharPickTimeout = 5;
            winnerCharPickUnit = TimeUnit.MINUTES;
            onWinnerCharPickTimeout = timeout -> {
            };
            onWinnerCharPickFailedInit = info -> {
            };

            loserCharCounterpickTimeout = 5;
            loserCharCounterpickUnit = TimeUnit.MINUTES;
            onLoserCharCounterpickTimeout = timeout -> {
            };
            onLoserCharCounterpickFailedInit = info -> {
            };

            onMessageChannelNotInCache = info -> {
            };

            onResult = (result, event) -> {
            };
        }

        @Nonnull
        public Builder setStrikeTimeout(long strikeTimeout, @Nonnull TimeUnit strikeUnit) {
            return setTimeout(strikeTimeout, strikeUnit);
        }

        @Nonnull
        public Builder setChannelWaiter(@Nonnull ChannelChoiceWaiter channelWaiter) {
            this.channelWaiter = channelWaiter;
            return setWaiter(channelWaiter.getEventWaiter());
        }

        @Nonnull
        public Builder setRuleset(@Nonnull Ruleset ruleset) {
            this.ruleset = ruleset;
            return this;
        }

        @Nonnull
        public Builder setFirstToWhatScore(@Nullable Integer firstToWhatScore) {
            this.firstToWhatScore = firstToWhatScore;
            return this;
        }

        @Nonnull
        public Builder setCharacters(@Nonnull List<Character> characters) {
            this.characters = characters;
            return this;
        }

        @Nonnull
        public Builder setCharacterTree(@Nonnull CharacterTree characterTree) {
            return setCharacters(characterTree.getAllCharacters());
        }

        @Nonnull
        public Builder setRpsInfo(@Nonnull RPSInfo rpsInfo) {
            this.rpsInfo = rpsInfo;
            return this;
        }

        @Nonnull
        public Builder setOnStrikeTimeout(@Nonnull Consumer<SmashSetStrikeTimeoutEvent> onStrikeTimeout) {
            this.onStrikeTimeout = onStrikeTimeout;
            return this;
        }

        @Nonnull
        public Builder setDoubleBlindTimeout(long doubleBlindTimeout, @Nonnull TimeUnit doubleBlindUnit) {
            this.doubleBlindTimeout = doubleBlindTimeout;
            this.doubleBlindUnit = doubleBlindUnit;
            return this;
        }

        @Nonnull
        public Builder setOnDoubleBlindTimeout(@Nonnull Consumer<SmashSetDoubleBlindTimeoutEvent> onDoubleBlindTimeout) {
            this.onDoubleBlindTimeout = onDoubleBlindTimeout;
            return this;
        }

        @Nonnull
        public Builder setOnDoubleBlindFailedInit(@Nonnull Consumer<SmashSetStateInfo> onDoubleBlindFailedInit) {
            this.onDoubleBlindFailedInit = onDoubleBlindFailedInit;
            return this;
        }

        @Nonnull
        public Builder setReportGameTimeout(long reportGameTimeout, @Nonnull TimeUnit reportGameUnit) {
            this.reportGameTimeout = reportGameTimeout;
            this.reportGameUnit = reportGameUnit;
            return this;
        }

        @Nonnull
        public Builder setUsersDisplay(@Nonnull String user1Display, @Nonnull String user2Display) {
            this.user1Display = user1Display;
            this.user2Display = user2Display;
            return this;
        }

        @Nonnull
        public Builder setOnReportGameTimeout(@Nonnull Consumer<SmashSetReportGameTimeoutEvent> onReportGameTimeout) {
            this.onReportGameTimeout = onReportGameTimeout;
            return this;
        }

        @Nonnull
        public Builder setBanTimeout(long banTimeout, @Nonnull TimeUnit banUnit) {
            this.banTimeout = banTimeout;
            this.banUnit = banUnit;
            return this;
        }

        @Nonnull
        public Builder setOnBanTimeout(@Nonnull Consumer<SmashSetBanTimeoutEvent> onBanTimeout) {
            this.onBanTimeout = onBanTimeout;
            return this;
        }

        @Nonnull
        public Builder setPickStageTimeout(long pickStageTimeout, @Nonnull TimeUnit pickStageUnit) {
            this.pickStageTimeout = pickStageTimeout;
            this.pickStageUnit = pickStageUnit;
            return this;
        }

        @Nonnull
        public Builder setOnPickStageTimeout(@Nonnull Consumer<SmashSetPickStageTimeoutEvent> onPickStageTimeout) {
            this.onPickStageTimeout = onPickStageTimeout;
            return this;
        }

        @Nonnull
        public Builder setWinnerCharPickTimeout(long winnerCharPickTimeout, @Nonnull TimeUnit winnerCharPickUnit) {
            this.winnerCharPickTimeout = winnerCharPickTimeout;
            this.winnerCharPickUnit = winnerCharPickUnit;
            return this;
        }

        @Nonnull
        public Builder setOnWinnerCharPickTimeout(@Nonnull Consumer<SmashSetCharPickTimeoutEvent> onWinnerCharPickTimeout) {
            this.onWinnerCharPickTimeout = onWinnerCharPickTimeout;
            return this;
        }

        @Nonnull
        public Builder setOnWinnerCharPickFailedInit(@Nonnull Consumer<SmashSetStateInfo> onWinnerCharPickFailedInit) {
            this.onWinnerCharPickFailedInit = onWinnerCharPickFailedInit;
            return this;
        }

        @Nonnull
        public Builder setLoserCharCounterpickTimeout(long loserCharCounterpickTimeout, @Nonnull TimeUnit loserCharCounterpickUnit) {
            this.loserCharCounterpickTimeout = loserCharCounterpickTimeout;
            this.loserCharCounterpickUnit = loserCharCounterpickUnit;
            return this;
        }

        @Nonnull
        public Builder setOnLoserCharCounterpickTimeout(@Nonnull Consumer<SmashSetCharPickTimeoutEvent> onLoserCharCounterpickTimeout) {
            this.onLoserCharCounterpickTimeout = onLoserCharCounterpickTimeout;
            return this;
        }

        @Nonnull
        public Builder setOnLoserCharCounterpickFailedInit(@Nonnull Consumer<SmashSetStateInfo> onLoserCharCounterpickFailedInit) {
            this.onLoserCharCounterpickFailedInit = onLoserCharCounterpickFailedInit;
            return this;
        }

        @Nonnull
        public Builder setOnMessageChannelNotInCache(@Nonnull Consumer<SmashSetStateInfo> onMessageChannelNotInCache) {
            this.onMessageChannelNotInCache = onMessageChannelNotInCache;
            return this;
        }

        @Nonnull
        public Builder setOnResult(@Nonnull BiConsumer<SmashSetResult, ButtonClickEvent> onResult) {
            this.onResult = onResult;
            return this;
        }

        @Nullable
        public ChannelChoiceWaiter getChannelWaiter() {
            return channelWaiter;
        }

        @Nullable
        public Ruleset getRuleset() {
            return ruleset;
        }

        @Nullable
        public Integer getFirstToWhatScore() {
            return firstToWhatScore;
        }

        @Nullable
        public List<Character> getCharacters() {
            return characters;
        }

        @Nullable
        public RPSInfo getRpsInfo() {
            return rpsInfo;
        }

        @Nonnull
        public Consumer<SmashSetStrikeTimeoutEvent> getOnStrikeTimeout() {
            return onStrikeTimeout;
        }

        public long getDoubleBlindTimeout() {
            return doubleBlindTimeout;
        }

        @Nonnull
        public TimeUnit getDoubleBlindUnit() {
            return doubleBlindUnit;
        }

        @Nonnull
        public Consumer<SmashSetDoubleBlindTimeoutEvent> getOnDoubleBlindTimeout() {
            return onDoubleBlindTimeout;
        }

        @Nonnull
        public Consumer<SmashSetStateInfo> getOnDoubleBlindFailedInit() {
            return onDoubleBlindFailedInit;
        }

        public long getReportGameTimeout() {
            return reportGameTimeout;
        }

        @Nonnull
        public TimeUnit getReportGameUnit() {
            return reportGameUnit;
        }

        @Nullable
        public String getUser1Display() {
            return user1Display;
        }

        @Nullable
        public String getUser2Display() {
            return user2Display;
        }

        @Nonnull
        public Consumer<SmashSetReportGameTimeoutEvent> getOnReportGameTimeout() {
            return onReportGameTimeout;
        }

        public long getBanTimeout() {
            return banTimeout;
        }

        @Nonnull
        public TimeUnit getBanUnit() {
            return banUnit;
        }

        @Nonnull
        public Consumer<SmashSetBanTimeoutEvent> getOnBanTimeout() {
            return onBanTimeout;
        }

        public long getPickStageTimeout() {
            return pickStageTimeout;
        }

        @Nonnull
        public TimeUnit getPickStageUnit() {
            return pickStageUnit;
        }

        @Nonnull
        public Consumer<SmashSetPickStageTimeoutEvent> getOnPickStageTimeout() {
            return onPickStageTimeout;
        }

        public long getWinnerCharPickTimeout() {
            return winnerCharPickTimeout;
        }

        @Nonnull
        public TimeUnit getWinnerCharPickUnit() {
            return winnerCharPickUnit;
        }

        @Nonnull
        public Consumer<SmashSetCharPickTimeoutEvent> getOnWinnerCharPickTimeout() {
            return onWinnerCharPickTimeout;
        }

        @Nonnull
        public Consumer<SmashSetStateInfo> getOnWinnerCharPickFailedInit() {
            return onWinnerCharPickFailedInit;
        }

        public long getLoserCharCounterpickTimeout() {
            return loserCharCounterpickTimeout;
        }

        @Nonnull
        public TimeUnit getLoserCharCounterpickUnit() {
            return loserCharCounterpickUnit;
        }

        @Nonnull
        public Consumer<SmashSetCharPickTimeoutEvent> getOnLoserCharCounterpickTimeout() {
            return onLoserCharCounterpickTimeout;
        }

        @Nonnull
        public Consumer<SmashSetStateInfo> getOnLoserCharCounterpickFailedInit() {
            return onLoserCharCounterpickFailedInit;
        }

        @Nonnull
        public Consumer<SmashSetStateInfo> getOnMessageChannelNotInCache() {
            return onMessageChannelNotInCache;
        }

        @Nonnull
        public BiConsumer<SmashSetResult, ButtonClickEvent> getOnResult() {
            return onResult;
        }

        @Nonnull
        @Override
        public SmashSetMenu build() {
            preBuild();

            if (channelWaiter == null) throw new IllegalStateException("ChannelWaiter must be set");
            if (ruleset == null) throw new IllegalStateException("Ruleset must be set");
            if (characters == null) throw new IllegalStateException("Characters must be set");
            if (firstToWhatScore == null) throw new IllegalStateException("FirstToWhatScore must be set");
            if (user1Display == null || user2Display == null)
                throw new IllegalStateException("UsersDisplay must be set");

            // We know user1, user2 nonnull because preBuild
            //noinspection ConstantConditions
            return new SmashSetMenu(channelWaiter, getUser1(), getUser2(), ruleset, characters, firstToWhatScore,
                    rpsInfo,
                    getTimeout(), getUnit(), onStrikeTimeout,
                    doubleBlindTimeout, doubleBlindUnit, onDoubleBlindTimeout, onDoubleBlindFailedInit,
                    reportGameTimeout, reportGameUnit, user1Display, user2Display, onReportGameTimeout,
                    banTimeout, banUnit, onBanTimeout,
                    pickStageTimeout, pickStageUnit, onPickStageTimeout,
                    winnerCharPickTimeout, winnerCharPickUnit, onWinnerCharPickTimeout, onWinnerCharPickFailedInit,
                    loserCharCounterpickTimeout, loserCharCounterpickUnit, onLoserCharCounterpickTimeout, onLoserCharCounterpickFailedInit,
                    onMessageChannelNotInCache, onResult);
        }
    }
}
