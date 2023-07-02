package dev.phoenix616.updater;

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
import dev.phoenix616.updater.sources.UpdateSource;

import java.util.LinkedHashMap;
import java.util.Map;

public class PluginConfig {
    private String name;
    private UpdateSource source;
    private String fileNameFormat;
    private Map<String, String> placeholders;

    public PluginConfig(String name, UpdateSource source, String fileNameFormat, Map<String, String> placeholders) {
        this.name = name;
        this.source = source;
        this.fileNameFormat = fileNameFormat;
        this.placeholders = new LinkedHashMap<>(placeholders);
        this.placeholders.putIfAbsent("name", name);
    }

    public Map<String, String> getPlaceholders() {
        return placeholders;
    }

    public Map<String, String> getPlaceholders(String nameFallback) {
        if (getPlaceholders().containsKey(nameFallback)) {
            return getPlaceholders();
        }
        Map<String, String> placeholders = new LinkedHashMap<>(getPlaceholders());
        placeholders.put(nameFallback, getName());
        return placeholders;
    }

    public String getFileName(String version) {
        return Replacer.replaceIn(fileNameFormat, "name", name, "version", Updater.sanitize(version), "rawversion", version);
    }

    public String getName() {
        return name;
    }

    public UpdateSource getSource() {
        return source;
    }
}
