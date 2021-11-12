package com.github.gpluscb.toni.util.discord.menu;

import com.jagrosh.jdautilities.commons.waiter.EventWaiter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.TimeUnit;

public abstract class TwoUsersChoicesActionMenu extends ActionMenu {
    private final long user1;
    private final long user2;

    public TwoUsersChoicesActionMenu(@Nonnull EventWaiter waiter, long user1, long user2, long timeout, @Nonnull TimeUnit unit) {
        super(waiter, timeout, unit);

        this.user1 = user1;
        this.user2 = user2;
    }

    public long getUser2() {
        return user2;
    }

    public long getUser1() {
        return user1;
    }

    public abstract class TwoUsersMenuStateInfo extends MenuStateInfo {
        public long getUser1() {
            return user1;
        }

        public long getUser2() {
            return user2;
        }
    }

    public static abstract class Builder<T extends Builder<T, V>, V extends TwoUsersChoicesActionMenu> extends ActionMenu.Builder<T, V> {
        @Nullable
        private Long user1;
        @Nullable
        private Long user2;

        public Builder(@Nonnull Class<T> clazz) {
            super(clazz);
        }

        @SuppressWarnings("unchecked")
        @Nonnull
        public T setUsers(long user1, long user2) {
            this.user1 = user1;
            this.user2 = user2;
            return (T) this;
        }

        @Nullable
        public Long getUser1() {
            return user1;
        }

        @Nullable
        public Long getUser2() {
            return user2;
        }

        @Override
        protected void preBuild() {
            super.preBuild();
            if (user1 == null || user2 == null) throw new IllegalStateException("Users must be set");
        }
    }
}
