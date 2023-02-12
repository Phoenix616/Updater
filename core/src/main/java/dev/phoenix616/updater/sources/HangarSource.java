package dev.phoenix616.updater.sources;

/*
 * PhoenixUpdater - core
 * Copyright (C) 2023 Max Lee aka Phoenix616 (max@themoep.de)
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
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

public class HangarSource extends UpdateSource {

    private static final List<String> REQUIRED_PLACEHOLDERS = Arrays.asList("user");
    private static final String API_HEADER = "application/json";
    private static final String VERSION_URL = "https://hangar.papermc.io/api/v1/projects/%user%/%project%/versions&limit=1&offset=0";

    public HangarSource(Updater updater) {
        super(updater, REQUIRED_PLACEHOLDERS);
    }

    private JsonObject getLatestRelease(PluginConfig config) {
        try {
            String versionUrl = VERSION_URL;
            List<String> properties = new ArrayList<>(Arrays.asList("Accept", API_HEADER));
            if (config.getPlaceholders().containsKey("channel")) {
                versionUrl += "&channel=%channel%";
            }
            if (config.getPlaceholders().containsKey("platform")) {
                versionUrl += "&platform=%platform%";
            }
            if (config.getPlaceholders().containsKey("versiontag")) {
                versionUrl += "&vTag=%versiontag%";
            }
            String s = updater.query(new URL(new Replacer().replace(config.getPlaceholders("project")).replaceIn(versionUrl)), properties.toArray(new String[0]));
            if (s != null) {
                try {
                    JsonElement json = new JsonParser().parse(s);
                    if (json.isJsonObject()) {
                        JsonArray result = json.getAsJsonObject().getAsJsonArray("result");
                        if (result != null && result.size() > 0) {
                            JsonElement release = result.getAsJsonArray().get(0);
                            if (release.isJsonObject()) {
                                return release.getAsJsonObject();
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

    /**
     * Get the download URL to use from a specific platform downlaod json object.
     * Prefers Hangar-native over external downloads
     * @param download The object
     * @return The URL
     * @throws MalformedURLException Thrown when the url provided by the api is invalid
     */
    private URL getDownloadUrl(JsonObject download) throws MalformedURLException {
        if (download.has("fileInfo") && download.get("fileInfo").isJsonObject()
                && download.has("downloadUrl") && download.get("downloadUrl").isJsonPrimitive()) {
            return new URL(download.get("downloadUrl").getAsString());
        } else if (download.has("externalUrl") && download.get("externalUrl").isJsonPrimitive()) {
            return new URL(download.get("externalUrl").getAsString());
        }
        return null;
    }

    private String getMd5Hash(JsonObject download) {
        if (download.has("fileInfo") && !download.get("fileInfo").isJsonObject()) {
            JsonObject fileInfo = download.getAsJsonObject("fileInfo");
            if (fileInfo.has("md5Hash") && fileInfo.get("md5Hash").isJsonPrimitive()) {
                return download.get("md5Hash").getAsString();
            }
        }
        return null;
    }

    private String getVersion(JsonObject release) {
        if (release != null) {
            JsonElement versionElement = release.get("name");
            if (versionElement != null && versionElement.isJsonPrimitive() && versionElement.getAsJsonPrimitive().isString()) {
                return versionElement.getAsString();
            }
        }
        return null;
    }

    @Override
    public String getLatestVersion(PluginConfig config) {
        return getVersion(getLatestRelease(config));
    }

    @Override
    public URL getUpdateUrl(PluginConfig config) throws MalformedURLException, FileNotFoundException {
        JsonObject release = getLatestRelease(config);
        if (release != null && release.has("downloads") && release.get("downloads").isJsonObject()) {
            JsonObject downloads = release.getAsJsonObject("downloads");
            if (config.getPlaceholders().containsKey("platform")) {
                String platform = config.getPlaceholders().get("platform").toUpperCase(Locale.ROOT);
                if (downloads.has(platform) && downloads.get(platform).isJsonObject()) {
                    return getDownloadUrl(downloads.getAsJsonObject(platform));
                }
            } else if (!downloads.entrySet().isEmpty()) {
                JsonElement platformEntry = downloads.entrySet().iterator().next().getValue();
                if (platformEntry.isJsonObject()) {
                    return getDownloadUrl(platformEntry.getAsJsonObject());
                }
            }
        }
        throw new FileNotFoundException("Not found");
    }

    @Override
    public File downloadUpdate(PluginConfig config) {
        JsonObject release = getLatestRelease(config);
        String version = getVersion(release);
        if (version != null && release.has("downloads") && release.get("downloads").isJsonObject()) {
            JsonObject downloads = release.getAsJsonObject("downloads");
            URL downloadUrl = null;
            String md5 = null;
            try {
                if (config.getPlaceholders().containsKey("platform")) {
                    String platform = config.getPlaceholders().get("platform").toUpperCase(Locale.ROOT);
                    if (downloads.has(platform) && downloads.get(platform).isJsonObject()) {
                        downloadUrl = getDownloadUrl(downloads.getAsJsonObject(platform));
                        md5 = getMd5Hash(downloads.getAsJsonObject(platform));
                    }
                } else if (!downloads.entrySet().isEmpty()) {
                    JsonElement platformEntry = downloads.entrySet().iterator().next().getValue();
                    if (platformEntry.isJsonObject()) {
                        downloadUrl = getDownloadUrl(platformEntry.getAsJsonObject());
                        md5 = getMd5Hash(platformEntry.getAsJsonObject());
                    }
                }

                if (downloadUrl != null) {
                    File target = new File(updater.getTempFolder(), config.getName() + "-" + version + ".jar");

                    try {
                        HttpURLConnection con = (HttpURLConnection) downloadUrl.openConnection();
                        con.setRequestProperty("User-Agent", updater.getUserAgent());
                        con.setUseCaches(false);
                        con.connect();
                        try (InputStream in = con.getInputStream()) {
                            if (Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING) > 0) {
                                if (md5 != null) {
                                    byte[] hash = MessageDigest.getInstance("MD5").digest(Files.readAllBytes(target.toPath()));
                                    String stringHash = String.format("%x", new BigInteger(1, hash));
                                    if (md5.equalsIgnoreCase(stringHash)) {
                                        return target;
                                    } else {
                                        updater.log(Level.SEVERE, "Check sum of file (" + stringHash + ") does not match provided one (" + md5 + ")");
                                    }
                                } else {
                                    return target;
                                }
                            }
                        }
                    } catch (IOException | NoSuchAlgorithmException e) {
                        updater.log(Level.SEVERE, "Error while trying to download update " + version + " for " + config.getName() + " from source " + getName() + "! " + e.getMessage());
                    }
                } else {
                    updater.log(Level.SEVERE, "Unable to find download URL for latest version of " + config.getName() + " from source " + getName() + "!");
                }
            } catch (MalformedURLException e) {
                updater.log(Level.SEVERE, "Invalid URL for getting latest version for " + config.getName() + " from source " + getName() + "! " + e.getMessage());
            }
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
