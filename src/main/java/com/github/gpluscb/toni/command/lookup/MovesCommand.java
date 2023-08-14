package com.github.gpluscb.toni.command.lookup;

import com.github.gpluscb.toni.command.Command;
import com.github.gpluscb.toni.command.CommandContext;
import com.github.gpluscb.toni.command.CommandInfo;
import com.github.gpluscb.toni.smashset.Character;
import com.github.gpluscb.toni.smashset.CharacterTree;
import com.github.gpluscb.toni.ultimateframedata.CharacterData;
import com.github.gpluscb.toni.ultimateframedata.UltimateframedataClient;
import com.github.gpluscb.toni.util.FailLogger;
import com.github.gpluscb.toni.util.MiscUtil;
import com.github.gpluscb.toni.util.OneOfTwo;
import com.github.gpluscb.toni.util.PairNonnull;
import com.github.gpluscb.toni.util.discord.EmbedUtil;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static net.dv8tion.jda.api.interactions.commands.build.OptionData.MAX_CHOICES;
import static net.dv8tion.jda.api.interactions.components.selections.SelectOption.LABEL_MAX_LENGTH;

public class MovesCommand implements Command {
    private static final Logger log = LogManager.getLogger(MovesCommand.class);

    @Nonnull
    private final UltimateframedataClient client;
    @Nonnull
    private final EventWaiter waiter;
    @Nonnull
    private final List<Character> characters;

    public MovesCommand(@Nonnull UltimateframedataClient client, @Nonnull EventWaiter waiter, @Nonnull CharacterTree characters) {
        this.client = client;
        this.waiter = waiter;
        this.characters = characters.getAllCharacters();
    }

    @Override
    public void execute(@Nonnull CommandContext ctx) {
        String characterName = ctx.getOptionNonNull("character").getAsString().toLowerCase();

        Optional<Short> idOptional = characters.stream().filter(character -> character.altNames().contains(characterName)).map(c -> {
                    Short id__ = c.id();
                    return Optional.ofNullable(id__);
                })
                .findAny().orElse(null);

        // I think this is ok here, I don't really want Optional<Optional<Short>> or other weird constructs
        //noinspection OptionalAssignedToNull
        if (idOptional == null) {
            ctx.reply(String.format("""
                            I don't know the character "%s", sorry. Note that I only know the English names.
                            Also make sure you only put the **character name** in the `character` option, and put the **move name** in the `move` option.""",
                    characterName)).queue();
            return;
        }

        if (idOptional.isEmpty()) {
            ctx.reply("This character by itself doesn't have a page on ultimateframedata, but sub-characters probably do!" +
                    " So try for example `Charizard` instead of `Pokémon Trainer`.").queue();
            return;
        }

        short id = idOptional.get();

        OptionMapping moveNameMapping = ctx.getOption("move");
        String moveName = moveNameMapping == null ? null : moveNameMapping.getAsString();

        ctx.getEvent().deferReply().queue();

        client.getCharacter(id).whenComplete(FailLogger.logFail((response, t) -> {
            try {
                if (t != null) {
                    // TODO: Potentially quite spammy during my outages
                    log.catching(t);
                    ctx.reply("The request to my ultimateframedata api service failed. *Microservices*, amirite? Tell my dev if this happens a lot - I've already annoyed them about it, but it can't hurt to give them some more context.").queue();
                    return;
                }

                boolean startMoveRequested = moveName != null;
                PairNonnull<Integer, Integer> startMove = startMoveRequested ? findMove(response, moveName) : null;

                sendReply(ctx, response, startMove, startMoveRequested);
            } catch (Exception e) {
                log.catching(e);
                ctx.reply("Ouch, an error. This one's really bad, sorry. I'll send a report to my dev. If it keeps happening you might want to provide them with some context too.").queue();
            }
        }));
    }

