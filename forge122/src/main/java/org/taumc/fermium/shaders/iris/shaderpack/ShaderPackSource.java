package org.taumc.fermium.shaders.iris.shaderpack;

import org.taumc.fermium.shaders.iris.program.ShaderProgramSet;
import org.taumc.fermium.shaders.iris.program.ShaderProgramSource;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ShaderPackSource {
    private final File file;

    public ShaderPackSource(File file) {
        this.file = file;
    }

    public ShaderProgramSet loadProgramSet() throws IOException {
        ShaderProgramSet set = new ShaderProgramSet();

        if (file == null || !file.exists()) {
            return set;
        }

        if (file.isFile() && file.getName().toLowerCase().endsWith(".zip")) {
            loadZip(set);
        }

        return set;
    }

    private void loadZip(ShaderProgramSet set) throws IOException {
        try (ZipFile zip = new ZipFile(file)) {
            Set<String> names = discoverProgramNames(zip);

            for (String name : names) {
                String vertexPath = "shaders/" + name + ".vsh";
                String fragmentPath = "shaders/" + name + ".fsh";

                String vertex = readZipString(zip, vertexPath);
                String fragment = readZipString(zip, fragmentPath);

                if (vertex == null || fragment == null) {
                    continue;
                }

                set.add(new ShaderProgramSource(name, vertexPath, fragmentPath, vertex, fragment));
            }
        }
    }

    private Set<String> discoverProgramNames(ZipFile zip) {
        Set<String> names = new LinkedHashSet<>();

        zip.stream().forEach(entry -> {
            String name = entry.getName();

            if (!name.startsWith("shaders/")) {
                return;
            }

            if (!name.endsWith(".vsh") && !name.endsWith(".fsh")) {
                return;
            }

            String programName = name.substring("shaders/".length());
            programName = programName.substring(0, programName.length() - 4);

            if (!programName.contains("/")) {
                names.add(programName);
            }
        });

        return names;
    }

    private String readZipString(ZipFile zip, String path) throws IOException {
        ZipEntry entry = zip.getEntry(path);
        if (entry == null) {
            return null;
        }

        try (InputStream in = zip.getInputStream(entry)) {
            return readString(in);
        }
    }

    private static String readString(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];

        int read;
        while ((read = in.read(buffer)) >= 0) {
            out.write(buffer, 0, read);
        }

        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}
