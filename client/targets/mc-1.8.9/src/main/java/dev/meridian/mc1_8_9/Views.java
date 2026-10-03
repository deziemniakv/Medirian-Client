package dev.meridian.mc1_8_9;

import dev.meridian.platform.EffectView;
import dev.meridian.platform.EntityView;
import dev.meridian.platform.ItemView;
import dev.meridian.platform.PlayerView;
import dev.meridian.platform.SidebarView;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Read-only views over 1.8.9 game objects. */
final class Views {

    private Views() {
    }

    static class Entity implements EntityView {
        final LivingEntity entity;

        Entity(LivingEntity entity) {
            this.entity = entity;
        }

        @Override
        public int entityId() {
            return entity.getEntityId();
        }

        @Override
        public String name() {
            return entity instanceof PlayerEntity ? ((PlayerEntity) entity).getGameProfile().getName() : entity.getTranslationKey();
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
            return entity.getAbsorption();
        }

        @Override
        public int hurtTime() {
            return entity.hurtTime;
        }

        @Override
        public boolean isPlayer() {
            return entity instanceof PlayerEntity;
        }

        @Override
        public boolean isAlive() {
            return entity.isAlive();
        }

        @Override
        public int armorValue() {
            return entity.getArmorProtectionValue();
        }

        @Override
        public double distanceToPlayer() {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            return player == null ? 0 : player.distanceTo(entity);
        }

        @Override
        public Object handle() {
            return entity;
        }
    }

    static final class LocalPlayerView extends Entity implements PlayerView {

        LocalPlayerView(ClientPlayerEntity player) {
            super(player);
        }

        private ClientPlayerEntity player() {
            return (ClientPlayerEntity) entity;
        }

        @Override
        public double x() {
            return entity.x;
        }

        @Override
        public double y() {
            return entity.y;
        }

        @Override
        public double z() {
            return entity.z;
        }

        @Override
        public float yaw() {
            return entity.yaw;
        }

        @Override
        public float pitch() {
            return entity.pitch;
        }

        @Override
        public Dimension dimension() {
            int id = entity.dimension;
            return id == 0 ? Dimension.OVERWORLD : id == -1 ? Dimension.NETHER : id == 1 ? Dimension.END : Dimension.OTHER;
        }

        @Override
        public ItemView armor(int slot) {
            // 1.8.9 armor slots: 0 = boots … 3 = helmet
            return new Item(player().getArmorSlot(3 - slot));
        }

        @Override
        public ItemView mainHand() {
            return new Item(player().getMainHandStack());
        }

        @Override
        public ItemView offHand() {
            return null;
        }

        @Override
        public List<EffectView> effects() {
            Collection<StatusEffectInstance> active = entity.getStatusEffectInstances();
            if (active.isEmpty()) {
                return Collections.emptyList();
            }
            List<EffectView> result = new ArrayList<EffectView>(active.size());
            for (StatusEffectInstance instance : active) {
                result.add(new Effect(instance));
            }
            Collections.sort(result, new Comparator<EffectView>() {
                @Override
                public int compare(EffectView a, EffectView b) {
                    return b.durationTicks() - a.durationTicks();
                }
            });
            return result;
        }

        @Override
        public boolean sprinting() {
            return entity.isSprinting();
        }

        @Override
        public boolean sneaking() {
            return entity.isSneaking();
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
            return stack == null || stack.count <= 0;
        }

        @Override
        public int count() {
            return stack == null ? 0 : stack.count;
        }

        @Override
        public int damage() {
            return stack != null && stack.isDamageable() ? stack.getDamage() : 0;
        }

        @Override
        public int maxDamage() {
            return stack != null && stack.isDamageable() ? stack.getMaxDamage() : 0;
        }

        @Override
        public String name() {
            return stack == null ? "" : stack.getCustomName();
        }

        @Override
        public Object handle() {
            return stack;
        }
    }

    static final class Effect implements EffectView {
        private final StatusEffectInstance instance;
        private final String name;

        Effect(StatusEffectInstance instance) {
            this.instance = instance;
            this.name = I18n.translate(instance.getTranslationKey());
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
            return instance.isPermanent();
        }

        @Override
        public boolean beneficial() {
            StatusEffect type = StatusEffect.STATUS_EFFECTS[instance.getEffectId()];
            return type != null && !type.isNegative();
        }

        @Override
        public Object handle() {
            return instance;
        }
    }

    /** Sidebar snapshot; text values are legacy formatted strings. */
    static final class Sidebar implements SidebarView {
        private final String title;
        private final List<String> lines;
        private final List<String> scores;

        Sidebar(String title, List<String> lines, List<String> scores) {
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
