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

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.JsonPathException;
import com.typesafe.config.Config;
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
import java.util.Collections;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class DirectSource extends UpdateSource {
    private final String latestVersion;

    private final String download;
    private final String versionPath;
    private final String versionRegex;
    private final String downloadPath;
    private final String downloadRegex;

    public DirectSource(String name, Updater updater, Config config) {
        super(updater, SourceType.DIRECT, name,
                config.hasPath("required-parameters")
                        ? config.getStringList("required-parameters")
                        : config.hasPath("required-placeholders")
                                ? config.getStringList("required-placeholders")
                                : Collections.emptyList());
        this.latestVersion = config.getString("latest-version");
        this.download = config.getString("download");
        this.versionPath = config.hasPath("version-json-path") ? config.getString("version-json-path") : null;
        this.versionRegex = config.hasPath("version-regex-pattern") ? config.getString("version-regex-pattern") : null;
        this.downloadPath = config.hasPath("download-json-path") ? config.getString("download-json-path") : null;
        this.downloadRegex = config.hasPath("download-regex-pattern") ? config.getString("download-regex-pattern") : null;
    }

    @Override
    public String getLatestVersion(PluginConfig config) {
        try {
            Replacer replacer = new Replacer().replace(config.getParameters());
            String r = updater.query(new URL(replacer.replaceIn(latestVersion)));
            if (r != null && !r.isEmpty()) {
                try {
                    return getParsedValue(config.getName(), r, versionPath, versionRegex, replacer);
                } catch (PatternSyntaxException e) {
                    updater.log(Level.SEVERE, "Invalid regex pattern for getting latest direct version for " + config.getName() + " from source " + getName() + "! " + e.getMessage());
                } catch (JsonPathException e) {
                    updater.log(Level.SEVERE, "Error while trying to use JSONPath " + versionPath + " in " +  config.getName() + " from source " + getName() + "! " + e.getMessage());
                } catch (ClassCastException e) {
                    updater.log(Level.SEVERE, "Invalid json result to get latest version for " + config.getName() + " from source " + getName() + "! ('" + r + "') " + e.getMessage());
                }
            }
        } catch (MalformedURLException e) {
            updater.log(Level.SEVERE, "Invalid URL for getting latest direct version for " + config.getName() + " from source " + getName() + "! " + e.getMessage());
        }
        return null;
    }

    @Override
    public URL getUpdateUrl(PluginConfig config) throws MalformedURLException {
        String version = getLatestVersion(config);
        if (version != null) {
            Replacer replacer = new Replacer().replace(config.getParameters()).replace("version", version);
            if (downloadPath == null && downloadRegex == null) {
                return new URL(replacer.replaceIn(download));
            }
            String downloadQuery = updater.query(new URL(replacer.replaceIn(download)));
            if (downloadQuery != null && !downloadQuery.isEmpty()) {
                String downloadUrl = getParsedValue(config.getName(), downloadQuery, downloadPath, downloadRegex, replacer);
                if (downloadUrl != null) {
                    return new URL(downloadUrl);
                }
            }
        }
        return null;
    }

    @Override
    public File downloadUpdate(PluginConfig config) {
        try {
            URL source = getUpdateUrl(config);
            if (source == null) {
                updater.log(Level.SEVERE, "No download URL found for " + config.getName() + " from source " + getName() + "!");
                return null;
            }
            File target = new File(updater.getTempFolder(), config.getName() + "-" + source.getPath().substring(source.getPath().lastIndexOf('/') + 1));

            HttpURLConnection con = (HttpURLConnection) source.openConnection();
            con.setUseCaches(false);
            con.setRequestProperty("User-Agent", updater.getUserAgent());
            con.connect();
            try (InputStream in = con.getInputStream()) {
                if (Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING) > 0) {
                    return target;
                }
            }
        } catch (IOException e) {
            updater.log(Level.SEVERE, "Error while trying to download update for " + config.getName() + " from source " + getName() + "! (" + e.getMessage());
        }
        return null;
    }

    private String getParsedValue(String name, String value, String jsonPath, String regex, Replacer replacer) {
        if (value.startsWith("[") && value.endsWith("]") || value.startsWith("{") && value.endsWith("}")) {
            if (jsonPath != null) {
                try {
                    Object rawValue = JsonPath.compile(replacer.replaceIn(jsonPath)).read(value);
                    if (rawValue != null) {
                        value = rawValue.toString();
                    } else {
                        updater.log(Level.SEVERE, "No value found for JSON path '" + jsonPath + "' for " + name + " from source " + getName() + " in json '" + value + "'!");
                        return null;
                    }
                } catch (JsonPathException e) {
                    updater.log(Level.SEVERE, "Error while trying to use JSONPath " + jsonPath + " in " +  name + " from source " + getName() + " on '" + value + "'! " + e.getMessage());
                }
            } else if (regex == null) {
                updater.log(Level.SEVERE, "No regex nor JSON path specified for " + name + " from source " + getName() + "!");
                return null;
            }
        }
        if (regex != null) {
            try {
                Matcher matcher = Pattern.compile(replacer.replaceIn(regex)).matcher(value);
                if (matcher.matches()) {
                    if (matcher.groupCount() > 0) {
                        value = matcher.group(1);
                    } else {
                        value = matcher.group();
                    }
                } else {
                    updater.log(Level.SEVERE, "Return value '" + value + "' does not match regex pattern '" + regex + "' in " + name + " from source " + getName() + "!");
                    return null;
                }
            } catch (PatternSyntaxException e) {
                updater.log(Level.SEVERE, "Invalid regex pattern '" + regex + "' in source " + getName() + "! " + e.getMessage());
            }
        }
        return value;
    }
}