    /**
     * @return First is MoveSection idx, second is move idx. If first is -1, it's misc
     */
    @Nullable
    private PairNonnull<Integer, Integer> findMove(@Nonnull CharacterData data, @Nonnull String name) {
        String normalisedName = normaliseMoveName(name);

        // Special case for just misc
        if ((normalisedName == null ? name : normalisedName).equals("misc"))
            return new PairNonnull<>(-1, -1);

        PairNonnull<Integer, Integer> foundMove = null;

        List<CharacterData.MoveSection> sections = data.moveSections();
        for (int i = 0; i < sections.size(); i++) {
            List<CharacterData.MoveData> moves = sections.get(i).moves();
            for (int j = 0; j < moves.size(); j++) {
                String moveName = moves.get(j).moveName();

                if (moveName != null) {
                    String moveNameLowercase = moveName.toLowerCase();

                    // Direct match -> return early
                    if (moveNameLowercase.contains(name)) return new PairNonnull<>(i, j);

                    // Indirect match -> don't return just yet, we might find a direct match later
                    // Only check for the first move we find indirectly, otherwise "neutral air" will find "neutral air dodge"
                    if (normalisedName != null && foundMove == null && moveNameLowercase.contains(normalisedName))
                        foundMove = new PairNonnull<>(i, j);
                }
            }
        }

        return foundMove;
    }

    @Nullable
    private String normaliseMoveName(@Nonnull String moveName) {
        // General replacements
        // Guarantee space after usually first word for backair|neutralair|sideb etc.
        String name = moveName
                // for airdodge, with the trim this shouldn't mess anything up
                .replaceAll("air", "air ")
                .replaceAll("aerial", "air")
                // at the top because of (forward[s)pecial] conflict
                .replaceAll("special", "b")
                .replaceAll("neutral", "neutral ")
                .replaceAll("normal", "neutral ")
                .replaceAll("forwards", "forward ")
                .replaceAll("side", "side ")
                .replaceAll("back(wards?)?", "back ")
                .replaceAll("down(wards?)?", "down ")
                .replaceAll("up(wards?)?", "up ")
                .replaceAll("miscellaneous", "misc")
                .replaceAll("get ?up", "getup ")
                .replaceAll("ledge", "ledge ")
                // remove double spaces
                .replaceAll(" +", " ")
                // and remove leading and trailing spaces
                .trim();

        int length = name.length();

        // bair|nair|fair|dair|zair|b air|etc.
        // we might have "z air" as moveName and return "z air" here but I don't care too much
        if ((length == 4 || length == 5) && name.endsWith("air")) {
            char firstChar = name.charAt(0);
            String expandedCharacter = firstChar == 'z' ? "z" : expandMoveCharacterNBFD(name.charAt(0));
            if (expandedCharacter != null) return String.format("%s air", expandedCharacter);
        }

        // ftilt|f tilt|etc.
        if ((length == 5 || length == 6) && name.endsWith("tilt")) {
            String expandedCharacter = expandMoveCharacterNBFD(name.charAt(0));
            if (expandedCharacter != null) return String.format("%s tilt", expandedCharacter);
        }

        // fsmash|f smash|etc.
        if ((length == 6 || length == 7) && name.endsWith("smash")) {
            String expandedCharacter = expandMoveCharacterNBFD(name.charAt(0));
            if (expandedCharacter != null) return String.format("%s smash", expandedCharacter);
        }

        // Other special cases
        if (name.matches("forward ?b")) return "side b";

        // If we didn't do anything we might as well skip the check later
        return name.equals(moveName) ? null : name;
    }

    @Nullable
    private String expandMoveCharacterNBFD(char character) {
        return switch (character) {
            case 'n' -> "neutral";
            case 'f' -> "forward";
            case 'b' -> "back";
            case 'd' -> "down";
            default -> null;
        };
    }

    private void sendReply(@Nonnull CommandContext ctx, @Nullable CharacterData data, @Nullable PairNonnull<Integer, Integer> startMove, boolean startMoveRequested) {
        if (data == null) {
            log.error("Valid character requested, but not found by ufd service.");
            ctx.reply("Oh this is a bug. I was sure my buddy program would know about that character but it didn't. I'll tell my dev about it, but you can give them some context too.").queue();
            return;
        }

        User author = ctx.getUser();
        Member member = ctx.getMember();

        new CustomSelectionActionMenu(
                EmbedUtil.getPreparedUFD(member, author).build(),
                author.getIdLong(),
                data,
                ctx.getEvent(),
                startMove,
                startMoveRequested
        );
    }

