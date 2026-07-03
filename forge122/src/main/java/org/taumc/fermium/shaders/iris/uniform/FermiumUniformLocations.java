package org.taumc.fermium.shaders.iris.uniform;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL20;

public final class FermiumUniformLocations {
    private static final Logger LOGGER = LogManager.getLogger("Fermium/Uniforms");

    public final int texture;
    public final int lightmap;

    public final int gbufferProjection;
    public final int gbufferProjectionInverse;
    public final int gbufferPreviousProjection;

    public final int gbufferModelView;
    public final int gbufferModelViewInverse;
    public final int gbufferPreviousModelView;

    public final int shadowProjection;
    public final int shadowModelView;

    public final int cameraPosition;
    public final int previousCameraPosition;

    public final int worldTime;
    public final int frameTimeCounter;
    public final int frameCounter;

    public final int rainStrength;
    public final int wetness;
    public final int eyeBrightness;
    public final int eyeBrightnessSmooth;
    public final int isEyeInWater;

    public final int heldItemId;
    public final int heldBlockLightValue;

    private FermiumUniformLocations(int program) {
        this.texture = uniform(program, "texture");
        this.lightmap = uniform(program, "lightmap");

        this.gbufferProjection = uniform(program, "gbufferProjection");
        this.gbufferProjectionInverse = uniform(program, "gbufferProjectionInverse");
        this.gbufferPreviousProjection = uniform(program, "gbufferPreviousProjection");

        this.gbufferModelView = uniform(program, "gbufferModelView");
        this.gbufferModelViewInverse = uniform(program, "gbufferModelViewInverse");
        this.gbufferPreviousModelView = uniform(program, "gbufferPreviousModelView");

        this.shadowProjection = uniform(program, "shadowProjection");
        this.shadowModelView = uniform(program, "shadowModelView");

        this.cameraPosition = uniform(program, "cameraPosition");
        this.previousCameraPosition = uniform(program, "previousCameraPosition");

        this.worldTime = uniform(program, "worldTime");
        this.frameTimeCounter = uniform(program, "frameTimeCounter");
        this.frameCounter = uniform(program, "frameCounter");

        this.rainStrength = uniform(program, "rainStrength");
        this.wetness = uniform(program, "wetness");
        this.eyeBrightness = uniform(program, "eyeBrightness");
        this.eyeBrightnessSmooth = uniform(program, "eyeBrightnessSmooth");
        this.isEyeInWater = uniform(program, "isEyeInWater");

        this.heldItemId = uniform(program, "heldItemId");
        this.heldBlockLightValue = uniform(program, "heldBlockLightValue");
    }

    public static FermiumUniformLocations create(int program) {
        FermiumUniformLocations locations = new FermiumUniformLocations(program);
        locations.logSummary(program);
        return locations;
    }

    private static int uniform(int program, String name) {
        return GL20.glGetUniformLocation(program, name);
    }

    private void logSummary(int program) {
        LOGGER.info("Uniform locations for GL program {}", program);
        log("texture", this.texture);
        log("lightmap", this.lightmap);
        log("gbufferProjection", this.gbufferProjection);
        log("gbufferProjectionInverse", this.gbufferProjectionInverse);
        log("gbufferModelView", this.gbufferModelView);
        log("gbufferModelViewInverse", this.gbufferModelViewInverse);
        log("cameraPosition", this.cameraPosition);
        log("worldTime", this.worldTime);
        log("frameTimeCounter", this.frameTimeCounter);
        log("rainStrength", this.rainStrength);
        log("eyeBrightness", this.eyeBrightness);
        log("isEyeInWater", this.isEyeInWater);
        log("heldItemId", this.heldItemId);
        log("heldBlockLightValue", this.heldBlockLightValue);
    }

    private static void log(String name, int location) {
        if (location >= 0) {
            LOGGER.info(" - {} = {}", name, location);
        }
    }
}
