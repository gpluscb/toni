package com.github.gpluscb.toni.util.discord;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.sharding.ShardManager;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.appender.AppenderLoggingException;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.layout.PatternLayout;

import javax.annotation.Nonnull;
import java.io.Serializable;
import java.util.concurrent.TimeUnit;

@Plugin(name = "Discord", category = "Core", elementType = "appender", printObject = true)
public class DiscordAppenderImpl extends AbstractAppender {
    // Minimum of 5secs between error logs
    private static final long MIN_MILLIS_BETWEEN_LOGS = 5000;
    private final long logChannelId;
    private static ShardManager shardManager;

    private long lastLog = 0;

    protected DiscordAppenderImpl(String name, Filter filter, Layout<? extends Serializable> layout,
                                  boolean ignoreExceptions, Property[] properties, long logChannelId) {
        super(name, filter, layout, ignoreExceptions, properties);
        this.logChannelId = logChannelId;
    }

    public static synchronized void setShardManager(@Nonnull ShardManager shardManager) {
        if (DiscordAppenderImpl.shardManager != null) throw new IllegalStateException("shardManager already set");
        DiscordAppenderImpl.shardManager = shardManager;
    }

    @Override
    public synchronized void append(LogEvent event) {
        try {
            if (shardManager == null) return;

            MessageChannel channel = shardManager.getTextChannelById(logChannelId);
            if (channel == null) return;
            // TODO: There's a race condition here where shardManager may shut down after this check. I don't care too much about it, it's not critical.
            switch (channel.getJDA().getStatus()) {
                case SHUTTING_DOWN:
                case SHUTDOWN:
                    return;
            }

            // We need to make sure we don't log too often. Otherwise we might have infinite recursion on DNS Resolution errors.
            // So if the last log was a while ago, ignore
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastLog < MIN_MILLIS_BETWEEN_LOGS) return;
            lastLog = currentTime;

            byte[] bytes = getLayout().toByteArray(event);
            String message = new String(bytes);

            String logMessage = "```" + message;
            logMessage = (logMessage.length() > 1997 ? logMessage.substring(0, 1997) : logMessage) + "```";

            channel.sendMessage(logMessage).timeout(3, TimeUnit.SECONDS).queue(null, Throwable::printStackTrace); // Avoid infinite loops by not auto logging on failure
        } catch (RuntimeException e) {
            if (!ignoreExceptions()) throw new AppenderLoggingException(e);
        }
    }

    @PluginFactory
    public static DiscordAppenderImpl createAppender(@PluginAttribute("name") String name, @PluginAttribute("ChannelID") Long logChannelId,
                                                     @PluginElement("Layout") Layout<? extends Serializable> layout,
                                                     @PluginElement("Filter") Filter filter, @PluginElement("Properties") Property[] properties) {
        if (name == null) {
            LOGGER.error("No name provided for DiscordAppender");
            return null;
        }

        if (logChannelId == null) {
            LOGGER.error("No ChannelID provided for DiscordAppender");
            return null;
        }

        if (properties == null) {
            properties = new Property[0];
        }

        if (layout == null) {
            layout = PatternLayout.createDefaultLayout();
        }

        return new DiscordAppenderImpl(name, filter, layout, true, properties, logChannelId);
    }
}