    /**
     * @param hitboxPageAndMove -1 for hitbox page means don't show
     */
    @Nonnull
    private EmbedBuilder applyMove(@Nonnull EmbedBuilder embed, @Nonnull CharacterData data, @Nonnull OneOfTwo<CharacterData.MoveSection, CharacterData.MiscData> section, @Nullable PairNonnull<Integer, CharacterData.MoveData> hitboxPageAndMove) {
        String url = String.format("%s#%s", data.ufdUrl(), section.map(CharacterData.MoveSection::htmlId, CharacterData.MiscData::htmlId));
        String nameCapitalized = MiscUtil.capitalizeFirst(data.name());
        String sectionName = section.map(CharacterData.MoveSection::sectionName, u -> "Misc");

        if (hitboxPageAndMove == null) {
            embed.setTitle(String.format("%s - %s", nameCapitalized, sectionName), url);
        } else {
            CharacterData.MoveData move = hitboxPageAndMove.getU();
            String moveName = move.moveName();
            if (moveName == null) moveName = "Some Unnamed Move";
            embed.setTitle(String.format("%s - %s - %s", nameCapitalized, sectionName, moveName), url);
        }

        List<EmbedUtil.InlineField> fields = new ArrayList<>();

        if (hitboxPageAndMove == null) {
            // This only happens in misc section
            CharacterData.StatsData stats = data.miscData().stats();

            if (stats != null) {
                String weight = stats.weight();
                if (weight != null) fields.add(new EmbedUtil.InlineField("Weight", weight));

                String gravity = stats.gravity();
                if (gravity != null) fields.add(new EmbedUtil.InlineField("Gravity", gravity));

                String runSpeed = stats.runSpeed();
                if (runSpeed != null) fields.add(new EmbedUtil.InlineField("Run Speed", runSpeed));

                String walkSpeed = stats.walkSpeed();
                if (walkSpeed != null) fields.add(new EmbedUtil.InlineField("Walk Speed", walkSpeed));

                String initialDash = stats.initialDash();
                if (initialDash != null) fields.add(new EmbedUtil.InlineField("Initial Dash", initialDash));

                String airSpeed = stats.airSpeed();
                if (airSpeed != null) fields.add(new EmbedUtil.InlineField("Air Speed", airSpeed));

                String totalAirAcceleration = stats.totalAirAcceleration();
                if (totalAirAcceleration != null)
                    fields.add(new EmbedUtil.InlineField("Total Air Acceleration", totalAirAcceleration));

                String shFhShffFhffFrames = stats.shFhShffFhffFrames();
                if (shFhShffFhffFrames != null)
                    fields.add(new EmbedUtil.InlineField("SH / FH / SHFF / FHFF Frames", shFhShffFhffFrames));

                String fallSpeedFastFallSpeed = stats.fallSpeedFastFallSpeed();
                if (fallSpeedFastFallSpeed != null)
                    fields.add(new EmbedUtil.InlineField("Fall Speed / Fast Fall Speed", fallSpeedFastFallSpeed));

                List<String> oosOptions = stats.oosOptions();
                if (!oosOptions.isEmpty())
                    fields.add(new EmbedUtil.InlineField("Out Of Shield options", oosOptions.get(0)));
                for (int i = 1; i < oosOptions.size(); i++)
                    fields.add(new EmbedUtil.InlineField("", oosOptions.get(i)));

                String shieldGrab = stats.shieldGrab();
                if (shieldGrab != null) fields.add(new EmbedUtil.InlineField("Shield Grab (after shieldstun)", shieldGrab));

                String shieldDrop = stats.shieldDrop();
                if (shieldDrop != null) fields.add(new EmbedUtil.InlineField("Shield Drop", shieldDrop));

                String jumpSquat = stats.jumpSquat();
                if (jumpSquat != null) fields.add(new EmbedUtil.InlineField("Jump Squat", jumpSquat));
            }
        } else {
            int hitboxPage = hitboxPageAndMove.getT();
            CharacterData.MoveData move = hitboxPageAndMove.getU();

            String startup = move.startup();
            if (startup != null) fields.add(new EmbedUtil.InlineField("Startup", startup));

            String totalFrames = move.totalFrames();
            if (totalFrames != null) fields.add(new EmbedUtil.InlineField("Total Frames", totalFrames));

            String landingLag = move.landingLag();
            if (landingLag != null) fields.add(new EmbedUtil.InlineField("Landing Lag", landingLag));

            String notes = move.notes();
            if (notes != null) fields.add(new EmbedUtil.InlineField("Notes", notes));

            String baseDamage = move.baseDamage();
            if (baseDamage != null) fields.add(new EmbedUtil.InlineField("Base Damage", baseDamage));

            String shieldLag = move.shieldLag();
            if (shieldLag != null) fields.add(new EmbedUtil.InlineField("Shield Lag", shieldLag));

            String shieldStun = move.shieldStun();
            if (shieldStun != null) fields.add(new EmbedUtil.InlineField("Shield Stun", shieldStun));

            String advantage = move.advantage();
            if (advantage != null) fields.add(new EmbedUtil.InlineField("Frame Advantage", advantage));

            String activeFrames = move.activeFrames();
            if (activeFrames != null) fields.add(new EmbedUtil.InlineField("Active Frames", activeFrames));

            String endlag = move.endlag();
            if (endlag != null) fields.add(new EmbedUtil.InlineField("Endlag", endlag));

            String hopsAutocancel = move.hopsAutocancel();
            if (hopsAutocancel != null) fields.add(new EmbedUtil.InlineField("Autocancels on", hopsAutocancel));

            String hopsActionable = move.hopsActionable();
            if (hopsActionable != null)
                fields.add(new EmbedUtil.InlineField("Actionable before landing", hopsActionable));

            String whichHitbox = move.whichHitbox();
            if (whichHitbox != null) fields.add(new EmbedUtil.InlineField("Info about which hitbox?", whichHitbox));

            List<CharacterData.HitboxData> hitboxes = move.hitboxes();
            int hitboxesSize = hitboxes.size();

            String hitboxNote;

            if (hitboxesSize == 0) {
                hitboxNote = "No hitbox images available";
            } else {
                String noteWithoutName = String.format("%s/%d", hitboxPage < 0 ? "-" : hitboxPage + 1, hitboxesSize);

                if (hitboxPage < 0) hitboxNote = noteWithoutName;
                else {
                    CharacterData.HitboxData hitbox = hitboxes.get(hitboxPage);

                    String name = hitbox.name();
                    hitboxNote = name == null ? noteWithoutName : String.format("%s | %s", name, noteWithoutName);
                    embed.setImage(hitbox.url());
                }
            }

            fields.add(new EmbedUtil.InlineField("Shown hitbox", hitboxNote));
        }

        embed.appendDescription(EmbedUtil.parseInlineFields(fields));

        return embed;
    }

