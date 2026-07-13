package io.github.sweetzonzi.py_port.network.python.payload;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyContext;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyHandleResult;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyPayload;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyPayloadType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.CompletableFuture;

public record AttackEntityPayload(
        int attacker_id,
        int target_id
) implements PyPayload {

    public static final Codec<AttackEntityPayload> CODEC =
            RecordCodecBuilder.create(instance ->
                    instance.group(
                            Codec.INT.fieldOf("attacker_id")
                                    .forGetter(AttackEntityPayload::attacker_id),
                            Codec.INT.fieldOf("target_id")
                                    .forGetter(AttackEntityPayload::target_id)
                    ).apply(instance, AttackEntityPayload::new)
            );

    public static final PyPayloadType<AttackEntityPayload> TYPE =
            new PyPayloadType<>("attack_entity", CODEC);

    @Override
    public PyPayloadType<?> type() {
        return TYPE;
    }

    public static PyHandleResult handle(
            AttackEntityPayload payload,
            PyContext context
    ) {
        var server = context.getServer();

        if (server == null) {
            return PyHandleResult.fail("Server not running");
        }

        CompletableFuture<JsonObject> future = new CompletableFuture<>();

        server.execute(() -> {
            try {
                Entity attacker = null;
                ServerLevel attackerLevel = null;

                for (ServerLevel level : server.getAllLevels()) {
                    Entity found = level.getEntity(payload.attacker_id());
                    if (found != null) {
                        attacker = found;
                        attackerLevel = level;
                        break;
                    }
                }

                if (attacker == null || attackerLevel == null) {
                    future.completeExceptionally(
                            new RuntimeException("Attacker not found: " + payload.attacker_id())
                    );
                    return;
                }

                Entity target = attackerLevel.getEntity(payload.target_id());

                if (target == null) {
                    future.completeExceptionally(
                            new RuntimeException("Target not found: " + payload.target_id())
                    );
                    return;
                }

                if (attacker == target) {
                    future.completeExceptionally(
                            new RuntimeException("Entity cannot attack itself")
                    );
                    return;
                }

                JsonObject result = new JsonObject();

                double distance = attacker.distanceTo(target);

                if (distance > 4.5) {
                    result.addProperty("hit", false);
                    result.addProperty("reason", "Target out of range");
                    result.addProperty("distance", distance);
                    future.complete(result);
                    return;
                }

                boolean hit = false;
                float damageDealt = 0.0f;

                if (attacker instanceof ServerPlayer player) {
                    // ========== 玩家攻击：使用属性值而非武器 ==========
                    if (player.getAttackStrengthScale(0.5f) < 0.9f) {
                        result.addProperty("hit", false);
                        result.addProperty("reason", "Player attack is cooling down");
                        future.complete(result);
                        return;
                    }

                    player.lookAt(
                            net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
                            target.getBoundingBox().getCenter()
                    );

                    // 强制使用 attack_damage 属性值
                    float attackDamage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);

                    if (target instanceof LivingEntity livingTarget) {
                        DamageSource source = attackerLevel.damageSources().playerAttack(player);
                        hit = livingTarget.hurt(source, attackDamage);
                        damageDealt = attackDamage;

                        // 播放攻击动画
                        player.swing(InteractionHand.MAIN_HAND);
                        player.resetAttackStrengthTicker();  // 重置攻击冷却
                    }

                } else if (attacker instanceof Mob mob) {
                    if (!(target instanceof LivingEntity livingTarget) || !livingTarget.isAlive()) {
                        result.addProperty("hit", false);
                        result.addProperty("reason", "Target is not a living entity");
                        future.complete(result);
                        return;
                    }

                    mob.lookAt(
                            net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
                            livingTarget.getBoundingBox().getCenter()
                    );

                    // 绕过和平模式检查，使用属性值
                    float attackDamage = (float) mob.getAttributeValue(Attributes.ATTACK_DAMAGE);

                    // 直接创建伤害源并施加伤害（绕过 doHurtTarget 的和平模式检查）
                    DamageSource source = attackerLevel.damageSources().mobAttack(mob);
                    hit = livingTarget.hurt(source, attackDamage);
                    damageDealt = attackDamage;

                    // 播放攻击动画
                    mob.swing(InteractionHand.MAIN_HAND);

                    // 设置攻击关系（用于仇恨系统等）
                    if (hit) {
                        mob.setLastHurtMob(livingTarget);
                        livingTarget.setLastHurtByMob(mob);

                        // 应用击退效果
                        double knockback = mob.getAttributeValue(Attributes.ATTACK_KNOCKBACK);
                        if (knockback > 0) {
                            Vec3 knockbackVec = new Vec3(
                                    livingTarget.getX() - mob.getX(),
                                    0.0,
                                    livingTarget.getZ() - mob.getZ()
                            ).normalize().scale(knockback * 0.5);
                            livingTarget.push(knockbackVec.x, 0.4, knockbackVec.z);
                        }
                    }

                } else {
                    result.addProperty("hit", false);
                    result.addProperty("reason", "Attacker is neither a player nor a mob");
                    future.complete(result);
                    return;
                }

                result.addProperty("hit", hit);
                result.addProperty("damage_dealt", damageDealt);
                result.addProperty("distance", distance);

                if (target instanceof LivingEntity livingTarget) {
                    result.addProperty("target_health", livingTarget.getHealth());
                    result.addProperty("target_max_health", livingTarget.getMaxHealth());
                } else {
                    result.addProperty("target_health", -1.0f);
                }

                future.complete(result);

            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        try {
            return PyHandleResult.success(future.get());
        } catch (Exception e) {
            return PyHandleResult.fail("Attack failed: " + e.getMessage());
        }
    }
}