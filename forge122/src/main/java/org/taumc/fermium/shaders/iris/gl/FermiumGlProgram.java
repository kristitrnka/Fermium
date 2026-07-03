package org.taumc.fermium.shaders.iris.gl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL11;
import org.taumc.fermium.shaders.iris.program.ShaderProgramSource;

public final class FermiumGlProgram {
    private static final Logger LOGGER = LogManager.getLogger("Fermium/GLProgram");

    private FermiumGlProgram() {
    }

    public static int compileTest(ShaderProgramSource source) {
        LOGGER.info("Compiling DEBUG TEXTURED Fermium shader program: {}", source.getName());

        String vertexSource =
                "#version 120\n"
              + "attribute vec3 a_PosId;\n"
              + "attribute vec4 a_Color;\n"
              + "uniform vec3 u_RegionOffset;\n"
              + "uniform mat4 u_ModelViewMatrix;\n"
              + "uniform mat4 u_ProjectionMatrix;\n"
              + "attribute vec2 a_TexCoord;\n"
              + "varying vec4 v_Color;\n"
              + "varying vec2 v_TexCoord;\n"
              + "void main() {\n"
              + "    vec3 pos = a_PosId + u_RegionOffset;\n"
              + "    gl_Position = u_ProjectionMatrix * u_ModelViewMatrix * vec4(pos, 1.0);\n"
              + "    v_Color = a_Color;\n"
              + "    v_TexCoord = a_TexCoord;\n"
              + "}\n";

        String fragmentSource =
                "#version 120\n"
              + "uniform sampler2D texture;\n"
              + "varying vec4 v_Color;\n"
              + "varying vec2 v_TexCoord;\n"
              + "void main() {\n"
              + "    vec4 tex = texture2D(texture, v_TexCoord);\n"
              + "    gl_FragColor = tex * v_Color;\n"
              + "}\n";

        int vertexShader = 0;
        int fragmentShader = 0;
        int program = 0;

        try {
            vertexShader = compileShader(GL20.GL_VERTEX_SHADER, "FERMIUM_DEBUG_TEXTURED_VERTEX", vertexSource);
            fragmentShader = compileShader(GL20.GL_FRAGMENT_SHADER, "FERMIUM_DEBUG_TEXTURED_FRAGMENT", fragmentSource);

            if (vertexShader == 0 || fragmentShader == 0) {
                LOGGER.warn("DEBUG TEXTURED shader compile failed for {}", source.getName());
                return 0;
            }

            program = GL20.glCreateProgram();

            GL20.glBindAttribLocation(program, 0, "a_PosId");
            GL20.glBindAttribLocation(program, 1, "a_Color");
            GL20.glBindAttribLocation(program, 2, "a_TexCoord");

            GL20.glAttachShader(program, vertexShader);
            GL20.glAttachShader(program, fragmentShader);
            bindCeleritasDebugAttributes(program);
            GL20.glLinkProgram(program);

            String log = GL20.glGetProgramInfoLog(program, GL20.glGetProgrami(program, GL20.GL_INFO_LOG_LENGTH));
            if (log != null && !log.trim().isEmpty()) {
                LOGGER.info("DEBUG TEXTURED program link log for {}:\n{}", source.getName(), log);
            }

            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0) {
                LOGGER.error("DEBUG TEXTURED program link FAILED for {}", source.getName());
                GL20.glDeleteProgram(program);
                return 0;
            }

            LOGGER.info("DEBUG TEXTURED program link OK for {}. GL id={}", source.getName(), program);
            return program;
        } finally {
            if (vertexShader != 0) {
                GL20.glDeleteShader(vertexShader);
            }
            if (fragmentShader != 0) {
                GL20.glDeleteShader(fragmentShader);
            }
        }
    }

    public static void destroyProgram(int program) {
        if (program != 0) {
            GL20.glDeleteProgram(program);
            LOGGER.info("Deleted GL shader program {}", program);
        }
    }

    private static String patchVertexShader(String path, String source) {
        if (source == null) {
            return null;
        }

        if (source.contains("attribute vec3 a_PosId")) {
            return source;
        }

        String patched = source;

        patched = patched.replace("gl_Vertex", "fm_Vertex");
        patched = patched.replace("gl_MultiTexCoord0", "fm_MultiTexCoord0");
        patched = patched.replace("gl_MultiTexCoord1", "fm_MultiTexCoord1");
        patched = patched.replace("ftransform()", "(gl_ModelViewProjectionMatrix * fm_Vertex)");

        String preamble =
                "\n"
              + "// Fermium Celeritas vertex compatibility layer\n"
              + "attribute vec3 a_PosId;\n"
              + "attribute vec4 a_Color;\n"
              + "uniform vec3 u_RegionOffset;\n"
              + "uniform mat4 u_ModelViewMatrix;\n"
              + "uniform mat4 u_ProjectionMatrix;\n"
              + "attribute vec2 a_TexCoord;\n"
              + "vec3 fm_DrawTranslation = vec3(0.0);\n"
              + "#define fm_Vertex vec4(a_PosId + fm_DrawTranslation, 1.0)\n"
              + "#define fm_MultiTexCoord0 vec4(a_TexCoord, 0.0, 1.0)\n"
              + "#define fm_MultiTexCoord1 vec4(1.0, 1.0, 0.0, 1.0)\n"
              + "#define gl_Color a_Color\n"
              + "\n";

        int lineEnd = patched.indexOf('\n');
        if (patched.startsWith("#version") && lineEnd >= 0) {
            patched = patched.substring(0, lineEnd + 1) + preamble + patched.substring(lineEnd + 1);
        } else {
            patched = preamble + patched;
        }

        LOGGER.info("Patched vertex shader for Celeritas vertex compatibility: {}", path);
        return patched;
    }

    private static int compileShader(int type, String path, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);

        int compiled = GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS);
        String log = GL20.glGetShaderInfoLog(shader, 32768);

        if (log != null && !log.trim().isEmpty()) {
            LOGGER.info("Shader compile log for {}:\n{}", path, log);
        }

        if (compiled == 0) {
            LOGGER.error("Shader compile FAILED: {}", path);
            GL20.glDeleteShader(shader);
            drainGlErrors("after shader compile failed: " + path);
            return 0;
        }

        LOGGER.info("Shader compile OK: {}", path);
        return shader;
    }

    private static void deleteShader(int shader) {
        if (shader != 0) {
            GL20.glDeleteShader(shader);
        }
    }

    private static void drainGlErrors(String stage) {
        int error;
        while ((error = GL11.glGetError()) != 0) {
            LOGGER.warn("Drained GL error {} at {}", error, stage);
        }
    }

    private static String makeDebugFragmentShader(String original) {
        String version = "#version 120\n";

        if (original != null) {
            String[] lines = original.split("\\r?\\n");
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("#version")) {
                    version = trimmed + "\n";
                    break;
                }
            }
        }

        return version
                + "void main() {\n"
                + "    gl_FragColor = vec4(1.0, 0.0, 1.0, 1.0);\n"
                + "}\n";
    }


    private static String makeDebugVertexShader(String original) {
        String version = "#version 120\n";

        if (original != null) {
            String[] lines = original.split("\\r?\\n");
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("#version")) {
                    version = trimmed + "\n";
                    break;
                }
            }
        }

        return version
                + "attribute vec3 a_PosId;\n"
                  + "void main() {\n"
                + "    vec3 pos = a_PosId + u_RegionOffset;\n"
                + "    gl_Position = u_ProjectionMatrix * u_ModelViewMatrix * vec4(pos, 1.0);\n"
                + "}\n";
    }

    private static void bindCeleritasDebugAttributes(int program) {
        GL20.glBindAttribLocation(program, 0, "a_PosId");
        GL20.glBindAttribLocation(program, 1, "a_Color");
        GL20.glBindAttribLocation(program, 2, "a_TexCoord");
        // GL20.glBindAttribLocation(program, 3, "a_LightCoord");
    }


}