    @Nonnull
    @Override
    public List<net.dv8tion.jda.api.interactions.commands.Command.Choice> onAutocomplete(@Nonnull CommandAutoCompleteInteractionEvent event) {
        String input = event.getFocusedOption().getValue().toLowerCase();

        // Limiting to one name per character for more diversity
        // More "proper" names will tend to be earlier in the altNames, so we choose the most "proper" matching name
        return characters.stream()
                .flatMap(character -> character.altNames().stream()
                        .filter(name -> name.startsWith(input))
                        .limit(1))
                .map(name -> new net.dv8tion.jda.api.interactions.commands.Command.Choice(name, name))
                .limit(MAX_CHOICES)
                .toList();
    }

    @Nonnull
    @Override
    public CommandInfo getInfo() {
        return new CommandInfo.Builder()
                .setRequiredBotPerms(new Permission[]{Permission.MESSAGE_EMBED_LINKS, Permission.MESSAGE_HISTORY})
                .setShortHelp("Displays the moves of a character using data from [ultimateframedata.com](https://ultimateframedata.com).")
                .setDetailedHelp("""
                        Looks up the moves of a character on [ultimateframedata.com](https://ultimateframedata.com).
                        Use the drop-down menus to select the move section, move, and hitbox image.
                        Slash command options:
                        • `character`: The character name (or nickname). Has to be English.
                        • (Optional) `move`: The move name. By default I'll show you jab 1, and you can select the move/hitbox you want to see via the select menus.""")
                .setCommandData(Commands.slash("moves", "Displays moves of a smash ultimate character")
                        .addOption(OptionType.STRING, "character", "The character name", true, true)
                        .addOption(OptionType.STRING, "move", "The move name (e.g. `fair`, `down b`)", false))
                .build();
    }

    private class CustomSelectionActionMenu {
        private static final String sectionMenuId = "section";
        private static final String moveMenuId = "move";
        private static final String hitboxMenuId = "hitbox";

        @Nonnull
        private final MessageEmbed template;

        private final long user;
        @Nullable
        private Long messageId;

        @Nonnull
        private final CharacterData data;

        private int sectionPage;
        private int movePage;
        private int hitboxPage;

        private boolean displayCouldNotFindMove;

