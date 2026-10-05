package dev.meridian.mc1_21_8;

import com.mojang.blaze3d.platform.Window;
import dev.meridian.ui.MeridianScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Native 1.21.8 screen hosting a shared {@link MeridianScreen}. */
public final class ScreenBridge extends Screen {

    private final MeridianScreen screen;

    public ScreenBridge(MeridianScreen screen) {
        super(Component.literal("Meridian"));
        this.screen = screen;
    }

    public MeridianScreen meridian() {
        return screen;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Window window = minecraft.getWindow();
        // sub-pixel mouse position for smooth dragging at large GUI scales
        double x = minecraft.mouseHandler.getScaledXPos(window);
        double y = minecraft.mouseHandler.getScaledYPos(window);
        screen.renderFrame(ModernPlatform.get().gfx().begin(graphics), x, y, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Meridian screens draw their own lightweight backdrop (no blur pass).
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        return screen.onMouseClicked(x, y, button);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        return screen.onMouseReleased(x, y, button);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dragX, double dragY) {
        return screen.onMouseDragged(x, y, button);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        return screen.onMouseScrolled(x, y, vertical);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        screen.onKeyPressed(KeyCodes.fromGlfw(key), (modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL) != 0,
                (modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT) != 0);
        return true;
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        return screen.onCharTyped(c);
    }

    @Override
    public boolean isPauseScreen() {
        return screen.pausesGame();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false; // Escape is handled by the Meridian screen (returns to its parent)
    }

    @Override
    public void removed() {
        screen.onRemoved();
    }
}
