package org.embeddedt.embeddium.impl.render.chunk;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.gl.device.RenderDevice;
import org.embeddedt.embeddium.impl.gl.shader.*;
import org.embeddedt.embeddium.impl.render.chunk.shader.*;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.shader.ShaderLoader;
import org.jetbrains.annotations.Nullable;
import org.taumc.fermium.shaders.bridge.FermiumChunkRenderBridge;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import static org.taumc.celeritas.lwjgl.LWJGLServiceProvider.LWJGL;

public abstract class ShaderChunkRenderer implements ChunkRenderer {
    private static final Logger LOGGER = LogManager.getLogger(ShaderChunkRenderer.class);

    private final Map<ChunkShaderOptions, @Nullable GlProgram<ChunkShaderInterface>> programs = new Object2ObjectOpenHashMap<>();

    protected final RenderPassConfiguration<?> renderPassConfiguration;
    protected final RenderDevice device;
    protected GlProgram<ChunkShaderInterface> activeProgram;
    protected final boolean enableLegacyGLPatches;

    private boolean lastFermiumShaderActive;
    private boolean fermiumFramebufferActiveForPass;
    private int lastFermiumRevision;

    public ShaderChunkRenderer(RenderDevice device, RenderPassConfiguration<?> renderPassConfiguration) {
        this.device = device;
        this.renderPassConfiguration = renderPassConfiguration;
        this.enableLegacyGLPatches = !LWJGL.isOpenGLVersionSupported(3, 2);

        if (this.enableLegacyGLPatches) {
            LOGGER.warn("System does not support modern GLSL, will attempt to patch terrain shaders");
        }
    }

    protected @Nullable GlProgram<ChunkShaderInterface> compileProgram(ChunkShaderOptions options) {
        GlProgram<ChunkShaderInterface> program = this.programs.get(options);

        if (program == null && !this.programs.containsKey(options)) {
            try {
                program = this.createShader("blocks/block_layer_opaque", options);
            } catch (Exception e) {
                LOGGER.error("There was an error creating a chunk program. Terrain will not render until this is fixed.", e);
            }

            this.programs.put(options, program);
        }

        return program;
    }

    private static final Pattern VERSION_DIRECTIVE = Pattern.compile("^#version.*$", Pattern.MULTILINE);
    private static final Pattern IN_PARAM = Pattern.compile("^in ", Pattern.MULTILINE);
    private static final Pattern OUT_PARAM = Pattern.compile("^out ", Pattern.MULTILINE);

    private static final String LEGACY_PREAMBLE = String.join("\n",
            "#version 120",
            "#extension GL_EXT_gpu_shader4 : require",
            "#define LEGACY",
            "#define uint unsigned int",
            "#define texture texture2D"
    ) + "\n";

    private GlShader loadShader(ShaderType type, String path, ShaderConstants constants) {
        String shaderSource = ShaderParser.parseShader(
                ShaderLoader.getShaderSource(path),
                ShaderLoader::getShaderSource,
                constants
        );

        if (this.enableLegacyGLPatches) {
            if (type != ShaderType.VERTEX && type != ShaderType.FRAGMENT) {
                throw new IllegalStateException("Cannot load non-vertex/fragment shader on old GL");
            }

            shaderSource = VERSION_DIRECTIVE.matcher(shaderSource).replaceFirst(LEGACY_PREAMBLE);

            if (type == ShaderType.VERTEX) {
                shaderSource = IN_PARAM.matcher(shaderSource).replaceAll("attribute ");
            } else {
                shaderSource = IN_PARAM.matcher(shaderSource).replaceAll("varying ");
            }

            shaderSource = OUT_PARAM.matcher(shaderSource).replaceAll("varying ");
        }

        shaderSource = applyFermiumSourcePatch(type, path, shaderSource);

        return new GlShader(type, path, shaderSource);
    }

