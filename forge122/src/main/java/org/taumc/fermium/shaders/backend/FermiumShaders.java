package org.taumc.fermium.shaders.backend;

import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.taumc.fermium.shaders.backend.pack.FermiumShaderPack;
import org.taumc.fermium.shaders.backend.pack.FermiumShaderSource;
import org.taumc.fermium.shaders.backend.gl.FermiumFramebufferSet;
import org.taumc.fermium.shaders.iris.pipeline.FermiumShaderPipeline;
import org.taumc.fermium.shaders.iris.preprocessor.FermiumShaderPreprocessor;
import org.taumc.fermium.shaders.iris.program.ShaderProgramSource;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FermiumShaders {
    private static final Logger LOGGER = LogManager.getLogger("Fermium/Shaders");

    private static final Pattern DRAWBUFFERS_PATTERN = Pattern.compile("/\\*\\s*DRAWBUFFERS\\s*:\\s*([^*]+)\\*/");

    private static FermiumShaderPipeline pipeline;
    private static ShaderProgramSource selectedTerrainSource;
    private static FermiumFramebufferSet framebufferSet;

    private static String activeShaderPack = "";
    private static boolean shadersEnabled = false;
    private static boolean terrainBridgeLogged = false;
    private static int terrainPreviewColorTexture = 0;

    private FermiumShaders() {
    }

    public static void applyShaderPack(String shaderPackName) {
        if (shaderPackName == null) {
            shaderPackName = "";
        }

        activeShaderPack = shaderPackName;
        shadersEnabled = !shaderPackName.isEmpty();
        selectedTerrainSource = null;
        terrainBridgeLogged = false;

        if (!shadersEnabled) {
            LOGGER.info("Shaders disabled");
            destroyPipeline();
            return;
        }

        File shaderpacksDir = new File(System.getProperty("user.dir"), "shaderpacks");
        File shaderpack = new File(shaderpacksDir, shaderPackName);

        if (!shaderpack.exists()) {
            LOGGER.warn("Selected shaderpack does not exist: {}", shaderpack.getAbsolutePath());
            shadersEnabled = false;
            activeShaderPack = "";
            selectedTerrainSource = null;
            destroyPipeline();
            return;
        }

        LOGGER.info("Selected shaderpack: {}", shaderpack.getName());

        FermiumShaderPack pack = new FermiumShaderPack(shaderpack);
        pack.scan();

        if (!pack.isValid()) {
            LOGGER.warn("Shaderpack is not valid, disabling shaders");
            shadersEnabled = false;
            activeShaderPack = "";
            selectedTerrainSource = null;
            destroyPipeline();
            return;
        }

        ShaderProgramSource selectedSource = debugLoadShaderSources(pack);
        selectedTerrainSource = selectedSource;

        destroyPipeline();

        if (selectedSource != null && selectedSource.isComplete()) {
            pipeline = new FermiumShaderPipeline(selectedSource);
            pipeline.init();
            createFramebufferBackend(selectedSource);
        } else {
            LOGGER.warn("No complete selected shader source for shader pipeline");
        }

        reloadPipeline(shaderpack);
    }

    private static ShaderProgramSource debugLoadShaderSources(FermiumShaderPack pack) {
        String[] candidates = new String[] {
                "gbuffers_terrain",
                "gbuffers_block",
                "gbuffers_textured",
                "gbuffers_basic",
                "gbuffers_water",
                "composite",
                "final"
        };

        for (String program : candidates) {
            try {
                FermiumShaderSource vertex = pack.loadShaderSource(program, "vsh");
                FermiumShaderSource fragment = pack.loadShaderSource(program, "fsh");

                if (vertex == null && fragment == null) {
                    continue;
                }

                LOGGER.info("Shader source program: {}", program);

                if (vertex != null) {
                    LOGGER.info(" - vertex path: {}", vertex.getPath());
                    LOGGER.info(" - vertex version: {}", vertex.detectVersion());
                    LOGGER.info(" - vertex includes: {}", vertex.countIncludes());
                    LOGGER.info(" - vertex first lines:\\n{}", vertex.firstLines(5));
                }

                if (fragment != null) {
                    LOGGER.info(" - fragment path: {}", fragment.getPath());
                    LOGGER.info(" - fragment version: {}", fragment.detectVersion());
                    LOGGER.info(" - fragment includes: {}", fragment.countIncludes());
                    LOGGER.info(" - fragment first lines:\\n{}", fragment.firstLines(5));
                }

                if (vertex != null && fragment != null) {
                    String processedVertex = FermiumShaderPreprocessor.preprocess(pack, vertex.getPath(), vertex.getSource());
                    String processedFragment = FermiumShaderPreprocessor.preprocess(pack, fragment.getPath(), fragment.getSource());

                    return new ShaderProgramSource(
                            program,
                            vertex.getPath(),
                            fragment.getPath(),
                            processedVertex,
                            processedFragment
                    );
                }

                return null;
            } catch (Exception e) {
                LOGGER.warn("Failed to debug-load shader source for {}", program, e);
            }
        }

        LOGGER.warn("No candidate shader source could be loaded");
        return null;
    }

    public static String transformTerrainFragment(String celeritasPath, String celeritasSource) {
        if (!shadersEnabled || selectedTerrainSource == null || celeritasSource == null) {
            return celeritasSource;
        }

        String fragment = selectedTerrainSource.getFragmentSource();
        String drawBuffers = detectDrawBuffers(fragment);

        if (!terrainBridgeLogged) {
            terrainBridgeLogged = true;

            LOGGER.warn(
                    "Terrain shaderpack bridge active. pack={}, program={}, fragment={}, drawbuffers={}, celeritasPath={}, mode=source-transform-ready-no-fake-effect",
                    activeShaderPack,
                    selectedTerrainSource.getName(),
                    selectedTerrainSource.getFragmentPath(),
                    drawBuffers,
                    celeritasPath
            );

            if (drawBuffers != null && drawBuffers.length() > 1) {
                LOGGER.warn("Shaderpack requests multiple render targets via DRAWBUFFERS:{}; MRT/colortex backend is next step.", drawBuffers);
            }
        }

        return celeritasSource;
    }


    private static int firstDrawBufferIndex(String drawBuffers) {
        if (drawBuffers == null || drawBuffers.equals("none") || drawBuffers.isEmpty()) {
            return 0;
        }

        for (int i = 0; i < drawBuffers.length(); i++) {
            char c = drawBuffers.charAt(i);

            if (c >= '0' && c <= '7') {
                return c - '0';
            }
        }

        return 0;
    }

    private static String detectDrawBuffers(String source) {
        if (source == null) {
            return "none";
        }

        Matcher matcher = DRAWBUFFERS_PATTERN.matcher(source);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        return "none";
    }


    public static void beginTerrainFramebuffer() {
        if (!shadersEnabled || framebufferSet == null || !framebufferSet.isCreated()) {
            return;
        }

        framebufferSet.bindForTerrain();
    }

    public static void endTerrainFramebuffer() {
        if (!shadersEnabled || framebufferSet == null || !framebufferSet.isCreated()) {
            return;
        }

        framebufferSet.unbindToDefault();

        // Debug final pass: copy Celeritas terrain colortex back to the default framebuffer.
        framebufferSet.copyColorToDefaultFramebuffer(terrainPreviewColorTexture);
    }

    public static boolean areShadersEnabled() {
        return shadersEnabled;
    }

    public static String getActiveShaderPack() {
        return activeShaderPack;
    }

    public static File getShaderpacksDirectory() {
        return new File(System.getProperty("user.dir"), "shaderpacks");
    }

    private static void reloadPipeline(File shaderpack) {
        Minecraft mc = Minecraft.getMinecraft();

        LOGGER.info("Preparing Fermium shader pipeline for {}", shaderpack.getName());

        if (mc.world == null) {
            LOGGER.info("No world loaded yet; shader pipeline will be initialized later");
            return;
        }

        LOGGER.warn("Shader backend is not ported yet. UI/config layer is working.");
    }

    private static void destroyPipeline() {
        if (framebufferSet != null) {
            framebufferSet.destroy();
            framebufferSet = null;
            LOGGER.info("Destroyed Fermium framebuffer backend");
        }

        if (pipeline != null) {
            pipeline.destroy();
            pipeline = null;
        }

        LOGGER.info("Destroyed Fermium shader pipeline placeholder");
    }

    private static void createFramebufferBackend(ShaderProgramSource source) {
        Minecraft mc = Minecraft.getMinecraft();

        if (mc == null) {
            LOGGER.warn("Cannot create Fermium framebuffer backend: Minecraft instance is null");
            return;
        }

        String drawBuffers = detectDrawBuffers(source.getFragmentSource());
        terrainPreviewColorTexture = 0;

        LOGGER.info("Fermium terrain preview output will copy colortex0; shaderpack requested DRAWBUFFERS:{} for later", drawBuffers);

        framebufferSet = new FermiumFramebufferSet();

        boolean ok = framebufferSet.create(
                Math.max(1, mc.displayWidth),
                Math.max(1, mc.displayHeight),
                drawBuffers
        );

        if (ok) {
            LOGGER.info("Fermium framebuffer backend ready for shaderpack program {} with DRAWBUFFERS:{}", source.getName(), drawBuffers);
        } else {
            LOGGER.warn("Fermium framebuffer backend failed to initialize");
            framebufferSet = null;
        }
    }
}
