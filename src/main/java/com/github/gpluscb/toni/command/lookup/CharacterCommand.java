package com.github.gpluscb.toni.command.lookup;

import com.github.gpluscb.toni.command.Command;
import com.github.gpluscb.toni.command.CommandContext;
import com.github.gpluscb.toni.ultimateframedata.CharacterData;
import com.github.gpluscb.toni.ultimateframedata.UltimateframedataClient;
import com.github.gpluscb.toni.util.*;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

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
    public void execute(@Nonnull CommandContext ctx) {
        int argNum = ctx.getArgNum();
        if (argNum == 0) {
            ctx.reply("Too few arguments. I can't get you character data if I don't know what you're searching for. Use `help character` for help.").queue();
            return;
        }

        // T: id, U: response in case Id is not found
        OneOfTwo<Pair<Short, String>, String> idAndMoveNameOrResponse = findCharacterIdAndMoveNameOrResponse(ctx);

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
                PairNonnull<CharacterData.MoveSection, Integer> startMove = startMoveRequested ? findMove(response, moveName) : null;

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
    private OneOfTwo<Pair<Short, String>, String> findCharacterIdAndMoveNameOrResponse(@Nonnull CommandContext ctx) {
        int characterArgNum = 0;
        OneOfTwo<Short, String> id = OneOfTwo.ofU("I couldn't find anything for that input, sorry");
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

    @Nullable
    private PairNonnull<CharacterData.MoveSection, Integer> findMove(@Nonnull CharacterData data, @Nonnull String name) {
        String normalisedName = normaliseMoveName(name);

        PairNonnull<CharacterData.MoveSection, Integer> foundMove = null;

        for (CharacterData.MoveSection section : CharacterData.MoveSection.values()) {
            List<CharacterData.MoveData> moves = data.getMoves(section);
            for (int i = 0; i < moves.size(); i++) {
                String moveName = moves.get(i).getMoveName();

                if (moveName != null) {
                    String moveNameLowercase = moveName.toLowerCase();

                    // Direct match -> return early
                    if (moveNameLowercase.contains(name)) return new PairNonnull<>(section, i);

                    // Indirect match -> don't return just yet, we might find a direct match later
                    if (normalisedName != null && moveNameLowercase.contains(normalisedName))
                        foundMove = new PairNonnull<>(section, i);
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

    private void sendReply(@Nonnull CommandContext ctx, @Nullable CharacterData data, @Nullable PairNonnull<CharacterData.MoveSection, Integer> startMove, boolean startMoveRequested) {
        if (data == null) {
            log.error("Valid character requested, but not found by ufd service.");
            ctx.reply("Oh this is a bug. I was sure my buddy program would know about that character but it didn't. I'll tell my dev about it, but you can give them some context too.").queue();
            return;
        }

        User author = ctx.getAuthor();
        Member member = ctx.getEvent().getMember();

        MovesEmbedPaginator pages = new MovesEmbedPaginator(EmbedUtil.getPreparedUFD(member, author).build(), data, startMove, startMoveRequested);
        ButtonActionMenu menu = new ButtonActionMenu.Builder()
                .setEventWaiter(waiter)
                .addUsers(author.getIdLong())
                .registerButton(Constants.ARROW_DOUBLE_BACKWARD, pages::prevSection)
                .registerButton(Constants.ARROW_DOUBLE_FORWARD, pages::nextSection)
                .registerButton(Constants.ARROW_BACKWARD, pages::prevMove)
                .registerButton(Constants.ARROW_FORWARD, pages::nextMove)
                .registerButton(Constants.FRAME, pages::nextHitbox)
                .setStart(pages.getCurrent())
                .build();

        menu.displayReplying(ctx.getMessage());
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

    /**
     * @param hitboxPage -1 means don't show
     */
    @Nonnull
    private EmbedBuilder applyMove(@Nonnull EmbedBuilder embed, @Nonnull CharacterData data, @Nonnull CharacterData.MoveSection section, int hitboxPage, @Nonnull CharacterData.MoveData move) {
        String moveName = move.getMoveName();
        if (moveName == null) moveName = "Some Move";
        embed.setTitle(String.format("%s - %s - %s", MiscUtil.capitalizeFirst(data.getName()), section.displayName(), moveName), data.getUfdUrl());

        List<EmbedUtil.InlineField> fields = new ArrayList<>();

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
            String noteWithoutName = String.format("%s/%d (Use %s)", hitboxPage < 0 ? "-" : hitboxPage + 1, hitboxesSize, Constants.FRAME);

            if (hitboxPage < 0) hitboxNote = noteWithoutName;
            else {
                CharacterData.HitboxData hitbox = hitboxes.get(hitboxPage);

                String name = hitbox.getName();
                hitboxNote = name == null ? noteWithoutName : String.format("%s | %s", name, noteWithoutName);
                embed.setImage(hitbox.getUrl());
            }
        }

        fields.add(new EmbedUtil.InlineField("Shown hitbox", hitboxNote));

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
        return new String[]{"character", "char", "ufd", "moves", "move", "hitboxes"};
    }

    @Nullable
    @Override
    public String getShortHelp() {
        return "Displays the moves of a character using data from [ultimateframedata.com](https://ultimateframedata.com). Usage: `character <CHARACTER NAME...> [MOVE NAME...]`";
    }

    @Nullable
    @Override
    public String getDetailedHelp() {
        return "`char[acter]|ufd|move[s]|hitboxes <CHARACTER NAME...> [MOVE NAME...]`\n" +
                "Looks up the moves of a character on [ultimateframedata.com](https://ultimateframedata.com).\n" +
                String.format("Use the %s/%s reactions to cycle through move categories%n", Constants.ARROW_DOUBLE_BACKWARD, Constants.ARROW_DOUBLE_FORWARD) +
                String.format("Use the %s/%s reactions to cycle through moves within one category%n", Constants.ARROW_BACKWARD, Constants.ARROW_FORWARD) +
                String.format("If one move has multiple hitbox images, use the %s reaction to cycle through them.", Constants.FRAME);
    }

    private class MovesEmbedPaginator {
        @Nonnull
        private final MessageEmbed template;
        @Nonnull
        private final CharacterData data;

        @Nonnull
        private CharacterData.MoveSection sectionPage;
        private int movePage;
        private int hitboxPage;

        private boolean displayCouldNotFindMove;

        public MovesEmbedPaginator(@Nonnull MessageEmbed template, @Nonnull CharacterData data, @Nullable PairNonnull<CharacterData.MoveSection, Integer> startMove, boolean startMoveRequested) {
            this.template = template;
            this.data = data;

            sectionPage = CharacterData.MoveSection.NORMALS;
            movePage = 0;
            hitboxPage = 0;

            displayCouldNotFindMove = startMoveRequested && startMove == null;

            if (startMove != null) {
                sectionPage = startMove.getT();
                movePage = startMove.getU();
            }
        }

        @Nonnull
        public synchronized Message nextSection() {
            sectionPage = sectionPage.next();
            movePage = 0;
            hitboxPage = 0;
            return getCurrent();
        }

        @Nonnull
        public synchronized Message prevSection() {
            sectionPage = sectionPage.prev();
            movePage = 0;
            hitboxPage = 0;
            return getCurrent();
        }

        @Nonnull
        public synchronized Message nextMove() {
            movePage = (movePage + 1) % sectionPage.getMoveData(data).size();
            hitboxPage = 0;
            return getCurrent();
        }

        @Nonnull
        public synchronized Message prevMove() {
            movePage--;
            if (movePage < 0) movePage = sectionPage.getMoveData(data).size() - 1;
            hitboxPage = 0;
            return getCurrent();
        }

        @Nonnull
        public synchronized Message nextHitbox() {
            int hitboxUrlsSize = sectionPage.getMoveData(data).get(movePage).getHitboxes().size();
            hitboxPage++;
            if (hitboxPage >= hitboxUrlsSize) hitboxPage = -1;
            return getCurrent();
        }

        @Nonnull
        public synchronized Message getCurrent() {
            try {
                EmbedBuilder embed = new EmbedBuilder(template);

                if (displayCouldNotFindMove) {
                    embed.appendDescription("*I could not find the move you searched for. Use the reactions to navigate through them manually.*\n");
                    displayCouldNotFindMove = false;
                }

                CharacterData.MoveData move = sectionPage.getMoveData(data).get(movePage);

                applyMove(embed, data, sectionPage, hitboxPage, move);

                return new MessageBuilder().setEmbed(embed.build()).build();
            } catch (Exception e) {
                log.catching(e);
                return new MessageBuilder("There was a severe unexpected problem with displaying the move data, I don't really know how that happened. I'll tell  my dev, you can go shoot them a message about this too if you want to.").build();
            }
        }
    }
}
