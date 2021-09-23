package com.github.gpluscb.toni.command.lookup;

import com.github.gpluscb.toni.command.Command;
import com.github.gpluscb.toni.command.MessageCommandContext;
import com.github.gpluscb.toni.ultimateframedata.CharacterData;
import com.github.gpluscb.toni.ultimateframedata.UltimateframedataClient;
import com.github.gpluscb.toni.util.*;
import com.github.gpluscb.toni.util.discord.EmbedUtil;
import com.github.gpluscb.toni.util.smash.CharacterTree;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.interaction.SelectionMenuEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.SelectionMenu;
import net.dv8tion.jda.api.requests.restaction.MessageAction;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static net.dv8tion.jda.api.interactions.components.selections.SelectOption.LABEL_MAX_LENGTH;

public class CharacterCommand implements Command {
    private static final Logger log = LogManager.getLogger(CharacterCommand.class);

    @Nonnull
    private final UltimateframedataClient client;
    @Nonnull
    private final EventWaiter waiter;
    @Nonnull
    private final List<CharacterTree.Character> characters;

    public CharacterCommand(@Nonnull UltimateframedataClient client, @Nonnull EventWaiter waiter, @Nonnull CharacterTree characters) {
        this.client = client;
        this.waiter = waiter;
        this.characters = characters.getAllCharacters();
    }

