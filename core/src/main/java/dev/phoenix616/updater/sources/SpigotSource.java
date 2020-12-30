package dev.phoenix616.updater.sources;

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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import de.themoep.minedown.adventure.Replacer;
import dev.phoenix616.updater.PluginConfig;
import dev.phoenix616.updater.Updater;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.regex.Matcher;

public class SpigotSource extends UpdateSource {

    private static final List<String> REQUIRED_PLACEHOLDERS = Arrays.asList("resourceid");
    private static final String VERSION_URL = "https://api.spiget.org/v2/resources/%resourceid%/versions/latest";
    private static final String DOWNLOAD_URL = "https://api.spiget.org/v2/resources/%resourceid%/versions/%versionid%/download";
    private static final String DETAILS_URL = "https://api.spiget.org/v2/resources/%resourceid%";

    public SpigotSource(Updater updater) {
        super(updater, REQUIRED_PLACEHOLDERS);
    }

    @Override
    public String getLatestVersion(PluginConfig config) {
        try {
            String s = updater.query(new URL(new Replacer().replace(config.getPlaceholders()).replaceIn(VERSION_URL)));
            if (s != null) {
                try {
                    JsonElement json = new JsonParser().parse(s);
                    if (json.isJsonObject() && ((JsonObject) json).has("name") && ((JsonObject) json).has("id")) {
                            return ((JsonObject) json).get("name").getAsString();
                    }
                } catch (JsonParseException e) {
                    updater.log(Level.SEVERE, "Invalid Json returned when getting latest version for " + config.getName() + " from source " + getName() + ": " + s + ". Error: " + e.getMessage());
                }
            }
        } catch (MalformedURLException e) {
            updater.log(Level.SEVERE, "Invalid URL for getting latest version for " + config.getName() + " from source " + getName() + "! " + e.getMessage());
        }
        return null;
    }

    @Override
    public File downloadUpdate(PluginConfig config) {
        try {
            String s = updater.query(new URL(new Replacer().replace(config.getPlaceholders()).replaceIn(VERSION_URL)));
            if (s != null) {
                try {
                    JsonElement json = new JsonParser().parse(s);
                    if (json.isJsonObject()
                            && ((JsonObject) json).has("name")
                            && ((JsonObject) json).has("id")) {
                        String version = ((JsonObject) json).get("name").getAsString();
                        long id = ((JsonObject) json).get("id").getAsLong();
                        File target = new File(updater.getTargetFolder(), config.getFileName(version));

                        URL source = new URL(new Replacer().replace(config.getPlaceholders()).replace("versionid", String.valueOf(id)).replaceIn(DOWNLOAD_URL));
                        try {
                            HttpURLConnection con = (HttpURLConnection) source.openConnection();
                            con.setRequestProperty("User-Agent", updater.getUserAgent());
                            con.setUseCaches(false);
                            con.connect();
                            if (con.getResponseCode() == HttpURLConnection.HTTP_OK) {
                                try (InputStream in = con.getInputStream()) {
                                    if (Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING) > 0) {
                                        return target;
                                    }
                                }
                            } else {
                                updater.log(Level.SEVERE, "Unable to download " + version + " for " + config.getName() + " from source " + getName() + "! " + con.getResponseMessage());
                                if (con.getResponseCode() == HttpURLConnection.HTTP_UNAVAILABLE) {
                                    String details = updater.query(new URL(new Replacer().replace(config.getPlaceholders()).replaceIn(DETAILS_URL)));
                                    JsonObject detailsJson = new JsonParser().parse(details).getAsJsonObject();
                                    if (detailsJson.has("links") && detailsJson.get("links").getAsJsonObject().size() > 0) {
                                        for (Map.Entry<String, JsonElement> link : detailsJson.get("links").getAsJsonObject().entrySet()) {
                                            if (link.getValue().isJsonPrimitive() && ((JsonPrimitive) link.getValue()).isString()) {
                                                String linkStr = link.getValue().getAsString();
                                                if (linkStr.contains("github.com/")) {
                                                    Matcher matcher = Updater.GITHUB_PATTERN.matcher(linkStr);
                                                    if (matcher.matches()) {
                                                        String ghUser = matcher.group("user");
                                                        String ghRepository = matcher.group("repo");

                                                        updater.log(Level.INFO, "Found GitHub repository at " + ghUser + "/" + ghRepository + ". Checking if it has releases!");
                                                        config.getPlaceholders().put("user", ghUser);
                                                        config.getPlaceholders().put("repository", ghRepository);
                                                        UpdateSource ghSource = updater.getSource(SourceType.GITHUB.name());
                                                        String ghVersion = ghSource.getLatestVersion(config);
                                                        if (ghVersion != null) {
                                                            if (ghVersion.equalsIgnoreCase(version)) {
                                                                updater.log(Level.INFO, "Found matching release on GitHub: " + ghVersion + ". Downloading it...");
                                                                return ghSource.downloadUpdate(config);
                                                            } else {
                                                                updater.log(Level.WARNING, "Found non-matching release on GitHub: " + ghVersion + ". Spigot version was " + version + ". If you would like to download the update from GitHub instead then please adjust your plugins config!");
                                                            }
                                                        } else {
                                                            updater.log(Level.WARNING, "Unable to find release on GitHub. Here is the URL to manually download it from Spigot: " + source);
                                                        }
                                                        return null;
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (IOException e) {
                            updater.log(Level.SEVERE, "Error while trying to download update " + version + " for " + config.getName() + " from source " + getName() + "! " + e.getMessage());
                        }
                    }
                } catch (JsonParseException | IllegalStateException e) {
                    updater.log(Level.SEVERE, "Invalid Json returned when getting latest version for " + config.getName() + " from source " + getName() + ": " + s + ". Error: " + e.getMessage());
                }
            }
        } catch (MalformedURLException e) {
            updater.log(Level.SEVERE, "Invalid URL for getting latest version for " + config.getName() + " from source " + getName() + "! " + e.getMessage());
        }
        return null;
    }

    @Override
    public String getName() {
        return getType().name();
    }

    @Override
    public SourceType getType() {
        return SourceType.SPIGOT;
    }
}
