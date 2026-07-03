package org.taumc.fermium.shaders.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import org.taumc.fermium.shaders.discovery.FermiumShaderpackDirectoryManager;
import org.taumc.fermium.shaders.backend.FermiumShaders;

import java.awt.Desktop;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

public class FermiumShaderScreen extends GuiScreen {
    private final GuiScreen parent;

    private FermiumShaderpackDirectoryManager directoryManager;
    private FermiumShaderPackSelectionList shaderPackList;

    private File configFile;

    private String selectedPack = "";
    private String appliedPack = "";

    private GuiButton applyButton;
    private GuiButton settingsButton;

    public FermiumShaderScreen(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        File gameDir = new File(System.getProperty("user.dir"));

        File shaderpacksDir = new File(gameDir, "shaderpacks");
        File configDir = new File(gameDir, "config");

        if (!configDir.exists()) {
            configDir.mkdirs();
        }

        this.configFile = new File(configDir, "fermium-shaders.properties");
        this.directoryManager = new FermiumShaderpackDirectoryManager(shaderpacksDir);

        loadConfig();

        this.buttonList.clear();

        int listTop = 40;
        int listBottom = this.height - 62;

        this.shaderPackList = new FermiumShaderPackSelectionList(this, this.mc, this.width, this.height, listTop, listBottom, 28);
        this.shaderPackList.setEntries(this.directoryManager.scanShaderpacks());

        int y = this.height - 52;
        int centerX = this.width / 2;

        this.buttonList.add(new GuiButton(0, centerX - 204, y, 98, 20, "Open Folder"));
        this.buttonList.add(new GuiButton(1, centerX - 102, y, 98, 20, "Refresh"));

        this.applyButton = new GuiButton(2, centerX + 2, y, 98, 20, "Apply");
        this.buttonList.add(this.applyButton);

        this.buttonList.add(new GuiButton(3, centerX + 104, y, 98, 20, "Done"));

        this.settingsButton = new GuiButton(4, centerX - 100, y + 24, 200, 20, "Shader Pack Settings...");
        this.settingsButton.enabled = false;
        this.buttonList.add(this.settingsButton);

        updateButtons();
    }

    public FontRenderer getFontRenderer() {
        return this.fontRenderer;
    }

    public String getSelectedPack() {
        return this.selectedPack == null ? "" : this.selectedPack;
    }

    public void selectShaderpack(String configName) {
        this.selectedPack = configName == null ? "" : configName;
        updateButtons();
    }

    public void applyChanges() {
        this.appliedPack = getSelectedPack();
        saveConfig();
        FermiumShaders.applyShaderPack(this.appliedPack);
        updateButtons();
    }

    private void updateButtons() {
        if (this.applyButton != null) {
            this.applyButton.enabled = !getSelectedPack().equals(this.appliedPack == null ? "" : this.appliedPack);
        }

        if (this.settingsButton != null) {
            this.settingsButton.enabled = false;
        }
    }

    private void loadConfig() {
        this.selectedPack = "";
        this.appliedPack = "";

        if (this.configFile == null || !this.configFile.exists()) {
            return;
        }

        Properties properties = new Properties();

        try (FileInputStream input = new FileInputStream(this.configFile)) {
            properties.load(input);
            this.selectedPack = properties.getProperty("shaderPack", "");
            this.appliedPack = this.selectedPack;
        } catch (IOException ignored) {
        }
    }

    private void saveConfig() {
        Properties properties = new Properties();
        properties.setProperty("shaderPack", getSelectedPack());
        properties.setProperty("enableShaders", Boolean.toString(!getSelectedPack().isEmpty()));

        try (FileOutputStream output = new FileOutputStream(this.configFile)) {
            properties.store(output, "Fermium shader configuration");
        } catch (IOException ignored) {
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 0) {
            openFolder(this.directoryManager.getDirectory());
        }

        if (button.id == 1) {
            this.directoryManager.ensureDirectoryExists();
            this.shaderPackList = new FermiumShaderPackSelectionList(this, this.mc, this.width, this.height, 40, this.height - 62, 28);
            this.shaderPackList.setEntries(this.directoryManager.scanShaderpacks());
        }

        if (button.id == 2) {
            applyChanges();
        }

        if (button.id == 3) {
            Minecraft.getMinecraft().displayGuiScreen(this.parent);
        }
    }

    private static void openFolder(File folder) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(folder);
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();

        if (this.shaderPackList != null) {
            this.shaderPackList.handleMouseInput();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();

        if (this.shaderPackList != null) {
            this.shaderPackList.drawScreen(mouseX, mouseY, partialTicks);
        }

        this.drawCenteredString(this.fontRenderer, "Fermium Shaders", this.width / 2, 10, 0xFFFFFF);
        this.drawCenteredString(this.fontRenderer, "Select Shader Pack", this.width / 2, 24, 0xAAAAAA);

        String selected = getSelectedPack().isEmpty() ? "OFF" : getSelectedPack();
        String applied = (this.appliedPack == null || this.appliedPack.isEmpty()) ? "OFF" : this.appliedPack;

        this.drawCenteredString(this.fontRenderer, "Selected: " + selected, this.width / 2, this.height - 74, 0xFFFF55);

        if (!selected.equals(applied)) {
            this.drawCenteredString(this.fontRenderer, "Unsaved changes", this.width / 2, this.height - 64, 0xFFAA00);
        }

        this.drawString(this.fontRenderer, "shaderpacks/", 6, this.height - 12, 0x888888);
        this.drawString(this.fontRenderer, "Backend not ported yet", this.width - 124, this.height - 12, 0x888888);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
