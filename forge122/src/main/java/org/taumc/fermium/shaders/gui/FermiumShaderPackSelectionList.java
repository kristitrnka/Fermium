package org.taumc.fermium.shaders.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiSlot;
import org.taumc.fermium.shaders.discovery.FermiumShaderpackDirectoryManager.ShaderpackInfo;

import java.util.ArrayList;
import java.util.List;

public class FermiumShaderPackSelectionList extends GuiSlot {
    private final FermiumShaderScreen screen;
    private final List<ShaderpackInfo> entries = new ArrayList<>();

    public FermiumShaderPackSelectionList(FermiumShaderScreen screen, Minecraft mc, int width, int height, int top, int bottom, int slotHeight) {
        super(mc, width, height, top, bottom, slotHeight);
        this.screen = screen;
    }

    public void setEntries(List<ShaderpackInfo> shaderpacks) {
        this.entries.clear();

        if (shaderpacks != null) {
            this.entries.addAll(shaderpacks);
        }
    }

    @Override
    protected int getSize() {
        return this.entries.size();
    }

    @Override
    protected void elementClicked(int slotIndex, boolean doubleClick, int mouseX, int mouseY) {
        ShaderpackInfo entry = getEntry(slotIndex);

        if (entry == null) {
            return;
        }

        this.screen.selectShaderpack(entry.getConfigName());

        if (doubleClick) {
            this.screen.applyChanges();
        }
    }

    @Override
    protected boolean isSelected(int slotIndex) {
        ShaderpackInfo entry = getEntry(slotIndex);

        if (entry == null) {
            return false;
        }

        String selected = this.screen.getSelectedPack();
        if (selected == null) {
            selected = "";
        }

        return selected.equals(entry.getConfigName());
    }

    @Override
    protected void drawBackground() {
    }

    @Override
    protected void drawSlot(int slotIndex, int x, int y, int slotHeight, int mouseX, int mouseY, float partialTicks) {
        ShaderpackInfo entry = getEntry(slotIndex);

        if (entry == null) {
            return;
        }

        boolean selected = isSelected(slotIndex);

        int color = selected ? 0xFFFF55 : 0xFFFFFF;
        String text = entry.getDisplayName();

        if (entry.isOff()) {
            color = selected ? 0xFFFF55 : 0xAAAAAA;
        }

        if (selected) {
            text = "> " + text + " <";
        }

        this.screen.drawCenteredString(this.screen.getFontRenderer(), text, this.width / 2, y + 3, color);

        if (!entry.isOff() && entry.getFile() != null) {
            String type = entry.getFile().isDirectory() ? "Folder shaderpack" : "ZIP shaderpack";
            this.screen.drawCenteredString(this.screen.getFontRenderer(), type, this.width / 2, y + 13, 0x777777);
        }
    }

    private ShaderpackInfo getEntry(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= this.entries.size()) {
            return null;
        }

        return this.entries.get(slotIndex);
    }
}
