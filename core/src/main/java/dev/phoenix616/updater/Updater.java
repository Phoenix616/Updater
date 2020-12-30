package dev.phoenix616.updater;

/*
 * PhoenixUpdater - core
 * Copyright (C) 2020 Max Lee aka Phoenix616 (max@themoep.de)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueFactory;
import com.typesafe.config.ConfigValueType;
import dev.phoenix616.updater.sources.BukkitSource;
import dev.phoenix616.updater.sources.DirectSource;
import dev.phoenix616.updater.sources.FileSource;
import dev.phoenix616.updater.sources.GitHubSource;
import dev.phoenix616.updater.sources.GitLabSource;
import dev.phoenix616.updater.sources.SourceType;
import dev.phoenix616.updater.sources.SpigotSource;
import dev.phoenix616.updater.sources.TeamCitySource;
import dev.phoenix616.updater.sources.UpdateSource;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public abstract class Updater {

    public static final Pattern GITHUB_PATTERN = Pattern.compile(".*https?://(?:www\\.)?github\\.com/(?<user>[\\w\\-]+)/(?<repo>[\\w\\-]+)(?:[/#].*)?.*");
    public static final Pattern SPIGOT_PATTERN = Pattern.compile(".*https?://(?:www\\.)?spigotmc\\.org/resources/.*\\.(?<id>\\d+)(?:[/#].*)?.*");
    private Map<String, UpdateSource> sources = new HashMap<>();
    private Map<String, PluginConfig> plugins = new HashMap<>();

    private final static Config PLUGIN_DEFAULTS = ConfigFactory.empty()
            .withValue("file-name-format", ConfigValueFactory.fromAnyRef("%name%.jar-%version%"));

    private Cache<URL, String> queryCache = Caffeine.newBuilder().expireAfterWrite(30, TimeUnit.SECONDS).build();

    public Updater() {
        loadConfig();
    }

    private void loadConfig() {
        Config sourcesConfig = getConfig("sources");

        sources.clear();

        addSource(new BukkitSource(this));
        addSource(new GitHubSource(this));
        addSource(new GitLabSource(this));
        addSource(new SpigotSource(this));

        for (String sourceName : sourcesConfig.root().keySet()) {
            try {
                Config sourceConfig = sourcesConfig.getConfig(sourceName);
                SourceType type = SourceType.valueOf(sourceConfig.getString("type").toUpperCase(Locale.ROOT));
                switch (type) {
                    case FILE:
                        addSource(new FileSource(
                                sourceName,
                                this,
                                sourceConfig.getString("latest-version"),
                                sourceConfig.getString("download"),
                                sourceConfig.getStringList("required-placeholders")
                        ));
                        break;
                    case DIRECT:
                        addSource(new DirectSource(
                                sourceName,
                                this,
                                sourceConfig.getString("latest-version"),
                                sourceConfig.getString("download"),
                                sourceConfig.getStringList("required-placeholders")
                        ));
                        break;
                    case TEAMCITY:
                        addSource(new TeamCitySource(
                                sourceName,
                                this,
                                sourceConfig.getString("url"),
                                sourceConfig.hasPath("token") ? sourceConfig.getString("token") : null
                        ));
                }
            } catch (ConfigException | IllegalArgumentException e) {
                log(Level.SEVERE, "Error while loading source " + sourceName + " config!", e);
            }
        }

        Config pluginsConfig = getConfig("plugins");

        for (String pluginName : pluginsConfig.root().keySet()) {
            try {
                Config pluginConfig = pluginsConfig.getConfig(pluginName).withFallback(PLUGIN_DEFAULTS);
                UpdateSource source = getSource(pluginConfig.getString("source"));
                if (source == null) {
                    throw new IllegalArgumentException("No source by the name " + pluginConfig.getString("source") + " found.");
                }

                addPlugin(new PluginConfig(
                        pluginName,
                        source,
                        pluginConfig.getString("file-name-format"),
                        pluginConfig.hasPath("placeholders") ? toMap(pluginConfig.getConfig("placeholders")) : Collections.emptyMap()
                ));
            } catch (ConfigException | IllegalArgumentException e) {
                log(Level.SEVERE, "Error while loading plugin " + pluginName + " config!", e);
            }
        }
    }

    public boolean run(Sender sender, String[] args) {
        PluginConfig plugin = null;
        boolean checkOnly = false;
        boolean dontLink = getDontLink();

        File targetFolder = getTargetFolder();

        String par = "";
        int i = 0;
        while (i + 1 < args.length) {
            i++;
            int start = 0;
            if (args[i].startsWith("-")) {
                start = 1;
            } else if (args[i].startsWith("--")) {
                start = 2;
            } else if (par.isEmpty()){
                sender.sendMessage(Level.WARNING, "Wrong parameter " + args[i] + "!");
                return false;
            }

            par = args[i].substring(start);
            if (i + 1 < args.length) {
                i++;
                String value = args[i];
                if (value.startsWith("\"")) {
                    boolean endFound = false;
                    StringBuilder sb = new StringBuilder(value);
                    for (int j = i + 1; j < args.length; j++) {
                        sb.append(" ").append(args[j]);
                        if (args[j].endsWith("\"")) {
                            endFound = true;
                            i = j;
                            break;
                        }
                    }
                    if (endFound) {
                        value = sb.toString();
                        value = value.substring(1, value.length() - 1);
                    }
                }

                if (targetFolder == null && ("t".equals(par) || "target-folder".equalsIgnoreCase(par))) {
                    targetFolder = new File(value);
                } else if ("p".equals(par) || "plugin".equalsIgnoreCase(par)) {
                    plugin = getPlugin(value);
                    if (plugin == null) {
                        sender.sendMessage(Level.WARNING, "No Plugin found with name " + args[0]);
                        return true;
                    }
                }
            }

            if ("c".equals(par) || "check-only".equalsIgnoreCase(par)) {
                checkOnly = true;
            } else if ("d".equals(par) || "dont-link".equalsIgnoreCase(par)) {
                dontLink = true;
            }
        }

        if (targetFolder == null) {
            sender.sendMessage(Level.WARNING, "Target folder not specified!");
            return false;
        } else if (!targetFolder.exists()) {
            sender.sendMessage(Level.WARNING, "Target folder does not exist! " + targetFolder);
            return true;
        }

        if (plugin != null) {
            check(sender, plugin, !checkOnly, dontLink);
        } else {
            checkExistingJars(sender);
            check(sender, !checkOnly, dontLink);
        }

        return true;
    }

    private void checkExistingJars(Sender sender) {
        for (File file : Objects.requireNonNull(getTargetFolder().listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".jar")))) {
            String pluginName = file.getName().substring(0, file.getName().length() - 4);
            if (getPlugin(pluginName) != null) {
                continue;
            }
            try {
                ZipFile jar = new ZipFile(file);
                ZipEntry pluginDescription = jar.getEntry("plugin.yml");
                if (pluginDescription == null) {
                    pluginDescription = jar.getEntry("bungee.yml");
                }
                if (pluginDescription != null) {
                    try (BufferedReader in = new BufferedReader(new InputStreamReader(jar.getInputStream(pluginDescription)))) {
                        String line;
                        while ((line = in.readLine()) != null) {
                            Matcher spigotMatcher = SPIGOT_PATTERN.matcher(line);
                            if (spigotMatcher.matches()) {
                                String id = spigotMatcher.group("id");
                                sender.sendMessage(Level.INFO, "Found link to SpigotMC resource page in " + file.getName() + "! If you want to update from there add the following to your plugins config:\n\n"
                                        + pluginName + " {\n"
                                        + "  type = spigot\n"
                                        + "  placeholders {\n"
                                        + "    resourceid = " + id + "\n"
                                        + "  }\n"
                                        + "}\n");
                            }
                            Matcher ghMatcher = GITHUB_PATTERN.matcher(line);
                            if (ghMatcher.matches()) {
                                String ghUser = ghMatcher.group("user");
                                String ghRepository = ghMatcher.group("repo");
                                sender.sendMessage(Level.INFO, "Found link to GitHub repository in " + file.getName() + "! If you want to update from there add the following to your plugins config:\n\n"
                                        + pluginName + " {\n"
                                        + "  type = github\n"
                                        + "  placeholders {\n"
                                        + "    user = " + ghUser + "\n"
                                        + "    repository = " + ghRepository + "\n"
                                        + "  }\n"
                                        + "}\n");
                            }
                        }
                    }
                }
            } catch (IOException e) {
                sender.sendMessage(Level.SEVERE, "Error while trying to check content of " + file.getName() + "!", e);
            }
        }
    }

    private void check(Sender sender, boolean update, boolean dontLink) {
        for (PluginConfig plugin : plugins.values()) {
            check(sender, plugin, update, dontLink);
        }
    }

    private void check(Sender sender, PluginConfig plugin, boolean update, boolean dontLink) {
        String latestVersion = plugin.getSource().getLatestVersion(plugin);
        if (latestVersion != null) {
            sender.sendMessage(Level.INFO, "Found latest version of " + plugin.getName() + " " + latestVersion + " on " + plugin.getSource().getName());

            if (update) {
                sender.sendMessage(Level.INFO, "Downloading " + plugin.getName() + " " + latestVersion + "...");
                File updatedFile = plugin.getSource().downloadUpdate(plugin);
                if (updatedFile != null) {
                    System.out.print(" Done!");
                    if (!dontLink) {
                        File pluginFile = new File(getTargetFolder(), plugin.getName() + ".jar");
                        try {
                            Files.createSymbolicLink(pluginFile.toPath(), updatedFile.toPath());
                            sender.sendMessage(Level.INFO, "Linked " + pluginFile + " to " + updatedFile);
                        } catch (IOException e) {
                            sender.sendMessage(Level.WARNING, "Failed to create symbolic link from " + pluginFile + " to " + updatedFile + "! (" + e.getMessage() + ") Creating hard link.");
                            try {
                                Files.createLink(pluginFile.toPath(), updatedFile.toPath());
                                sender.sendMessage(Level.INFO, "Linked " + pluginFile + " to " + updatedFile);
                            } catch (IOException e1) {
                                sender.sendMessage(Level.SEVERE, "Error hilw linking!", e1);
                            }
                        }
                    }
                }
            }
        } else {
            sender.sendMessage(Level.SEVERE, "Unable to find new version for " + plugin.getName() + " from " + plugin.getSource().getType() + " source " + plugin.getSource().getName());
        }
    }

    private Map<String, String> toMap(Config config) {
        Map<String, String> map = new LinkedHashMap<>();

        for (Map.Entry<String, ConfigValue> entry : config.entrySet()) {
            if (entry.getValue().valueType() == ConfigValueType.STRING) {
                map.put(entry.getKey(), (String) entry.getValue().unwrapped());
            } else {
                map.put(entry.getKey(), String.valueOf(entry.getValue().unwrapped()));
            }
        }

        return map;
    }

    public UpdateSource getSource(String source) {
        return sources.get(source.toUpperCase(Locale.ROOT));
    }

    private void addSource(UpdateSource source) {
        sources.put(source.getName().toUpperCase(Locale.ROOT), source);
        log(Level.FINE, "Added " + source.getType() + " source " + source.getName());
    }

    private PluginConfig getPlugin(String plugin) {
        return plugins.get(plugin.toUpperCase(Locale.ROOT));
    }

    private boolean addPlugin(PluginConfig plugin) {
        List<String> requiredPlaceholders = new ArrayList<>();
        for (String requiredPlaceholder : plugin.getSource().getRequiredPlaceholders()) {
            if (!plugin.getPlaceholders().containsKey(requiredPlaceholder)) {
                requiredPlaceholders.add(requiredPlaceholder);
            }
        }
        if (!requiredPlaceholders.isEmpty()) {
            log(Level.SEVERE, "Plugin " + plugin.getName() + " does not specify all placeholders that are required by " + plugin.getSource().getType() + " source " + plugin.getSource().getName() + "! The following placeholders are missing: " + String.join(", ", requiredPlaceholders));
            return false;
        }

        if (plugin.getSource().getType() == SourceType.SPIGOT) {
            log(Level.WARNING, "Automatic downloading from SpigotMC.org will most likely fail due to Cloudflare");
        }

        plugins.put(plugin.getName().toUpperCase(Locale.ROOT), plugin);
        log(Level.FINE, "Added plugin " + plugin.getName() + " from " + plugin.getSource().getType() + " source " + plugin.getSource().getName());
        return true;
    }

    private Config getConfig(String name) {
        saveResource(name + ".hocon");
        Config fallbackConfig;
        try {
            fallbackConfig = ConfigFactory.parseResourcesAnySyntax(name + ".hocon");
        } catch (ConfigException e) {
            log(Level.SEVERE, "Error while loading " + name + ".hocon fallback config!", e);
            fallbackConfig = ConfigFactory.empty("Empty " + name + ".hocon fallback due to loading error: " + e.getMessage());
        }
        try {
            return ConfigFactory.parseFile(new File(name + ".hocon")).withFallback(fallbackConfig);
        } catch (ConfigException e) {
            log(Level.SEVERE, "Error while loading " + name + ".hocon config!", e);
            return fallbackConfig;
        }
    }

    private void saveResource(String name) {
        InputStream inputStream = getClass().getResourceAsStream("/" + name);
        if (inputStream != null) {
            File file = new File(name);
            if (!file.exists()) {
                try {
                    Files.copy(inputStream, file.toPath());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } else {
            log(Level.WARNING, "No resource " + name + " found!");
        }
    }

    public abstract void log(Level level, String message, Throwable... exception);

    public abstract File getTargetFolder();

    protected abstract boolean getDontLink();

    public abstract String getName();

    public abstract String getVersion();

    public String query(URL url, String... properties) {
        return queryCache.get(url, u -> {
            try {
                HttpURLConnection con = (HttpURLConnection) u.openConnection();
                con.setRequestProperty("User-Agent", getUserAgent());
                for (int i = 0; i + 1 < properties.length; i += 2) {
                    con.addRequestProperty(properties[i], properties[i+1]);
                }
                StringBuilder msg = new StringBuilder();
                con.setUseCaches(false);
                con.connect();
                if (con.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
                        String line;
                        while ((line = in.readLine()) != null) {
                            if (msg.length() != 0) {
                                msg.append("\n");
                            }
                            msg.append(line);
                        }
                    }
                    return msg.toString();
                }
            } catch (IOException e) {
                log(Level.SEVERE, "Error while trying to query url " + url.toString() + ".", e);
            }
            return null;
        });
    }

    public String getUserAgent() {
        return getName() + "/" + getVersion();
    }
}
