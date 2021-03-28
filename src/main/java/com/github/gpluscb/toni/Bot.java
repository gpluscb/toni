package com.github.gpluscb.toni;

import at.stefangeyer.challonge.exception.DataAccessException;
import com.github.gpluscb.ggjava.api.GGClient;
import com.github.gpluscb.toni.command.Command;
import com.github.gpluscb.toni.command.CommandCategory;
import com.github.gpluscb.toni.command.CommandDispatcher;
import com.github.gpluscb.toni.command.CommandListener;
import com.github.gpluscb.toni.command.admin.EvalCommand;
import com.github.gpluscb.toni.command.admin.ShutdownCommand;
import com.github.gpluscb.toni.command.admin.StatusCommand;
import com.github.gpluscb.toni.command.admin.UpdateSmashdataCommand;
import com.github.gpluscb.toni.command.game.BlindPickCommand;
import com.github.gpluscb.toni.command.game.RandomCharacterCommand;
import com.github.gpluscb.toni.command.game.RandomPlayerCommand;
import com.github.gpluscb.toni.command.game.RockPaperScissorsCommand;
import com.github.gpluscb.toni.command.help.HelpCommand;
import com.github.gpluscb.toni.command.help.PingCommand;
import com.github.gpluscb.toni.command.help.PrivacyCommand;
import com.github.gpluscb.toni.command.lookup.CharacterCommand;
import com.github.gpluscb.toni.command.lookup.SmashdataCommand;
import com.github.gpluscb.toni.command.lookup.TournamentCommand;
import com.github.gpluscb.toni.statsposting.dbots.DBotsClient;
import com.github.gpluscb.toni.statsposting.PostGuildRoutine;
import com.github.gpluscb.toni.smashdata.SmashdataManager;
import com.github.gpluscb.toni.smashgg.GGManager;
import com.github.gpluscb.toni.statsposting.topgg.TopggClient;
import com.github.gpluscb.toni.ultimateframedata.UltimateframedataClient;
import com.github.gpluscb.toni.util.CharacterTree;
import com.github.gpluscb.toni.util.DMChoiceWaiter;
import com.github.gpluscb.toni.util.DiscordAppenderImpl;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.MessageAction;
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder;
import net.dv8tion.jda.api.sharding.ShardManager;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import okhttp3.OkHttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.security.auth.login.LoginException;
import java.io.*;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

// TODO: Update to java 14
public class Bot {
    private static final Logger log = LogManager.getLogger(Bot.class);

    @Nonnull
    private final CommandDispatcher dispatcher;
    @Nonnull
    private final ShardManager shardManager;
    @Nonnull
    private final GGManager ggManager;
    @Nonnull
    private final PostGuildRoutine postGuildRoutine;
    /*@Nonnull
    private final ListenerManager challongeManager;
    @Nonnull
    private final TournamentListener listener;
    @Nonnull
    private final RetrofitRestClient client;*/ // TODO: CHALLONGE FEATURES ON HOLD
    @Nonnull
    private final SmashdataManager smashdata;
    @Nonnull
    private final ScheduledExecutorService waiterPool;

    public static void main(String[] args) {
        if (args.length != 1) {
            log.error("Give config file as first argument");
            return;
        }

        try {
            log.info("Booting...");
            new Bot(args[0]);
        } catch (Exception e) {
            log.error("Exception caught while constructing bot: ", e);
        }
    }

