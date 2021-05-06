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

import dev.phoenix616.updater.PluginConfig;
import dev.phoenix616.updater.Updater;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;

public abstract class UpdateSource {
    protected final Updater updater;
    private final Collection<String> requiredPlaceholders;

    public UpdateSource(Updater updater, Collection<String> requiredPlaceholders) {
        this.updater = updater;
        this.requiredPlaceholders = requiredPlaceholders;
    }

    /**
     * Get a collection of placeholders that an update config needs to define for this source to work
     * @return The list of placeholders
     */
    public Collection<String> getRequiredPlaceholders() {
        return requiredPlaceholders;
    }

    /**
     * Get the latest version of a plugin.
     * @param config The plugin config
     * @return The latest version string or <code>null</code> if not found or an error occured
     */
    public abstract String getLatestVersion(PluginConfig config);

    /**
     * Get the download URL for the latest update
     * @param config The plugin config
     * @return The download URL for the latest update
     * @throws MalformedURLException Thrown when the URL returned by the API is not valid
     * @throws FileNotFoundException Thrown when no update URL could be found
     */
    public abstract URL getUpdateUrl(PluginConfig config) throws MalformedURLException, FileNotFoundException;

    /**
     * Download the latest version of a plugin into the target folder specified by the Updater.
     * @param config The plugin config
     * @return A reference to the newly downloaded file or <code>null</code> if not found
     */
    public abstract File downloadUpdate(PluginConfig config);

    /**
     * Get the name of the source
     * @return The name of the source
     */
    public abstract String getName();

    /**
     * Get the type of this source
     * @return The type
     */
    public abstract SourceType getType();
}
