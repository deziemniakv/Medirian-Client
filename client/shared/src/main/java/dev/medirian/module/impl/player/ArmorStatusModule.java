package dev.medirian.module.impl.player;

import dev.medirian.core.Medirian;
import dev.medirian.hud.HudSurface;
import dev.medirian.hud.Anchor;
import dev.medirian.hud.HudElement;
import dev.medirian.hud.HudStyle;
import dev.medirian.i18n.I18n;
import dev.medirian.module.Category;
import dev.medirian.module.Module;
import dev.medirian.platform.Capability;
import dev.medirian.platform.ItemView;
import dev.medirian.platform.PlayerView;
import dev.medirian.render.Gfx;
import dev.medirian.setting.BooleanSetting;
import dev.medirian.setting.ModeSetting;

/** Equipped armor (and optionally held items) with durability. */
public final class ArmorStatusModule extends Module {

    /** Direction of the item list. */
    public enum Orientation { VERTICAL, HORIZONTAL }

    /** How durability is displayed. */
    public enum Durability { VALUE, PERCENT, NONE }

    private final ModeSetting<Orientation> orientation;
    private final ModeSetting<Durability> durability;
    private final BooleanSetting showHand;
    private final BooleanSetting colored;
    private final BooleanSetting shadow;

    private final ItemView[] slots = new ItemView[6];
    private int slotCount;

    public ArmorStatusModule() {
        super("armorstatus", "Armor Status", Category.PLAYER, "Shows your armor and held item with durability.");
        requires(Capability.WORLD_ICONS);
        enableByDefault();
        orientation = add(new ModeSetting<Orientation>("orientation", "Orientation", Orientation.VERTICAL));
        durability = add(new ModeSetting<Durability>("durability", "Durability", Durability.VALUE));
        showHand = add(new BooleanSetting("showHand", "Show held items", true));
        colored = add(new BooleanSetting("colored", "Color by durability", true));
        shadow = add(new BooleanSetting("shadow", "Text shadow", true));
        hud(new Element());
    }

    private final class Element extends HudElement {

        Element() {
            super(ArmorStatusModule.this, Anchor.BOTTOM_RIGHT, -4, -4);
        }

        @Override
        public void tick() {
            collect();
        }

        private void collect() {
            slotCount = 0;
            PlayerView player = Medirian.get().game().player();
            if (player == null) {
                return;
            }
            if (showHand.on()) {
                add(player.mainHand());
                add(player.offHand());
            }
            for (int slot = 0; slot < 4; slot++) {
                add(player.armor(slot));
            }
        }

        private void add(ItemView item) {
            if (item != null && !item.isEmpty()) {
                slots[slotCount++] = item;
            }
        }

        @Override
        public boolean hasContent() {
            return slotCount > 0;
        }

        @Override
        public void render(Gfx g, boolean editor) {
            if (editor && slotCount == 0) {
                collect();
            }
            boolean vertical = orientation.is(Orientation.VERTICAL);
            if (slotCount == 0) {
                String empty = I18n.tr("hud.armorstatus.empty", "No armor");
                width = g.textWidth(empty) + 10;
                height = 16;
                HudSurface.panel(g, 0, 0, width, height, HudStyle.DEFAULT_BACKGROUND);
                g.text(empty, 5, 4, HudStyle.DEFAULT_TEXT, true);
                return;
            }
            int x = 0;
            int y = 0;
            int maxWidth = 0;
            for (int i = 0; i < slotCount; i++) {
                ItemView item = slots[i];
                g.item(item, x, y);
                String text = text(item);
                int textWidth = text == null ? 0 : g.textWidth(text);
                if (text != null) {
                    g.text(text, x + 18, y + 4, color(item), shadow.on());
                }
                int entryWidth = 18 + (text == null ? 0 : textWidth + 2);
                if (vertical) {
                    maxWidth = Math.max(maxWidth, entryWidth);
                    y += 17;
                } else {
                    x += entryWidth + 4;
                }
            }
            width = vertical ? maxWidth : Math.max(16, x - 4);
            height = vertical ? Math.max(16, y - 1) : 16;
        }

        private String text(ItemView item) {
            if (item.maxDamage() <= 0) {
                return item.count() > 1 ? String.valueOf(item.count()) : null;
            }
            int remaining = item.maxDamage() - item.damage();
            switch (durability.get()) {
                case PERCENT:
                    return Math.round(remaining * 100f / item.maxDamage()) + "%";
                case NONE:
                    return null;
                default:
                    return String.valueOf(remaining);
            }
        }

        private int color(ItemView item) {
            if (!colored.on() || item.maxDamage() <= 0) {
                return HudStyle.DEFAULT_TEXT;
            }
            float ratio = (item.maxDamage() - item.damage()) / (float) item.maxDamage();
            if (ratio > 0.6f) {
                return 0xFF7BE0A0;
            }
            if (ratio > 0.3f) {
                return 0xFFF0C674;
            }
            return 0xFFF07178;
        }
    }
}
