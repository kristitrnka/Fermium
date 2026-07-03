package org.taumc.fermium.shaders.iris.pipeline;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.taumc.fermium.shaders.iris.program.ShaderProgramSource;

public final class FermiumShaderPipeline {
    private static final Logger LOGGER = LogManager.getLogger("Fermium/Pipeline");

    private static FermiumShaderPipeline activePipeline;

    private final ShaderProgramSource terrainSource;

    public FermiumShaderPipeline(ShaderProgramSource terrainSource) {
        this.terrainSource = terrainSource;
    }

    public boolean init() {
        if (this.terrainSource == null || !this.terrainSource.isComplete()) {
            LOGGER.warn("Shader pipeline init failed: missing terrain shader source");
            return false;
        }

        activePipeline = this;

        LOGGER.info("Fermium shader source pipeline initialized. Terrain program source={}", this.terrainSource.getName());
        LOGGER.info("Fermium is not binding its own terrain GL program; Celeritas remains the renderer.");

        return true;
    }

    public void destroy() {
        if (activePipeline == this) {
            activePipeline = null;
        }

        LOGGER.info("Fermium shader source pipeline destroyed");
    }

    public static boolean isActive() {
        return activePipeline != null && activePipeline.terrainSource != null && activePipeline.terrainSource.isComplete();
    }

    public static ShaderProgramSource getTerrainSource() {
        return isActive() ? activePipeline.terrainSource : null;
    }

    public static void bindTerrain() {
        // No-op.
        // Fermium must not bind a custom terrain GL program.
        // Celeritas owns chunk rendering.
    }

    public static void setMatrices(float[] modelView, float[] projection) {
        // No-op.
        // Matrices are uploaded by Celeritas to its own shader interface.
    }

    public static void setRegionOffset(float x, float y, float z) {
        // No-op.
        // Region offset is uploaded by Celeritas.
    }

    public static void logAfterDrawProgram(int currentProgram) {
        // No-op.
    }

    public static void unbind() {
        // No-op.
        // Do not call glUseProgram(0) here.
    }
}
