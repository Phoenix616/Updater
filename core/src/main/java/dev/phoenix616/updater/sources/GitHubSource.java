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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import de.themoep.minedown.adventure.Replacer;
import dev.phoenix616.updater.ContentType;
import dev.phoenix616.updater.PluginConfig;
import dev.phoenix616.updater.Updater;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class GitHubSource extends UpdateSource {

    private static final List<String> REQUIRED_PARAMETERS = List.of("user");
    private static final String API_HEADER = "application/vnd.github.v3+json";
    private static final String RELEASES_URL = "https://api.github.com/repos/%user%/%repository%/releases";

    public GitHubSource(Updater updater) {
        super(updater, SourceType.GITHUB, REQUIRED_PARAMETERS);
    }

    @Override
    public String getLatestVersion(PluginConfig config) {
        try {
            Release release = getRelease(config);
            if (release != null) {
                return release.tagName();
            }
        } catch (MalformedURLException e) {
            updater.log(Level.SEVERE, "Invalid URL for getting latest version for " + config.getName() + " from source " + getName() + "! " + e.getMessage());
        }
        return null;
    }

    @Override
    public URL getUpdateUrl(PluginConfig config) throws MalformedURLException, FileNotFoundException {
        Release release = getRelease(config);
        if (release != null) {
            return new URL(release.downloadUrl());
        }
        throw new FileNotFoundException("Not found");
    }

    private Release getRelease(PluginConfig config) throws MalformedURLException {
        List<String> properties = new ArrayList<>(Arrays.asList("Accept", API_HEADER));
        if (config.getParameters().containsKey("token")) {
            Collections.addAll(properties, "Authorization", "token " + config.getParameters().get("token"));
        } else if (config.getParameters().containsKey("username") && config.getParameters().containsKey("password")) {
            String userPass = config.getParameters().get("username") + ":" + config.getParameters().get("password");
            Collections.addAll(properties, "Authorization", "Basic " + Base64.getEncoder().encodeToString(userPass.getBytes()));
        }
        String channel = config.getParameters().get("channel");
        if (channel != null && !"release".equals(channel) && !"prerelease".equals(channel)) {
            updater.log(Level.SEVERE, "Invalid channel '" + channel + " for " + config.getName() + " from source " + getName() + "! Must be 'release' or 'prerelease'.");
            return null;
        }
        String s = updater.query(new URL(new Replacer().replace(config.getParameters("repository")).replaceIn(RELEASES_URL)), properties.toArray(new String[0]));
        if (s != null) {
            try {
                JsonElement json = new JsonParser().parse(s);
                if (json.isJsonArray() && ((JsonArray) json).size() > 0) {
                    String draft = config.getParameters().get("draft");
                    String author = config.getParameters().get("author");
                    for (JsonElement release : ((JsonArray) json)) {
                        if (release.isJsonObject()
                                && ((JsonObject) release).has("tag_name")
                                && ((JsonObject) release).has("assets")
                                && ((JsonObject) release).get("assets").isJsonArray()) {
                            boolean isPrerelease = ((JsonObject) release).has("prerelease") && ((JsonObject) release).get("prerelease").getAsBoolean();
                            boolean isDraft = ((JsonObject) release).has("draft") && ((JsonObject) release).get("draft").getAsBoolean();
                            if (isDraft && !"true".equalsIgnoreCase(draft)) {
                                continue;
                            }
                            if ("release".equalsIgnoreCase(channel) && isPrerelease) {
                                continue;
                            } else if ("prerelease".equalsIgnoreCase(channel) && !isPrerelease) {
                                continue;
                            }

                            if (author != null && !author.isEmpty() && ((JsonObject) release).has("author")) {
                                String authorName = ((JsonObject) release).get("author").getAsJsonObject().get("login").getAsString();
                                if (!author.equalsIgnoreCase(authorName)) {
                                    continue;
                                }
                            }

                            for (JsonElement asset : ((JsonObject) release).getAsJsonArray("assets")) {
                                if (matches(config, asset)) {
                                    return new Release(
                                            ((JsonObject) release).get("tag_name").getAsString(),
                                            ((JsonObject) asset).get("name").getAsString(),
                                            ((JsonObject) asset).get("browser_download_url").getAsString()
                                    );
                                }
                            }
                        }
                    }
                }
                updater.log(Level.WARNING, "Json did not contain release entry for " + config.getName() + " from source " + getName() + ": " + json);
            } catch (JsonParseException e) {
                updater.log(Level.SEVERE, "Invalid Json returned when getting latest version for " + config.getName() + " from source " + getName() + ": " + s + ". Error: " + e.getMessage());
            }
        } else {
            updater.log(Level.WARNING, "Query didn't return anything for " + config.getName() + " from source " + getName() + "!");
        }
        return null;
    }

    private boolean matches(PluginConfig config, JsonElement asset) {
        if (asset.isJsonObject()
                && ((JsonObject) asset).has("browser_download_url")
                && ((JsonObject) asset).has("content_type")
                && ((JsonObject) asset).has("name")) {
            String contentType = ((JsonObject) asset).get("content_type").getAsString();
            if (ContentType.JAR.matches(contentType) || ContentType.ZIP.matches(contentType)) {
                String filePatternString = config.getParameters().get("file-pattern");
                if (filePatternString == null) {
                    return true;
                }
                try {
                    Pattern filePattern = Pattern.compile(filePatternString);
                    String name = ((JsonObject) asset).get("name").getAsString();
                    return filePattern.matcher(name).matches();
                } catch (PatternSyntaxException ex) {
                    updater.log(Level.SEVERE, "Could not compile file-pattern regex " + filePatternString + " for " + config.getName());
                }
            }
        }
        return false;
    }

    @Override
    public File downloadUpdate(PluginConfig config) {
        try {
            Release release = getRelease(config);
            if (release != null) {
                String name = release.assetName();
                String version = release.tagName();
                updater.log(Level.FINE, "Downloading update file for " + config.getName() + " " + version + ": " + name);
                File target = new File(updater.getTempFolder(), config.getName() + "-" + version + "-" + name);

                try {
                    URL source = new URL(release.downloadUrl());

                    HttpURLConnection con = (HttpURLConnection) source.openConnection();
                    con.setRequestProperty("User-Agent", updater.getUserAgent());
                    con.addRequestProperty("Accept", API_HEADER);
                    con.addRequestProperty("Accept", "application/octet-stream");
                    if (config.getParameters().containsKey("token")) {
                        con.addRequestProperty("Authorization", "token " + config.getParameters().get("token"));
                    } else if (config.getParameters().containsKey("username") && config.getParameters().containsKey("password")) {
                        String userPass = config.getParameters().get("username") + ":" + config.getParameters().get("password");
                        con.setRequestProperty("Authorization", "Basic " + Base64.getEncoder().encodeToString(userPass.getBytes()));
                    }
                    con.setUseCaches(false);
                    con.connect();
                    try (InputStream in = con.getInputStream()) {
                        if (Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING) > 0) {
                            return target;
                        }
                    }
                } catch (IOException e) {
                    updater.log(Level.SEVERE, "Error while trying to download update " + version + " for " + config.getName() + " from source " + getName() + "! " + e.getMessage());
                }
            }
        } catch (MalformedURLException e) {
            updater.log(Level.SEVERE, "Invalid URL for getting latest version for " + config.getName() + " from source " + getName() + "! " + e.getMessage());
        }
        return null;
    }

    record Release(String tagName, String assetName, String downloadUrl) {

    }
}
