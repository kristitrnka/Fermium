package org.taumc.fermium.shaders.backend.pack;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.taumc.fermium.shaders.backend.program.FermiumProgramSet;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class FermiumShaderPack {
    private static final Logger LOGGER = LogManager.getLogger("Fermium/ShaderPack");

    private final File file;
    private final boolean zip;

    private boolean valid;
    private boolean hasProperties;
    private boolean hasTerrain;
    private boolean hasComposite;
    private boolean hasFinal;

    private final Set<String> discoveredPrograms = new TreeSet<>();

    private Properties properties = new Properties();

    public FermiumShaderPack(File file) {
        this.file = file;
        this.zip = file.isFile() && file.getName().toLowerCase().endsWith(".zip");
    }

    public void scan() {
        this.valid = false;
        this.hasProperties = false;
        this.hasTerrain = false;
        this.hasComposite = false;
        this.hasFinal = false;
        this.discoveredPrograms.clear();
        this.properties = new Properties();

        if (!this.file.exists()) {
            LOGGER.warn("Shaderpack does not exist: {}", this.file.getAbsolutePath());
            return;
        }

        try {
            if (this.zip) {
                scanZip();
            } else {
                scanFolder();
            }
        } catch (IOException e) {
            LOGGER.error("Failed to scan shaderpack {}", this.file.getName(), e);
        }

        LOGGER.info("Shaderpack scan: {}", this.file.getName());
        LOGGER.info(" - valid: {}", this.valid);
        LOGGER.info(" - shaders.properties: {}", this.hasProperties);
        LOGGER.info(" - gbuffers_terrain: {}", this.hasTerrain);
        LOGGER.info(" - composite: {}", this.hasComposite);
        LOGGER.info(" - final: {}", this.hasFinal);
        LOGGER.info(" - discovered programs: {}", this.discoveredPrograms.size());

        for (String program : this.discoveredPrograms) {
            LOGGER.info("   * {}", program);
        }

        createProgramSet().logSummary();
    }

    private void scanZip() throws IOException {
        try (ZipFile zipFile = new ZipFile(this.file)) {
            String root = findShadersRoot(zipFile);

            if (root == null) {
                LOGGER.warn("No shaders/ folder found in {}", this.file.getName());
                return;
            }

            this.valid = true;

            this.hasProperties = zipFile.getEntry(root + "shaders.properties") != null;

            zipFile.stream()
                    .map(ZipEntry::getName)
                    .filter(name -> name.startsWith(root))
                    .filter(name -> name.endsWith(".vsh") || name.endsWith(".fsh"))
                    .forEach(name -> addDiscoveredProgram(root, name));

            this.hasTerrain = hasProgramAnywhere("gbuffers_terrain");
            this.hasComposite = hasProgramAnywhere("composite") || hasProgramAnywhere("composite0");
            this.hasFinal = hasProgramAnywhere("final");

            ZipEntry propertiesEntry = zipFile.getEntry(root + "shaders.properties");
            if (propertiesEntry != null) {
                try (InputStream input = zipFile.getInputStream(propertiesEntry)) {
                    this.properties.load(input);
                }
            }
        }
    }

    private String findShadersRoot(ZipFile zipFile) {
        if (zipFile.getEntry("shaders/") != null || zipFile.getEntry("shaders/shaders.properties") != null) {
            return "shaders/";
        }

        return zipFile.stream()
                .map(ZipEntry::getName)
                .filter(name -> name.endsWith("/shaders/") || name.endsWith("/shaders/shaders.properties"))
                .map(name -> {
                    int index = name.indexOf("shaders/");
                    return index >= 0 ? name.substring(0, index + "shaders/".length()) : null;
                })
                .filter(name -> name != null)
                .findFirst()
                .orElse(null);
    }

    private void scanFolder() {
        File shaders = new File(this.file, "shaders");

        if (!shaders.exists() || !shaders.isDirectory()) {
            LOGGER.warn("No shaders/ folder found in {}", this.file.getName());
            return;
        }

        this.valid = true;

        File propertiesFile = new File(shaders, "shaders.properties");
        this.hasProperties = propertiesFile.exists();

        discoverFolderPrograms(shaders, shaders);

        this.hasTerrain = hasProgramAnywhere("gbuffers_terrain");
        this.hasComposite = hasProgramAnywhere("composite") || hasProgramAnywhere("composite0");
        this.hasFinal = hasProgramAnywhere("final");

        if (propertiesFile.exists()) {
            try (InputStream input = new java.io.FileInputStream(propertiesFile)) {
                this.properties.load(input);
            } catch (IOException e) {
                LOGGER.warn("Failed to read shaders.properties", e);
            }
        }
    }


    private void discoverFolderPrograms(File root, File current) {
        File[] files = current.listFiles();

        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                discoverFolderPrograms(root, file);
                continue;
            }

            String name = file.getName();

            if (name.endsWith(".vsh") || name.endsWith(".fsh")) {
                String relative = root.toURI().relativize(file.toURI()).getPath();
                addDiscoveredProgram("", relative);
            }
        }
    }

    private void addDiscoveredProgram(String root, String path) {
        String relative = path;

        if (root != null && !root.isEmpty() && relative.startsWith(root)) {
            relative = relative.substring(root.length());
        }

        if (!relative.endsWith(".vsh") && !relative.endsWith(".fsh")) {
            return;
        }

        int dot = relative.lastIndexOf('.');
        if (dot > 0) {
            relative = relative.substring(0, dot);
        }

        if (relative.startsWith("/")) {
            relative = relative.substring(1);
        }

        if (!relative.isEmpty()) {
            this.discoveredPrograms.add(relative);
        }
    }

    private boolean hasProgram(String name) {
        return this.discoveredPrograms.contains(name);
    }

    private boolean hasProgramAnywhere(String name) {
        if (this.discoveredPrograms.contains(name)) {
            return true;
        }

        for (String program : this.discoveredPrograms) {
            if (program.equals(name) || program.endsWith("/" + name)) {
                return true;
            }
        }

        return false;
    }

    public FermiumProgramSet createProgramSet() {
        return new FermiumProgramSet(this.discoveredPrograms);
    }

    private static boolean hasAny(ZipFile zipFile, String... paths) {
        for (String path : paths) {
            if (zipFile.getEntry(path) != null) {
                return true;
            }
        }

        return false;
    }

    public boolean isValid() {
        return this.valid;
    }

    public Properties getProperties() {
        return this.properties;
    }

    public FermiumShaderSource loadShaderSource(String program, String extension) throws IOException {
        if (extension == null) {
            extension = "";
        }

        if (!extension.startsWith(".")) {
            extension = "." + extension;
        }

        String path = resolveShaderPath(program, extension);

        if (path == null) {
            return null;
        }

        String source;

        if (this.zip) {
            source = readZipText(path);
        } else {
            source = readFolderText(path);
        }

        if (source == null) {
            return null;
        }

        return new FermiumShaderSource(path, source);
    }

    private String resolveShaderPath(String program, String extension) {
        String[] preferred = new String[] {
                program,
                "world0/" + program,
                "world1/" + program,
                "world-1/" + program
        };

        for (String candidate : preferred) {
            if (this.discoveredPrograms.contains(candidate)) {
                return "shaders/" + candidate + extension;
            }
        }

        for (String found : this.discoveredPrograms) {
            if (found.equals(program) || found.endsWith("/" + program)) {
                return "shaders/" + found + extension;
            }
        }

        return null;
    }

    public String readShaderFile(String relativePath) throws IOException {
        if (relativePath == null) {
            return null;
        }

        relativePath = relativePath.replace('\\', '/');

        if (relativePath.startsWith("/")) {
            relativePath = relativePath.substring(1);
        }

        if (relativePath.startsWith("shaders/")) {
            relativePath = relativePath.substring("shaders/".length());
        }

        String path = "shaders/" + relativePath;

        if (this.zip) {
            return readZipText(path);
        }

        return readFolderText(path);
    }

    private String readZipText(String path) throws IOException {
        try (ZipFile zipFile = new ZipFile(this.file)) {
            String root = findShadersRoot(zipFile);

            if (root == null) {
                return null;
            }

            String relative = path;

            if (relative.startsWith("shaders/")) {
                relative = relative.substring("shaders/".length());
            }

            ZipEntry entry = zipFile.getEntry(root + relative);

            if (entry == null) {
                return null;
            }

            try (InputStream input = zipFile.getInputStream(entry)) {
                return readAllUtf8(input);
            }
        }
    }

    private String readFolderText(String path) throws IOException {
        File sourceFile = new File(this.file, path);

        if (!sourceFile.exists()) {
            return null;
        }

        try (InputStream input = new java.io.FileInputStream(sourceFile)) {
            return readAllUtf8(input);
        }
    }

    private static String readAllUtf8(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];

        int read;

        while ((read = input.read(buffer)) >= 0) {
            output.write(buffer, 0, read);
        }

        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }
}
