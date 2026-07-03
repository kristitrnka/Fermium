package org.taumc.fermium.shaders.iris.preprocessor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.taumc.fermium.shaders.backend.pack.FermiumShaderPack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FermiumShaderPreprocessor {
    private static final Logger LOGGER = LogManager.getLogger("Fermium/Preprocessor");

    private static final Pattern INCLUDE_PATTERN = Pattern.compile("^\\s*#include\\s+([\\\"<])([^\\\">]+)[\\\">].*$");
    private static final int MAX_INCLUDE_DEPTH = 32;
    private static int resolvedIncludes;

    private FermiumShaderPreprocessor() {
    }

    public static String preprocess(FermiumShaderPack pack, String path, String source) throws IOException {
        Set<String> includeStack = new HashSet<String>();
        resolvedIncludes = 0;
        String result = preprocessInternal(pack, normalizePath(path), source, includeStack, 0);
        result = moveExtensionsAfterVersion(result);
        LOGGER.info("Preprocessed shader source: {} -> {} chars, {} includes", path, result.length(), resolvedIncludes);
        return result;
    }

    private static String preprocessInternal(FermiumShaderPack pack, String currentPath, String source, Set<String> includeStack, int depth) throws IOException {
        if (source == null) {
            return "";
        }

        if (depth > MAX_INCLUDE_DEPTH) {
            LOGGER.warn("Max include depth reached at {}", currentPath);
            return "";
        }

        StringBuilder out = new StringBuilder(source.length() + 1024);
        String[] lines = source.split("\\r?\\n", -1);

        for (String line : lines) {
            Matcher matcher = INCLUDE_PATTERN.matcher(line);

            if (!matcher.matches()) {
                out.append(line).append('\n');
                continue;
            }

            String include = matcher.group(2);
            String includePath = resolveIncludePath(currentPath, include);

            if (includeStack.contains(includePath)) {
                LOGGER.warn("Skipping recursive include {} from {}", includePath, currentPath);
                out.append("// Fermium skipped recursive include: ").append(includePath).append('\n');
                continue;
            }

            String includeSource = pack.readShaderFile(includePath);

            if (includeSource == null) {
                LOGGER.warn("Missing include {} resolved as {} from {}", include, includePath, currentPath);
                out.append("// Fermium missing include: ").append(includePath).append('\n');
                continue;
            }

            resolvedIncludes++;

            includeStack.add(includePath);
            String processedInclude = preprocessInternal(pack, includePath, includeSource, includeStack, depth + 1);
            includeStack.remove(includePath);

            out.append("// Fermium begin include: ").append(includePath).append('\n');
            out.append(stripVersionLines(processedInclude));
            out.append("// Fermium end include: ").append(includePath).append('\n');
        }

        return out.toString();
    }

    private static String resolveIncludePath(String currentPath, String include) {
        include = include.replace('\\', '/');

        boolean absolute = include.startsWith("/");

        if (absolute) {
            include = include.substring(1);
        }

        if (include.startsWith("shaders/")) {
            include = include.substring("shaders/".length());
        }

        if (absolute) {
            return include;
        }

        if (include.startsWith("lib/") || include.startsWith("program/") || include.startsWith("world0/") || include.startsWith("world1/") || include.startsWith("world-1/")) {
            return include;
        }

        String normalizedCurrent = normalizePath(currentPath);
        int slash = normalizedCurrent.lastIndexOf('/');

        if (slash >= 0) {
            return normalizedCurrent.substring(0, slash + 1) + include;
        }

        return include;
    }

    private static String normalizePath(String path) {
        path = path.replace('\\', '/');

        if (path.startsWith("shaders/")) {
            path = path.substring("shaders/".length());
        }

        if (path.startsWith("/")) {
            path = path.substring(1);
        }

        return path;
    }

    private static String stripVersionLines(String source) {
        StringBuilder out = new StringBuilder(source.length());
        String[] lines = source.split("\\r?\\n", -1);

        for (String line : lines) {
            if (line.trim().startsWith("#version")) {
                out.append("// Fermium stripped include version: ").append(line).append('\n');
                continue;
            }

            out.append(line).append('\n');
        }

        return out.toString();
    }

    private static String moveExtensionsAfterVersion(String source) {
        String[] lines = source.split("\\r?\\n", -1);

        String versionLine = null;
        List<String> extensions = new ArrayList<String>();
        List<String> rest = new ArrayList<String>();

        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.startsWith("#version") && versionLine == null) {
                versionLine = line;
                continue;
            }

            if (trimmed.startsWith("#extension")) {
                if (!extensions.contains(line)) {
                    extensions.add(line);
                }
                continue;
            }

            rest.add(line);
        }

        StringBuilder out = new StringBuilder(source.length() + 256);

        if (versionLine != null) {
            out.append(versionLine).append('\n');
        }

        for (String extension : extensions) {
            out.append(extension).append('\n');
        }

        for (String line : rest) {
            out.append(line).append('\n');
        }

        return out.toString();
    }
}
