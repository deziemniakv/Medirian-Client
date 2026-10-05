package dev.meridian.mc1_21_8;

import dev.meridian.platform.EffectView;
import dev.meridian.platform.EntityView;
import dev.meridian.platform.ItemView;
import dev.meridian.platform.PlayerView;
import dev.meridian.platform.SidebarView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Read-only views over 1.21.8 game objects. */
final class Views {

    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private Views() {
    }

    /** A living entity (attack target, local player). */
    static class Entity implements EntityView {
        final LivingEntity entity;

        Entity(LivingEntity entity) {
            this.entity = entity;
        }

        @Override
        public int entityId() {
            return entity.getId();
        }

        @Override
        public String name() {
            return entity.getName().getString();
        }

        @Override
        public float health() {
            return entity.getHealth();
        }

        @Override
        public float maxHealth() {
            return entity.getMaxHealth();
        }

        @Override
        public float absorption() {
            return entity.getAbsorptionAmount();
        }

        @Override
        public int hurtTime() {
            return entity.hurtTime;
        }

        @Override
        public boolean isPlayer() {
            return entity instanceof Player;
        }

        @Override
        public boolean isAlive() {
            return entity.isAlive();
        }

        @Override
        public int armorValue() {
            return entity.getArmorValue();
        }

        @Override
        public double distanceToPlayer() {
            LocalPlayer player = Minecraft.getInstance().player;
            return player == null ? 0 : player.getEyePosition().distanceTo(entity.getBoundingBox().getCenter());
        }

        @Override
        public Object handle() {
            return entity;
        }
    }

    /** The local player; reads live data from {@code Minecraft.player}. */
    static final class LocalPlayerView extends Entity implements PlayerView {

        LocalPlayerView(LocalPlayer player) {
            super(player);
        }

        private LocalPlayer player() {
            return (LocalPlayer) entity;
        }

        @Override
        public double x() {
            return entity.getX();
        }

        @Override
        public double y() {
            return entity.getY();
        }

        @Override
        public double z() {
            return entity.getZ();
        }

        @Override
        public float yaw() {
            return entity.getYRot();
        }

        @Override
        public float pitch() {
            return entity.getXRot();
        }

        @Override
        public Dimension dimension() {
            var key = entity.level().dimension();
            if (key == Level.OVERWORLD) {
                return Dimension.OVERWORLD;
            }
            if (key == Level.NETHER) {
                return Dimension.NETHER;
            }
            return key == Level.END ? Dimension.END : Dimension.OTHER;
        }

        @Override
        public String dimensionId() {
            return entity.level().dimension().location().toString();
        }

        @Override
        public ItemView armor(int slot) {
            return new Item(entity.getItemBySlot(ARMOR[slot]));
        }

        @Override
        public ItemView mainHand() {
            return new Item(entity.getMainHandItem());
        }

        @Override
        public ItemView offHand() {
            return new Item(entity.getOffhandItem());
        }

        @Override
        public List<EffectView> effects() {
            var active = entity.getActiveEffects();
            if (active.isEmpty()) {
                return Collections.emptyList();
            }
            List<EffectView> result = new ArrayList<>(active.size());
            for (MobEffectInstance instance : active) {
                if (instance.showIcon()) {
                    result.add(new Effect(instance));
                }
            }
            result.sort((a, b) -> Integer.compare(b.durationTicks(), a.durationTicks()));
            return result;
        }

        @Override
        public boolean sprinting() {
            return player().isSprinting();
        }

        @Override
        public boolean sneaking() {
            return player().isShiftKeyDown();
        }

        @Override
        public double distanceToPlayer() {
            return 0;
        }
    }

    static final class Item implements ItemView {
        private final ItemStack stack;

        Item(ItemStack stack) {
            this.stack = stack;
        }

        @Override
        public boolean isEmpty() {
            return stack.isEmpty();
        }

        @Override
        public int count() {
            return stack.getCount();
        }

        @Override
        public int damage() {
            return stack.isDamageableItem() ? stack.getDamageValue() : 0;
        }

        @Override
        public int maxDamage() {
            return stack.isDamageableItem() ? stack.getMaxDamage() : 0;
        }

        @Override
        public String name() {
            return stack.getHoverName().getString();
        }

        @Override
        public Object handle() {
            return stack;
        }
    }

    static final class Effect implements EffectView {
        private final MobEffectInstance instance;
        private final String name;

        Effect(MobEffectInstance instance) {
            this.instance = instance;
            this.name = instance.getEffect().value().getDisplayName().getString();
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public int amplifier() {
            return instance.getAmplifier();
        }

        @Override
        public int durationTicks() {
            return instance.getDuration();
        }

        @Override
        public boolean infinite() {
            return instance.isInfiniteDuration();
        }

        @Override
        public boolean beneficial() {
            return instance.getEffect().value().isBeneficial();
        }

        @Override
        public Object handle() {
            return instance;
        }
    }

    /** Immutable snapshot of the sidebar (rebuilt a few times per second). */
    static final class Sidebar implements SidebarView {
        private final Component title;
        private final List<Component> lines;
        private final List<Component> scores;

        Sidebar(Component title, List<Component> lines, List<Component> scores) {
            this.title = title;
            this.lines = lines;
            this.scores = scores;
        }

        @Override
        public Object title() {
            return title;
        }

        @Override
        public int lineCount() {
            return lines.size();
        }

        @Override
        public Object line(int index) {
            return lines.get(index);
        }

        @Override
        public Object score(int index) {
            return scores.get(index);
        }
    }
}
