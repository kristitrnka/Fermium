package org.taumc.fermium.shaders.bridge;

import java.lang.reflect.Method;

public final class FermiumChunkRenderBridge {
    private static int revision;
    private static boolean resolved;
    private static boolean available;

    private static Method isActive;
    private static Method bindTerrain;
    private static Method unbind;
    private static Method setRegionOffset;

    private static Method transformTerrainFragment;
    private static Method beginTerrainFramebuffer;
    private static Method endTerrainFramebuffer;

    private FermiumChunkRenderBridge() {
    }

    private static void resolve() {
        if (resolved) {
            return;
        }

        resolved = true;

        try {
            Class<?> pipeline = Class.forName("org.taumc.fermium.shaders.iris.pipeline.FermiumShaderPipeline");

            isActive = pipeline.getMethod("isActive");
            bindTerrain = pipeline.getMethod("bindTerrain");
            unbind = pipeline.getMethod("unbind");
            setRegionOffset = pipeline.getMethod("setRegionOffset", float.class, float.class, float.class);

            try {
                Class<?> shaders = Class.forName("org.taumc.fermium.shaders.backend.FermiumShaders");
                transformTerrainFragment = shaders.getMethod("transformTerrainFragment", String.class, String.class);
                beginTerrainFramebuffer = shaders.getMethod("beginTerrainFramebuffer");
                endTerrainFramebuffer = shaders.getMethod("endTerrainFramebuffer");
            } catch (Throwable ignored) {
                transformTerrainFragment = null;
            }

            available = true;
        } catch (Throwable t) {
            available = false;
        }
    }

    public static boolean isActive() {
        resolve();

        if (!available || isActive == null) {
            return false;
        }

        try {
            Object result = isActive.invoke(null);
            return Boolean.TRUE.equals(result);
        } catch (Throwable t) {
            return false;
        }
    }

    public static String transformTerrainFragment(String path, String shaderSource) {
        resolve();

        if (!available || transformTerrainFragment == null || shaderSource == null) {
            return shaderSource;
        }

        try {
            Object result = transformTerrainFragment.invoke(null, path, shaderSource);
            return result instanceof String ? (String) result : shaderSource;
        } catch (Throwable t) {
            return shaderSource;
        }
    }


    public static void beginTerrainFramebuffer() {
        resolve();

        if (!available || beginTerrainFramebuffer == null) {
            return;
        }

        try {
            beginTerrainFramebuffer.invoke(null);
        } catch (Throwable ignored) {
        }
    }

    public static void endTerrainFramebuffer() {
        resolve();

        if (!available || endTerrainFramebuffer == null) {
            return;
        }

        try {
            endTerrainFramebuffer.invoke(null);
        } catch (Throwable ignored) {
        }
    }

    public static void bindTerrain() {
        resolve();

        if (!available || bindTerrain == null) {
            return;
        }

        try {
            bindTerrain.invoke(null);
        } catch (Throwable ignored) {
        }
    }

    public static void unbind() {
        resolve();

        if (!available || unbind == null) {
            return;
        }

        try {
            unbind.invoke(null);
        } catch (Throwable ignored) {
        }
    }

    public static void setRegionOffset(float x, float y, float z) {
        resolve();

        if (!available || setRegionOffset == null) {
            return;
        }

        try {
            setRegionOffset.invoke(null, x, y, z);
        } catch (Throwable ignored) {
        }
    }

    public static int getRevision() {
        return revision;
    }

    private static void bumpRevision() {
        revision++;
    }

}
