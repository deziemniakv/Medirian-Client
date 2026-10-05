package dev.medirian.module.impl.misc;

import dev.medirian.core.Medirian;
import dev.medirian.event.Events;
import dev.medirian.i18n.I18n;
import dev.medirian.module.Category;
import dev.medirian.module.Module;
import dev.medirian.notify.NotificationManager;
import dev.medirian.platform.Capability;
import dev.medirian.setting.ActionSetting;
import dev.medirian.setting.BooleanSetting;

/** Screenshot helper: a Medirian notification instead of the chat line, optional path copy, folder shortcut. */
public final class ScreenshotModule extends Module {

    private final BooleanSetting replaceChat;
    private final BooleanSetting copyPath;

    public ScreenshotModule() {
        super("screenshot", "Screenshot Tool", Category.MISC, "Cleaner screenshot feedback and quick access to your screenshots.");
        requires(Capability.SCREENSHOT_EVENTS);
        enableByDefault();
        replaceChat = add(new BooleanSetting("replaceChat", "Notification instead of chat message", true));
        copyPath = add(new BooleanSetting("copyPath", "Copy file path to clipboard", false));
        add(new ActionSetting("openFolder", "Screenshots folder", "Open",
                () -> Medirian.get().platform().actions().openFolder(Medirian.get().platform().actions().screenshotsDirectory())));
        on(Events.ScreenshotTaken.class, e -> {
            if (copyPath.on()) {
                Medirian.get().platform().actions().setClipboard(e.file.getAbsolutePath());
            }
            String detail = e.file.getName() + (copyPath.on() ? " · " + I18n.tr("notify.screenshot.copied", "path copied") : "");
            Medirian.get().notifications().post(I18n.tr("notify.screenshot", "Screenshot saved"), detail,
                    NotificationManager.Level.SUCCESS);
        });
    }

    public boolean replacesChatMessage() {
        return replaceChat.on();
    }
}
