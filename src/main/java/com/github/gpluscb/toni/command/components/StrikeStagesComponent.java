package com.github.gpluscb.toni.command.components;

import com.github.gpluscb.toni.util.ButtonActionMenu;
import com.github.gpluscb.toni.util.Constants;
import com.github.gpluscb.toni.util.smash.Ruleset;
import com.github.gpluscb.toni.util.smash.Stage;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import net.dv8tion.jda.api.entities.Emoji;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.interactions.components.Button;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static net.dv8tion.jda.api.interactions.components.Button.LABEL_MAX_LENGTH;

// TODO: I want to have a inner class here that has a builder where you can onStrike(int strikeNum, Function something something) or something like that.
public class StrikeStagesComponent {
    @Nonnull
    private final EventWaiter waiter;
    @Nonnull
    private final List<Stage> stageList;

    public StrikeStagesComponent(@Nonnull EventWaiter waiter, @Nonnull List<Stage> stageList) {
        this.waiter = waiter;
        this.stageList = stageList;
    }

    @Nonnull
    public CompletableFuture<List<Set<Integer>>> attachStageStriking(@Nonnull Message message, @Nonnull Ruleset ruleset, long striker1, long striker2) {
        CompletableFuture<List<Set<Integer>>> stageStrikingResult = new CompletableFuture<>();

        StageStrikingHandler handler = new StageStrikingHandler(stageStrikingResult, ruleset, striker1, striker2);

        ButtonActionMenu.Builder builder = new ButtonActionMenu.Builder()
                .setEventWaiter(waiter)
                .setDeletionButton(null)
                .addUsers(striker1, striker2)
                .setStart(message)
                .setTimeout(3, TimeUnit.MINUTES)
                .setTimeoutAction(handler::timeout);

        for (Stage starter : ruleset.getStarters()) {
            builder.registerButton(
                    Button.secondary(String.valueOf(starter.getStageId()), StringUtils.abbreviate(starter.getName(), LABEL_MAX_LENGTH)),
                    e -> handler.handleStrike(e, starter.getStageId())
            );
        }

        ButtonActionMenu menu = builder.build();

        menu.display(message);
    }
}
