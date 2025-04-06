package dev.phoenix616.updater;

/*
 * PhoenixUpdater - core
 * Copyright (C) 2021 Max Lee aka Phoenix616 (max@themoep.de)
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

import java.util.Collections;
import java.util.HashSet;

public enum ContentType {
    JAR("jar", "octet-stream", "java-archive", "x-java-archive"),
    ZIP("zip", "x-zip", "x-zip-compressed", "x-compressed");

    private static final String APPLICATION = "application/";
    private final HashSet<String> types = new HashSet<>();

    ContentType(String... types) {
        Collections.addAll(this.types, types);
    }

    public boolean matches(String string) {
        if (string != null && string.startsWith(APPLICATION)) {
            return types.contains(string.substring(APPLICATION.length()));
        }
        return false;
    }

    public boolean urlMatches(String string) {
        return string != null && string.endsWith("." + name().toLowerCase());
    }
}
