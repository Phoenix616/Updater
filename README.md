# PhoenixUpdater
Tool for updating Minecraft plugins from multiple different sources.

## Sources
The following sources can be used for updating:

- DevBukkit
- GitHub releases
- GitLab releases (including from self-hosted instances)
- TeamCity CI builds (e.g. for WorldGuard/WorldEdit)
- Own hosted Jenkins (via direct download source or if locally hosted file source)
- SpigotMC (downloads will most likely fail due to DDOS protection, it tries to scan resource pages for GitHub releases too though)

## ToDo

- [x] Options handling
- [x] Download and linking logic
- [x] Version caching and checking
- [ ] Show changes between versions
- [ ] Specify semantic version update level
- [ ] Configurator utility
- [ ] More sources (e.g. CIs like GitHub actions, Travis or Circle. PPs welcome!)
- [ ] Plugin implementation
- [ ] Plugin-provided updater configs
- [ ] Provide a hosted repository for update configs of popular plugins

## Usage

Run it with `java -jar PhoenixUpdater.jar <options>`

If the plugin option is not specified then it will scan all jar files in the target folder path too for possible new plugin sources.

### Options
- `-t <path>`, `--target-folder <path>` Target folder where updates get downloaded to (**Required**)
- `-p <name>`, `--plugin <name>` Only check/update one plugin (*Optional*)
- `-c`, `--check-only` Only check for new versions, don't download updates (*Optional*)
- `-d`, `--dont-link`  Only download new versions, don't link them (*Optional*)

### Configs
The program uses multiple configuration files for specifying where the updates for different plugins should come from. These are the `plugins.hocon` and `sources.hocon` files. They configure individual plugins and custom sources respectively.

#### Inbuilt source types
- `bukkit` Downloads from dev.bukkit.org.  
  Required plugin placeholders: `pluginid` (can be obtained from [here](https://servermods.forgesvc.net/servermods/projects?search=worldedit))
- `github` Downloads GitHub releases.  
  Required plugin placeholders: `user`, `repository`  
  Optional: `token` or `username` and `password`
- `gitlab` Downloads GitLab releases.  
  Required plugin placeholders: `user`, `repository`  
  Optional: `token`, `apiurl` (Defaults to `https://gitlab.com/api/v4/`)
- `spigot` Tries to download from SpigotMC.org and falls back to GitHub if found.  
  Required plugin placeholders: `resourceid`

#### Required placeholders for custom source types
- `teamcity` Downloads from a self-hosted teamcity instance.  
  Required plugin placeholders: `project`, `buildtype`
- `direct` Queries the version and downloads the jar directly from an URL. E.g. like Jenkins allows it.  
  Required placeholders are defined in the `sources.hocon` entry
- `direct` Queries the version and copies the jar directly from a file location. E.g. like you can do it with Jenkins if it runs on the same server.  
  Required placeholders are defined in the `sources.hocon` entry

## Downloads
Downloads are currently available on the Minebench.de CI server: https://ci.minebench.de/job/PhoenixUpdater/

## License
This program is licensed under the terms of the [AGPLv3](LICENSE).

```
 Copyright (C) 2020 Max Lee aka Phoenix616 (max@themoep.de)

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License as published
 by the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with this program.  If not, see <https://www.gnu.org/licenses/>.
```