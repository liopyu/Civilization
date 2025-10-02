package net.liopyu.civilization.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class AdventurerScreen extends AbstractContainerScreen<AdventurerMenu> {
    // Vanilla generic 9x? background; top 166px is the 9x3 layout
    private static final ResourceLocation BG = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");

    public AdventurerScreen(AdventurerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;   // standard width
        this.imageHeight = 166;  // 9x3 container height
        this.inventoryLabelY = this.imageHeight - 94; // vanilla offset
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        // Draw vanilla 9x3 background region
        g.blit(BG, x, y, 0, 0, this.imageWidth, this.imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(this.font, this.title, 8, 6, 0x404040, false);
        g.drawString(this.font, this.playerInventoryTitle, 8, this.inventoryLabelY + 2, 0x404040, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);
        this.renderTooltip(g, mouseX, mouseY);
    }
}