    private String applyFermiumSourcePatch(ShaderType type, String path, String shaderSource) {
        if (type != ShaderType.FRAGMENT) {
            return shaderSource;
        }

        if (!path.endsWith("blocks/block_layer_opaque.fsh")) {
            return shaderSource;
        }

        return FermiumChunkRenderBridge.transformTerrainFragment(path, shaderSource);
    }

    protected GlProgram<ChunkShaderInterface> createShader(String path, ChunkShaderOptions options) {
        ShaderConstants constants = options.constants();

        List<GlShader> loadedShaders = new ArrayList<>();

        loadedShaders.add(loadShader(
                ShaderType.VERTEX,
                "sodium:" + path + ".vsh",
                constants
        ));

        loadedShaders.add(loadShader(
                ShaderType.FRAGMENT,
                "sodium:" + path + ".fsh",
                constants
        ));

        try {
            var builder = GlProgram.builder("sodium:chunk_shader");

            loadedShaders.forEach(builder::attachShader);

            int i = 0;
            for (var attr : options.pass().vertexType().getVertexFormat().getAttributes()) {
                builder.bindAttribute(attr.getName(), i++);
            }

            if (!this.enableLegacyGLPatches) {
                builder.bindFragmentData("fragColor", ChunkShaderBindingPoints.FRAG_COLOR);
            }

            return builder.link((shader) -> new DefaultChunkShaderInterface(shader, options));
        } finally {
            loadedShaders.forEach(GlShader::delete);
        }
    }

    protected List<ChunkShaderComponent.Factory<?>> getShaderComponents() {
        var componentFactories = new ArrayList<ChunkShaderComponent.Factory<?>>(4);
        componentFactories.add(ChunkShaderFogComponent.FOG_SERVICE.getFogMode());
        return componentFactories;
    }

    protected void begin(TerrainRenderPass pass) {
        boolean fermiumShaderActive = FermiumChunkRenderBridge.isActive();
        int fermiumRevision = FermiumChunkRenderBridge.getRevision();

        if (fermiumShaderActive != this.lastFermiumShaderActive || fermiumRevision != this.lastFermiumRevision) {
            this.programs.values().stream()
                    .filter(Objects::nonNull)
                    .forEach(GlProgram::delete);

            this.programs.clear();
            this.lastFermiumShaderActive = fermiumShaderActive;
            this.lastFermiumRevision = fermiumRevision;

            LOGGER.warn("Fermium shader state changed active={} revision={}, clearing Celeritas chunk shader cache", fermiumShaderActive, fermiumRevision);
        }

        if (fermiumShaderActive) {
            String fermiumPassName = String.valueOf(pass).toLowerCase();
            this.fermiumFramebufferActiveForPass = fermiumPassName.contains("opaque") || fermiumPassName.contains("solid");

            if (this.fermiumFramebufferActiveForPass) {
                FermiumChunkRenderBridge.beginTerrainFramebuffer();
            }
        }

        pass.startDrawing();

        ChunkShaderOptions options = new ChunkShaderOptions(getShaderComponents(), pass);

        this.activeProgram = this.compileProgram(options);

        if (this.activeProgram != null) {
            this.activeProgram.bind();
            this.activeProgram.getInterface().setupState(pass);
        }
    }

    protected void end(TerrainRenderPass pass) {
        boolean fermiumShaderActive = FermiumChunkRenderBridge.isActive();

        if (this.activeProgram != null) {
            this.activeProgram.getInterface().restoreState();
            this.activeProgram.unbind();
            this.activeProgram = null;
        }

        pass.endDrawing();

        if (fermiumShaderActive) {
            if (this.fermiumFramebufferActiveForPass) {
                FermiumChunkRenderBridge.endTerrainFramebuffer();
                this.fermiumFramebufferActiveForPass = false;
            }
        }
    }

    @Override
    public void delete(CommandList commandList) {
        this.programs.values().stream()
                .filter(Objects::nonNull)
                .forEach(GlProgram::delete);
    }

    @Override
    public RenderPassConfiguration<?> getRenderPassConfiguration() {
        return this.renderPassConfiguration;
    }
}
