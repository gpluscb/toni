package com.github.gpluscb.toni.command.components;

import com.github.gpluscb.toni.util.Constants;
import com.github.gpluscb.toni.util.PairNonnull;
import com.github.gpluscb.toni.util.discord.ButtonActionMenu;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import net.dv8tion.jda.api.entities.Emoji;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.interactions.components.Button;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class ReportGameComponent {
    @Nonnull
    private final EventWaiter waiter;

    public ReportGameComponent(@Nonnull EventWaiter waiter) {
        this.waiter = waiter;
    }

    @Nonnull
    public CompletableFuture<ReportGameResult> attachReportGame(@Nonnull Message message, long user1, long user2) {
        PairNonnull<CompletableFuture<ReportGameResult>, ButtonActionMenu> initPair = initReportGame(message, user1, user2);

        CompletableFuture<ReportGameResult> reportGameResult = initPair.getT();
        ButtonActionMenu menu = initPair.getU();

        menu.display(message);

        return reportGameResult;
    }

    private PairNonnull<CompletableFuture<ReportGameResult>, ButtonActionMenu> initReportGame(@Nonnull Message message, long user1, long user2) {
        CompletableFuture<ReportGameResult> reportGameOutcome = new CompletableFuture<>();

        ReportGameComponent.ReportGameHandler handler = new ReportGameComponent.ReportGameHandler(user1, user2, reportGameOutcome);

        ButtonActionMenu menu = new ButtonActionMenu.Builder()
                .setEventWaiter(waiter)
                .setDeletionButton(null)
                .addUsers(user1, user2)
                .setStart(message)
                .registerButton(Button.secondary("user1", Emoji.fromUnicode(Constants.ROCK)), handler::user1Button)
                .registerButton(Button.secondary("user2", Emoji.fromUnicode(Constants.PAPER)), handler::user2Button)
                .setTimeout(20, TimeUnit.MINUTES)
                .setTimeoutAction(handler::timeout)
                .build();

        return new PairNonnull<>(reportGameOutcome, menu);
    }

    public static class ReportGameResult {
        private final long winner;
        // TODO: were there conflicts?

        public ReportGameResult(long winner) {
            this.winner = winner;
        }

        public long getWinner() {
            return winner;
        }
    }
}
