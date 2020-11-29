package com.github.gpluscb.smashggnotifications;

import javax.annotation.Nonnull;

public class Config {
    @Nonnull
    private final String ggToken;
    @Nonnull
    private final String discordToken;
    @Nonnull
    private final String challongeUsername;
    @Nonnull
    private final String challongeToken;
    @Nonnull
    private final String inviteUrl;
    private final long botId;
    @Nonnull
    private final String supportServer;
    private final long devId;
    @Nonnull
    private final String stopwordListLocation;
    @Nonnull
    private final String stateDbLocation;
    @Nonnull
    private final String smashdataDbLocation;
    @Nonnull
    private final String charactersFileLocation;

    public Config(@Nonnull String ggToken, @Nonnull String discordToken, @Nonnull String challongeUsername, @Nonnull String challongeToken, @Nonnull String inviteUrl, long botId, @Nonnull String supportServer, long devId, @Nonnull String stopwordListLocation, @Nonnull String stateDbLocation, @Nonnull String smashdataDbLocation, @Nonnull String charactersFileLocation) {
        this.ggToken = ggToken;
        this.discordToken = discordToken;
        this.challongeUsername = challongeUsername;
        this.challongeToken = challongeToken;
        this.botId = botId;
        this.inviteUrl = inviteUrl;
        this.supportServer = supportServer;
        this.devId = devId;
        this.stopwordListLocation = stopwordListLocation;
        this.stateDbLocation = stateDbLocation;
        this.smashdataDbLocation = smashdataDbLocation;
        this.charactersFileLocation = charactersFileLocation;
    }

    public void check() {
        if (ggToken == null) throw new IllegalStateException("ggToken may not be null");
        if (challongeUsername == null) throw new IllegalStateException("challongeUsername may not be null");
        if (challongeToken == null) throw new IllegalStateException("challongeToken may not be null");
        if (discordToken == null) throw new IllegalStateException("discordToken may not be null");
        if (inviteUrl == null) throw new IllegalStateException("inviteUrl may not be null");
        if (supportServer == null) throw new IllegalStateException("supportServer may not be null");
        if (stopwordListLocation == null) throw new IllegalStateException("stopwordListLocation may not be null");
        if (stateDbLocation == null) throw new IllegalStateException("stateDbLocation may not be null");
        if (smashdataDbLocation == null) throw new IllegalStateException("smashdataDbLocation may not be null");
        if (charactersFileLocation == null) throw new IllegalStateException("characterFileLocation may not be null");
    }

    @Nonnull
    public String getGGToken() {
        return ggToken;
    }

    @Nonnull
    public String getDiscordToken() {
        return discordToken;
    }

    @Nonnull
    public String getChallongeUsername() {
        return challongeUsername;
    }

    @Nonnull
    public String getChallongeToken() {
        return challongeToken;
    }

    @Nonnull
    public String getInviteUrl() {
        return inviteUrl;
    }

    public long getBotId() {
        return botId;
    }

    @Nonnull
    public String getSupportServer() {
        return supportServer;
    }

    public long getDevId() {
        return devId;
    }

    @Nonnull
    public String getStateDbLocation() {
        return stateDbLocation;
    }

    @Nonnull
    public String getSmashdataDbLocation() {
        return smashdataDbLocation;
    }

    @Nonnull
    public String getCharactersFileLocation() {
        return charactersFileLocation;
    }

    @Nonnull
    public String getStopwordListLocation() {
        return stopwordListLocation;
    }
}
