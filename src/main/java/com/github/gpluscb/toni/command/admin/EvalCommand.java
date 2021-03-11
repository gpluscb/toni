package com.github.gpluscb.toni.command.admin;

import com.github.gpluscb.toni.command.Command;
import com.github.gpluscb.toni.command.CommandContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

public class EvalCommand implements Command {
    @Override
    public void execute(@Nonnull CommandContext ctx) {
        if (!ctx.memberHasBotAdminPermission()) return;

        String args = ctx.getArgsFrom(0);

        // ScriptEngine code modified from https://github.com/jagrosh/Vortex/blob/f059c0b34e16093f25414dd05d4d93fa8bf0afa5/src/main/java/com/jagrosh/vortex/commands/owner/EvalCmd.java#L48-L62
        // Copyright notice of original file:
        /*
         * Copyright 2016 John Grosh (jagrosh).
         *
         * Licensed under the Apache License, Version 2.0 (the "License");
         * you may not use this file except in compliance with the License.
         * You may obtain a copy of the License at
         *
         *      http://www.apache.org/licenses/LICENSE-2.0
         *
         * Unless required by applicable law or agreed to in writing, software
         * distributed under the License is distributed on an "AS IS" BASIS,
         * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
         * See the License for the specific language governing permissions and
         * limitations under the License.
         */
        ScriptEngine engine = new ScriptEngineManager().getEngineByName("graal.js");
        Bindings bindings = engine.getBindings(ScriptContext.ENGINE_SCOPE);
        bindings.put("polyglot.js.allowHostAccess", true);
        engine.put("ctx", ctx);

        try {
            ctx.reply(String.format("There you go:```%s```", engine.eval(args))).queue();
        } catch (Exception e) {
            ctx.reply(String.format("There was an error:```%s```", e)).queue();
        }
    }

    @Nonnull
    @Override
    public String[] getAliases() {
        return new String[]{"eval"};
    }

    @Nullable
    @Override
    public String getShortHelp() {
        return null;
    }

    @Nullable
    @Override
    public String getDetailedHelp() {
        return null;
    }
}
