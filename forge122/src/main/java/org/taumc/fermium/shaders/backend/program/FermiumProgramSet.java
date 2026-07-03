package org.taumc.fermium.shaders.backend.program;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Set;
import java.util.TreeSet;

public final class FermiumProgramSet {
    private static final Logger LOGGER = LogManager.getLogger("Fermium/ProgramSet");

    private final Set<String> programs = new TreeSet<>();

    public FermiumProgramSet(Set<String> discoveredPrograms) {
        if (discoveredPrograms != null) {
            this.programs.addAll(discoveredPrograms);
        }
    }

    public boolean hasExact(String program) {
        return this.programs.contains(program);
    }

    public boolean hasProgram(String program) {
        if (this.programs.contains(program)) {
            return true;
        }

        for (String found : this.programs) {
            if (found.equals(program) || found.endsWith("/" + program)) {
                return true;
            }
        }

        return false;
    }

    public void logSummary() {
        LOGGER.info("ProgramSet summary:");
        LOGGER.info(" - total programs: {}", this.programs.size());
        LOGGER.info(" - gbuffers_basic: {}", hasProgram("gbuffers_basic"));
        LOGGER.info(" - gbuffers_textured: {}", hasProgram("gbuffers_textured"));
        LOGGER.info(" - gbuffers_terrain: {}", hasProgram("gbuffers_terrain"));
        LOGGER.info(" - gbuffers_block: {}", hasProgram("gbuffers_block"));
        LOGGER.info(" - gbuffers_water: {}", hasProgram("gbuffers_water"));
        LOGGER.info(" - shadow: {}", hasProgram("shadow"));
        LOGGER.info(" - composite: {}", hasProgram("composite"));
        LOGGER.info(" - final: {}", hasProgram("final"));
    }

    public Set<String> getPrograms() {
        return this.programs;
    }
}
