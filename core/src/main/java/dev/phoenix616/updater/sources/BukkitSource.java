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
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

public class BukkitSource extends UpdateSource {

    private static final List<String> REQUIRED_PLACEHOLDERS = Arrays.asList("pluginid");
    private static final String VERSION_URL = "https://api.curseforge.com/servermods/files?projectIds=%pluginid%";

    public BukkitSource(Updater updater) {
        super(updater, SourceType.BUKKIT, REQUIRED_PLACEHOLDERS);
    }

    @Override
    public String getLatestVersion(PluginConfig config) {
        try {
            String[] properties = new String[0];
            if (config.getPlaceholders().containsKey("apikey")) {
                properties = new String[]{"X-API-Key", config.getPlaceholders().get("apikey")};
            }
            String s = updater.query(new URL(new Replacer().replace(config.getPlaceholders()).replaceIn(VERSION_URL)), properties);
            if (s != null) {
                try {
                    JsonElement json = new JsonParser().parse(s);
                    if (json.isJsonArray() && ((JsonArray) json).size() > 0) {
                        JsonObject lastUpdate = ((JsonArray) json).get(((JsonArray) json).size() - 1).getAsJsonObject();
                        return lastUpdate.get("name").getAsString();
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

    private JsonObject getUpdateInfo(PluginConfig config) throws MalformedURLException, JsonParseException {
        String[] properties = new String[0];
        if (config.getPlaceholders().containsKey("apikey")) {
            properties = new String[]{"X-API-Key", config.getPlaceholders().get("apikey")};
        }
        String s = updater.query(new URL(new Replacer().replace(config.getPlaceholders()).replaceIn(VERSION_URL)), properties);
        if (s != null) {
            JsonElement json = new JsonParser().parse(s);
            if (json.isJsonArray() && ((JsonArray) json).size() > 0) {
                return ((JsonArray) json).get(((JsonArray) json).size() - 1).getAsJsonObject();
            }
        }
        return null;
    }

    @Override
    public URL getUpdateUrl(PluginConfig config) throws MalformedURLException, FileNotFoundException {
        try {
            JsonObject lastUpdate = getUpdateInfo(config);
            if (lastUpdate != null) {
                String downloadUrl = lastUpdate.get("fileUrl").getAsString();
                return new URL(downloadUrl);
            }
        } catch (JsonParseException e) {
            updater.log(Level.SEVERE, "Invalid Json returned when getting latest version for " + config.getName() + " from source " + getName() + ". Error: " + e.getMessage());
        }
        throw new FileNotFoundException("Not found");
    }

    @Override
    public File downloadUpdate(PluginConfig config) {
        try {
            try {
                JsonObject lastUpdate = getUpdateInfo(config);
                String version = lastUpdate.get("name").getAsString();
                String downloadUrl = lastUpdate.get("fileUrl").getAsString();
                String md5 = lastUpdate.get("md5").getAsString();

                File target = new File(updater.getTempFolder(), config.getName() + "-" + lastUpdate.get("fileName").getAsString());

                try {
                    URL source = new URL(downloadUrl);

                    HttpURLConnection con = (HttpURLConnection) source.openConnection();
                    con.setRequestProperty("User-Agent", updater.getUserAgent());
                    if (config.getPlaceholders().containsKey("apikey")) {
                        con.setRequestProperty("X-API-Key", config.getPlaceholders().get("apikey"));
                    }
                    con.setUseCaches(false);
                    con.connect();
                    try (InputStream in = con.getInputStream()) {
                        if (Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING) > 0) {
                            byte[] hash = MessageDigest.getInstance("MD5").digest(Files.readAllBytes(target.toPath()));
                            String stringHash = String.format("%x", new BigInteger(1, hash));
                            if (md5.equalsIgnoreCase(stringHash)) {
                                return target;
                            } else {
                                updater.log(Level.SEVERE, "Check sum of file (" + stringHash + ") does not match provided one (" + md5 + ")");
                            }
                        }
                    }
                } catch (IOException | NoSuchAlgorithmException e) {
                    updater.log(Level.SEVERE, "Error while trying to download update " + version + " for " + config.getName() + " from source " + getName() + "! " + e.getMessage());
                }
            } catch (JsonParseException e) {
                updater.log(Level.SEVERE, "Invalid Json returned when getting latest version for " + config.getName() + " from source " + getName() + ". Error: " + e.getMessage());
            }
        } catch (MalformedURLException e) {
            updater.log(Level.SEVERE, "Invalid URL for getting latest version for " + config.getName() + " from source " + getName() + "! " + e.getMessage());
        }
        return null;
    }
}
