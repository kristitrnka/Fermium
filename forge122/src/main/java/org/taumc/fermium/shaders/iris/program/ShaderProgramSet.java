package org.taumc.fermium.shaders.iris.program;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class ShaderProgramSet {
    private final Map<String, ShaderProgramSource> programs = new LinkedHashMap<>();

    public void add(ShaderProgramSource source) {
        if (source != null) {
            programs.put(source.getName(), source);
        }
    }

    public ShaderProgramSource get(String name) {
        return programs.get(name);
    }

    public boolean has(String name) {
        return programs.containsKey(name);
    }

    public int size() {
        return programs.size();
    }

    public Collection<ShaderProgramSource> all() {
        return programs.values();
    }

    public ShaderProgramSource getTerrainFallback() {
        if (has("gbuffers_terrain")) {
            return get("gbuffers_terrain");
        }

        if (has("gbuffers_block")) {
            return get("gbuffers_block");
        }

        if (has("gbuffers_textured")) {
            return get("gbuffers_textured");
        }

        if (has("gbuffers_basic")) {
            return get("gbuffers_basic");
        }

        return null;
    }
}
