package me.example.icemage;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Vec3d;

import java.util.*;

public class IceMage implements ModInitializer {

    private static final double RADIUS = 2.5;          // зона 5x5x5
    private static final int FREEZE_TICKS = 5 * 20;    // 5 секунд
    private static final int PASSIVE_TICKS = 20;       // 1 секунда
    private static final int COOLDOWN_TICKS = 15 * 20; // перезарядка

    private static final Map<UUID, Integer> FROZEN_UNTIL = new HashMap<>();
    private static final Map<UUID, Vec3d> FROZEN_POS = new HashMap<>();

    @Override
    public void onInitialize() {

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            Entity attacker = source.getAttacker();

            if (attacker != null && FROZEN_UNTIL.containsKey(attacker.getUuid())) {
                return false;
            }

            if (entity instanceof PlayerEntity mage && hasStaff(mage)
                    && attacker instanceof LivingEntity victim) {
                victim.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.SLOWNESS, PASSIVE_TICKS, 1));
            }
            return true;
        });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (FROZEN_UNTIL.containsKey(player.getUuid())) return ActionResult.FAIL;
            return ActionResult.PASS;
        });

        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (world.isClient() || !isStaff(stack)) return TypedActionResult.pass(stack);

            if (player.getItemCooldownManager().isCoolingDown(stack.getItem())) {
                return TypedActionResult.fail(stack);
            }

            LivingEntity target = null;
            double best = Double.MAX_VALUE;
            for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class,
                    player.getBoundingBox().expand(RADIUS), le -> le != player && le.isAlive())) {
                double d = e.squaredDistanceTo(player);
                if (d < best) { best = d; target = e; }
            }

            if (target == null) {
                player.sendMessage(Text.literal("§7Рядом нет врагов."), true);
                return TypedActionResult.fail(stack);
            }

            freeze(target, (ServerWorld) world);
            player.getItemCooldownManager().set(stack.getItem(), COOLDOWN_TICKS);
            player.sendMessage(Text.literal("§bВраг заморожен!"), true);
            return TypedActionResult.success(stack);
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            int now = server.getTicks();
            Iterator<Map.Entry<UUID, Integer>> it = FROZEN_UNTIL.entrySet().iterator();

            while (it.hasNext()) {
                Map.Entry<UUID, Integer> en = it.next();
                UUID id = en.getKey();
                Entity e = null;
                for (ServerWorld w : server.getWorlds()) {
                    e = w.getEntity(id);
                    if (e != null) break;
                }

                boolean expired = now >= en.getValue() || e == null || !e.isAlive();
                if (expired) {
                    if (e instanceof MobEntity mob) mob.setAiDisabled(false);
                    FROZEN_POS.remove(id);
                    it.remove();
                    continue;
                }

                Vec3d pos = FROZEN_POS.get(id);
                if (e instanceof ServerPlayerEntity p) {
                    if (Math.abs(p.getX() - pos.x) > 0.01 || Math.abs(p.getZ() - pos.z) > 0.01) {
                        p.networkHandler.requestTeleport(pos.x, p.getY(), pos.z, p.getYaw(), p.getPitch());
                    }
                    p.setVelocity(0, Math.min(p.getVelocity().y, 0), 0);
                    p.velocityModified = true;
                }
                e.setFrozenTicks(e.getMinFreezeDamageTicks());
            }
        });
    }

    private static void freeze(LivingEntity target, ServerWorld world) {
        FROZEN_UNTIL.put(target.getUuid(), world.getServer().getTicks() + FREEZE_TICKS);
        FROZEN_POS.put(target.getUuid(), target.getPos());

        if (target instanceof MobEntity mob) mob.setAiDisabled(true);
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, FREEZE_TICKS, 6));

        world.spawnParticles(ParticleTypes.SNOWFLAKE,
                target.getX(), target.getY() + 1, target.getZ(), 40, 0.4, 0.8, 0.4, 0.02);
        world.playSound(null, target.getBlockPos(), SoundEvents.BLOCK_GLASS_BREAK,
                SoundCategory.PLAYERS, 1f, 0.6f);
    }

    private static boolean isStaff(ItemStack s) {
        NbtComponent c = s.get(DataComponentTypes.CUSTOM_DATA);
        return c != null && c.contains("ice_staff");
    }

    private static boolean hasStaff(PlayerEntity p) {
        for (int i = 0; i < p.getInventory().size(); i++) {
            if (isStaff(p.getInventory().getStack(i))) return true;
        }
        return false;
    }
}
