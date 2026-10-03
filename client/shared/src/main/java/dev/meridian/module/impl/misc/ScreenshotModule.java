package dev.meridian.module.impl.misc;

import dev.meridian.core.Meridian;
import dev.meridian.event.Events;
import dev.meridian.i18n.I18n;
import dev.meridian.module.Category;
import dev.meridian.module.Module;
import dev.meridian.notify.NotificationManager;
import dev.meridian.platform.Capability;
import dev.meridian.setting.ActionSetting;
import dev.meridian.setting.BooleanSetting;

/** Screenshot helper: a Meridian notification instead of the chat line, optional path copy, folder shortcut. */
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
                () -> Meridian.get().platform().actions().openFolder(Meridian.get().platform().actions().screenshotsDirectory())));
        on(Events.ScreenshotTaken.class, e -> {
            if (copyPath.on()) {
                Meridian.get().platform().actions().setClipboard(e.file.getAbsolutePath());
            }
            String detail = e.file.getName() + (copyPath.on() ? " · " + I18n.tr("notify.screenshot.copied", "path copied") : "");
            Meridian.get().notifications().post(I18n.tr("notify.screenshot", "Screenshot saved"), detail,
                    NotificationManager.Level.SUCCESS);
        });
    }

    public boolean replacesChatMessage() {
        return replaceChat.on();
    }
}
