package com.github.gpluscb.toni.command.modal;

import com.github.gpluscb.toni.util.discord.WaitableModalHandler;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Modal;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;

import javax.annotation.Nonnull;

public class CharPickModalHandler extends WaitableModalHandler<String> {
    public CharPickModalHandler(@Nonnull EventWaiter waiter) {
        super(waiter);
    }

    @Nonnull
    @Override
    public Modal.Builder toModal() {
        TextInput characterComponent = TextInput.create("character", "Character", TextInputStyle.SHORT)
                .setPlaceholder("e.g. 'Rosalina', 'Link'")
                .setRequired(true)
                .setRequiredRange(1, 50)
                .build();

        // ID will be overwritten
        return Modal.create("_", "Character Selection").addActionRows(ActionRow.of(characterComponent));
    }

    @Nonnull
    @Override
    public String fromModalInteraction(@Nonnull ModalInteractionEvent event) {
        // It is expected to throw here on invalid modal
        //noinspection ConstantConditions
        return event.getValue("character").getAsString();
    }
}
