package org.taumc.fermium.shaders;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiVideoSettings;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(Side.CLIENT)
public final class FermiumShaderGuiHooks {
    private static final int SHADERS_BUTTON_ID = 694201;

    private FermiumShaderGuiHooks() {
    }

    @SubscribeEvent
    public static void onGuiInit(GuiScreenEvent.InitGuiEvent.Post event) {
        GuiScreen gui = event.getGui();

        if (!shouldAddShadersButton(gui)) {
            return;
        }

        int x = gui.width / 2 - 100;
        int y = gui.height - 52;

        event.getButtonList().add(new GuiButton(SHADERS_BUTTON_ID, x, y, 200, 20, "Shaders..."));
    }

    @SubscribeEvent
    public static void onButtonClick(GuiScreenEvent.ActionPerformedEvent.Post event) {
        if (event.getButton().id != SHADERS_BUTTON_ID) {
            return;
        }

        Minecraft.getMinecraft().displayGuiScreen(new FermiumShaderScreen(event.getGui()));
    }

    private static boolean shouldAddShadersButton(GuiScreen gui) {
        if (gui instanceof GuiVideoSettings) {
            return true;
        }

        String name = gui.getClass().getName().toLowerCase();

        return name.contains("video")
                || name.contains("sodium")
                || name.contains("embeddium")
                || name.contains("celeritas");
    }
}
