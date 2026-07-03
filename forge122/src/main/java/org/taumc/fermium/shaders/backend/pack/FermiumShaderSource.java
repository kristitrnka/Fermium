package org.taumc.fermium.shaders.backend.pack;

public final class FermiumShaderSource {
    private final String path;
    private final String source;

    public FermiumShaderSource(String path, String source) {
        this.path = path;
        this.source = source;
    }

    public String getPath() {
        return this.path;
    }

    public String getSource() {
        return this.source;
    }

    public String detectVersion() {
        String[] lines = this.source.split("\\r?\\n");

        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.startsWith("#version")) {
                return trimmed;
            }
        }

        return "no #version";
    }

    public int countIncludes() {
        int count = 0;
        String[] lines = this.source.split("\\r?\\n");

        for (String line : lines) {
            if (line.trim().startsWith("#include")) {
                count++;
            }
        }

        return count;
    }

    public String firstLines(int maxLines) {
        String[] lines = this.source.split("\\r?\\n");
        StringBuilder builder = new StringBuilder();

        int count = Math.min(maxLines, lines.length);

        for (int i = 0; i < count; i++) {
            builder.append(i + 1).append(": ").append(lines[i]).append("\\n");
        }

        return builder.toString();
    }
}
