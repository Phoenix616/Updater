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
import java.util.Optional;
import java.util.logging.Level;

public class ModrinthSource extends UpdateSource {

    private static final String API_HEADER = "application/json";
    private static final String VERSION_URL = "https://api.modrinth.com/v2/project/%project%/version?featured=%featured%";

    public ModrinthSource(Updater updater) {
        super(updater, SourceType.MODRINTH, List.of());
    }

    private Optional<ReleaseInfo> getLatestRelease(PluginConfig config) {
        try {
            String versionUrl = VERSION_URL;
            List<String> properties = new ArrayList<>(Arrays.asList("Accept", API_HEADER));
            if (config.getParameters().containsKey("platform")) {
                versionUrl += "&loaders=[\"%platform%\"]";
            }
            if (config.getParameters().containsKey("platform-version")) {
                versionUrl += "&game_versions=[\"%platform-version%\"]";
            }
            if (!config.getParameters().containsKey("featured")) {
                config.getParameters().put("featured", "true");
            }
            String s = updater.query(new URL(new Replacer().replace(config.getParameters("project")).replaceIn(versionUrl)), properties.toArray(new String[0]));
            if (s != null) {
                try {
                    JsonElement json = new JsonParser().parse(s);
                    if (json.isJsonArray()) {
                        JsonArray versionArray = json.getAsJsonArray();
                        if (versionArray.size() > 0) {
                            JsonElement release = versionArray.get(0);
                            if (release.isJsonObject()) {
                                return Optional.ofNullable(buildReleaseInfo(release.getAsJsonObject()));
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
        return Optional.empty();
    }

    private ReleaseInfo buildReleaseInfo(JsonObject release) throws MalformedURLException {
        if (release != null && release.has("files") && release.get("files").isJsonArray()) {
            JsonElement versionElement = release.get("version_number");
            if (versionElement != null && versionElement.isJsonPrimitive()) {
                String version = versionElement.getAsString();
                JsonArray downloads = release.getAsJsonArray("files");
                for (JsonElement fileEntry : downloads) {
                    if (fileEntry.isJsonObject()) {
                        JsonObject file = fileEntry.getAsJsonObject();
                        if (file.has("primary") && file.get("primary").isJsonPrimitive() && file.getAsJsonPrimitive("primary").isBoolean()) {
                            if (!file.get("primary").getAsBoolean()) {
                                // not the primary download
                                continue;
                            }
                        }
                        if (file.has("url") && file.get("url").isJsonPrimitive()) {
                            String sha1hash = null;
                            if (file.has("hashes") && file.get("hashes").isJsonObject()) {
                                JsonObject hashes = file.getAsJsonObject("hashes");
                                if (hashes.has("sha1") && hashes.get("sha1").isJsonPrimitive()) {
                                    sha1hash = hashes.get("sha1").getAsString();
                                }
                            }
                            return new ReleaseInfo(version, new URL(file.get("url").getAsString()), sha1hash);
                        }
                    }
                }
            }
        }
        return null;
    }

    @Override
    public String getLatestVersion(PluginConfig config) {
        return getLatestRelease(config).map(ReleaseInfo::version).orElse(null);
    }

    @Override
    public URL getUpdateUrl(PluginConfig config) throws MalformedURLException, FileNotFoundException {
        Optional<ReleaseInfo> release = getLatestRelease(config);
        if (release.isPresent()) {
            return release.get().url();
        }
        throw new FileNotFoundException("Not found");
    }

    @Override
    public File downloadUpdate(PluginConfig config) {
        Optional<ReleaseInfo> release = getLatestRelease(config);
        if (release.isPresent()) {
            try {
                File target = new File(updater.getTempFolder(), config.getName() + "-" + release.get().version() + ".jar");

                HttpURLConnection con = (HttpURLConnection) release.get().url().openConnection();
                con.setRequestProperty("User-Agent", updater.getUserAgent());
                con.setUseCaches(false);
                con.connect();
                try (InputStream in = con.getInputStream()) {
                    if (Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING) > 0) {
                        if (release.get().sha1hash() != null) {
                            byte[] hash = MessageDigest.getInstance("SHA1").digest(Files.readAllBytes(target.toPath()));
                            String stringHash = String.format("%x", new BigInteger(1, hash));
                            if (release.get().sha1hash().equalsIgnoreCase(stringHash)) {
                                return target;
                            } else {
                                updater.log(Level.SEVERE, "Check sum of file (" + stringHash + ") does not match provided one (" + release.get().sha1hash() + ")");
                            }
                        } else {
                            return target;
                        }
                    }
                }
            } catch (IOException | NoSuchAlgorithmException e) {
                updater.log(Level.SEVERE, "Error while trying to download update " + release.get().version() + " for " + config.getName() + " from source " + getName() + "! " + e.getMessage());
            }
        } else {
            updater.log(Level.SEVERE, "Unable to get download for " + config.getName() + " from source " + getName() + "!");
        }
        return null;
    }

    private record ReleaseInfo(String version, URL url, String sha1hash) {}
}
