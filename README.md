# PhoenixUpdater
Tool for updating Minecraft plugins from multiple different sources.

Requires Java 17.

## Sources
The following sources can be used for updating:

- [DevBukkit](https://dev.bukkit.org)
- GitHub releases
- GitLab releases (including from self-hosted instances)
- [Hangar](https://hangar.papermc.io) (PaperMC plugins)
- [Modrinth](https://modrinth.com)
- TeamCity CI builds (e.g. for WorldGuard/WorldEdit)
- Own hosted Jenkins (via direct download source or if locally hosted file source)
- [SpigotMC](https://spigotmc.org) using [Spiget](https://spiget.org) (Downloads might fail depending on whether Spiget has the files cached or not as SpigotMC.org uses DDOS protection in front of downloads. The source tries to scan resource pages for GitHub releases too though)

## ToDo

- [x] Options handling
- [x] Download and linking logic
- [x] Version caching and checking
- [ ] Show changes between versions
- [ ] Specify semantic version update level
- [ ] Configurator utility
- [ ] More sources (e.g. CIs like GitHub actions, Travis or Circle. PRs welcome!)
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
- `-l <level>`, `--log-level <level>`  Only print messages of the specified level or higher (*Optional*, default: `INFO`)
- `dont-search-existing-jars` Don't search for existing jars in the target folder to find new plugins (*Optional*)

### Configs
The program uses multiple configuration files for specifying where the updates for different plugins should come from. These are the `plugins.hocon` and `sources.hocon` files. They configure individual plugins and custom sources respectively.

#### Inbuilt source types
- `bukkit` Downloads from dev.bukkit.org.  
  Required plugin parameters: `pluginid` (can be obtained from [here](https://servermods.forgesvc.net/servermods/projects?search=worldedit))
- `github` Downloads GitHub releases.  
  Required plugin parameters: `user`
  Optional: `repository` (Defaults to plugin name), `file-pattern` (to match a specific file of a release), `token` or `username` and `password`
- `gitlab` Downloads GitLab releases.  
  Required plugin parameters: `user`
  Optional: `repository` (Defaults to plugin name), `token`, `apiurl` (Defaults to `https://gitlab.com/api/v4/`)
- `hangar` Downloads Hangar.papermc.io releases.
  Optional: `project` (Defaults to plugin name), `channel`, `platform`, `platform-version`
- `modrinth` Downloads Modrinth releases.
  Optional parameters: `project` (Defaults to plugin name), `featured` (Defaults to true), `platform`, `platform-version`
- `spigot` Tries to download from SpigotMC.org and falls back to GitHub if found.  
  Required plugin parameters: `resourceid`

#### Required parameters for custom source types
- `teamcity` Downloads from a self-hosted teamcity instance.  
  Required plugin parameters: `project`, `buildtype`
- `direct` Queries the version and downloads the jar directly from an URL. E.g. like Jenkins allows it.  
  Required parameters are defined in the `sources.hocon` entry
- `file` Queries the version and copies the jar directly from a file location. E.g. like you can do it with Jenkins if it runs on the same server.  
  Required parameters are defined in the `sources.hocon` entry 

Direct source version checks which return more than just the version can have an additional config option `version-regex-pattern` or `version-json-path` to specify a [JSON path](https://github.com/json-path/JsonPath?tab=readme-ov-file#operators) to the version string in the response json of the query. (See [this online tool](https://jsonpath.com/) if you need help with JSON paths)

Direct source versions which do not use a static nor versioned url for downloads can use the `download-regex-pattern` or `download-json-path` config options similarly to the versions one to query the actual update url from the response of querying the `download` url.

Downloads that return a zip file can use the `zip-entry-pattern` parameter to define the regex pattern of the file to look for inside the zip file.

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