package org.taumc.fermium.shaders.backend.gl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.EXTFramebufferObject;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;

import java.nio.IntBuffer;
import java.nio.ByteBuffer;
import java.util.Arrays;

public final class FermiumFramebufferSet {
    private static final Logger LOGGER = LogManager.getLogger("Fermium/Framebuffer");

    private static final int COLOR_TARGETS = 8;

    private int width;
    private int height;

    private int framebuffer;
    private final int[] colorTextures = new int[COLOR_TARGETS];
    private int depthTexture;

    public boolean create(int width, int height, String drawBuffers) {
        destroy();

        this.width = Math.max(1, width);
        this.height = Math.max(1, height);

        LOGGER.info("Creating Fermium framebuffer set {}x{}, requested DRAWBUFFERS:{}", this.width, this.height, drawBuffers);

        try {
            this.framebuffer = EXTFramebufferObject.glGenFramebuffersEXT();
            EXTFramebufferObject.glBindFramebufferEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT, this.framebuffer);

            for (int i = 0; i < COLOR_TARGETS; i++) {
                this.colorTextures[i] = createColorTexture(this.width, this.height);

                EXTFramebufferObject.glFramebufferTexture2DEXT(
                        EXTFramebufferObject.GL_FRAMEBUFFER_EXT,
                        EXTFramebufferObject.GL_COLOR_ATTACHMENT0_EXT + i,
                        GL11.GL_TEXTURE_2D,
                        this.colorTextures[i],
                        0
                );
            }

            this.depthTexture = createDepthTexture(this.width, this.height);

            EXTFramebufferObject.glFramebufferTexture2DEXT(
                    EXTFramebufferObject.GL_FRAMEBUFFER_EXT,
                    EXTFramebufferObject.GL_DEPTH_ATTACHMENT_EXT,
                    GL11.GL_TEXTURE_2D,
                    this.depthTexture,
                    0
            );

            // Debug Celeritas bridge mode:
            // Celeritas chunk shader writes a single color output, so force terrain into colortex0.
            // Shaderpack DRAWBUFFERS are parsed and stored for later real gbuffers/composite passes.
            setupDrawBuffers("0");

            int status = EXTFramebufferObject.glCheckFramebufferStatusEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT);

