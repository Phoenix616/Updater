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
import com.typesafe.config.ConfigRenderOptions;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueFactory;
import com.typesafe.config.ConfigValueType;
import dev.phoenix616.updater.sources.BukkitSource;
import dev.phoenix616.updater.sources.DirectSource;
import dev.phoenix616.updater.sources.FileSource;
import dev.phoenix616.updater.sources.GitHubSource;
import dev.phoenix616.updater.sources.GitLabSource;
import dev.phoenix616.updater.sources.HangarSource;
import dev.phoenix616.updater.sources.ModrinthSource;
import dev.phoenix616.updater.sources.SourceType;
import dev.phoenix616.updater.sources.SpigotSource;
import dev.phoenix616.updater.sources.TeamCitySource;
import dev.phoenix616.updater.sources.UpdateSource;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public abstract class Updater {

    public static final Pattern GITHUB_PATTERN = Pattern.compile(".*https?://(?:www\\.)?github\\.com/(?<user>[\\w\\-]+)/(?<repo>[\\w\\-]+)(?:[/#].*)?.*");
    public static final Pattern HANGAR_PATTERN = Pattern.compile(".*https?://hangar\\.papermc\\.io/(?<author>[\\w\\-]+)/(?<project>[\\w\\-]+)(?:[/#].*)?.*");
    public static final Pattern SPIGOT_PATTERN = Pattern.compile(".*https?://(?:www\\.)?spigotmc\\.org/resources/.*\\.(?<id>\\d+)(?:[/#].*)?.*");
    private final Map<String, UpdateSource> sources = new HashMap<>();
    private final Map<String, PluginConfig> plugins = new HashMap<>();

    private Config versions;

    private final static Config PLUGIN_DEFAULTS = ConfigFactory.empty()
            .withValue("file-name-format", ConfigValueFactory.fromAnyRef("%name%.jar-%version%"));

    private final Cache<URL, String> queryCache = Caffeine.newBuilder().expireAfterWrite(30, TimeUnit.SECONDS).build();

    private File targetFolder;

    public Updater(File targetFolder) {
        this.targetFolder = targetFolder;
        loadConfig();
    }

    private void loadConfig() {
        Config sourcesConfig = getConfig("sources");

        sources.clear();

        addSource(new BukkitSource(this));
        addSource(new GitHubSource(this));
        addSource(new GitLabSource(this));
        addSource(new HangarSource(this));
        addSource(new ModrinthSource(this));
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
                                sourceConfig.hasPath("required-placeholders") ? sourceConfig.getStringList("required-placeholders") : Collections.emptyList()
                        ));
                        break;
                    case DIRECT:
                        addSource(new DirectSource(
                                sourceName,
                                this,
                                sourceConfig.getString("latest-version"),
                                sourceConfig.getString("download"),
                                sourceConfig.hasPath("required-placeholders") ? sourceConfig.getStringList("required-placeholders") : Collections.emptyList()
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

        String par = "";
        for (int i = 0; i < args.length; i++) {
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
                        sender.sendMessage(Level.WARNING, "No Plugin found with name " + value);
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

        versions = getConfig(new File(targetFolder, "versions.conf"));

        boolean r;
        if (plugin != null) {
            r = check(sender, plugin, !checkOnly, dontLink);
        } else {
            checkExistingJars(sender);
            r = check(sender, !checkOnly, dontLink);
        }

        // Check if plugin was updated, if so save versions config
        if (r && !checkOnly) {
            try {
                saveInstalledVersions();
            } catch (IOException e) {
                sender.sendMessage(Level.SEVERE, "Failed to save versions file!", e);
            }
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
                            Matcher hangarMatcher = HANGAR_PATTERN.matcher(line);
                            if (hangarMatcher.matches()) {
                                String hangarAuthor = hangarMatcher.group("author");
                                String hangarProject = hangarMatcher.group("project");
                                sender.sendMessage(Level.INFO, "Found link to a Hanger project page in " + file.getName() + "! If you want to update from there add the following to your plugins config:\n\n"
                                        + pluginName + " {\n"
                                        + "  type = hangar\n"
                                        + "  placeholders {\n"
                                        + "    author = " + hangarAuthor + "\n"
                                        + "    project = " + hangarProject + "\n"
                                        + "  }\n"
                                        + "}\n");
                            }
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

    private boolean check(Sender sender, boolean update, boolean dontLink) {
        boolean r = false;
        for (PluginConfig plugin : plugins.values()) {
            r |= check(sender, plugin, update, dontLink);
        }
        return r;
    }

    private boolean check(Sender sender, PluginConfig plugin, boolean update, boolean dontLink) {
        String latestVersion = plugin.getSource().getLatestVersion(plugin);
        if (latestVersion != null && isNewVersion(plugin, latestVersion)) {
            sender.sendMessage(Level.INFO, "Found new version of " + plugin.getName() + " on " + plugin.getSource().getName() + ": " + latestVersion);

            if (update) {
                sender.sendMessage(Level.INFO, "Downloading " + plugin.getName() + " " + latestVersion + "...");
                File downloadedFile = plugin.getSource().downloadUpdate(plugin);
                if (downloadedFile != null) {
                    sender.sendMessage(Level.INFO, "Done!");
                    try {
                        String contentType = Files.probeContentType(downloadedFile.toPath());
                        if (ContentType.JAR.matches(contentType)) {
                            sender.sendMessage(Level.INFO, "Successfully downloaded plugin jar file!");
                        } else if (ContentType.ZIP.matches(contentType)) {
                            sender.sendMessage(Level.INFO, "Downloaded a zip archive. Trying to unpack it...");

                            ZipFile zip = new ZipFile(downloadedFile);

                            String zipEntryPatternString = plugin.getPlaceholders().get("zip-entry-pattern");
                            Pattern zipEntryPattern = null;
                            if (zipEntryPatternString != null) {
                                try {
                                    zipEntryPattern = Pattern.compile(zipEntryPatternString);
                                } catch (PatternSyntaxException ex) {
                                    log(Level.SEVERE, "Could not compile regex pattern " + zipEntryPattern + " for " + plugin.getName());
                                }
                            }
                            Pattern finalZipEntryPattern = zipEntryPattern;
                            Optional<? extends ZipEntry> entry = zip.stream()
                                    .filter(e -> finalZipEntryPattern != null ? finalZipEntryPattern.matcher(e.getName()).matches() : e.getName().endsWith(".jar"))
                                    .filter(e -> !e.getName().endsWith("-sources.jar") && !e.getName().endsWith("-javadoc.jar"))
                                    .max((o1, o2) -> Long.compare(o2.getSize(), o1.getSize()));
                            if (entry.isPresent()) {
                                downloadedFile = new File(getTempFolder(), entry.get().getName().contains("/")
                                        ? entry.get().getName().substring(entry.get().getName().lastIndexOf('/'))
                                        : entry.get().getName());
                                try {
                                    Files.copy(zip.getInputStream(entry.get()), downloadedFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                                } catch (IOException e) {
                                    sender.sendMessage(Level.SEVERE, "Error while trying to unpack file " + entry.get().getName() + " from " + zip.getName() + ". Aborting!", e);
                                    return false;
                                }
                            } else {
                                sender.sendMessage(Level.SEVERE, "Unable to find jar file in zip archive. Aborting!");
                                return false;
                            }
                        } else if (contentType != null) {
                            sender.sendMessage(Level.INFO, "Downloaded a " + contentType + " file which isn't supported. Trying to link it anyways...");
                        } else {
                            sender.sendMessage(Level.INFO, "Unable to detect file content type... hoping for the best :S");
                        }
                    } catch (IOException e) {
                        sender.sendMessage(Level.SEVERE, "Error while trying to get type of downloaded file!", e);
                    }

                    File versionedFile = new File(getTargetFolder(), plugin.getFileName(latestVersion));
                    try {
                        Files.move(downloadedFile.toPath(), versionedFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        if (!dontLink) {
                            File pluginFile = new File(getTargetFolder(), plugin.getName() + ".jar");
                            if (pluginFile.exists()) {
                                pluginFile.delete();
                            }
                            try {
                                Files.createSymbolicLink(pluginFile.toPath(), getTargetFolder().toPath().relativize(versionedFile.toPath()));
                                sender.sendMessage(Level.INFO, "Linked " + pluginFile + " to " + versionedFile);
                                setInstalledVersion(plugin, latestVersion);
                            } catch (IOException e) {
                                sender.sendMessage(Level.WARNING, "Failed to create symbolic link from " + pluginFile + " to " + versionedFile + "! (" + e.getMessage() + ") Creating hard link.");
                                try {
                                    Files.createLink(getTargetFolder().toPath(), versionedFile.toPath());
                                    sender.sendMessage(Level.INFO, "Linked " + pluginFile + " to " + versionedFile);
                                } catch (IOException e1) {
                                    sender.sendMessage(Level.SEVERE, "Error while linking!", e1);
                                    return false;
                                }
                            }
                        }
                    } catch (IOException e) {
                        sender.sendMessage(Level.SEVERE, "Failed to move temporary file to versioned " + downloadedFile + " to " + versionedFile + "! (" + e.getMessage() + ")");
                    }
                    return true;
                }
            } else {
                try {
                    sender.sendMessage(Level.INFO, "Get update from " + plugin.getSource().getUpdateUrl(plugin));
                } catch (MalformedURLException | FileNotFoundException e) {
                    sender.sendMessage(Level.SEVERE, "Error while trying to get download URL: " + e.getMessage());
                }
                return true;
            }
        } else {
            sender.sendMessage(Level.INFO, "No new version for " + plugin.getName() + " found from " + plugin.getSource().getType() + " source " + plugin.getSource().getName() + " (got " + latestVersion + ")");
        }
        return false;
    }

    private boolean isNewVersion(PluginConfig plugin, String latestVersion) {
        String installedVersion = getInstalledVersion(plugin);
        if (installedVersion != null) {
            installedVersion = sanitize(installedVersion);
            latestVersion = sanitize(latestVersion);

            try {
                // Try parsing them as build numbers
                int installedBuild = Integer.parseInt(installedVersion);
                try {
                    int latestBuild = Integer.parseInt(latestVersion);
                    return installedBuild < latestBuild;
                } catch (NumberFormatException e) {
                    // if installed is integer but latest isn't then we assume that the format changed and treat it as new
                    return true;
                }
            } catch (NumberFormatException ignored) {}

            if (installedVersion.indexOf('.') > 0 && latestVersion.indexOf('.') > 0) {
                try {
                    int[] installedSemVer = parseSemVer(installedVersion);
                    int[] latestSemVer = parseSemVer(latestVersion);
                    return compareTo(latestSemVer, installedSemVer) > 0;
                } catch (NumberFormatException e) {
                    return true;
                }
            }
        }
        return true;
    }

    private int compareTo(int[] latestSemVer, int[] installedSemVer) {
        for (int i = 0; i < installedSemVer.length && i < latestSemVer.length; i++) {
            int latestVersionInt = latestSemVer[i];
            int installedVersionInt = installedSemVer[i];
            if (latestVersionInt > installedVersionInt) {
                return 1;
            } else if (latestVersionInt < installedVersionInt) {
                return -1;
            }
        }

        if (installedSemVer.length < latestSemVer.length) {
            return 1;
        } else if (installedSemVer.length > latestSemVer.length) {
            return -1;
        }
        return 0;
    }

    public static String sanitize(String version) {
        return version.split("[\\s(\\-#\\[{]", 2)[0];
    }

    private int[] parseSemVer(String version) throws NumberFormatException {
        String[] split = version.split("\\.");
        int[] semVer = new int[split.length];
        for (int i = 0; i < split.length; i++) {
            semVer[i] = Integer.parseInt(split[i]);
        }
        return semVer;
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

    private String getInstalledVersion(PluginConfig plugin) {
        String pluginKey = plugin.getName().toLowerCase(Locale.ROOT);
        if (versions.hasPath("plugins." + pluginKey)) {
            ConfigValue value = versions.getValue("plugins." + pluginKey);
            if (value.valueType() == ConfigValueType.STRING) {
                return (String) value.unwrapped();
            }
        }

        return null;
    }

    private void setInstalledVersion(PluginConfig plugin, String version) {
        versions = versions.withValue("plugins." + plugin.getName().toLowerCase(Locale.ROOT), ConfigValueFactory.fromAnyRef(version));
    }

    private void saveInstalledVersions() throws IOException {
        File versionsFile = new File(getTargetFolder(), "versions.conf");
        if (!versionsFile.exists()) {
            versionsFile.createNewFile();
        }
        try (FileWriter writer = new FileWriter(versionsFile)) {
            writer.write(versions.resolve().root().render(ConfigRenderOptions.defaults().setOriginComments(false)));
        }
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
            log(Level.WARNING, "Automatic downloading from SpigotMC.org will most likely fail due to Cloudflare. If the plugin has a GitHub release it will be used though.");
        }

        plugins.put(plugin.getName().toUpperCase(Locale.ROOT), plugin);
        log(Level.FINE, "Added plugin " + plugin.getName() + " from " + plugin.getSource().getType() + " source " + plugin.getSource().getName());
        return true;
    }

    private Config getConfig(String name) {
        return getConfig(new File(name + ".conf"));
    }

    private Config getConfig(File file) {
        saveResource(file);
        Config fallbackConfig;
        try {
            fallbackConfig = ConfigFactory.parseResourcesAnySyntax(file.getName());
        } catch (ConfigException e) {
            log(Level.SEVERE, "Error while loading " + file.getName() + " fallback config!", e);
            fallbackConfig = ConfigFactory.empty("Empty " + file.getName() + " fallback due to loading error: " + e.getMessage());
        }
        try {
            return ConfigFactory.parseFile(file).withFallback(fallbackConfig);
        } catch (ConfigException e) {
            log(Level.SEVERE, "Error while loading " + file.getPath() + " config!", e);
            return fallbackConfig;
        }
    }

    private void saveResource(File file) {
        InputStream inputStream = getClass().getResourceAsStream("/" + file.getName());
        if (inputStream != null) {
            if (!file.exists()) {
                try {
                    Files.copy(inputStream, file.toPath());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } else {
            log(Level.WARNING, "No resource " + file.getName() + " found!");
        }
    }

    private File getTargetFolder() {
        return targetFolder;
    }

    public abstract void log(Level level, String message, Throwable... exception);

    public abstract File getTempFolder();

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
                } else {
                    log(Level.WARNING, con.getResponseCode() + "/" + con.getResponseMessage() + " while trying to query url " + url.toString());
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