        public CustomSelectionActionMenu(@Nonnull MessageEmbed template, long user, @Nonnull CharacterData data, @Nonnull SlashCommandInteractionEvent event, @Nullable PairNonnull<Integer, Integer> startMove, boolean startMoveRequested) {
            this.user = user;
            this.template = template;
            messageId = null;
            this.data = data;

            sectionPage = 0;
            movePage = 0;
            hitboxPage = 0;

            displayCouldNotFindMove = startMoveRequested && startMove == null;

            if (startMove != null) {
                sectionPage = startMove.getT();
                movePage = startMove.getU();
            }

            MessageCreateData start = getCurrent();
            List<ActionRow> actionRows = prepareActionRows();

            event.getHook().sendMessage(start).addComponents(actionRows).queue(this::awaitEvents);
        }

        private List<ActionRow> prepareActionRows() {
            List<ActionRow> actionRows = new ArrayList<>(3);

            actionRows.add(ActionRow.of(StringSelectMenu.create(sectionMenuId).addOptions(currentSectionOptions()).build()));
            actionRows.add(ActionRow.of(StringSelectMenu.create(moveMenuId).addOptions(currentMoveOptions()).build()));

            List<SelectOption> hitboxOptions = currentHitboxOptions();
            if (!hitboxOptions.isEmpty())
                actionRows.add(ActionRow.of(StringSelectMenu.create(hitboxMenuId).addOptions(currentHitboxOptions()).build()));

            return actionRows;
        }

        private synchronized void awaitEvents(@Nonnull Message message) {
            messageId = message.getIdLong();
            waiter.waitForEvent(StringSelectInteractionEvent.class,
                    e -> {
                        if (checkSelection(e)) handleSelection(e);
                        // Return false to endlessly keep awaiting until timeout
                        return false;
                    },
                    MiscUtil.emptyConsumer(),
                    20, TimeUnit.MINUTES,
                    FailLogger.logFail(() -> timeout(message.getJDA(), message.getChannel().getIdLong())) // This might swallow exceptions otherwise
            );
        }

        private boolean checkSelection(@Nonnull StringSelectInteractionEvent e) {
            String id = e.getComponentId();

            if (messageId == null || e.getMessageIdLong() != messageId || !(id.equals(sectionMenuId)
                    || id.equals(moveMenuId)
                    || id.equals(hitboxMenuId))) {
                return false;
            }

            if (e.getUser().getIdLong() == user) {
                e.reply("You cannot use this interaction.").queue();
                return false;
            }

            return true;
        }

        private void handleSelection(@Nonnull StringSelectInteractionEvent e) {
            e.deferEdit().queue();
            String id = e.getComponentId();
            String value = e.getValues().get(0); // We require exactly one selection

            try {
                int valueInt = Integer.parseInt(value);

                switch (id) {
                    case sectionMenuId -> {
                        sectionPage = valueInt;
                        movePage = sectionPage == -1 ? -1 : 0;
                        hitboxPage = 0;
                    }
                    case moveMenuId -> {
                        movePage = valueInt;
                        hitboxPage = 0;
                    }
                    case hitboxMenuId -> hitboxPage = valueInt;
                    default -> {
                        log.error("Unknown selection menu id: {}", id);
                        // We know because of the check that messageId is not null here
                        //noinspection ConstantConditions
                        e.getChannel().editMessageById(messageId, "This is a bug! Sorry, I just got very unexpected Data. I've told my dev about it, but you can give them some context too.")
                                .setReplace(true)
                                .queue();
                        return;
                    }
                }

                MessageCreateData current = getCurrent();
                // We know because of the check that messageId is not null here
                //noinspection ConstantConditions
                e.getChannel().editMessageById(messageId, MessageEditData.fromCreateData(current))
                        .setComponents(prepareActionRows())
                        .queue();
            } catch (NumberFormatException ex) {
                log.error("Non-Integer component value: {}", value);
                // We know because of the check that messageId is not null here
                //noinspection ConstantConditions
                e.getChannel().editMessageById(messageId, "This is a bug! Sorry, I just got very unexpected Data. I've told my dev about it, but you can give them some context too.")
                        .setReplace(true)
                        .queue();
            }
        }

