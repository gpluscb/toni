package com.github.gpluscb.toni.command.components;

import com.github.gpluscb.toni.util.PairNonnull;
import com.github.gpluscb.toni.util.discord.ButtonActionMenu;
import com.github.gpluscb.toni.util.smash.Ruleset;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.interactions.components.Button;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static net.dv8tion.jda.api.interactions.components.Button.LABEL_MAX_LENGTH;

public class BanStagesComponent {/*
    @Nonnull
    private final EventWaiter waiter;

    public BanStagesComponent(@Nonnull EventWaiter waiter) {
        this.waiter = waiter;
    }

    @Nonnull
    public CompletableFuture<List<Integer>> attachBanStages(@Nonnull Message message, @Nonnull Ruleset ruleset, long user) {
        PairNonnull<CompletableFuture<List<Integer>>, ButtonActionMenu> initPair = initBanStages(message, ruleset, user);

        CompletableFuture<List<Integer>> banStagesResult = initPair.getT();
        ButtonActionMenu menu = initPair.getU();

        menu.display(message);

        return banStagesResult;
    }

    @Nonnull
    private PairNonnull<CompletableFuture<List<Integer>>, ButtonActionMenu> initBanStages(@Nonnull Message message, @Nonnull Ruleset ruleset, long user) {
        CompletableFuture<List<Integer>> banStagesOutcome = new CompletableFuture<>();

        BanStagesComponent.BanStagesHandler handler = new BanStagesComponent.BanStagesHandler(ruleset, user, banStagesOutcome);

        ButtonActionMenu.Builder builder = new ButtonActionMenu.Builder()
                .setWaiter(waiter)
                .setDeletionButton(null)
                .addUsers(user)
                .setStart(message)
                .setTimeout(10, TimeUnit.MINUTES)
                .setTimeoutAction(handler::timeout);

        // TODO: With a set we could grey out dsr illegal stages
        ruleset.getStagesStream().forEach(stage -> {
            int id = stage.getStageId();
            builder.registerButton(Button.secondary(String.valueOf(stage.getStageId()), StringUtils.abbreviate(stage.getName(), LABEL_MAX_LENGTH)),
                    e -> handler.handleBan(e, id));
        });

        ButtonActionMenu menu = builder.build();
        return new PairNonnull<>(banStagesOutcome, menu);
    }
*/}
