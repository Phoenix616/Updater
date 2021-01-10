package dev.phoenix616.updater.application;

/*
 * PhoenixUpdater - application
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

import dev.phoenix616.updater.Sender;
import dev.phoenix616.updater.Updater;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Level;

public class Main {

    private static String NAME = null;
    private static String VERSION = null;
    private static Properties p = new Properties();

    public static void main(String[] args) {
        
        try {
            InputStream s = Main.class.getClassLoader().getResourceAsStream("app.properties");
            p.load(s);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        NAME = p.getProperty("application.name");
        VERSION = p.getProperty("application.version");

        System.out.print(
                NAME + " " + VERSION
                + "\n"
                + "Copyright (C) 2020 Max Lee aka Phoenix616 (max@themoep.de)\n"
                + "    By using this program you agree to the terms of the AGPLv3\n"
                + "    The full license text can be found here: https://phoenix616.dev/licenses/agpl-v3.txt\n"
                + "    This program's source is available here: https://github.com/Phoenix616/Updater\n"
        );

        File tempFolder = new File(System.getProperty("java.io.tmpdir"), NAME);

        if (!tempFolder.exists()) {
            tempFolder.mkdirs();
        }

        Updater updater = new Updater(null) {

            @Override
            protected boolean getDontLink() {
                return false;
            }

            @Override
            public void log(Level level, String message, Throwable... exception) {
                System.out.println("[" + level + "] " + message);
                for (Throwable throwable : exception) {
                    throwable.printStackTrace();
                }
            }

            @Override
            public File getTempFolder() {
                return tempFolder;
            }

            @Override
            public String getName() {
                return NAME;
            }

            @Override
            public String getVersion() {
                return VERSION;
            }
        };

        Sender sender = new Sender() {
            @Override
            public void sendMessage(Level level, String message, Throwable... throwables) {
                updater.log(level, message, throwables);
            }
        };

        if (!updater.run(sender, args)){
            System.out.print("Usage: " + p.getProperty("application.name") + ".jar <options>\n" +
                    " -t <path>, --target-folder <path> Target folder where updates get downloaded to (Required)\n" +
                    " -p <name>, --plugin <name>        Only check/update one plugin (Optional)\n" +
                    " -c, --check-only                  Only check for new versions, don't download updates (Optional)\n" +
                    " -d, --dont-link                   Only download new versions, don't link them (Optional)\n");
        }
        tempFolder.delete();
    }
}