        private void timeout(@Nonnull JDA jda, long messageChannel) {
            MessageChannel channel = jda.getChannelById(MessageChannel.class, messageChannel);
            if (channel == null) {
                log.warn("MessageChannel for onTimeout not in cache for onTimeout");
                return;
            }

            // We know it is set before waiter waits
            //noinspection ConstantConditions
            channel.retrieveMessageById(messageId)
                    .flatMap(m -> m.editMessage(MessageEditData.fromMessage(m)).setComponents())
                    .queue(null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_MESSAGE));
        }

        @Nonnull
        private synchronized List<SelectOption> currentSectionOptions() {
            List<CharacterData.MoveSection> sections = data.moveSections();
            List<SelectOption> ret = new ArrayList<>(sections.size() + 1);

            for (int i = 0; i < sections.size(); i++) {
                CharacterData.MoveSection section = sections.get(i);
                ret.add(SelectOption.of(StringUtils.abbreviate(section.sectionName().replaceAll("\\\\", ""), LABEL_MAX_LENGTH), String.valueOf(i)).withDefault(i == sectionPage));
            }

            ret.add(SelectOption.of("Misc", "-1").withDefault(-1 == sectionPage));
            return ret;
        }

        @Nonnull
        private synchronized List<SelectOption> currentMoveOptions() {
            List<CharacterData.MoveData> moves = getCurrentMoves();
            List<SelectOption> ret = new ArrayList<>(moves.size());

            for (int i = 0; i < moves.size(); i++) {
                String moveName = moves.get(i).moveName();
                moveName = moveName == null ?
                        "Unknown Move"
                        : StringUtils.abbreviate(moveName, LABEL_MAX_LENGTH).replaceAll("\\\\", "");
                ret.add(
                        SelectOption.of(moveName, String.valueOf(i))
                                .withDefault(i == movePage)
                );
            }

            if (sectionPage == -1) ret.add(SelectOption.of("Misc Data", "-1").withDefault(-1 == movePage));

            return ret;
        }

        @Nonnull
        private synchronized List<SelectOption> currentHitboxOptions() {
            if (movePage == -1) return Collections.emptyList(); // Misc Page

            List<CharacterData.HitboxData> hitboxes = getCurrentMoves().get(movePage).hitboxes();
            if (hitboxes.isEmpty()) return Collections.emptyList();

            int hitboxSize = hitboxes.size();
            List<SelectOption> ret = new ArrayList<>(hitboxSize + 1);
            for (int i = 0; i < hitboxes.size(); i++) {
                String hitboxName = hitboxes.get(i).name();
                ret.add(
                        SelectOption.of(hitboxName == null ? String.format("Hitbox %d/%d", i + 1, hitboxSize) : StringUtils.abbreviate(hitboxName, LABEL_MAX_LENGTH), String.valueOf(i))
                                .withDefault(i == hitboxPage)
                );
            }

            ret.add(SelectOption.of("Hide", "-1").withDefault(-1 == hitboxPage));

            return ret;
        }

        @Nonnull
        private synchronized OneOfTwo<CharacterData.MoveSection, CharacterData.MiscData> getCurrentSection() {
            return sectionPage == -1 ?
                    OneOfTwo.ofU(data.miscData())
                    : OneOfTwo.ofT(data.moveSections().get(sectionPage));
        }

        @Nonnull
        private synchronized List<CharacterData.MoveData> getCurrentMoves() {
            return getCurrentSection().map(CharacterData.MoveSection::moves, CharacterData.MiscData::moves);
        }

        @Nonnull
        public synchronized MessageCreateData getCurrent() {
            try {
                EmbedBuilder embed = new EmbedBuilder(template);

                if (displayCouldNotFindMove) {
                    embed.appendDescription("*I could not find the move you searched for. Use the drop-down menus to navigate through them manually.*\n");
                    displayCouldNotFindMove = false;
                }

                List<CharacterData.MoveData> moves = getCurrentMoves();
                boolean isMiscPage = movePage == -1;
                PairNonnull<Integer, CharacterData.MoveData> hitboxPageAndMove = isMiscPage ? null : new PairNonnull<>(hitboxPage, moves.get(movePage));

                applyMove(embed, data, getCurrentSection(), hitboxPageAndMove);

                return new MessageCreateBuilder().setEmbeds(embed.build()).build();
            } catch (Exception e) {
                log.catching(e);
                return new MessageCreateBuilder()
                        .setContent("There was a severe unexpected problem with displaying the move data, I don't really know how that happened. I'll tell  my dev, you can go shoot them a message about this too if you want to.")
                        .build();
            }
        }
    }
}