            EXTFramebufferObject.glBindFramebufferEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT, 0);

            if (status != EXTFramebufferObject.GL_FRAMEBUFFER_COMPLETE_EXT) {
                LOGGER.warn("Fermium framebuffer is incomplete. Status=0x{}", Integer.toHexString(status));
                destroy();
                return false;
            }

            LOGGER.info("Fermium framebuffer created. fbo={}, colortex={}, depthtex={}",
                    this.framebuffer,
                    Arrays.toString(this.colorTextures),
                    this.depthTexture
            );

            return true;
        } catch (Throwable t) {
            LOGGER.warn("Fermium framebuffer backend failed during GL setup", t);

            try {
                EXTFramebufferObject.glBindFramebufferEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT, 0);
            } catch (Throwable ignored) {
            }

            destroy();
            return false;
        }
    }

    private static int createColorTexture(int width, int height) {
        int texture = GL11.glGenTextures();

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);

        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

        GL11.glTexImage2D(
                GL11.GL_TEXTURE_2D,
                0,
                GL11.GL_RGBA8,
                width,
                height,
                0,
                GL11.GL_RGBA,
                GL11.GL_UNSIGNED_BYTE,
                0L
        );

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

        return texture;
    }

    private static int createDepthTexture(int width, int height) {
        int texture = GL11.glGenTextures();

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);

        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

        GL11.glTexImage2D(
                GL11.GL_TEXTURE_2D,
                0,
                GL14.GL_DEPTH_COMPONENT24,
                width,
                height,
                0,
                GL11.GL_DEPTH_COMPONENT,
                GL11.GL_UNSIGNED_INT,
                0L
        );

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

        return texture;
    }

    private static void setupDrawBuffers(String drawBuffers) {
        if (drawBuffers == null || drawBuffers.equals("none") || drawBuffers.isEmpty()) {
            GL11.glDrawBuffer(EXTFramebufferObject.GL_COLOR_ATTACHMENT0_EXT);
            return;
        }

        IntBuffer buffers = BufferUtils.createIntBuffer(drawBuffers.length());

        for (int i = 0; i < drawBuffers.length(); i++) {
            char c = drawBuffers.charAt(i);

            if (c < '0' || c > '7') {
                continue;
            }

            int index = c - '0';
            buffers.put(EXTFramebufferObject.GL_COLOR_ATTACHMENT0_EXT + index);
        }

        buffers.flip();

        if (!buffers.hasRemaining()) {
            GL11.glDrawBuffer(EXTFramebufferObject.GL_COLOR_ATTACHMENT0_EXT);
            return;
        }

        try {
            GL20.glDrawBuffers(buffers);
            LOGGER.info("Configured MRT draw buffers for shaderpack: {}", drawBuffers);
        } catch (Throwable t) {
            int first = buffers.get(0);
            GL11.glDrawBuffer(first);
            LOGGER.warn("glDrawBuffers failed; using first shaderpack draw buffer only");
        }
    }

    public void destroy() {
        if (this.framebuffer != 0) {
            try {
                EXTFramebufferObject.glBindFramebufferEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT, 0);
                EXTFramebufferObject.glDeleteFramebuffersEXT(this.framebuffer);
            } catch (Throwable ignored) {
            }

            this.framebuffer = 0;
        }

        for (int i = 0; i < this.colorTextures.length; i++) {
            if (this.colorTextures[i] != 0) {
                try {
                    GL11.glDeleteTextures(this.colorTextures[i]);
                } catch (Throwable ignored) {
                }

                this.colorTextures[i] = 0;
            }
        }

        if (this.depthTexture != 0) {
            try {
                GL11.glDeleteTextures(this.depthTexture);
            } catch (Throwable ignored) {
            }

            this.depthTexture = 0;
        }
    }


    public void bindForTerrain() {
        if (!isCreated()) {
            return;
        }

        EXTFramebufferObject.glBindFramebufferEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT, this.framebuffer);
        GL11.glViewport(0, 0, this.width, this.height);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
    }

    public void unbindToDefault() {
        EXTFramebufferObject.glBindFramebufferEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT, 0);
        GL11.glViewport(0, 0, this.width, this.height);
    }


    public void debugReadColorPixel(int colorIndex) {
        if (!isCreated()) {
            return;
        }

        int attachment = EXTFramebufferObject.GL_COLOR_ATTACHMENT0_EXT + Math.max(0, Math.min(7, colorIndex));

        try {
            EXTFramebufferObject.glBindFramebufferEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT, this.framebuffer);
            GL11.glReadBuffer(attachment);

            int x = Math.max(0, this.width / 2);
            int y = Math.max(0, this.height / 2);

            ByteBuffer pixel = BufferUtils.createByteBuffer(4);
            GL11.glReadPixels(x, y, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);

            int r = pixel.get(0) & 255;
            int g = pixel.get(1) & 255;
            int b = pixel.get(2) & 255;
            int a = pixel.get(3) & 255;

            LOGGER.info("Fermium FBO debug pixel colortex{} at {},{} = rgba({}, {}, {}, {})",
                    colorIndex, x, y, r, g, b, a);
        } catch (Throwable t) {
            LOGGER.warn("Failed to read Fermium FBO debug pixel", t);
        } finally {
            EXTFramebufferObject.glBindFramebufferEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT, 0);
        }
    }

    public void copyColorToDefaultFramebuffer(int colorIndex) {
        if (!isCreated()) {
            return;
        }

        int safeIndex = Math.max(0, Math.min(7, colorIndex));
        int texture = this.colorTextures[safeIndex];

        int oldProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int oldTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int oldViewportX = GL11.glGetInteger(GL11.GL_VIEWPORT);
        int oldViewportY = GL11.glGetInteger(GL11.GL_VIEWPORT + 1);
        int oldViewportW = GL11.glGetInteger(GL11.GL_VIEWPORT + 2);
        int oldViewportH = GL11.glGetInteger(GL11.GL_VIEWPORT + 3);

        try {
            EXTFramebufferObject.glBindFramebufferEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT, 0);

            GL20.glUseProgram(0);

            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);

            GL11.glViewport(0, 0, this.width, this.height);

            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_FOG);

            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);

            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();
            GL11.glOrtho(0.0D, 1.0D, 0.0D, 1.0D, -1.0D, 1.0D);

            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();

            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0.0f, 0.0f);
            GL11.glVertex2f(0.0f, 0.0f);

            GL11.glTexCoord2f(1.0f, 0.0f);
            GL11.glVertex2f(1.0f, 0.0f);

            GL11.glTexCoord2f(1.0f, 1.0f);
            GL11.glVertex2f(1.0f, 1.0f);

            GL11.glTexCoord2f(0.0f, 1.0f);
            GL11.glVertex2f(0.0f, 1.0f);
            GL11.glEnd();

            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);

            GL11.glPopAttrib();

        } catch (Throwable t) {
            LOGGER.warn("Failed to copy Fermium framebuffer colortex{} to default framebuffer", safeIndex, t);
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, oldTexture);
            GL20.glUseProgram(oldProgram);
            GL11.glViewport(oldViewportX, oldViewportY, oldViewportW, oldViewportH);
            GL11.glDepthMask(true);
        }
    }

    public boolean isCreated() {
        return this.framebuffer != 0;
    }

    public int getFramebuffer() {
        return this.framebuffer;
    }

    public int getColorTexture(int index) {
        if (index < 0 || index >= this.colorTextures.length) {
            return 0;
        }

        return this.colorTextures[index];
    }

    public int getDepthTexture() {
        return this.depthTexture;
    }

    public int getWidth() {
        return this.width;
    }

    public int getHeight() {
        return this.height;
    }
}
