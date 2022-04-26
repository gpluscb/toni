package com.github.gpluscb.toni.util.discord;

import com.github.gpluscb.toni.util.MiscUtil;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.interactions.callbacks.IModalCallback;
import net.dv8tion.jda.api.interactions.components.Modal;
import net.dv8tion.jda.api.requests.restaction.interactions.ModalCallbackAction;

import javax.annotation.Nonnull;
import java.util.function.BiConsumer;

public abstract class WaitableModalHandler<T> {
    @Nonnull
    private final EventWaiter waiter;

    protected WaitableModalHandler(@Nonnull EventWaiter waiter) {
        this.waiter = waiter;
    }

    /**
     * ID will be set or overwritten by replyWith.
     */
    @Nonnull
    public abstract Modal.Builder toModal();

    @Nonnull
    public abstract T fromModalInteraction(@Nonnull ModalInteractionEvent event);

    /**
     * Assumes you will queue the returned RestAction quickly
     */
    @Nonnull
    public ModalCallbackAction replyWith(@Nonnull IModalCallback interaction, @Nonnull BiConsumer<T, ModalInteractionEvent> onReply) {
        String id = MiscUtil.randomString(5);
        Modal m = toModal().setId(id).build();

        // TODO: Timeout
        waiter.waitForEvent(
                ModalInteractionEvent.class,
                e -> e.getModalId().equals(id),
                e -> onReply.accept(fromModalInteraction(e), e)
        );

        return interaction.replyModal(m);
    }
}
