package com.github.gpluscb.smashggnotifications.command.help;

import com.github.gpluscb.smashggnotifications.command.Command;
import com.github.gpluscb.smashggnotifications.command.CommandContext;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Message;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class PrivacyCommand implements Command {
    /*@Nonnull
    private final MessageEmbed privacy;*/
    @Nonnull
    private final Message privacy;

    public PrivacyCommand(@Nonnull String supportServer, long devId) {
		/*EmbedBuilder privacyBuilder = new EmbedBuilder()
				.setTitle("About your data and privacy")
				.setDescription("I don't collect any personal identifying information (PII) or end user data (EUD). Here's the kinds of data I do collect:\n" +
						"**__Because the Challonge features are on hold right now, I do not persistently store any data. Once my dev works out the issues, the following will apply:__**\n" +
						"- If a tournament is linked with a channel, I store the respective tournament ID and channel ID.\n" +
						"- In the future I plan on storing user IDs of people who **explicitly** want to have their Discord account linked to a tournament participant.\n" +
						"I will need to store the data to keep providing the service of posting updates in linked channels even if I reboot.\n" +
						"I use the data only to provide tournament updates in linked channels.\n" +
						"I do not share the data with anyone. Only this application and my dev has access to the data. " +
						"My dev needs to have access to the data in case they need to manually correct/edit it.\n" +
						"If you have any concerns about me, you can contact my dev:\n" +
						String.format("• You can DM them directly if you have common servers: <@%d>%n", devId) +
						String.format("• You can go to [my support server](%s)%n", supportServer) +
						"• You can @ or dm me on Twitter, I promise you only the highest quality of tweets: [@tonissb](https://twitter.com/tonissb)\n" +
						"Currently I don't store any data pertaining to you specifically. " +
						"In the near future I will only store your ID after you explicitly link your Discord account to a tournament participant. " +
						"When that feature launches, you will be able to remove that data via a command to unlink your Discord account. " +
						"If you still have concerns about such data, you will be able to contact my dev using the methods described above.");
		privacy = privacyBuilder.build();*/
        privacy = new MessageBuilder("Find my privacy policy at <https://gist.github.com/gpluscb/66e3318e776a900222297e698006fe5e>. If you have questions, contact my dev:\n")
                .appendFormat("• You can DM them directly if you have common servers: <@%d>%n", devId)
                .appendFormat("• You can go to my support server: %s%n", supportServer)
                .append("• You can @ or dm me on Twitter, I promise you only the highest quality of tweets: <https://twitter.com/tonissb>").build();
    }

    @Override
    public void execute(@Nonnull CommandContext ctx) {
        ctx.reply(privacy).queue();
    }

    @Nonnull
    @Override
    public String[] getAliases() {
        return new String[]{"privacy", "data"};
    }

    @Nullable
    @Override
    public String getShortHelp() {
        return "Shows what data I collect and other privacy concerns. Usage: `privacy`";
    }

    @Nullable
    @Override
    public String getDetailedHelp() {
        return "`privacy|data`\n" +
                "Shows what data I collect and other privacy concerns as per <https://discord.com/developers/docs/legal#a-implement-good-privacy-practices>.";
    }
}
