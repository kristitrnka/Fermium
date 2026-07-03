package org.taumc.fermium.shaders.discovery;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class FermiumShaderpackDirectoryManager {
    private final File directory;

    public FermiumShaderpackDirectoryManager(File directory) {
        this.directory = directory;
        ensureDirectoryExists();
    }

    public File getDirectory() {
        return this.directory;
    }

    public void ensureDirectoryExists() {
        if (!this.directory.exists()) {
            this.directory.mkdirs();
        }
    }

    public List<ShaderpackInfo> scanShaderpacks() {
        ensureDirectoryExists();

        List<ShaderpackInfo> packs = new ArrayList<>();
        packs.add(ShaderpackInfo.off());

        File[] files = this.directory.listFiles();

        if (files == null) {
            return packs;
        }

        List<File> validFiles = new ArrayList<>();

        for (File file : files) {
            if (isShaderpack(file)) {
                validFiles.add(file);
            }
        }

        validFiles.sort(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));

        for (File file : validFiles) {
            packs.add(new ShaderpackInfo(file.getName(), file.getName(), file));
        }

        return packs;
    }

    private static boolean isShaderpack(File file) {
        if (file == null) {
            return false;
        }

        if (file.isDirectory()) {
            File shaders = new File(file, "shaders");
            return shaders.exists() && shaders.isDirectory();
        }

        return file.isFile() && file.getName().toLowerCase().endsWith(".zip");
    }

    public static final class ShaderpackInfo {
        private final String displayName;
        private final String configName;
        private final File file;

        private ShaderpackInfo(String displayName, String configName, File file) {
            this.displayName = displayName;
            this.configName = configName;
            this.file = file;
        }

        public static ShaderpackInfo off() {
            return new ShaderpackInfo("OFF", "", null);
        }

        public String getDisplayName() {
            return this.displayName;
        }

        public String getConfigName() {
            return this.configName;
        }

        public File getFile() {
            return this.file;
        }

        public boolean isOff() {
            return this.configName == null || this.configName.isEmpty();
        }
    }
}
