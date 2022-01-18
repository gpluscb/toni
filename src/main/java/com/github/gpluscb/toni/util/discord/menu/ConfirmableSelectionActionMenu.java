package com.github.gpluscb.toni.util.discord.menu;

import javax.annotation.Nonnull;

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

    public record Settings(@Nonnull SelectionActionMenu.Settings selectionActionMenuSettings) {}
}
