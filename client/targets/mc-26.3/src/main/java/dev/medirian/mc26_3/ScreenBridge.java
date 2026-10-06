package dev.medirian.mc26_3;

import com.mojang.blaze3d.platform.Window;
import dev.medirian.ui.MedirianScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Native 26.3 screen hosting a shared {@link MedirianScreen}. */
public final class ScreenBridge extends Screen {

    /** Medirian's main menu in place of Minecraft's title screen, unless it is switched off. */
    public static Screen replaceTitle(Screen screen) {
        if (screen != null && screen.getClass() == net.minecraft.client.gui.screens.TitleScreen.class) {
            MedirianScreen menu = dev.medirian.platform.Hooks.titleScreen();
            if (menu != null) {
                return new ScreenBridge(menu);
            }
        }
        return screen;
    }

    private final MedirianScreen screen;

    public ScreenBridge(MedirianScreen screen) {
        super(Component.literal("Medirian"));
        this.screen = screen;
    }

    public MedirianScreen medirian() {
        return screen;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        Window window = minecraft.getWindow();
        // sub-pixel mouse position for smooth dragging at large GUI scales
        double x = minecraft.mouseHandler.getScaledXPos(window);
        double y = minecraft.mouseHandler.getScaledYPos(window);
        screen.renderFrame(ModernPlatform.get().gfx().begin(graphics), x, y, partialTick);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // Medirian screens draw their own lightweight backdrop (no blur pass).
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return screen.onMouseClicked(event.x(), event.y(), event.button());
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return screen.onMouseReleased(event.x(), event.y(), event.button());
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        return screen.onMouseDragged(event.x(), event.y(), event.button());
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        return screen.onMouseScrolled(x, y, vertical);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        screen.onKeyPressed(KeyCodes.fromCode(event.key()), event.hasControlDown(), event.hasShiftDown());
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        boolean handled = false;
        for (char c : Character.toChars(event.codepoint())) {
            handled |= screen.onCharTyped(c);
        }
        return handled;
    }

    @Override
    public boolean isPauseScreen() {
        return screen.pausesGame();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false; // Escape is handled by the Medirian screen (returns to its parent)
    }

    @Override
    public void removed() {
        screen.onRemoved();
    }
}
