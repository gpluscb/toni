package com.github.gpluscb.toni;

import javax.annotation.Nonnull;

public record Config(@Nonnull String ggToken, @Nonnull String discordToken,
                     @Nonnull String dbotsToken, boolean mockBotLists, @Nonnull String toniApiToken,
                     @Nonnull String inviteUrl, long botId, @Nonnull String supportServer,
                     @Nonnull String twitterHandle, @Nonnull String github, long devId,
                     long adminGuildId, @Nonnull String stopwordListLocation,
                     @Nonnull String stateDbLocation, @Nonnull String smashdataDbLocation,
                     @Nonnull String rulesetsLocation, @Nonnull String charactersFileLocation) {
    @SuppressWarnings("ConstantConditions")
    public void check() {
        if (ggToken == null) throw new IllegalStateException("ggToken may not be null");
        if (discordToken == null) throw new IllegalStateException("discordToken may not be null");
        if (dbotsToken == null) throw new IllegalStateException("dbotsToken may not be null");
        if (toniApiToken == null) throw new IllegalStateException("toniApiToken may not be null");
        if (inviteUrl == null) throw new IllegalStateException("inviteUrl may not be null");
        if (supportServer == null) throw new IllegalStateException("supportServer may not be null");
        if (twitterHandle == null) throw new IllegalStateException("twitterHandle may not be null");
        if (stopwordListLocation == null) throw new IllegalStateException("stopwordListLocation may not be null");
        if (stateDbLocation == null) throw new IllegalStateException("stateDbLocation may not be null");
        if (smashdataDbLocation == null) throw new IllegalStateException("smashdataDbLocation may not be null");
        if (rulesetsLocation == null) throw new IllegalStateException("rulesetsLocation may not be null");
        if (charactersFileLocation == null) throw new IllegalStateException("characterFileLocation may not be null");
    }
}
