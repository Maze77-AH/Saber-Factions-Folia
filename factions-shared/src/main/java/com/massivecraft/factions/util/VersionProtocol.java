package com.massivecraft.factions.util;

import org.bukkit.Bukkit;

public class VersionProtocol {

    public static void printVersionInfo() {
        short version = getMajorCompatibilityVersion();
        switch (version) {
            case 7:
                Logger.print("Minecraft Version 1.7 found, disabling banners, itemflags inside GUIs, corners, and Titles.", Logger.PrefixType.DEFAULT);
                break;
            case 8:
                Logger.print("Minecraft Version 1.8 found, Title Fadeouttime etc will not be configurable.", Logger.PrefixType.DEFAULT);
                break;
            case 13:
                Logger.print("Minecraft Version 1.13 found, New Items will be used.", Logger.PrefixType.DEFAULT);
                break;
            case 14:
                Logger.print("Minecraft Version 1.14 found.", Logger.PrefixType.DEFAULT);
                break;
            case 15:
                Logger.print("Minecraft Version 1.15 found.", Logger.PrefixType.DEFAULT);
                break;
            case 16:
                Logger.print("Minecraft Version 1.16 found.", Logger.PrefixType.DEFAULT);
                break;
            case 17:
                Logger.print("Minecraft Version 1.17 found.", Logger.PrefixType.DEFAULT);
                break;
            case 18:
                Logger.print("Minecraft Version 1.18 found.", Logger.PrefixType.DEFAULT);
                break;
            case 19:
                Logger.print("Minecraft Version 1.19 found.", Logger.PrefixType.DEFAULT);
                break;
            default:
                if (version >= 20) {
                    Logger.print("Minecraft compatibility version " + getMinecraftVersionLabel() + " found.", Logger.PrefixType.DEFAULT);
                }
                break;
        }
    }

    public static short getMajorCompatibilityVersion() {
        String bukkitVersion = Bukkit.getBukkitVersion();
        short parsed = parseMajorCompatibilityVersion(bukkitVersion);
        if (parsed > 0) {
            return parsed;
        }

        try {
            return Short.parseShort(ReflectionUtils.PackageType.getServerVersion().split("_")[1]);
        } catch (RuntimeException ignored) {
            return 20;
        }
    }

    public static String getMinecraftVersionLabel() {
        String bukkitVersion = Bukkit.getBukkitVersion();
        int dashIndex = bukkitVersion.indexOf('-');
        return dashIndex > 0 ? bukkitVersion.substring(0, dashIndex) : bukkitVersion;
    }

    static short parseMajorCompatibilityVersion(String bukkitVersion) {
        if (bukkitVersion == null || bukkitVersion.isEmpty()) {
            return -1;
        }

        String version = bukkitVersion.split("-", 2)[0];
        String[] parts = version.split("\\.");
        if (parts.length == 0) {
            return -1;
        }

        try {
            if ("1".equals(parts[0]) && parts.length > 1) {
                return Short.parseShort(parts[1]);
            }

            return Short.parseShort(parts[0]);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    @Deprecated
    public static void printVerionInfo() {
        printVersionInfo();
    }
}
