[server]: https://discord.gg/jTHjBxJjUA
[invite]: https://discord.com/api/oauth2/authorize?client_id=698889469532569671&permissions=0&scope=bot%20applications.commands
[widget]: https://discord.com/api/guilds/624219440422322177/widget.png
[dbots]: https://discord.bots.gg/bots/698889469532569671

[![widget][]][server]

# SSBToni
Hi, I'm SSBToni, a Discord bot for the competitive Super Smash Bros. community (but mainly focused on Ultimate right now).

I can show you frame data and hitbox images via ultimateframedata, look up players via the smashdata.gg database, and more.

For a full list of features, see my [discord.bots.gg profile][dbots]. To invite me to your server, click [here][invite].
`toni, help` will get you started.

Please note that I'm still a baby, so I appreciate any feedback, feature requests, bug reports, whatever! You can
- [create an Issue](https://github.com/gpluscb/toni/issues/new/) here
- [tweet at me](https://twitter.com/tonissb)
- join [my support server][server]

Feel free to pr stuff too.

## Acknowledgements
Data sources:
- [ultimateframedata](https://ultimateframedata.com) for the character command.
- [smashdata.gg database](https://github.com/smashdata/ThePlayerDatabase) for the player command.
- [smash.gg API](https://developer.smashg.gg) for the tournament command.

Built on [JDA](https://github.com/DV8FromTheWorld/JDA) with [JDA-Utils](https://github.com/JDA-Applications/JDA-Utilities).

## About self hosting
I don't know why you would self-host, and I'd discourage it, but you can if you really want to.
Replace [config.example.json](resources/config.example.json) with an according config.json, and start the bot supplying the path to the config file as the only argument.

## Screenshots
![UFD command](https://imgur.com/hC3WvwH.png)

![Smashdata command](https://i.imgur.com/RQChnao.png)

![Random character command](https://i.imgur.com/rC9fh3j.png)
