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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;

public class FileSource extends UpdateSource {

    private final String latestVersion;
    private final String download;

    public FileSource(String name, Updater updater, String latestVersion, String download, List<String> requiredParameters) {
        super(updater, SourceType.FILE, name, requiredParameters);
        this.latestVersion = latestVersion;
        this.download = download;
    }

    @Override
    public String getLatestVersion(PluginConfig config) {
        File file = new File(new Replacer().replace(config.getParameters()).replaceIn(latestVersion));
        if (Files.isSymbolicLink(file.toPath())) {
            try {
                Path linked = Files.readSymbolicLink(file.toPath());
                if (Files.isDirectory(linked)) {
                    return linked.getFileName().toString();
                }
            } catch (IOException e) {
                updater.log(Level.SEVERE, "Error while trying to get latest version for " + config.getName() + " from source " + getName() + "! " + e.getMessage());
            }
        }
        if (file.isFile()) {
            try {
                List<String> lines = Files.readAllLines(file.toPath());
                if (!lines.isEmpty()) {
                    return lines.get(0);
                }
            } catch (IOException e) {
                updater.log(Level.SEVERE, "Error while trying to get latest version for " + config.getName() + " from source " + getName() + "! " + e.getMessage());
            }
        }
        return null;
    }

    @Override
    public URL getUpdateUrl(PluginConfig config) throws MalformedURLException, FileNotFoundException {
        File file = new File(new Replacer().replace(config.getParameters()).replaceIn(download));
        if (file.exists()) {
            return file.toURI().toURL();
        }
        throw new FileNotFoundException("Not found");
    }

    @Override
    public File downloadUpdate(PluginConfig config) {
        String version = getLatestVersion(config);
        if (version != null) {
            File source = new File(new Replacer().replace(config.getParameters()).replaceIn(download));
            File target = new File(updater.getTempFolder(), config.getFileName(version) + "-" + source.getName());

            try {
                return Files.copy(source.toPath(), target.toPath()).toFile();
            } catch (IOException e) {
                updater.log(Level.SEVERE, "Error while trying to copy update " + version + " for " + config.getName() + " from source " + getName() + "! " + e.getMessage());
            }
        }

        return null;
    }
}