    @Override
    public void execute(@Nonnull MessageCommandContext ctx) {
        int argNum = ctx.getArgNum();
        // T: id, U: response in case Id is not found
        OneOfTwo<Pair<Short, String>, String> idAndMoveNameOrResponse = argNum == 0 ?
                OneOfTwo.ofT(new Pair<>((short) 20, null))
                : findCharacterIdAndMoveNameOrResponse(ctx);

        if (idAndMoveNameOrResponse.isU()) {
            // We know because of the isU
            ctx.reply(idAndMoveNameOrResponse.getUOrThrow()).queue();
            return;
        }

        // We know because of the isU
        Pair<Short, String> idAndMoveName = idAndMoveNameOrResponse.getTOrThrow();
        short id = idAndMoveName.getT();
        @Nullable
        String moveName = idAndMoveName.getU();

        ctx.getChannel().sendTyping().queue();

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
     * @return T: Pair of id and move name. Note that move name may be null. U: Reason why we couldn't find anything.
     */
    @Nonnull
    private OneOfTwo<Pair<Short, String>, String> findCharacterIdAndMoveNameOrResponse(@Nonnull MessageCommandContext ctx) {
        int characterArgNum = 0;
        OneOfTwo<Short, String> id = OneOfTwo.ofU("I don't know that character, sorry. " +
                "Note that I only know the English names.");
        int argNum = ctx.getArgNum();
        for (int i = 1; i <= argNum; i++) {
            String characterName = ctx.getArgsRange(0, i).toLowerCase();

            OneOfTwo<Short, Optional<String>> id_ = characters.stream().filter(c -> c.getAltNames().contains(characterName))
                    .<OneOfTwo<Short, Optional<String>>>map(c -> {
                        Short id__ = c.getId();
                        return id__ == null ?
                                OneOfTwo.ofU(Optional.of("This character by itself doesn't have a page on ultimateframedata, but sub-characters probably do!" +
                                        " So try for example `Charizard` instead of `Pokémon Trainer`."))
                                : OneOfTwo.ofT(id__);
                    })
                    .findAny().orElse(OneOfTwo.ofU(Optional.empty()));

            if (id_.isU()) {
                Optional<String> response = id_.getUOrThrow();
                if (response.isPresent()) return OneOfTwo.ofU(response.get());
                // Else we haven't found anything
                continue;
            }

            // We know we found an id here, so let's store it for now in case we won't find anything better later
            id = OneOfTwo.ofT(id_.getTOrThrow());
            characterArgNum = i;
        }

        // Rest of the args will be move name
        String moveName = characterArgNum < argNum ? ctx.getArgsFrom(characterArgNum).toLowerCase() : null;

        return id.mapT(id_ -> new Pair<>(id_, moveName));
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

        List<CharacterData.MoveSection> sections = data.getMoveSections();
        for (int i = 0; i < sections.size(); i++) {
            List<CharacterData.MoveData> moves = sections.get(i).getMoves();
            for (int j = 0; j < moves.size(); j++) {
                String moveName = moves.get(j).getMoveName();

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
        switch (character) {
            case 'n':
                return "neutral";
            case 'f':
                return "forward";
            case 'b':
                return "back";
            case 'd':
                return "down";
            default:
                return null;
        }
    }

    private void sendReply(@Nonnull MessageCommandContext ctx, @Nullable CharacterData data, @Nullable PairNonnull<Integer, Integer> startMove, boolean startMoveRequested) {
        if (data == null) {
            log.error("Valid character requested, but not found by ufd service.");
            ctx.reply("Oh this is a bug. I was sure my buddy program would know about that character but it didn't. I'll tell my dev about it, but you can give them some context too.").queue();
            return;
        }

        User author = ctx.getUser();
        Member member = ctx.getEvent().getMember();

        new CustomSelectionActionMenu(
                EmbedUtil.getPreparedUFD(member, author).build(),
                author.getIdLong(),
                data,
                ctx.getMessage(),
                startMove,
                startMoveRequested
        );
    }

    /**
     * @param hitboxPageAndMove -1 for hitbox page means don't show
     */
    @Nonnull
    private EmbedBuilder applyMove(@Nonnull EmbedBuilder embed, @Nonnull CharacterData data, @Nonnull OneOfTwo<CharacterData.MoveSection, CharacterData.MiscData> section, @Nullable PairNonnull<Integer, CharacterData.MoveData> hitboxPageAndMove) {
        String url = String.format("%s#%s", data.getUfdUrl(), section.map(CharacterData.MoveSection::getHtmlId, CharacterData.MiscData::getHtmlId));
        String nameCapitalized = MiscUtil.capitalizeFirst(data.getName());
        String sectionName = section.map(CharacterData.MoveSection::getSectionName, u -> "Misc");

        if (hitboxPageAndMove == null) {
            embed.setTitle(String.format("%s - %s", nameCapitalized, sectionName), url);
        } else {
            CharacterData.MoveData move = hitboxPageAndMove.getU();
            String moveName = move.getMoveName();
            if (moveName == null) moveName = "Some Unnamed Move";
            embed.setTitle(String.format("%s - %s - %s", nameCapitalized, sectionName, moveName), url);
        }

        List<EmbedUtil.InlineField> fields = new ArrayList<>();

        if (hitboxPageAndMove == null) {
            // This only happens in misc section
            CharacterData.StatsData stats = data.getMiscData().getStats();

            if (stats != null) {
                String weight = stats.getWeight();
                if (weight != null) fields.add(new EmbedUtil.InlineField("Weight", weight));

                String gravity = stats.getGravity();
                if (gravity != null) fields.add(new EmbedUtil.InlineField("Gravity", gravity));

                String runSpeed = stats.getRunSpeed();
                if (runSpeed != null) fields.add(new EmbedUtil.InlineField("Run Speed", runSpeed));

                String walkSpeed = stats.getWalkSpeed();
                if (walkSpeed != null) fields.add(new EmbedUtil.InlineField("Walk Speed", walkSpeed));

                String initialDash = stats.getInitialDash();
                if (initialDash != null) fields.add(new EmbedUtil.InlineField("Initial Dash", initialDash));

                String airSpeed = stats.getAirSpeed();
                if (airSpeed != null) fields.add(new EmbedUtil.InlineField("Air Speed", airSpeed));

                String totalAirAcceleration = stats.getTotalAirAcceleration();
                if (totalAirAcceleration != null)
                    fields.add(new EmbedUtil.InlineField("Total Air Acceleration", totalAirAcceleration));

                String shFhShffFhffFrames = stats.getShFhShffFhffFrames();
                if (shFhShffFhffFrames != null)
                    fields.add(new EmbedUtil.InlineField("SH / FH / SHFF / FHFF Frames", shFhShffFhffFrames));

                String fallSpeedFastFallSpeed = stats.getFallSpeedFastFallSpeed();
                if (fallSpeedFastFallSpeed != null)
                    fields.add(new EmbedUtil.InlineField("Fall Speed / Fast Fall Speed", fallSpeedFastFallSpeed));

                List<String> oosOptions = stats.getOosOptions();
                if (!oosOptions.isEmpty())
                    fields.add(new EmbedUtil.InlineField("Out Of Shield options", oosOptions.get(0)));
                for (int i = 1; i < oosOptions.size(); i++)
                    fields.add(new EmbedUtil.InlineField("", oosOptions.get(i)));

                String shieldGrab = stats.getShieldGrab();
                if (shieldGrab != null) fields.add(new EmbedUtil.InlineField("Shield Grab", shieldGrab));

                String shieldDrop = stats.getShieldDrop();
                if (shieldDrop != null) fields.add(new EmbedUtil.InlineField("Shield Drop", shieldDrop));

                String jumpSquat = stats.getJumpSquat();
                if (jumpSquat != null) fields.add(new EmbedUtil.InlineField("Jump Squat", jumpSquat));
            }
        } else {
            int hitboxPage = hitboxPageAndMove.getT();
            CharacterData.MoveData move = hitboxPageAndMove.getU();

            String startup = move.getStartup();
            if (startup != null) fields.add(new EmbedUtil.InlineField("Startup", startup));

            String totalFrames = move.getTotalFrames();
            if (totalFrames != null) fields.add(new EmbedUtil.InlineField("Total Frames", totalFrames));

            String landingLag = move.getLandingLag();
            if (landingLag != null) fields.add(new EmbedUtil.InlineField("Landing Lag", landingLag));

            String notes = move.getNotes();
            if (notes != null) fields.add(new EmbedUtil.InlineField("Notes", notes));

            String baseDamage = move.getBaseDamage();
            if (baseDamage != null) fields.add(new EmbedUtil.InlineField("Base Damage", baseDamage));

            String shieldLag = move.getShieldLag();
            if (shieldLag != null) fields.add(new EmbedUtil.InlineField("Shield Lag", shieldLag));

            String shieldStun = move.getShieldStun();
            if (shieldStun != null) fields.add(new EmbedUtil.InlineField("Shield Stun", shieldStun));

            String advantage = move.getAdvantage();
            if (advantage != null) fields.add(new EmbedUtil.InlineField("Frame Advantage", advantage));

            String activeFrames = move.getActiveFrames();
            if (activeFrames != null) fields.add(new EmbedUtil.InlineField("Active Frames", activeFrames));

            String whichHitbox = move.getWhichHitbox();
            if (whichHitbox != null) fields.add(new EmbedUtil.InlineField("Info about which hitbox?", whichHitbox));

            List<CharacterData.HitboxData> hitboxes = move.getHitboxes();
            int hitboxesSize = hitboxes.size();

            String hitboxNote;

            if (hitboxesSize == 0) {
                hitboxNote = "No hitbox images available";
            } else {
                String noteWithoutName = String.format("%s/%d", hitboxPage < 0 ? "-" : hitboxPage + 1, hitboxesSize);

                if (hitboxPage < 0) hitboxNote = noteWithoutName;
                else {
                    CharacterData.HitboxData hitbox = hitboxes.get(hitboxPage);

                    String name = hitbox.getName();
                    hitboxNote = name == null ? noteWithoutName : String.format("%s | %s", name, noteWithoutName);
                    embed.setImage(hitbox.getUrl());
                }
            }

            fields.add(new EmbedUtil.InlineField("Shown hitbox", hitboxNote));
        }

        embed.appendDescription(EmbedUtil.parseInlineFields(fields));

        return embed;
    }

    @Nonnull
    @Override
    public Permission[] getRequiredBotPerms() {
        return new Permission[]{Permission.MESSAGE_EMBED_LINKS};
    }

    @Nonnull
    @Override
    public String[] getAliases() {
        return new String[]{"character", "char", "ufd", "moves", "move", "hitboxes", "hitbox"};
    }

    @Nullable
    @Override
    public String getShortHelp() {
        return "Displays the moves of a character using data from [ultimateframedata.com](https://ultimateframedata.com). Usage: `character <CHARACTER NAME...> [MOVE NAME...]`";
    }

    @Nullable
    @Override
    public String getDetailedHelp() {
        return "`character <CHARACTER NAME...> [MOVE NAME...]`\n" +
                "Looks up the moves of a character on [ultimateframedata.com](https://ultimateframedata.com).\n" +
                "Use the drop-down menus to select the move section, move, and hitbox image.\n" +
                "Aliases: `character`, `char`, `ufd`, `move`, `moves`, `hitboxes`, `hitbox`";
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

        public CustomSelectionActionMenu(@Nonnull MessageEmbed template, long user, @Nonnull CharacterData data, @Nonnull Message reference, @Nullable PairNonnull<Integer, Integer> startMove, boolean startMoveRequested) {
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

            boolean hasPerms = true;
            MessageChannel channel = reference.getChannel();
            if (channel instanceof TextChannel) {
                TextChannel textChannel = (TextChannel) channel;
                hasPerms = textChannel.getGuild().getSelfMember().hasPermission(textChannel, Permission.MESSAGE_HISTORY);
            }

            Message start = getCurrent();

            if (hasPerms)
                init(channel.sendMessage(start).referenceById(reference.getIdLong()));
            else
                init(channel.sendMessage(start));
        }

        private synchronized void init(@Nonnull MessageAction action) {
            List<ActionRow> actionRows = new ArrayList<>(3);

            actionRows.add(ActionRow.of(SelectionMenu.create(sectionMenuId).addOptions(currentSectionOptions()).build()));
            actionRows.add(ActionRow.of(SelectionMenu.create(moveMenuId).addOptions(currentMoveOptions()).build()));

            List<SelectOption> hitboxOptions = currentHitboxOptions();
            if (!hitboxOptions.isEmpty())
                actionRows.add(ActionRow.of(SelectionMenu.create(hitboxMenuId).addOptions(currentHitboxOptions()).build()));

            action.setActionRows(actionRows).queue(this::awaitEvents);
        }

        private synchronized void awaitEvents(@Nonnull Message message) {
            messageId = message.getIdLong();
            waiter.waitForEvent(SelectionMenuEvent.class,
                    this::checkSelection,
                    this::handleSelection,
                    20, TimeUnit.MINUTES,
                    FailLogger.logFail(() -> timeout(message.getJDA(), message.getChannel().getIdLong())) // This might swallow exceptions otherwise
            );
        }

        private boolean checkSelection(@Nonnull SelectionMenuEvent e) {
            String id = e.getComponentId();
            return messageId != null // Should never be null here but still
                    && e.getMessageIdLong() == messageId
                    && e.getUser().getIdLong() == user
                    && (id.equals(sectionMenuId)
                    || id.equals(moveMenuId)
                    || id.equals(hitboxMenuId));
        }

        private void handleSelection(@Nonnull SelectionMenuEvent e) {
            e.deferEdit().queue();
            String id = e.getComponentId();
            String value = e.getValues().get(0); // We require exactly one selection

            try {
                int valueInt = Integer.parseInt(value);

                switch (id) {
                    case sectionMenuId:
                        sectionPage = valueInt;
                        movePage = sectionPage == -1 ? -1 : 0;
                        hitboxPage = 0;
                        break;

                    case moveMenuId:
                        movePage = valueInt;
                        hitboxPage = 0;
                        break;

                    case hitboxMenuId:
                        hitboxPage = valueInt;
                        break;

                    default:
                        log.error("Unknown selection menu id: {}", id);
                        // We know because of the check that messageId is not null here
                        //noinspection ConstantConditions
                        e.getChannel().editMessageById(messageId, "This is a bug! Sorry, I just got very unexpected Data. I've told my dev about it, but you can give them some context too.")
                                .override(true)
                                .queue();
                        return;
                }

                Message current = getCurrent();
                // We know because of the check that messageId is not null here
                //noinspection ConstantConditions
                init(e.getChannel().editMessageById(messageId, current).setActionRows());
            } catch (NumberFormatException ex) {
                log.error("Non-Integer component value: {}", value);
                // We know because of the check that messageId is not null here
                //noinspection ConstantConditions
                e.getChannel().editMessageById(messageId, "This is a bug! Sorry, I just got very unexpected Data. I've told my dev about it, but you can give them some context too.")
                        .override(true)
                        .queue();
            }
        }

        private void timeout(@Nonnull JDA jda, long messageChannel) {
            MessageChannel channel = jda.getTextChannelById(messageChannel);
            if (channel == null) channel = jda.getPrivateChannelById(messageChannel);
            if (channel == null) {
                log.warn("MessageChannel for timeoutAction not in cache for timeoutAction");
                return;
            }

            // We know it is set before waiter waits
            //noinspection ConstantConditions
            channel.retrieveMessageById(messageId).flatMap(m -> m.editMessage(m).setActionRows()).queue();
        }

        @Nonnull
        private synchronized List<SelectOption> currentSectionOptions() {
            List<CharacterData.MoveSection> sections = data.getMoveSections();
            List<SelectOption> ret = new ArrayList<>(sections.size() + 1);

            for (int i = 0; i < sections.size(); i++) {
                CharacterData.MoveSection section = sections.get(i);
                ret.add(SelectOption.of(StringUtils.abbreviate(section.getSectionName(), LABEL_MAX_LENGTH), String.valueOf(i)).withDefault(i == sectionPage));
            }

            ret.add(SelectOption.of("Misc", "-1").withDefault(-1 == sectionPage));
            return ret;
        }

        @Nonnull
        private synchronized List<SelectOption> currentMoveOptions() {
            List<CharacterData.MoveData> moves = getCurrentMoves();
            List<SelectOption> ret = new ArrayList<>(moves.size());

            for (int i = 0; i < moves.size(); i++) {
                String moveName = moves.get(i).getMoveName();
                ret.add(
                        SelectOption.of(moveName == null ? "Unknown Move" : StringUtils.abbreviate(moveName, LABEL_MAX_LENGTH), String.valueOf(i))
                                .withDefault(i == movePage)
                );
            }

            if (sectionPage == -1) ret.add(SelectOption.of("Misc Data", "-1").withDefault(-1 == movePage));

            return ret;
        }

        @Nonnull
        private synchronized List<SelectOption> currentHitboxOptions() {
            if (movePage == -1) return Collections.emptyList(); // Misc Page

            List<CharacterData.HitboxData> hitboxes = getCurrentMoves().get(movePage).getHitboxes();
            if (hitboxes.isEmpty()) return Collections.emptyList();

            int hitboxSize = hitboxes.size();
            List<SelectOption> ret = new ArrayList<>(hitboxSize + 1);
            for (int i = 0; i < hitboxes.size(); i++) {
                String hitboxName = hitboxes.get(i).getName();
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
                    OneOfTwo.ofU(data.getMiscData())
                    : OneOfTwo.ofT(data.getMoveSections().get(sectionPage));
        }

        @Nonnull
        private synchronized List<CharacterData.MoveData> getCurrentMoves() {
            return getCurrentSection().map(CharacterData.MoveSection::getMoves, CharacterData.MiscData::getMoves);
        }

        @Nonnull
        public synchronized Message getCurrent() {
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

                return new MessageBuilder().setEmbeds(embed.build()).build();
            } catch (Exception e) {
                log.catching(e);
                return new MessageBuilder("There was a severe unexpected problem with displaying the move data, I don't really know how that happened. I'll tell  my dev, you can go shoot them a message about this too if you want to.").build();
            }
        }
    }
}
