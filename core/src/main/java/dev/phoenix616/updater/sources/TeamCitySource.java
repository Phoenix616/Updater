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
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

public class TeamCitySource extends UpdateSource {

    private static final List<String> REQUIRED_PLACEHOLDERS = Arrays.asList("buildtype");
    private final static String BUILD_URL = "%apiurl%/app/rest/builds/project:%project%,status:SUCCESS,branch:%branch%,buildType:%buildtype%";
    private final static String ARTIFACTS_URL = "%apiurl%/app/rest/builds/id:%buildid%/artifacts";
    private final static String ARTIFACT_DOWNLOAD_URL = "%apiurl%/app/rest/builds/id:%buildid%/artifacts/content/%filename%";
    private final String name;
    private final String url;
    private final String token;

    public TeamCitySource(String name, Updater updater, String url, String token) {
        super(updater, REQUIRED_PLACEHOLDERS);
        this.name = name;
        this.url = url;
        this.token = token;
    }

    private String getUrl(String url) {
        if (token == null && !url.contains("guest=1")) {
            if (url.contains("?")) {
                url += "&";
            } else {
                url += "?";
            }
            url += "guest=1";
        }
        return url;
    }

    @Override
    public String getLatestVersion(PluginConfig config) {
        try {
            List<String> properties = new ArrayList<>(Arrays.asList("Accept", "application/json"));
            if (token != null) {
                Collections.addAll(properties, "Authorization", "Bearer " + token);
            }
            String s = updater.query(new URL(new Replacer()
                    .replace("apiurl", url, "branch", "master")
                    .replace(config.getPlaceholders("project"))
                    .replaceIn(getUrl(BUILD_URL))
            ), properties.toArray(new String[0]));
            if (s != null) {
                try {
                    JsonObject json = new JsonParser().parse(s).getAsJsonObject();
                    if (json.has("number")) {
                        return json.get("number").getAsString();
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
    public URL getUpdateUrl(PluginConfig config) throws MalformedURLException, FileNotFoundException {

        List<String> properties = new ArrayList<>(Arrays.asList("Accept", "application/json"));
        if (token != null) {
            Collections.addAll(properties, "Authorization", "Bearer " + token);
        }
        String s = updater.query(new URL(new Replacer()
                .replace("apiurl", url, "branch", "master")
                .replace(config.getPlaceholders("project"))
                .replaceIn(getUrl(BUILD_URL))
        ), properties.toArray(new String[0]));
        if (s != null) {
            try {
                JsonObject json = new JsonParser().parse(s).getAsJsonObject();
                if (json.has("number")) {
                    String id = json.get("id").getAsString();

                    String artifactInfo = updater.query(new URL(new Replacer()
                            .replace("apiurl", url, "buildid", id)
                            .replace(config.getPlaceholders("project"))
                            .replaceIn(getUrl(ARTIFACTS_URL))
                    ), properties.toArray(new String[0]));
                    if (artifactInfo != null) {
                        JsonObject artifactInfoJson = new JsonParser().parse(artifactInfo).getAsJsonObject();
                        if (artifactInfoJson.has("file")) {
                            JsonArray files = artifactInfoJson.getAsJsonArray("file");
                            for (JsonElement file : files) {
                                if (file.isJsonObject()
                                        && ((JsonObject) file).has("name")
                                        && ((JsonObject) file).get("name").getAsString().endsWith(".jar")) {
                                    String fileName = ((JsonObject) file).get("name").getAsString();

                                    return new URL(new Replacer()
                                            .replace("apiurl", url, "buildid", id, "filename", fileName)
                                            .replace(config.getPlaceholders("project"))
                                            .replaceIn(getUrl(ARTIFACT_DOWNLOAD_URL))
                                    );
                                }
                            }
                        }
                    }

                }
            } catch (JsonParseException e) {
                updater.log(Level.SEVERE, "Invalid Json returned when getting latest version for " + config.getName() + " from source " + getName() + ": " + s + ". Error: " + e.getMessage());
            }
        }
        throw new FileNotFoundException("Not found");
    }

    @Override
    public File downloadUpdate(PluginConfig config) {
        try {
            List<String> properties = new ArrayList<>(Arrays.asList("Accept", "application/json"));
            if (token != null) {
                Collections.addAll(properties, "Authorization", "Bearer " + token);
            }
            String s = updater.query(new URL(new Replacer()
                    .replace("apiurl", url, "branch", "master")
                    .replace(config.getPlaceholders("project"))
                    .replaceIn(getUrl(BUILD_URL))
            ), properties.toArray(new String[0]));
            if (s != null) {
                try {
                    JsonObject json = new JsonParser().parse(s).getAsJsonObject();
                    if (json.has("number")) {
                        String version = json.get("number").getAsString();
                        String id = json.get("id").getAsString();

                        String artifactInfo = updater.query(new URL(new Replacer()
                                .replace("apiurl", url, "buildid", id)
                                .replace(config.getPlaceholders("project"))
                                .replaceIn(getUrl(ARTIFACTS_URL))
                        ), properties.toArray(new String[0]));
                        if (artifactInfo != null) {
                            JsonObject artifactInfoJson = new JsonParser().parse(artifactInfo).getAsJsonObject();
                            if (artifactInfoJson.has("file")) {
                                JsonArray files = artifactInfoJson.getAsJsonArray("file");
                                for (JsonElement file : files) {
                                    if (file.isJsonObject()
                                            && ((JsonObject) file).has("name")
                                            && ((JsonObject) file).get("name").getAsString().endsWith(".jar")) {
                                        String fileName = ((JsonObject) file).get("name").getAsString();
                                        File target = new File(updater.getTempFolder(), config.getName() + "-" + fileName);

                                        try {
                                            URL source = new URL(new Replacer()
                                                    .replace("apiurl", url, "buildid", id, "filename", fileName)
                                                    .replace(config.getPlaceholders("project"))
                                                    .replaceIn(getUrl(ARTIFACT_DOWNLOAD_URL))
                                            );

                                            HttpURLConnection con = (HttpURLConnection) source.openConnection();
                                            con.setRequestProperty("User-Agent", updater.getUserAgent());
                                            if (token != null) {
                                                con.setRequestProperty("Authorization", "Bearer " + token);
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
        return name;
    }

    @Override
    public SourceType getType() {
        return SourceType.TEAMCITY;
    }
}
