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
import java.util.List;
import java.util.logging.Level;

public class DirectSource extends UpdateSource {

    private final String name;
    private final String latestVersion;
    private final String download;

    public DirectSource(String name, Updater updater, String latestVersion, String download, List<String> requiredPlaceholders) {
        super(updater, requiredPlaceholders);
        this.name = name;
        this.latestVersion = latestVersion;
        this.download = download;
    }

    @Override
    public String getLatestVersion(PluginConfig config) {
        try {
            String r = updater.query(new URL(new Replacer().replace(config.getPlaceholders()).replaceIn(latestVersion)));
            if (r != null && !r.isEmpty()) {
                return r;
            }
        } catch (MalformedURLException e) {
            updater.log(Level.SEVERE, "Invalid URL for getting latest direct version for " + config.getName() + " from source " + getName() + "! " + e.getMessage());
        }
        return null;
    }

    @Override
    public File downloadUpdate(PluginConfig config) {
        String version = getLatestVersion(config);
        if (version != null) {
            File target = new File(updater.getTargetFolder(), config.getFileName(version));

            try {
                URL source = new URL(new Replacer().replace(config.getPlaceholders()).replaceIn(download));

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
                updater.log(Level.SEVERE, "Error while trying to download update " + version + " for " + config.getName() + " from source " + getName() + "! " + e.getMessage());
            }
        }

        return null;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public SourceType getType() {
        return SourceType.DIRECT;
    }
}
