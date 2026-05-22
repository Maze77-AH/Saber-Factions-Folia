package com.massivecraft.factions.util;

import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

public final class RealFactionsDataMigrator {

    private static final String[] LEGACY_DATA_FOLDERS = {"Factions", "SaberFactions"};

    private RealFactionsDataMigrator() {
    }

    public static void migrateIfNeeded(Plugin plugin) {
        Path target = plugin.getDataFolder().toPath();

        try {
            if (Files.exists(target) && isNonEmptyDirectory(target)) {
                return;
            }

            Path pluginsDirectory = target.getParent();
            if (pluginsDirectory == null) {
                return;
            }

            for (String folderName : LEGACY_DATA_FOLDERS) {
                Path source = pluginsDirectory.resolve(folderName);
                if (!Files.isDirectory(source) || source.equals(target)) {
                    continue;
                }

                copyDirectory(source, target);
                plugin.getLogger().info("Copied existing " + folderName + " data into RealFactions data folder.");
                return;
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("Unable to migrate existing Factions data into RealFactions: " + exception.getMessage());
        }
    }

    private static boolean isNonEmptyDirectory(Path directory) throws IOException {
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.findFirst().isPresent();
        }
    }

    private static void copyDirectory(Path source, Path target) throws IOException {
        Files.createDirectories(target);

        try (Stream<Path> paths = Files.walk(source)) {
            for (Path path : (Iterable<Path>) paths::iterator) {
                Path destination = target.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}
