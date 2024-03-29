package com.github.gpluscb.toni.command.menu;

import com.github.gpluscb.toni.menu.ActionMenu;
import com.github.gpluscb.toni.menu.TwoUsersChoicesActionMenu;
import com.github.gpluscb.toni.smashset.Character;
import com.github.gpluscb.toni.smashset.*;
import com.github.gpluscb.toni.util.Constants;
import com.github.gpluscb.toni.util.MiscUtil;
import com.github.gpluscb.toni.util.OneOfTwo;
import com.github.gpluscb.toni.util.discord.EmbedUtil;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public class SmashSetMenu extends TwoUsersChoicesActionMenu {
    @Nonnull
    private final Settings settings;

    @Nonnull
    private final ActionMenu startUnderlying;

    @Nonnull
    private final SmashSet set;
    @Nullable
    private SmashSet.SetState state;

    public SmashSetMenu(@Nonnull Settings settings) {
        super(settings.twoUsersChoicesActionMenuSettings());

        this.settings = settings;

        boolean doRPS = settings.rpsInfo() != null;
        Ruleset ruleset = settings.ruleset();

        set = new SmashSet(ruleset, settings.firstToWhatScore(), doRPS);

        // Init set
        if (doRPS) {
            state = set.startSetWithRPS().map(doubleBlind -> doubleBlind, rps -> rps);
        } else {
            SmashSet.Player firstStriker = ThreadLocalRandom.current().nextBoolean() ?
                    SmashSet.Player.PLAYER1
                    : SmashSet.Player.PLAYER2;

            state = set.startSetNoRPS(firstStriker).map(doubleBlind -> doubleBlind, strike -> strike);
        }

        if (ruleset.blindPickBeforeStage()) {
            // Manually set game number because we haven't tarted set yet
            MessageCreateData start = new MessageCreateBuilder()
                    .setEmbeds(prepareEmbed("Double Blind Pick", 1)
                            .setDescription("Alright, we start by doing a double blind pick." +
                                    " Please click on the button below to select the character you will play in the first game now.")
                            .build())
                    .build();

            startUnderlying = createDoubleBlindMenu(start);
        } else if (doRPS) {
            // Manually set game number because we haven't tarted set yet
            MessageCreateData start = new MessageCreateBuilder()
                    .setEmbeds(prepareEmbed("RPS", 1)
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
        startUnderlying.display(channel);
    }

    @Override
    public void display(@Nonnull MessageChannel channel, long messageId) {
        startUnderlying.display(channel, messageId);
    }

    @Override
    public void displayReplying(@Nonnull MessageChannel channel, long messageId) {
        startUnderlying.displayReplying(channel, messageId);
    }

    @Override
    public void displaySlashReplying(@Nonnull SlashCommandInteractionEvent event) {
        startUnderlying.displaySlashReplying(event);
    }

    @Override
    public void displayDeferredReplying(@Nonnull InteractionHook hook) {
        startUnderlying.displayDeferredReplying(hook);
    }

    @Nonnull
    @Override
    public List<ActionRow> getComponents() {
        return startUnderlying.getComponents();
    }

    @Override
    public void start(@Nonnull Message message) {
        startUnderlying.start(message);
    }

    @Nonnull
    private MessageCreateData createRPSTieMessage(@Nonnull RPSMenu.RPSResult rpsResult, @Nullable String messagePrepend) {
        return new MessageCreateBuilder()
                .setEmbeds(prepareEmbed("RPS")
                        .setDescription(String.format("%s" +
                                        "The RPS was a tie (both of you chose %s), so please try that again.",
                                messagePrepend == null ? "" : String.format("%s%n", messagePrepend),
                                rpsResult.getChoice1().getDisplayName()))
                        .build())
                .build();
    }

    @Nonnull
    private MessageCreateData createStrikeFirstMessage(@Nonnull RPSMenu.RPSResult rpsResult, @Nullable String messagePrepend) {
        // This will be a decisive result
        //noinspection ConstantConditions
        return new MessageCreateBuilder()
                .setEmbeds(prepareEmbed("RPS")
                        .setDescription(String.format("%s" +
                                        "**%s** chose %s, and **%s** chose %s in the rps, meaning **%s** won the RPS.%n" +
                                        "**%s**, you can decide if you want to strike first (%s), or second (%s) now.",
                                messagePrepend == null ? "" : String.format("%s%n", messagePrepend),
                                settings.user1Display(),
                                rpsResult.getChoice1().getDisplayName(),
                                settings.user2Display(),
                                rpsResult.getChoice2().getDisplayName(),
                                displayFromUser(rpsResult.getWinnerId()),
                                displayFromUser(rpsResult.getWinnerId()),
                                Constants.ONE,
                                Constants.TWO))
                        .build())
                .build();
    }

    @Nonnull
    private MessageCreateData createStrikeMessage(@Nonnull StrikeStagesMenu.UpcomingStrikeInfo info, @Nullable String messagePrepend) {
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

        return new MessageCreateBuilder().setEmbeds(builder.build()).build();
    }

    @Nonnull
    private MessageCreateData createBanMessage(@Nonnull BanStagesMenu.UpcomingBanInfo info, @Nullable String messagePrepend) {
        int stagesToBan = info.getStagesToBan();

        return new MessageCreateBuilder()
                .setEmbeds(prepareEmbed("Stage Ban / Counterpick")
                        .setDescription(String.format("%s" +
                                        "**%s**, please ban %d stage%s from the list below.",
                                messagePrepend == null ? "" : String.format("%s%n", messagePrepend),
                                displayFromUser(info.getBanStagesMenuSettings().banningUser()),
                                stagesToBan,
                                stagesToBan == 1 ? "" : "s"))
                        .build())
                .build();
    }

    @Nonnull
    private RPSAndStrikeStagesMenu createRPSAndStrikeStagesMenu(@Nonnull MessageCreateData start, @Nullable String rpsTieMessagePrepend, @Nullable String strikeFirstMessagePrepend, @Nonnull String strikeMessagePrepend) {
        return createRPSAndStrikeStagesMenu(start,
                (rpsResult, event) -> createRPSTieMessage(rpsResult, rpsTieMessagePrepend),
                (rpsResult, event) -> createStrikeFirstMessage(rpsResult, strikeFirstMessagePrepend),
                info -> createStrikeMessage(info, strikeMessagePrepend));
    }

    @Nonnull
    private RPSAndStrikeStagesMenu createRPSAndStrikeStagesMenu(@Nonnull MessageCreateData start, @Nonnull BiFunction<RPSMenu.RPSResult, ButtonInteractionEvent, MessageCreateData> rpsTieMessageProvider, @Nonnull BiFunction<RPSMenu.RPSResult, ButtonInteractionEvent, MessageCreateData> strikeFirstMessageProvider, @Nonnull Function<StrikeStagesMenu.UpcomingStrikeInfo, MessageCreateData> strikeMessageProducer) {
        RPSInfo rpsInfo = settings.rpsInfo();

        // Will only be called if we do rps
        //noinspection ConstantConditions
        return new RPSAndStrikeStagesMenu(new RPSAndStrikeStagesMenu.Settings.Builder()
                .setTwoUsersChoicesActionMenuSettings(new TwoUsersChoicesActionMenu.Settings.Builder()
                        .setActionMenuSettings(new ActionMenu.Settings.Builder()
                                .setWaiter(getActionMenuSettings().waiter())
                                .setTimeout(rpsInfo.strikeFirstChoiceTimeout(), rpsInfo.unit())
                                .build())
                        .setUsers(getTwoUsersChoicesActionMenuSettings().user1(), getTwoUsersChoicesActionMenuSettings().user2())
                        .build())
                .setStart(start)
                .setRuleset(settings.ruleset())
                .setRpsTimeout(rpsInfo.timeout(), rpsInfo.unit())
                .setStrikeTimeout(getActionMenuSettings().timeout(), getActionMenuSettings().unit())
                .setStrikeFirstMessageProvider(strikeFirstMessageProvider)
                .setRpsTieMessageProvider(rpsTieMessageProvider)
                .setStrikeMessageProducer(strikeMessageProducer)
                .setOnRPSTimeout(this::onRPSTimeout)
                .setOnStrikeFirstChoice(this::onStrikeFirstChoice)
                .setOnStrikeFirstTimeout(this::onFirstChoiceTimeout)
                .setOnUserStrikes(this::onUserStrikes)
                .setOnStrikeTimeout(this::onStrikeTimeout)
                .build());
    }

    @Nonnull
    private StrikeStagesMenu createStrikeStagesMenu(@Nullable String messagePrepend) {
        return createStrikeStagesMenu(info -> createStrikeMessage(info, messagePrepend));
    }

    @Nonnull
    private StrikeStagesMenu createStrikeStagesMenu(@Nonnull Function<StrikeStagesMenu.UpcomingStrikeInfo, MessageCreateData> strikeMessageProducer) {
        return new StrikeStagesMenu(new StrikeStagesMenu.Settings.Builder()
                .setTwoUsersChoicesActionMenuSettings(getTwoUsersChoicesActionMenuSettings())
                .setRuleset(settings.ruleset())
                .setStrikeMessageProducer(strikeMessageProducer)
                .setOnTimeout(this::onStrikeTimeout)
                .setOnUserStrikes(this::onUserStrikes)
                .build());
    }

    @Nonnull
    private BlindPickMenu createDoubleBlindMenu(@Nonnull MessageCreateData start) {
        return new BlindPickMenu(new BlindPickMenu.Settings.Builder()
                .setActionMenuSettings(new ActionMenu.Settings.Builder()
                        .setWaiter(getActionMenuSettings().waiter())
                        .setTimeout(settings.doubleBlindTimeout(), settings.doubleBlindUnit())
                        .build())
                .setWaiter(settings.waiter())
                .addUsers(getTwoUsersChoicesActionMenuSettings().user1(), getTwoUsersChoicesActionMenuSettings().user2())
                .setStart(start)
                .setCharacters(settings.characters())
                .setOnResult(this::onDoubleBlindResult)
                .setOnTimeout(this::onDoubleBlindTimeout)
                .build());
    }

    @Nonnull
    private BlindPickMenu createCharPickMenu(@Nonnull MessageCreateData start, long user, long timeout, @Nonnull TimeUnit unit, @Nonnull BiConsumer<BlindPickMenu.BlindPickResult, ModalInteractionEvent> onResult, @Nonnull Consumer<BlindPickMenu.BlindPickTimeoutEvent> onTimeout) {
        return new BlindPickMenu(new BlindPickMenu.Settings.Builder()
                .setActionMenuSettings(new ActionMenu.Settings.Builder()
                        .setWaiter(getActionMenuSettings().waiter())
                        .setTimeout(timeout, unit)
                        .build())
                .setWaiter(settings.waiter())
                .addUsers(user)
                .setStart(start)
                .setCharacters(settings.characters())
                .setOnResult(onResult)
                .setOnTimeout(onTimeout)
                .build());
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
        @SuppressWarnings({"ConstantConditions", "OptionalGetWithoutIsPresent"})
        Stage stage = settings.ruleset().getStagesStream().filter(stage_ -> stage_.stageId() == game.getStageId()).findAny().get();

        // Characters are already chosen here
        @SuppressWarnings("ConstantConditions")
        MessageCreateData start = new MessageCreateBuilder()
                .setEmbeds(prepareEmbed("Game Reporting")
                        .setDescription(String.format("You will play your next game on %s. " +
                                        "**%s** will play as %s, and **%s** will play as %s.%n" +
                                        "You can start the game now, report the winner here once you're done.",
                                stage.getDisplayName(),
                                settings.user1Display(),
                                user1Char.getDisplayName(),
                                settings.user2Display(),
                                user2Char.getDisplayName()))
                        .build())
                .build();

        long user1 = getTwoUsersChoicesActionMenuSettings().user1();
        long user2 = getTwoUsersChoicesActionMenuSettings().user2();

        BiFunction<ReportGameMenu.ReportGameConflict, ButtonInteractionEvent, MessageEditData> conflictMessageProvider = (conflict, e) ->
                new MessageEditBuilder()
                        .setEmbeds(prepareEmbed("Game Reporting")
                                .setDescription(String.format("You reported different winners. **%s** reported **%s**, and **%s** reported **%s** as the winner. " +
                                                "One of you has to change their choice.",
                                        displayFromUser(user1),
                                        displayFromUser(conflict.getUser1ReportedWinner()),
                                        displayFromUser(user2),
                                        displayFromUser(conflict.getUser2ReportedWinner())))
                                .build())
                        .build();

        return new ReportGameMenu(new ReportGameMenu.Settings.Builder()
                .setTwoUsersChoicesActionMenuSettings(new TwoUsersChoicesActionMenu.Settings.Builder()
                        .setActionMenuSettings(new ActionMenu.Settings.Builder()
                                .setWaiter(getActionMenuSettings().waiter())
                                .setTimeout(settings.reportGameTimeout(), settings.reportGameUnit())
                                .build())
                        .setUsers(user1, user2)
                        .build())
                .setUsersDisplay(settings.user1Display(), settings.user2Display())
                .setStart(start)
                .setConflictMessageProvider(conflictMessageProvider)
                .setOnResult(this::onReportGameResult)
                .setOnTimeout(this::onReportGameTimeout)
                .setOnConflict(this::onReportGameConflict)
                .build());
    }

    @Nonnull
    private BanPickStagesMenu createBanPickStagesMenu(@Nullable String banMessagePrepend, @Nonnull MessageCreateData pickStageStart) {
        return createBanPickStagesMenu(info -> createBanMessage(info, banMessagePrepend), pickStageStart);
    }

    @Nonnull
    private BanPickStagesMenu createBanPickStagesMenu(@Nonnull Function<BanStagesMenu.UpcomingBanInfo, MessageCreateData> banMessageProducer, @Nonnull MessageCreateData pickStageStart) {
        SmashSet.SetWinnerStageBanState banState = ((SmashSet.SetWinnerStageBanState) state);
        // At this point it will be displayed => not null
        @SuppressWarnings("ConstantConditions")
        long banningUser = userFromPlayer(banState.getPrevWinner());
        long pickingUser = userFromPlayer(banState.getPrevLoser());

        return new BanPickStagesMenu(new BanPickStagesMenu.Settings.Builder()
                .setTwoUsersChoicesActionMenuSettings(new TwoUsersChoicesActionMenu.Settings.Builder()
                        .setActionMenuSettings(new ActionMenu.Settings.Builder()
                                .setWaiter(getActionMenuSettings().waiter())
                                .setTimeout(settings.banTimeout(), settings.banUnit())
                                .build())
                        .setUsers(banningUser, pickingUser)
                        .build())
                .setRuleset(settings.ruleset())
                .setDsrIllegalStages(banState.getDSRIllegalStageIds())
                .setBanMessageProducer(banMessageProducer)
                .setPickStageStart(pickStageStart)
                .setPickTimeout(settings.pickStageTimeout(), settings.pickStageUnit())
                .setOnBanResult(this::onBanResult)
                .setOnBanTimeout(this::onBanTimeout)
                .setOnPickResult(this::onPickStageResult)
                .setOnPickTimeout(this::onPickStageTimeout)
                .build());
    }

    @Nonnull
    private BlindPickMenu createWinnerCharPickMenu(@Nonnull MessageCreateData start) {
        // At this point it will be displayed => not null
        @SuppressWarnings("ConstantConditions")
        long winner = userFromPlayer(((SmashSet.SetWinnerCharPickState) state).getPrevWinner());

        return createCharPickMenu(start, winner,
                settings.winnerCharPickTimeout(), settings.winnerCharPickUnit(),
                this::onWinnerCharPickResult, this::onWinnerCharPickTimeout);
    }

    @Nonnull
    private BlindPickMenu createLoserCharCounterpickMenu(@Nonnull MessageCreateData start) {
        // At this point it will be displayed => not null
        @SuppressWarnings("ConstantConditions")
        long loser = userFromPlayer(((SmashSet.SetLoserCharCounterpickState) state).getPrevLoser());

        return createCharPickMenu(start, loser,
                settings.loserCharCounterpickTimeout(), settings.loserCharCounterpickUnit(),
                this::onLoserCharCounterpickResult, this::onLoserCharCounterpickTimeout);
    }

    private synchronized void onStrikeFirstChoice(@Nonnull RPSAndStrikeStagesMenu.StrikeFirstChoiceResult result, @Nonnull ButtonInteractionEvent event) {
        SmashSet.Player winner = playerFromUser(result.getUserMakingChoice());
        SmashSet.Player firstStriker = playerFromUser(result.getFirstStriker());

        // At this point it will be displayed => not null
        //noinspection ConstantConditions
        state = ((SmashSet.SetRPSState) state).completeRPS(winner, firstStriker);
    }

    private synchronized void onUserStrikes(@Nonnull StrikeStagesMenu.UserStrikesInfo info, @Nonnull ButtonInteractionEvent event) {
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
                .setComponents()
                .queue();

        newState.onT(doubleBlind -> {
                    MessageCreateData start = new MessageCreateBuilder()
                            .setEmbeds(prepareEmbed("Double Blind Pick")
                                    .setDescription(String.format("You have struck to %s, " +
                                                    "so now we'll determine the characters you play by doing a double blind pick.%n" +
                                                    "Please click on the button below to select the character you'll play next game.",
                                            remainingStage.getDisplayName()))
                                    .build())
                            .build();

                    createDoubleBlindMenu(start).displayReplying(event.getMessage());
                })
                .onU(inGame -> createReportGameMenu().displayReplying(event.getMessage()));
    }

    private synchronized void onDoubleBlindResult(@Nonnull BlindPickMenu.BlindPickResult result, @Nonnull ModalInteractionEvent event) {
        // Will always be found
        Character user1Choice = result.getChoices().get(getTwoUsersChoicesActionMenuSettings().user1());

        // Will always be found
        Character user2Choice = result.getChoices().get(getTwoUsersChoicesActionMenuSettings().user2());

        // At this point it will be displayed => not null
        @SuppressWarnings("ConstantConditions")
        OneOfTwo<OneOfTwo<SmashSet.SetRPSState, SmashSet.SetStarterStrikingState>, SmashSet.SetInGameState> newState = ((SmashSet.SetDoubleBlindState) state)
                .completeDoubleBlind(user1Choice, user2Choice);

        state = newState.map(notInGame -> notInGame.map(rps -> rps, striking -> striking), inGame -> inGame);

        MessageChannel channel = result.getChannel();
        if (channel == null) {
            settings.onMessageChannelNotInCache().accept(new SmashSetStateInfo());
            return;
        }

        long messageId = result.getMessageId();

        event.editMessageEmbeds(prepareEmbed("Double Blind Pick")
                        .setDescription(String.format("**%s** chose %s and **%s** chose %s.",
                                settings.user1Display(),
                                user1Choice.getDisplayName(),
                                settings.user2Display(),
                                user2Choice.getDisplayName()))
                        .build())
                .setComponents()
                .queue();

        newState.map(
                notInGame -> {
                    String messagePrepend = String.format("The characters are decided. **%s** plays %s, and **%s** plays %s next game.",
                            settings.user1Display(),
                            user1Choice.getDisplayName(),
                            settings.user2Display(),
                            user2Choice.getDisplayName());

                    return notInGame.map(
                            rps -> {
                                MessageCreateData start = new MessageCreateBuilder()
                                        .setEmbeds(prepareEmbed("RPS")
                                                .setDescription(String.format("The characters are decided. **%s** plays %s, and **%s** plays %s next game.%n" +
                                                                "Now we'll play a game of RPS to determine who will strike first.",
                                                        settings.user1Display(),
                                                        user1Choice.getDisplayName(),
                                                        settings.user2Display(),
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

    private synchronized void onReportGameConflict(@Nonnull ReportGameMenu.ReportGameConflict reportConflict, @Nonnull ButtonInteractionEvent event) {
        boolean bothClaimedWin = reportConflict.getUser1ReportedWinner() == getTwoUsersChoicesActionMenuSettings().user1();
        SmashSet.Conflict conflict = new SmashSet.Conflict(bothClaimedWin);

        // At this point it will be displayed => not null
        // Copying because ReportGameMenu will mutate conflict
        //noinspection ConstantConditions
        ((SmashSet.SetInGameState) state).registerConflict(new SmashSet.Conflict(conflict.isBothClaimedWin()));
    }

    private synchronized void onReportGameResult(@Nonnull ReportGameMenu.ReportGameResult result, @Nonnull ButtonInteractionEvent event) {
        SmashSet.SetInGameState inGameState = (SmashSet.SetInGameState) state;
        OneOfTwo<OneOfTwo<SmashSet.SetWinnerStageBanState, SmashSet.SetWinnerCharPickState>, SmashSet.SetCompletedState> newState;

        SmashSet.Conflict conflict = result.getConflict();
        if (conflict != null) {
            // At this point it will be displayed => not null
            //noinspection ConstantConditions
            newState = inGameState.resolveConflict(conflict.getResolution());
        } else {
            SmashSet.Player winner = playerFromUser(result.getWinner());

            // At this point it will be displayed => not null
            //noinspection ConstantConditions
            newState = inGameState.completeGame(winner);
        }


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
                                settings.user1Display(),
                                user1Score,
                                user2Score,
                                settings.user2Display()))
                        .build())
                .setComponents()
                .queue();

        newState.onT(
                notComplete -> notComplete.onT(stageBan -> {
                    MessageCreateData pickStagesStart = new MessageCreateBuilder()
                            .setEmbeds(prepareEmbed("Stage Ban / Counterpick")
                                    .setDescription(String.format("It has been determined that **%s** won, and **%s** lost the last game and the stages have been banned.%n" +
                                                    "So **%s**, please counterpick one of the remaining stages.",
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

                    createBanPickStagesMenu(banMessagePrepend, pickStagesStart).displayReplying(event.getMessage());
                }).onU(charPick -> {
                            MessageCreateData start = new MessageCreateBuilder()
                                    .setEmbeds(prepareEmbed("Winner Character Pick")
                                            .setDescription(String.format("It has been determined that **%s** won, and **%s** lost the last game. " +
                                                            "Now, **%s**, you have to pick the character you will play next game first.%n" +
                                                            "Please click on the button below to select the character you'll play.",
                                                    displayFromUser(result.getWinner()),
                                                    displayFromUser(result.getLoser()),
                                                    displayFromUser(result.getWinner())))
                                            .build())
                                    .build();

                            createWinnerCharPickMenu(start).displayReplying(event.getMessage());
                        }
                )
        ).onU(completed -> this.onResult(event));
    }

    private synchronized void onBanResult(@Nonnull BanStagesMenu.BanResult result, @Nonnull ButtonInteractionEvent event) {
        // At this point it will be displayed => not null
        //noinspection ConstantConditions
        state = ((SmashSet.SetWinnerStageBanState) state).banStages(result.getBannedStageIds());
    }

    private synchronized void onPickStageResult(@Nonnull PickStageMenu.PickStageResult result, @Nonnull ButtonInteractionEvent event) {
        // At this point it will be displayed => not null
        @SuppressWarnings("ConstantConditions")
        OneOfTwo<SmashSet.SetWinnerCharPickState, SmashSet.SetInGameState> newState = ((SmashSet.SetLoserStageCounterpickState) state)
                .pickStage(result.getPickedStageId());

        state = newState.map(charPick -> charPick, inGame -> inGame);

        event.editMessageEmbeds(prepareEmbed("Stage Ban / Counterpick")
                        .setDescription(String.format("You will play the next game on %s.", result.getPickedStage().getDisplayName()))
                        .build())
                .setComponents()
                .queue();

        newState.onT(charPick -> {
            long prevWinner = userFromPlayer(charPick.getPrevWinner());

            MessageCreateData start = new MessageCreateBuilder()
                    .setEmbeds(prepareEmbed("Winner Character Pick")
                            .setDescription(String.format("You will play the next game on %s. " +
                                            "Now the winner of the previous game will have to pick the character.%n" +
                                            "**%s**, please click on the button below to select the character you'll play next game.",
                                    result.getPickedStage().getDisplayName(),
                                    displayFromUser(prevWinner)))
                            .build())
                    .build();

            createWinnerCharPickMenu(start).displayReplying(event.getMessage());
        }).onU(inGame -> createReportGameMenu().displayReplying(event.getMessage()));
    }

    private synchronized void onWinnerCharPickResult(@Nonnull BlindPickMenu.BlindPickResult result, @Nonnull ModalInteractionEvent event) {
        long user = result.getBlindPickMenuSettings().users().stream().findAny().orElseThrow();
        Character picked = result.getChoices().get(user);

        // At this point it will be displayed => not null
        //noinspection ConstantConditions
        SmashSet.SetLoserCharCounterpickState newState = ((SmashSet.SetWinnerCharPickState) state).pickCharacter(picked);
        state = newState;

        MessageChannel channel = result.getChannel();
        if (channel == null) {
            settings.onMessageChannelNotInCache().accept(new SmashSetStateInfo());
            return;
        }

        long messageId = result.getMessageId();

        event.editMessageEmbeds(prepareEmbed("Winner Character Pick")
                        .setDescription(String.format("**%s** picked %s.",
                                displayFromUser(user),
                                picked.getDisplayName()))
                        .build())
                .setComponents()
                .queue();

        long prevLoser = userFromPlayer(newState.getPrevLoser());

        MessageCreateData start = new MessageCreateBuilder()
                .setEmbeds(prepareEmbed("Loser Character Counterpick")
                        .setDescription(String.format("**%s** chose %s as their character, so now the loser of the previous game has to state their character.%n" +
                                        "**%s**, please click on the button below to select the character you will use next game.",
                                displayFromUser(user),
                                picked.getDisplayName(),
                                displayFromUser(prevLoser)))
                        .build())
                .build();

        createLoserCharCounterpickMenu(start).displayReplying(channel, messageId);
    }

    private synchronized void onLoserCharCounterpickResult(@Nonnull BlindPickMenu.BlindPickResult result, @Nonnull ModalInteractionEvent event) {
        long user = result.getBlindPickMenuSettings().users().stream().findAny().orElseThrow();
        Character picked = result.getChoices().get(user);

        // At this point it will be displayed => not null
        @SuppressWarnings("ConstantConditions")
        OneOfTwo<SmashSet.SetWinnerStageBanState, SmashSet.SetInGameState> newState = ((SmashSet.SetLoserCharCounterpickState) state)
                .pickCharacter(picked);

        state = newState.map(ban -> ban, inGame -> inGame);

        MessageChannel channel = result.getChannel();
        if (channel == null) {
            settings.onMessageChannelNotInCache().accept(new SmashSetStateInfo());
            return;
        }

        long messageId = result.getMessageId();

        event.editMessageEmbeds(prepareEmbed("Loser Character Counterpick")
                        .setDescription(String.format("**%s** counterpicked %s.",
                                displayFromUser(user),
                                picked.getDisplayName()))
                        .build())
                .setComponents()
                .queue();

        newState.map(
                stageBan -> {
                    Character user1Char = stageBan.getGame().getPlayer1Char();
                    Character user2Char = stageBan.getGame().getPlayer2Char();
                    long prevLoser = userFromPlayer(stageBan.getPrevLoser());

                    // Characters will have been determined at this point
                    @SuppressWarnings("ConstantConditions")
                    String banMessagePrepend = String.format("**%s** picked %s and **%s** picked %s. Now, onto stage banning.",
                            settings.user1Display(),
                            user1Char.getDisplayName(),
                            settings.user2Display(),
                            user2Char.getDisplayName());

                    MessageCreateData pickStageStart = new MessageCreateBuilder()
                            .setEmbeds(prepareEmbed("Stage Ban / Counterpick")
                                    .setDescription(String.format("**%s** picked %s, **%s** picked %s, and the stages have been banned.%n" +
                                                    "**%s**, please counterpick a stage from the list below now.",
                                            settings.user1Display(),
                                            user1Char.getDisplayName(),
                                            settings.user2Display(),
                                            user2Char.getDisplayName(),
                                            displayFromUser(prevLoser)))
                                    .build())
                            .build();

                    return createBanPickStagesMenu(banMessagePrepend, pickStageStart);
                },
                inGame -> createReportGameMenu()
        ).displayReplying(channel, messageId);
    }

    private void onResult(@Nonnull ButtonInteractionEvent event) {
        settings.onResult().accept(new SmashSetResult(), event);
    }

    private void onRPSTimeout(@Nonnull RPSMenu.RPSTimeoutEvent event) {
        // If this is called this is nonnull
        //noinspection ConstantConditions
        settings.rpsInfo().onRPSTimeout().accept(new SmashSetRPSTimeoutEvent(event));
    }

    private void onFirstChoiceTimeout(@Nonnull RPSAndStrikeStagesMenu.StrikeFirstChoiceTimeoutEvent event) {
        // If this is called this is nonnull
        //noinspection ConstantConditions
        settings.rpsInfo().onFirstChoiceTimeout().accept(new SmashSetStrikeFirstChoiceTimeoutEvent(event));
    }

    private void onStrikeTimeout(@Nonnull StrikeStagesMenu.StrikeStagesTimeoutEvent event) {
        settings.onStrikeTimeout().accept(new SmashSetStrikeTimeoutEvent(event));
    }

    private void onDoubleBlindTimeout(@Nonnull BlindPickMenu.BlindPickTimeoutEvent event) {
        settings.onDoubleBlindTimeout().accept(new SmashSetDoubleBlindTimeoutEvent(event));
    }

    private void onReportGameTimeout(@Nonnull ReportGameMenu.ReportGameTimeoutEvent event) {
        settings.onReportGameTimeout().accept(new SmashSetReportGameTimeoutEvent(event));
    }

    private void onBanTimeout(@Nonnull BanStagesMenu.BanStagesTimeoutEvent event) {
        settings.onBanTimeout().accept(new SmashSetBanTimeoutEvent(event));
    }

    private void onPickStageTimeout(@Nonnull PickStageMenu.PickStageTimeoutEvent event) {
        settings.onPickStageTimeout().accept(new SmashSetPickStageTimeoutEvent(event));
    }

    private void onWinnerCharPickTimeout(@Nonnull BlindPickMenu.BlindPickTimeoutEvent event) {
        settings.onWinnerCharPickTimeout().accept(new SmashSetCharPickTimeoutEvent(event));
    }

    private void onLoserCharCounterpickTimeout(@Nonnull BlindPickMenu.BlindPickTimeoutEvent event) {
        settings.onLoserCharCounterpickTimeout().accept(new SmashSetCharPickTimeoutEvent(event));
    }

    private long userFromPlayer(@Nonnull SmashSet.Player player) {
        return player == SmashSet.Player.PLAYER1 ? getTwoUsersChoicesActionMenuSettings().user1() : getTwoUsersChoicesActionMenuSettings().user2();
    }

    @Nonnull
    private SmashSet.Player playerFromUser(long user) {
        return user == getTwoUsersChoicesActionMenuSettings().user1() ? SmashSet.Player.PLAYER1 : SmashSet.Player.PLAYER2;
    }

    @Nonnull
    private String displayFromUser(long user) {
        return user == getTwoUsersChoicesActionMenuSettings().user1() ? settings.user1Display() : settings.user2Display();
    }

    @Nonnull
    private EmbedBuilder prepareEmbed(@Nonnull String title) {
        return prepareEmbed(title, set.getGames().size());
    }

    @Nonnull
    private EmbedBuilder prepareEmbed(@Nonnull String title, int gameNumber) {
        return EmbedUtil.getPrepared()
                .setTitle(String.format("%s vs %s | BO%s | Game %d | %s",
                        settings.user1Display(),
                        settings.user2Display(),
                        set.getBestOfWhatScore(),
                        gameNumber,
                        title))
                .setFooter(String.format("%s vs %s", settings.user1Display(), settings.user2Display()));
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

    @Nonnull
    public Settings getSmashSetMenuSettings() {
        return settings;
    }

    public class SmashSetStateInfo extends TwoUsersMenuStateInfo {
        @Nonnull
        public Settings getSmashSetMenuSettings() {
            return SmashSetMenu.this.getSmashSetMenuSettings();
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
        private final BlindPickMenu.BlindPickTimeoutEvent timeoutEvent;

        public SmashSetCharPickTimeoutEvent(@Nonnull BlindPickMenu.BlindPickTimeoutEvent timeoutEvent) {
            this.timeoutEvent = timeoutEvent;
        }

        @Nonnull
        public BlindPickMenu.BlindPickTimeoutEvent getTimeoutEvent() {
            return timeoutEvent;
        }
    }

    public record RPSInfo(long timeout, @Nonnull TimeUnit unit, long strikeFirstChoiceTimeout,
                          @Nonnull TimeUnit strikeFirstChoiceUnit,
                          @Nonnull Consumer<SmashSetStrikeFirstChoiceTimeoutEvent> onFirstChoiceTimeout,
                          @Nonnull Consumer<SmashSetRPSTimeoutEvent> onRPSTimeout) {
    }

    public record Settings(@Nonnull TwoUsersChoicesActionMenu.Settings twoUsersChoicesActionMenuSettings,
                           @Nonnull EventWaiter waiter, @Nonnull Ruleset ruleset,
                           @Nonnull List<Character> characters, int firstToWhatScore,
                           @Nullable RPSInfo rpsInfo,
                           @Nonnull Consumer<SmashSetStrikeTimeoutEvent> onStrikeTimeout,
                           long doubleBlindTimeout, @Nonnull TimeUnit doubleBlindUnit,
                           @Nonnull Consumer<SmashSetDoubleBlindTimeoutEvent> onDoubleBlindTimeout,
                           long reportGameTimeout, @Nonnull TimeUnit reportGameUnit, @Nonnull String user1Display,
                           @Nonnull String user2Display,
                           @Nonnull Consumer<SmashSetReportGameTimeoutEvent> onReportGameTimeout,
                           long banTimeout, @Nonnull TimeUnit banUnit,
                           @Nonnull Consumer<SmashSetBanTimeoutEvent> onBanTimeout,
                           long pickStageTimeout, @Nonnull TimeUnit pickStageUnit,
                           @Nonnull Consumer<SmashSetPickStageTimeoutEvent> onPickStageTimeout,
                           long winnerCharPickTimeout, @Nonnull TimeUnit winnerCharPickUnit,
                           @Nonnull Consumer<SmashSetCharPickTimeoutEvent> onWinnerCharPickTimeout,
                           long loserCharCounterpickTimeout, @Nonnull TimeUnit loserCharCounterpickUnit,
                           @Nonnull Consumer<SmashSetCharPickTimeoutEvent> onLoserCharCounterpickTimeout,
                           @Nonnull Consumer<SmashSetStateInfo> onMessageChannelNotInCache,
                           @Nonnull BiConsumer<SmashSetResult, ButtonInteractionEvent> onResult) {
        @Nonnull
        public static final Consumer<SmashSetStrikeTimeoutEvent> DEFAULT_ON_STRIKE_TIMEOUT = MiscUtil.emptyConsumer();
        public static final long DEFAULT_DOUBLE_BLIND_TIMEOUT = 5;
        @Nonnull
        public static final TimeUnit DEFAULT_DOUBLE_BLIND_UNIT = TimeUnit.MINUTES;
        @Nonnull
        public static final Consumer<SmashSetDoubleBlindTimeoutEvent> DEFAULT_ON_DOUBLE_BLIND_TIMEOUT = MiscUtil.emptyConsumer();
        @Nonnull
        public static final Consumer<SmashSetStateInfo> DEFAULT_ON_DOUBLE_BLIND_FAILED_INIT = MiscUtil.emptyConsumer();
        public static final long DEFAULT_REPORT_GAME_TIMEOUT = 40;
        @Nonnull
        public static final TimeUnit DEFAULT_REPORT_GAME_UNIT = TimeUnit.MINUTES;
        @Nonnull
        public static final Consumer<SmashSetReportGameTimeoutEvent> DEFAULT_ON_REPORT_GAME_TIMEOUT = MiscUtil.emptyConsumer();
        public static final long DEFAULT_BAN_TIMEOUT = 5;
        @Nonnull
        public static final TimeUnit DEFAULT_BAN_UNIT = TimeUnit.MINUTES;
        @Nonnull
        public static final Consumer<SmashSetBanTimeoutEvent> DEFAULT_ON_BAN_TIMEOUT = MiscUtil.emptyConsumer();
        public static final long DEFAULT_PICK_STAGE_TIMEOUT = 5;
        @Nonnull
        public static final TimeUnit DEFAULT_PICK_STAGE_UNIT = TimeUnit.MINUTES;
        @Nonnull
        public static final Consumer<SmashSetPickStageTimeoutEvent> DEFAULT_ON_PICK_STAGE_TIMEOUT = MiscUtil.emptyConsumer();
        public static final long DEFAULT_WINNER_CHAR_PICK_TIMEOUT = 5;
        @Nonnull
        public static final TimeUnit DEFAULT_WINNER_CHAR_PICK_UNIT = TimeUnit.MINUTES;
        @Nonnull
        public static final Consumer<SmashSetCharPickTimeoutEvent> DEFAULT_ON_WINNER_CHAR_PICK_TIMEOUT = MiscUtil.emptyConsumer();
        @Nonnull
        public static final Consumer<SmashSetStateInfo> DEFAULT_ON_WINNER_CHAR_PICK_FAILED_INIT = MiscUtil.emptyConsumer();
        public static final long DEFAULT_LOSER_CHAR_COUNTERPICK_TIMEOUT = 5;
        @Nonnull
        public static final TimeUnit DEFAULT_LOSER_CHAR_COUNTERPICK_UNIT = TimeUnit.MINUTES;
        @Nonnull
        public static final Consumer<SmashSetCharPickTimeoutEvent> DEFAULT_ON_LOSER_CHAR_COUNTERPICK_TIMEOUT = MiscUtil.emptyConsumer();
        @Nonnull
        public static final Consumer<SmashSetStateInfo> DEFAULT_ON_LOSER_CHAR_COUNTERPICK_FAILED_INIT = MiscUtil.emptyConsumer();
        @Nonnull
        public static final Consumer<SmashSetStateInfo> DEFAULT_ON_MESSAGE_CHANNEL_NOT_IN_CACHE = MiscUtil.emptyConsumer();
        @Nonnull
        public static final BiConsumer<SmashSetResult, ButtonInteractionEvent> DEFAULT_ON_RESULT = MiscUtil.emptyBiConsumer();

        public static class Builder {
            @Nullable
            private TwoUsersChoicesActionMenu.Settings twoUsersChoicesActionMenuSettings;

            @Nullable
            private EventWaiter waiter;

            @Nullable
            private Ruleset ruleset;
            @Nullable
            private List<Character> characters;
            @Nullable
            private Integer firstToWhatScore;

            @Nullable
            private RPSInfo rpsInfo;

            @Nonnull
            private Consumer<SmashSetStrikeTimeoutEvent> onStrikeTimeout = DEFAULT_ON_STRIKE_TIMEOUT;

            private long doubleBlindTimeout = DEFAULT_DOUBLE_BLIND_TIMEOUT;
            @Nonnull
            private TimeUnit doubleBlindUnit = DEFAULT_DOUBLE_BLIND_UNIT;
            @Nonnull
            private Consumer<SmashSetDoubleBlindTimeoutEvent> onDoubleBlindTimeout = DEFAULT_ON_DOUBLE_BLIND_TIMEOUT;

            private long reportGameTimeout = DEFAULT_REPORT_GAME_TIMEOUT;
            @Nonnull
            private TimeUnit reportGameUnit = DEFAULT_REPORT_GAME_UNIT;
            @Nullable
            private String user1Display;
            @Nullable
            private String user2Display;
            @Nonnull
            private Consumer<SmashSetReportGameTimeoutEvent> onReportGameTimeout = DEFAULT_ON_REPORT_GAME_TIMEOUT;

            private long banTimeout = DEFAULT_BAN_TIMEOUT;
            @Nonnull
            private TimeUnit banUnit = DEFAULT_BAN_UNIT;
            @Nonnull
            private Consumer<SmashSetBanTimeoutEvent> onBanTimeout = DEFAULT_ON_BAN_TIMEOUT;
            private long pickStageTimeout = DEFAULT_PICK_STAGE_TIMEOUT;
            @Nonnull
            private TimeUnit pickStageUnit = DEFAULT_PICK_STAGE_UNIT;
            @Nonnull
            private Consumer<SmashSetPickStageTimeoutEvent> onPickStageTimeout = DEFAULT_ON_PICK_STAGE_TIMEOUT;

            private long winnerCharPickTimeout = DEFAULT_WINNER_CHAR_PICK_TIMEOUT;
            @Nonnull
            private TimeUnit winnerCharPickUnit = DEFAULT_WINNER_CHAR_PICK_UNIT;
            @Nonnull
            private Consumer<SmashSetCharPickTimeoutEvent> onWinnerCharPickTimeout = DEFAULT_ON_WINNER_CHAR_PICK_TIMEOUT;

            private long loserCharCounterpickTimeout = DEFAULT_LOSER_CHAR_COUNTERPICK_TIMEOUT;
            @Nonnull
            private TimeUnit loserCharCounterpickUnit = DEFAULT_LOSER_CHAR_COUNTERPICK_UNIT;
            @Nonnull
            private Consumer<SmashSetCharPickTimeoutEvent> onLoserCharCounterpickTimeout = DEFAULT_ON_LOSER_CHAR_COUNTERPICK_TIMEOUT;

            @Nonnull
            private Consumer<SmashSetStateInfo> onMessageChannelNotInCache = DEFAULT_ON_MESSAGE_CHANNEL_NOT_IN_CACHE;

            @Nonnull
            private BiConsumer<SmashSetResult, ButtonInteractionEvent> onResult = DEFAULT_ON_RESULT;

            /**
             * timeout is strike stages timeout
             */
            @Nonnull
            public Builder setTwoUsersChoicesActionMenuSettings(@Nullable TwoUsersChoicesActionMenu.Settings twoUsersChoicesActionMenuSettings) {
                this.twoUsersChoicesActionMenuSettings = twoUsersChoicesActionMenuSettings;
                return this;
            }

            @Nonnull
            public Builder setWaiter(@Nonnull EventWaiter waiter) {
                this.waiter = waiter;
                return this;
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
            public Builder setOnMessageChannelNotInCache(@Nonnull Consumer<SmashSetStateInfo> onMessageChannelNotInCache) {
                this.onMessageChannelNotInCache = onMessageChannelNotInCache;
                return this;
            }

            @Nonnull
            public Builder setOnResult(@Nonnull BiConsumer<SmashSetResult, ButtonInteractionEvent> onResult) {
                this.onResult = onResult;
                return this;
            }

            @Nonnull
            public Settings build() {
                if (twoUsersChoicesActionMenuSettings == null)
                    throw new IllegalStateException("TwoUsersChoicesActionMenuSettings must be set");
                if (waiter == null) throw new IllegalStateException("ChannelWaiter must be set");
                if (ruleset == null) throw new IllegalStateException("Ruleset must be set");
                if (characters == null) throw new IllegalStateException("Characters must be set");
                if (firstToWhatScore == null) throw new IllegalStateException("FirstToWhatScore must be set");
                if (user1Display == null || user2Display == null)
                    throw new IllegalStateException("UsersDisplay must be set");

                return new Settings(twoUsersChoicesActionMenuSettings, waiter, ruleset, characters, firstToWhatScore,
                        rpsInfo,
                        onStrikeTimeout,
                        doubleBlindTimeout, doubleBlindUnit, onDoubleBlindTimeout,
                        reportGameTimeout, reportGameUnit, user1Display, user2Display, onReportGameTimeout,
                        banTimeout, banUnit, onBanTimeout,
                        pickStageTimeout, pickStageUnit, onPickStageTimeout,
                        winnerCharPickTimeout, winnerCharPickUnit, onWinnerCharPickTimeout,
                        loserCharCounterpickTimeout, loserCharCounterpickUnit, onLoserCharCounterpickTimeout,
                        onMessageChannelNotInCache, onResult);
            }
        }
    }
}
