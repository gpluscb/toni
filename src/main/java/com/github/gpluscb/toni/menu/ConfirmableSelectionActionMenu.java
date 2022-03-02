package com.github.gpluscb.toni.menu;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import org.apache.commons.lang3.NotImplementedException;

import javax.annotation.Nonnull;
import java.util.List;

public class ConfirmableSelectionActionMenu extends ActionMenu {
    @Nonnull
    private final Settings settings;

    @Nonnull
    private final SelectionActionMenu selectionUnderlying;

    public ConfirmableSelectionActionMenu(@Nonnull Settings settings) {
        super(settings.selectionActionMenuSettings().actionMenuSettings());

        this.settings = settings;

        selectionUnderlying = new SelectionActionMenu(settings.selectionActionMenuSettings());
    }

    // FIXME: This is a major design issue - How do we get *both* the ButtonActionMenu and the SelectionActionMenu on the same message,
    // ideally without editing it

    @Override
    public void display(@Nonnull MessageChannel channel) {
        throw new NotImplementedException();
    }

    @Override
    public void display(@Nonnull MessageChannel channel, long messageId) {
        throw new NotImplementedException();
    }

    @Override
    public void displayReplying(@Nonnull MessageChannel channel, long messageId) {
        throw new NotImplementedException();
    }

    @Override
    public void displaySlashReplying(@Nonnull SlashCommandEvent event) {
        throw new NotImplementedException();
    }

    @Override
    public void displayDeferredReplying(@Nonnull InteractionHook hook) {
        throw new NotImplementedException();
    }

    @Nonnull
    @Override
    public List<ActionRow> getComponents() {
        throw new NotImplementedException();
    }

    @Override
    public void start(@Nonnull Message message) {
        throw new NotImplementedException();
    }

    public record Settings(@Nonnull SelectionActionMenu.Settings selectionActionMenuSettings) {
    }
}
