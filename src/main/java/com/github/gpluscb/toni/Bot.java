package com.github.gpluscb.toni;

import com.github.gpluscb.ggjava.api.GGClient;
import com.github.gpluscb.toni.command.Command;
import com.github.gpluscb.toni.command.CommandCategory;
import com.github.gpluscb.toni.command.CommandDispatcher;
import com.github.gpluscb.toni.command.CommandListener;
import com.github.gpluscb.toni.command.admin.EvalCommand;
import com.github.gpluscb.toni.command.admin.ShutdownCommand;
import com.github.gpluscb.toni.command.admin.StatusCommand;
import com.github.gpluscb.toni.command.admin.UpdateSmashdataCommand;
import com.github.gpluscb.toni.command.game.*;
import com.github.gpluscb.toni.command.help.HelpCommand;
import com.github.gpluscb.toni.command.help.PingCommand;
import com.github.gpluscb.toni.command.help.TermsCommand;
import com.github.gpluscb.toni.command.lookup.MovesCommand;
import com.github.gpluscb.toni.command.lookup.SmashdataCommand;
import com.github.gpluscb.toni.command.lookup.TournamentCommand;
import com.github.gpluscb.toni.command.matchmaking.AvailableCommand;
import com.github.gpluscb.toni.command.matchmaking.UnrankedConfigCommand;
import com.github.gpluscb.toni.command.matchmaking.UnrankedLfgCommand;
import com.github.gpluscb.toni.matchmaking.UnrankedManager;
import com.github.gpluscb.toni.smashdata.SmashdataManager;
import com.github.gpluscb.toni.smashset.CharacterTree;
import com.github.gpluscb.toni.smashset.Ruleset;
import com.github.gpluscb.toni.smashset.Rulesets;
import com.github.gpluscb.toni.startgg.GGManager;
import com.github.gpluscb.toni.statsposting.BotListClient;
import com.github.gpluscb.toni.statsposting.PostGuildRoutine;
import com.github.gpluscb.toni.statsposting.dbots.DBotsClient;
import com.github.gpluscb.toni.statsposting.dbots.DBotsClientMock;
import com.github.gpluscb.toni.statsposting.dbots.StatsResponse;
import com.github.gpluscb.toni.statsposting.topgg.TopggClient;
import com.github.gpluscb.toni.statsposting.topgg.TopggClientMock;
import com.github.gpluscb.toni.ultimateframedata.UltimateframedataClient;
import com.github.gpluscb.toni.util.RecordTypeAdapterFactory;
import com.github.gpluscb.toni.util.discord.DiscordAppenderImpl;
import com.github.gpluscb.toni.util.discord.ShardsLoadListener;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder;
import net.dv8tion.jda.api.sharding.ShardManager;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import net.dv8tion.jda.api.utils.messages.MessageRequest;
import okhttp3.OkHttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.security.auth.login.LoginException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
    @Nonnull
    private final SmashdataManager smashdata;
    @Nonnull
    private final UnrankedManager unrankedManager;
    @Nonnull
    private final ScheduledExecutorService waiterPool;
    @Nonnull
    private final Gson gson;

    public static void main(String[] args) {
        if (args.length < 1) {
            log.error("Give config file as first argument, and optionally \"--hook-slash-commands\" as second argument");
            return;
        }

        boolean hookCommands = false;
        if (args.length == 2) {
            if (!args[1].equals("--hook-slash-commands")) {
                log.error("Second arg can only be \"--hook-slash-commands\"");
                return;
            }

            hookCommands = true;
        }

        if (args.length > 2) {
            log.error("Give config file as first argument, and optionally \"--hook-slash-commands\" as second argument");
            return;
        }

        try {
            log.info("Booting...");
            new Bot(args[0], hookCommands);
        } catch (Exception e) {
            log.error("Exception caught while constructing bot: ", e);
        }
    }

    public Bot(@Nonnull String configLocation, boolean hookCommands) throws LoginException, SQLException, IOException {
        log.trace("Loading Gson");
        gson = new GsonBuilder()
                .registerTypeAdapterFactory(new RecordTypeAdapterFactory())
                .create();

        log.trace("Loading config");
        Config cfg = loadConfig(configLocation);

        log.trace("Loading stopwords");
        List<String> stopwords;
        try {
            Path path = FileSystems.getDefault().getPath(cfg.stopwordListLocation());
            stopwords = Files.readAllLines(path);
        } catch (Exception e) {
            log.error("Exception while loading stopwords - shutting down", e);
            throw e;
        }

        log.trace("Creating OkHttp client");
        OkHttpClient okHttp = new OkHttpClient.Builder().build();

        log.trace("Building GGManager");
        ggManager = new GGManager(GGClient.builder(cfg.ggToken()).client(okHttp).build(), stopwords);

        // Avoid unintentional pings.
        MessageRequest.setDefaultMentions(Collections.emptyList());
        MessageRequest.setDefaultMentionRepliedUser(false);
        // Avoid too long request queue
        RestAction.setDefaultTimeout(30, TimeUnit.SECONDS);

        log.trace("Building UltimateframedataClient");
        UltimateframedataClient ufdClient = new UltimateframedataClient(okHttp, gson);

        log.trace("Building EventWaiter");
        waiterPool = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "EventWaiterPool [0 / 1] Waiter-Thread"));
        EventWaiter waiter = new EventWaiter(waiterPool, false);

        long botId = cfg.botId();

        boolean mockBotLists = cfg.mockBotLists();
        BotListClient<StatsResponse> dBotsClient;
        if (mockBotLists) {
            log.trace("Creating DBotsClientMock");
            dBotsClient = new DBotsClientMock();
        } else {
            log.trace("Creating DBotsClient");
            dBotsClient = new DBotsClient(cfg.dbotsToken(), okHttp, botId, gson);
        }

        BotListClient<Void> topggClient;
        if (mockBotLists) {
            log.trace("Creating TopggClientMock");
            topggClient = new TopggClientMock();
        } else {
            log.trace("Creating TopggClient");
            topggClient = new TopggClient(cfg.topggToken(), okHttp, botId);
        }

        log.trace("Constructing post stats routine");
        postGuildRoutine = new PostGuildRoutine(dBotsClient, topggClient);

        log.trace("Loading characters");
        CharacterTree characterTree;
        try (Reader file = new FileReader(cfg.charactersFileLocation())) {
            JsonArray json = JsonParser.parseReader(file).getAsJsonArray();
            characterTree = CharacterTree.fromJson(json);
        } catch (Exception e) {
            log.error("Exception while loading characters - shutting down", e);
            ggManager.shutdown();
            waiterPool.shutdownNow();
            throw e;
        }

        log.trace("Loading unranked manager");
        try {
            unrankedManager = new UnrankedManager(cfg.stateDbLocation());
        } catch (SQLException e) {
            log.error("Exception while loading unranked manager - shutting down", e);
            ggManager.shutdown();
            waiterPool.shutdownNow();
            throw e;
        }

        // TODO: Have this in the DB because custom rulesets??
        log.trace("Loading rulesets");
        List<Ruleset> rulesets;
        try {
            rulesets = loadRulesets(cfg.rulesetsLocation());
        } catch (Exception e) {
            log.error("Exception while loading rulesets - shutting down", e);
            ggManager.shutdown();
            unrankedManager.shutdown();
            waiterPool.shutdownNow();
            throw e;
        }

        log.trace("Loading smashdata");
        try {
            smashdata = new SmashdataManager(cfg.smashdataDbLocation(), gson);
        } catch (SQLException e) {
            log.error("Exception while loading smashdata - shutting down", e);
            ggManager.shutdown();
            unrankedManager.shutdown();
            waiterPool.shutdownNow();
            throw e;
        }

        log.trace("Loading commands");
        List<CommandCategory> commands = loadCommands(ufdClient, waiter, /*challonge, listener, */characterTree, rulesets);

        log.trace("Creating loadListener");
        long adminGuildId = cfg.adminGuildId();

        // TODO: Somehow notice if slash commands could not be hooked?
        ShardsLoadListener loadListener = new ShardsLoadListener(jda -> {
            if (hookCommands) {
                Guild adminGuild = jda.getGuildById(adminGuildId);

                if (adminGuild == null) return;

                log.trace("Admin guild loaded, hooking slash commands");
                hookSlashCommands(adminGuild, commands);
            }
        }, loadedShardManager -> {
            log.trace("Shards finished loading");

            log.trace("Starting post stats routine");
            postGuildRoutine.start(loadedShardManager);
        });

        try {
            log.trace("Building ShardManager");
            shardManager = DefaultShardManagerBuilder.createLight(cfg.discordToken())
                    .enableIntents(GatewayIntent.DIRECT_MESSAGES, GatewayIntent.GUILD_MESSAGES, GatewayIntent.DIRECT_MESSAGE_REACTIONS, GatewayIntent.GUILD_MESSAGE_REACTIONS)
                    .enableCache(CacheFlag.MEMBER_OVERRIDES)
                    .setMemberCachePolicy(MemberCachePolicy.NONE)
                    .setChunkingFilter(ChunkingFilter.NONE)
                    .addEventListeners(waiter, loadListener)
                    .setActivity(Activity.listening("Help: /help"))
                    .setUseShutdownNow(true)
                    .build();
        } catch (Exception e) {
            log.error("LoginException - shutting down", e);
            ggManager.shutdown();
            unrankedManager.shutdown();
            waiterPool.shutdownNow();
            throw e;
        }

        log.trace("Starting command listener and dispatcher");
        dispatcher = new CommandDispatcher(commands);

        CommandListener commandListener = new CommandListener(dispatcher, cfg);
        shardManager.addEventListener(commandListener);

        log.trace("Enabling discord appender");
        DiscordAppenderImpl.setShardManager(shardManager);

        log.info("Bot construction complete");
    }

    @Nonnull
    private Config loadConfig(@Nonnull String location) throws IOException {
        try (Reader configFile = new FileReader(location)) {
            Config config = gson.fromJson(configFile, Config.class);
            config.check();

            return config;
        }
    }

    @Nonnull
    private List<Ruleset> loadRulesets(@Nonnull String location) throws IOException {
        try (Reader rulesetsFile = new FileReader(location)) {
            Rulesets rulesets = gson.fromJson(rulesetsFile, Rulesets.class);
            rulesets.check();

            return rulesets.toRulesetList();
        }
    }

    @Nonnull
    private List<CommandCategory> loadCommands(@Nonnull UltimateframedataClient ufdClient, @Nonnull EventWaiter waiter, /*@Nonnull ChallongeExtension challonge, @Nonnull TournamentListener listener, */@Nonnull CharacterTree characterTree, @Nonnull List<Ruleset> rulesets) {
        List<CommandCategory> commands = new ArrayList<>();

        List<Command> adminCommands = new ArrayList<>();
        adminCommands.add(new ShutdownCommand(this));
        adminCommands.add(new EvalCommand());
        adminCommands.add(new StatusCommand());
        adminCommands.add(new UpdateSmashdataCommand(smashdata));
        commands.add(new CommandCategory(null, null, adminCommands));

        List<Command> infoCommands = new ArrayList<>();
        infoCommands.add(new HelpCommand(commands));
        infoCommands.add(new TermsCommand());
        infoCommands.add(new PingCommand());
        commands.add(new CommandCategory("info", "Bot information commands", infoCommands));

        List<Command> gameCommands = new ArrayList<>();
        gameCommands.add(new RandomCharacterCommand(characterTree));
        gameCommands.add(new RandomPlayerCommand());
        gameCommands.add(new RPSCommand(waiter));
        gameCommands.add(new BlindPickCommand(waiter, characterTree));
        gameCommands.add(new StrikeStagesCommand(waiter, rulesets));
        gameCommands.add(new CounterpickStagesCommand(waiter, rulesets));
        gameCommands.add(new SmashSetCommand(waiter, rulesets, characterTree));
        gameCommands.add(new RulesetsCommand(waiter, rulesets));
        commands.add(new CommandCategory("game", "Smash Bros. utility commands", gameCommands));

        List<Command> lookupCommands = new ArrayList<>();
        lookupCommands.add(new TournamentCommand(ggManager, waiter));
        lookupCommands.add(new MovesCommand(ufdClient, waiter, characterTree));
        lookupCommands.add(new SmashdataCommand(waiter, smashdata));
        commands.add(new CommandCategory("lookup", "Lookup commands for other websites", lookupCommands));

        List<Command> matchmakingCommands = new ArrayList<>();
        matchmakingCommands.add(new UnrankedConfigCommand(unrankedManager));
        matchmakingCommands.add(new AvailableCommand(unrankedManager));
        matchmakingCommands.add(new UnrankedLfgCommand(unrankedManager, waiter));
        commands.add(new CommandCategory("matchmaking", "Commands for matchmaking", matchmakingCommands));

        return commands;
    }

    private void hookSlashCommands(@Nonnull Guild adminGuild, @Nonnull List<CommandCategory> commands) {
        Map<Boolean, List<Command>> map = commands.stream().flatMap(cat -> cat.commands().stream()).collect(Collectors.groupingBy(cmd -> cmd.getInfo().adminOnly()));
        List<CommandData> globalCommands = map.get(false).stream().map(cmd -> cmd.getInfo().commandData()).toList();
        List<CommandData> adminOnlyCommands = map.get(true).stream().map(cmd -> cmd.getInfo().commandData()).toList();

        shardManager.getShardCache().forEachUnordered(jda -> jda.updateCommands().addCommands(globalCommands).queue());

        adminGuild.updateCommands().addCommands(adminOnlyCommands).queue();
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

        try {
            unrankedManager.shutdown();
        } catch (SQLException e) {
            log.catching(e);
        }

        waiterPool.shutdownNow();
    }
}
