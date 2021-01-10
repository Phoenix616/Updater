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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

public class GitHubSource extends UpdateSource {

    private static final List<String> REQUIRED_PLACEHOLDERS = Arrays.asList("user");
    private static final String API_HEADER = "application/vnd.github.v3+json";
    private static final String RELEASES_URL = "https://api.github.com/repos/%user%/%repository%/releases";

    public GitHubSource(Updater updater) {
        super(updater, REQUIRED_PLACEHOLDERS);
    }

    @Override
    public String getLatestVersion(PluginConfig config) {
        try {
            List<String> properties = new ArrayList<>(Arrays.asList("Accept", API_HEADER));
            if (config.getPlaceholders().containsKey("token")) {
                Collections.addAll(properties, "Authorization", "token " + config.getPlaceholders().get("token"));
            } else if (config.getPlaceholders().containsKey("username") && config.getPlaceholders().containsKey("password")) {
                String userPass = config.getPlaceholders().get("username") + ":" + config.getPlaceholders().get("password");
                Collections.addAll(properties, "Authorization", "Basic " + Base64.getEncoder().encodeToString(userPass.getBytes()));
            }
            String s = updater.query(new URL(new Replacer().replace(config.getPlaceholders("repository")).replaceIn(RELEASES_URL)), properties.toArray(new String[0]));
            if (s != null) {
                try {
                    JsonElement json = new JsonParser().parse(s);
                    if (json.isJsonArray() && ((JsonArray) json).size() > 0) {
                        for (JsonElement release : ((JsonArray) json)) {
                            if (release.isJsonObject()
                                    && ((JsonObject) release).has("tag_name")
                                    && ((JsonObject) release).has("assets")
                                    && ((JsonObject) release).get("assets").isJsonArray()) {
                                for (JsonElement asset : ((JsonObject) release).getAsJsonArray("assets")) {
                                    if (asset.isJsonObject()
                                            && ((JsonObject) asset).has("content_type")
                                            && Updater.CONTENT_TYPE_JAR.equals(((JsonObject) asset).get("content_type").getAsString())) {
                                        return ((JsonObject) release).get("tag_name").getAsString();
                                    }
                                }
                            }
                        }
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
            List<String> properties = new ArrayList<>(Arrays.asList("Accept", API_HEADER));
            if (config.getPlaceholders().containsKey("token")) {
                Collections.addAll(properties, "Authorization", "token " + config.getPlaceholders().get("token"));
            } else if (config.getPlaceholders().containsKey("username") && config.getPlaceholders().containsKey("password")) {
                String userPass = config.getPlaceholders().get("username") + ":" + config.getPlaceholders().get("password");
                Collections.addAll(properties, "Authorization", "Basic " + Base64.getEncoder().encodeToString(userPass.getBytes()));
            }
            String s = updater.query(new URL(new Replacer().replace(config.getPlaceholders("repository")).replaceIn(RELEASES_URL)), properties.toArray(new String[0]));
            if (s != null) {
                try {
                    JsonElement json = new JsonParser().parse(s);
                    if (json.isJsonArray() && ((JsonArray) json).size() > 0) {
                        for (JsonElement release : ((JsonArray) json)) {
                            if (release.isJsonObject()
                                    && ((JsonObject) release).has("tag_name")
                                    && ((JsonObject) release).has("assets")
                                    && ((JsonObject) release).get("assets").isJsonArray()) {
                                for (JsonElement asset : ((JsonArray) ((JsonObject) release).get("assets"))) {
                                    if (asset.isJsonObject()
                                            && ((JsonObject) asset).has("browser_download_url")
                                            && ((JsonObject) asset).has("content_type")
                                            && ((JsonObject) asset).has("name")
                                            && Updater.CONTENT_TYPE_JAR.equals(((JsonObject) asset).get("content_type").getAsString())) {
                                        String version = ((JsonObject) release).get("tag_name").getAsString();
                                        File target = new File(updater.getTempFolder(), ((JsonObject) asset).get("name").getAsString());

                                        try {
                                            URL source = new URL(((JsonObject) asset).get("browser_download_url").getAsString());

                                            HttpURLConnection con = (HttpURLConnection) source.openConnection();
                                            con.setRequestProperty("User-Agent", updater.getUserAgent());
                                            con.addRequestProperty("Accept", API_HEADER);
                                            con.addRequestProperty("Accept", "application/octet-stream");
                                            if (config.getPlaceholders().containsKey("token")) {
                                                con.addRequestProperty("Authorization", "token " + config.getPlaceholders().get("token"));
                                            } else if (config.getPlaceholders().containsKey("username") && config.getPlaceholders().containsKey("password")) {
                                                String userPass = config.getPlaceholders().get("username") + ":" + config.getPlaceholders().get("password");
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
                                }
                            }
                        }
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
    public String getName() {
        return getType().name();
    }

    @Override
    public SourceType getType() {
        return SourceType.GITHUB;
    }
}