    public Bot(@Nonnull String configLocation) throws LoginException, SQLException, DataAccessException, IOException {
        log.trace("Loading config");
        Config cfg = loadConfig(configLocation);

        log.trace("Loading stopwords");
        List<String> stopwords;
        try {
            Path path = FileSystems.getDefault().getPath(cfg.getStopwordListLocation());
            stopwords = Files.readAllLines(path);
        } catch (Exception e) {
            log.error("Exception while loading stopwords - shutting down", e);
            throw e;
        }

        log.trace("Creating OkHttp client");
        OkHttpClient okHttp = new OkHttpClient.Builder().build();

        log.trace("Building GGManager");
        ggManager = new GGManager(GGClient.builder(cfg.getGGToken()).client(okHttp).build(), stopwords);

		/*log.trace("Building ListenerManager");
		client = new RetrofitRestClient();

		ChallongeExtension challonge;
		try {
			challonge = new ChallongeExtension(new Credentials(config.getChallongeUsername(), config.getChallongeToken()), new GsonSerializer(), client);

			challongeManager = new ListenerManager(challonge, 30 * 1000);
		} catch(DataAccessException e) {
			log.error("DataAccessException - shutting down", e);
			ggManager.shutdown();
			client.close();
			throw e;
		}*/

        // Avoid unintentional pings.
        MessageAction.setDefaultMentions(Collections.emptyList());
        MessageAction.setDefaultMentionRepliedUser(false);
        // Avoid too long request queue
        RestAction.setDefaultTimeout(30, TimeUnit.SECONDS);

        log.trace("Building UltimateframedataClient");
        UltimateframedataClient ufdClient = new UltimateframedataClient(okHttp);

        log.trace("Building EventWaiter");
        waiterPool = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "EventWaiterPool [0 / 1] Waiter-Thread"));
        EventWaiter waiter = new EventWaiter(waiterPool, false);
        DMChoiceWaiter dmWaiter = new DMChoiceWaiter(waiter);

        try {
            log.trace("Building ShardManager");
            shardManager = DefaultShardManagerBuilder.createLight(cfg.getDiscordToken())
                    .enableIntents(GatewayIntent.DIRECT_MESSAGES, GatewayIntent.GUILD_MESSAGES, GatewayIntent.DIRECT_MESSAGE_REACTIONS, GatewayIntent.GUILD_MESSAGE_REACTIONS)
                    .enableCache(CacheFlag.MEMBER_OVERRIDES)
                    .setMemberCachePolicy(MemberCachePolicy.NONE)
                    .setChunkingFilter(ChunkingFilter.NONE)
                    .addEventListeners(waiter)
                    .setActivity(Activity.listening("Help: \"Toni, Help\""))
                    .build();
        } catch (LoginException e) {
            log.error("LoginException - shutting down", e);
            ggManager.shutdown();
            //challongeManager.shutdown();
            //client.close();
            waiterPool.shutdownNow();
            throw e;
        }

		/* log.trace("Registering TournamentListener");
		try {
			listener = new TournamentListener(shardManager, cfg.getStateDbLocation());
			challongeManager.addListener(listener);
		} catch(SQLException e) {
			log.error("Exception while registering TournamentListener - shutting down", e);
			ggManager.shutdown();
			shardManager.shutdown();
			challongeManager.shutdown();
			client.close();
			waiterPool.shutdownNow();
			throw e;
		}*/

        log.trace("Loading characters");
        CharacterTree characterTree;
        try (Reader file = new FileReader(cfg.getCharactersFileLocation())) {
            JsonArray json = JsonParser.parseReader(file).getAsJsonArray();
            characterTree = CharacterTree.fromJson(json);
        } catch (Exception e) {
            log.error("Exception while loading characters - shutting down", e);
            ggManager.shutdown();
            shardManager.shutdown();
            // challongeManager.shutdown();
            // listener.shutdown();
            // client.close();
            waiterPool.shutdownNow();
            throw e;
        }

        log.trace("Loading smashdata");
        try {
            smashdata = new SmashdataManager(cfg.getSmashdataDbLocation());
        } catch (SQLException e) {
            log.error("Exception while loading smashdata - shutting down", e);
            ggManager.shutdown();
            shardManager.shutdown();
            // challongeManager.shutdown();
            // listener.shutdown();
            // client.close();
            waiterPool.shutdownNow();
            throw e;
        }

        log.trace("Loading commands");
        List<CommandCategory> commands = loadCommands(ufdClient, waiter, dmWaiter, /*challonge, listener, */characterTree, cfg);
        dispatcher = new CommandDispatcher(commands);

        CommandListener commandListener = new CommandListener(dmWaiter, dispatcher, cfg.getBotId());
        shardManager.addEventListener(commandListener);

        log.trace("Enabling discord appender");
        DiscordAppenderImpl.setShardManager(shardManager);

        log.trace("Creating DBotsClient");
        DBotsClient dBotsClient = new DBotsClient(cfg.getDbotsToken(), okHttp, 698889469532569671L); // FIXME: hardcoded

        log.trace("Creating TopGGClient");
        TopggClient topggClient = new TopggClient(cfg.getTopggToken(), okHttp, 698889469532569671L); // FIXME: hardcoded

        log.trace("Starting post stats routine");
        postGuildRoutine = new PostGuildRoutine(dBotsClient, topggClient, shardManager);

        log.info("Bot construction complete");
    }

    @Nonnull
    private Config loadConfig(@Nonnull String location) throws IOException {
        try (Reader configFile = new FileReader(location)) {
            Gson gson = new Gson();
            Config config = gson.fromJson(configFile, Config.class);
            config.check();

            return config;
        }
    }

    @Nonnull
    private List<CommandCategory> loadCommands(@Nonnull UltimateframedataClient ufdClient, @Nonnull EventWaiter waiter, @Nonnull DMChoiceWaiter dmWaiter, /*@Nonnull ChallongeExtension challonge, @Nonnull TournamentListener listener, */@Nonnull CharacterTree characterTree, @Nonnull Config cfg) {
        String supportServer = cfg.getSupportServer();
        long devId = cfg.getDevId();
        String inviteUrl = cfg.getInviteUrl();
        String twitterHandle = cfg.getTwitterHandle();

        List<CommandCategory> commands = new ArrayList<>();

        List<Command> adminCommands = new ArrayList<>();
        adminCommands.add(new ShutdownCommand(this));
        adminCommands.add(new EvalCommand());
        adminCommands.add(new StatusCommand());
        adminCommands.add(new UpdateSmashdataCommand(smashdata));
        commands.add(new CommandCategory(null, null, adminCommands));

        List<Command> infoCommands = new ArrayList<>();
        infoCommands.add(new HelpCommand(commands, supportServer, inviteUrl, twitterHandle, devId));
        infoCommands.add(new PrivacyCommand(supportServer, twitterHandle, devId));
        infoCommands.add(new PingCommand());
        commands.add(new CommandCategory("info", "Bot information commands", infoCommands));

        List<Command> gameCommands = new ArrayList<>();
        gameCommands.add(new RandomCharacterCommand(characterTree));
        gameCommands.add(new RandomPlayerCommand());
        gameCommands.add(new RockPaperScissorsCommand(dmWaiter));
        gameCommands.add(new BlindPickCommand(dmWaiter, characterTree));
        commands.add(new CommandCategory("game", "Smash Bros. utility commands", gameCommands));

        List<Command> lookupCommands = new ArrayList<>();
        lookupCommands.add(new TournamentCommand(ggManager, waiter));
        lookupCommands.add(new CharacterCommand(ufdClient, waiter, characterTree));
        lookupCommands.add(new SmashdataCommand(waiter, smashdata));
        // TODO: Feature is on hold
        // lookupCommands.add(new SubscribeCommand(challonge, listener));
        // lookupCommands.add(new UnsubscribeCommand(challonge, listener));
        commands.add(new CommandCategory("lookup", "Lookup commands for other websites", lookupCommands));

        return commands;
    }

    public void shutdown() {
        log.info("Shutting down");
        ggManager.shutdown();
        shardManager.shutdown();
        dispatcher.shutdown();
        postGuildRoutine.shutdown();
        try {
            smashdata.shutdown();
        } catch (SQLException e) {
            log.catching(e);
        }

		/*challongeManager.shutdown();
		try {
			listener.shutdown();
		} catch (SQLException e) {
			log.catching(e);
		}
		client.close();*/
        waiterPool.shutdownNow();
    }
}
