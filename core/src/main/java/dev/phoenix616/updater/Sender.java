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

import java.util.logging.Level;

public abstract class Sender {
    private Level logLevel;

    public abstract void sendMessage(Level level, String message, Throwable... throwables);

    public void setLogLevel(Level logLevel) {
        this.logLevel = logLevel;
    }

    public Level getLogLevel() {
        return logLevel;
    }
}
