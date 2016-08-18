package net.fabricmc.base;

import java.io.File;

public class Fabric {

    private static File gameDir;
    private static File configDir;

    public static File getGameDir() {
        return gameDir;
    }

    public static File getConfigDir() {
        if (configDir == null) {
            configDir = new File(gameDir, "config");
            if (!configDir.exists()) {
                configDir.mkdirs();
            }
        }
        return configDir;
    }

}
