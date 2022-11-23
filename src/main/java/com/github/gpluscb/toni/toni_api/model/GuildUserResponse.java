package com.github.gpluscb.toni.toni_api.model;

import javax.annotation.Nonnull;

public record GuildUserResponse(long guildId, long userId, int rank, @Nonnull Rating rating) {
}
