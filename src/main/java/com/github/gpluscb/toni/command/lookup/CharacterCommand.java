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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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

        String moveName;
        Short id = null;
        if (argNum == 1) {
            String characterName = ctx.getArgsFrom(0).toLowerCase();
            id = characters.stream().filter(c -> c.getAltNames().contains(characterName)).map(CharacterTree.Character::getId).filter(Objects::nonNull).findAny().orElse(null);
            moveName = null;
        } else {
            // Find longest string of arguments that is also a character name
            int characterArgNum = 0;
            for (int i = 1; i <= argNum; i++) {
                String characterName = ctx.getArgsRange(0, i).toLowerCase();
                Short id_ = characters.stream().filter(c -> c.getAltNames().contains(characterName)).map(CharacterTree.Character::getId).filter(Objects::nonNull).findAny().orElse(null);

                if (id_ == null) continue;

                id = id_;
                characterArgNum = i;
            }

            // Rest of arguments will be interpreted as move name
            moveName = characterArgNum < argNum ? ctx.getArgsFrom(characterArgNum) : null;
        }

        if (id == null) {
            ctx.reply("I couldn't find anything for that input, sorry.").queue();
            return;
        }

        ctx.getChannel().sendTyping().queue();

        client.getCharacter(id).whenComplete(FailLogger.logFail((response, t) -> {
            try {
                if (t != null) {
                    // TODO: Potentially quite spammy during my outages
                    log.catching(t);
                    ctx.reply("The request to my ultimateframedata api service failed. *Microservices*, amirite? Tell my dev if this happens a lot - I've already annoyed them about it, but it can't hurt to give them some more context.").queue();
                    return;
                }

                sendReply(ctx, moveName, response);
            } catch (Exception e) {
                log.catching(e);
                ctx.reply("Ouch, an error. This one's really bad, sorry. I'll send a report to my dev. If it keeps happening you might want to provide them with some context too.").queue();
            }
        }));
    }

    private void sendReply(@Nonnull CommandContext ctx, @Nullable String moveName, @Nullable CharacterData data) {
        if (data == null) {
            log.error("Valid character requested, but not found by ufd service.");
            ctx.reply("Oh this is a bug. I was sure my buddy program would know about that character but it didn't. I'll tell my dev about it, but you can give them some context too.").queue();
            return;
        }

        User author = ctx.getAuthor();
        Member member = ctx.getEvent().getMember();

        MovesEmbedPaginator pages = new MovesEmbedPaginator(EmbedUtil.getPreparedUFD(member, author).build(), data, moveName);
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

    /**
     * @param hitboxPage -1 means don't show
     */
    @Nonnull
    private EmbedBuilder applyMove(@Nonnull EmbedBuilder embed, @Nonnull CharacterData data, @Nonnull MoveSection section, int hitboxPage, @Nonnull CharacterData.MoveData move) {
        String moveName = move.getMoveName();
        if (moveName == null) moveName = "Some Move";
        embed.setTitle(String.format("%s - %s - %s", MiscUtil.capitalizeFirst(data.getName()), section.display(), moveName), data.getUfdUrl());

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
        private MoveSection sectionPage;
        private int movePage;
        private int hitboxPage;

        private boolean displayCouldNotFindMove;

        public MovesEmbedPaginator(@Nonnull MessageEmbed template, @Nonnull CharacterData data, @Nullable String startMove) {
            this.template = template;
            this.data = data;

            sectionPage = MoveSection.NORMALS;

            hitboxPage = -1;
            movePage = 0;

            displayCouldNotFindMove = false;

            if (startMove == null) return;
            // TODO normalization function: side smash -> forward smash, ftilt -> forward tilt, upb -> up b and so on
            String startMoveNormalized = startMove.toLowerCase();

            if (tryAssignSectionPageAndMovePage(data.getNormals(), MoveSection.NORMALS, startMoveNormalized)) return;
            if (tryAssignSectionPageAndMovePage(data.getAerials(), MoveSection.AERIALS, startMoveNormalized)) return;
            if (tryAssignSectionPageAndMovePage(data.getSpecials(), MoveSection.SPECIALS, startMoveNormalized)) return;
            if (tryAssignSectionPageAndMovePage(data.getGrabs(), MoveSection.GRABS, startMoveNormalized)) return;
            if (tryAssignSectionPageAndMovePage(data.getDodges(), MoveSection.DODGES, startMoveNormalized)) return;

            displayCouldNotFindMove = true;
        }

        /**
         * Helper
         */
        private boolean tryAssignSectionPageAndMovePage(@Nonnull List<CharacterData.MoveData> moves, @Nonnull MoveSection section, @Nonnull String name) {
            for (int i = 0; i < moves.size(); i++) {
                String moveName = moves.get(i).getMoveName();

                if (moveName != null && moveName.toLowerCase().contains(name)) {
                    sectionPage = section;
                    movePage = i;
                    return true;
                }
            }

            return false;
        }

        @Nonnull
        public synchronized Message nextSection() {
            sectionPage = sectionPage.next();
            movePage = 0;
            hitboxPage = -1;
            return getCurrent();
        }

        @Nonnull
        public synchronized Message prevSection() {
            sectionPage = sectionPage.prev();
            movePage = 0;
            hitboxPage = -1;
            return getCurrent();
        }

        @Nonnull
        public synchronized Message nextMove() {
            movePage = (movePage + 1) % sectionPage.getMoveData(data).size();
            hitboxPage = -1;
            return getCurrent();
        }

        @Nonnull
        public synchronized Message prevMove() {
            movePage--;
            if (movePage < 0) movePage = sectionPage.getMoveData(data).size() - 1;
            hitboxPage = -1;
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

    private enum MoveSection {
        NORMALS,
        AERIALS,
        SPECIALS,
        GRABS,
        DODGES;

        @Nonnull
        public MoveSection next() {
            switch (this) {
                case NORMALS:
                    return AERIALS;
                case AERIALS:
                    return SPECIALS;
                case SPECIALS:
                    return GRABS;
                case GRABS:
                    return DODGES;
                case DODGES:
                    return NORMALS;
                default:
                    throw new IllegalStateException("Nothing matches");
            }
        }

        @Nonnull
        public MoveSection prev() {
            switch (this) {
                case NORMALS:
                    return DODGES;
                case AERIALS:
                    return NORMALS;
                case SPECIALS:
                    return AERIALS;
                case GRABS:
                    return SPECIALS;
                case DODGES:
                    return GRABS;
                default:
                    throw new IllegalStateException("Nothing matches");
            }
        }

        @Nonnull
        public List<CharacterData.MoveData> getMoveData(@Nonnull CharacterData data) {
            switch (this) {
                case NORMALS:
                    return data.getNormals();
                case AERIALS:
                    return data.getAerials();
                case SPECIALS:
                    return data.getSpecials();
                case GRABS:
                    return data.getGrabs();
                case DODGES:
                    return data.getDodges();
                default:
                    throw new IllegalStateException("Nothing matches");
            }
        }

        @Nonnull
        public String display() {
            switch (this) {
                case NORMALS:
                    return "Ground Move";
                case AERIALS:
                    return "Aerial";
                case SPECIALS:
                    return "Special";
                case GRABS:
                    return "Grab";
                case DODGES:
                    return "Dodge";
                default:
                    throw new IllegalStateException("Nothing matches");
            }
        }
    }
}
