package dev.medirian.mc1_8_9;

import dev.medirian.ui.MedirianScreen;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * Native 1.8.9 screen hosting a shared {@link MedirianScreen}. Mouse input is read straight from
 * LWJGL events with sub-pixel precision; keyboard events are mapped to portable keys.
 */
public final class ScreenBridge extends Screen {

    private final MedirianScreen screen;
    private int pressedButton = -1;

    public ScreenBridge(MedirianScreen screen) {
        this.screen = screen;
    }

    public MedirianScreen medirian() {
        return screen;
    }

    private double guiX(int rawX) {
        return rawX * (double) width / client.width;
    }

    private double guiY(int rawY) {
        return height - rawY * (double) height / client.height - 1;
    }

    @Override
    public void render(int mouseX, int mouseY, float delta) {
        LegacyGfx gfx = LegacyPlatform.get().gfx().begin();
        screen.renderFrame(gfx, guiX(Mouse.getX()), guiY(Mouse.getY()), delta);
        gfx.end();
    }

    @Override
    public void handleMouse() {
        double x = guiX(Mouse.getEventX());
        double y = guiY(Mouse.getEventY());
        int button = Mouse.getEventButton();
        if (button >= 0) {
            if (Mouse.getEventButtonState()) {
                pressedButton = button;
                screen.onMouseClicked(x, y, button);
            } else {
                screen.onMouseReleased(x, y, button);
                if (button == pressedButton) {
                    pressedButton = -1;
                }
            }
        } else if (pressedButton >= 0) {
            screen.onMouseDragged(x, y, pressedButton);
        }
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            screen.onMouseScrolled(x, y, wheel > 0 ? 1 : -1);
        }
    }

    @Override
    public void handleKeyboard() {
        if (!Keyboard.getEventKeyState()) {
            return;
        }
        int code = Keyboard.getEventKey();
        char c = Keyboard.getEventCharacter();
        boolean ctrl = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
        boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
        if (code != Keyboard.KEY_NONE) {
            screen.onKeyPressed(KeyCodes.fromLwjgl(code), ctrl, shift);
        }
        if (c >= 32 && c != 127) {
            screen.onCharTyped(c);
        }
        if (code == Keyboard.KEY_F11) {
            client.toggleFullscreen();
        }
    }

    @Override
    public boolean shouldPauseGame() {
        return screen.pausesGame();
    }

    @Override
    public void removed() {
        Keyboard.enableRepeatEvents(false);
        screen.onRemoved();
    }

    @Override
    public void init() {
        Keyboard.enableRepeatEvents(true);
    }
}
