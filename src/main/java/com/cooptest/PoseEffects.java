package com.cooptest;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
public class PoseEffects {
    public static void playIdleEffects(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        Vec3d pos = player.getPos();
        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP,
                SoundCategory.PLAYERS, 1.0f, 1.2f);
    }
    public static void playActionEffects(ServerPlayerEntity pusher, ServerPlayerEntity target) {
        ServerWorld world = pusher.getServerWorld();
        Vec3d pusherPos = pusher.getPos();
        Vec3d targetPos = target.getPos();
        float yaw = pusher.getYaw();
        double radians = Math.toRadians(yaw);
        double forwardX = -Math.sin(radians) * 0.8;
        double forwardZ = Math.cos(radians) * 0.8;
        double rightX = Math.cos(radians) * 0.3;
        double rightZ = Math.sin(radians) * 0.3;
        double leftX = -rightX;
        double leftZ = -rightZ;
        double handY = pusherPos.y + 1.0;
        double rightHandX = pusherPos.x + forwardX + rightX;
        double rightHandZ = pusherPos.z + forwardZ + rightZ;
        double leftHandX = pusherPos.x + forwardX + leftX;
        double leftHandZ = pusherPos.z + forwardZ + leftZ;
        world.playSound(null, pusherPos.x, pusherPos.y, pusherPos.z,
                SoundEvents.ENTITY_PLAYER_ATTACK_STRONG,
                SoundCategory.PLAYERS, 1.0f, 0.8f);
        world.playSound(null, pusherPos.x, pusherPos.y, pusherPos.z,
                SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP,
                SoundCategory.PLAYERS, 1.0f, 1.0f);
        for (int i = 0; i < 5; i++) {
            world.spawnParticles(ParticleTypes.CLOUD,
                    rightHandX, handY, rightHandZ,
                    1, 0.1, 0.1, 0.1, 0.02);
        }
        for (int i = 0; i < 5; i++) {
            world.spawnParticles(ParticleTypes.CLOUD,
                    leftHandX, handY, leftHandZ,
                    1, 0.1, 0.1, 0.1, 0.02);
        }
        world.spawnParticles(ParticleTypes.POOF,
                rightHandX, handY, rightHandZ,
                3, 0.1, 0.1, 0.1, 0.02);
        world.spawnParticles(ParticleTypes.POOF,
                leftHandX, handY, leftHandZ,
                3, 0.1, 0.1, 0.1, 0.02);
        world.playSound(null, targetPos.x, targetPos.y, targetPos.z,
                SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP,
                SoundCategory.PLAYERS, 0.8f, 1.5f);
        world.spawnParticles(ParticleTypes.POOF,
                targetPos.x, targetPos.y + 0.5, targetPos.z,
                3, 0.2, 0.2, 0.2, 0.02);
        pusher.setVelocity(pusher.getVelocity().add(0, -0.15, 0));
        pusher.velocityModified = true;
    }
    public static void playLaunchTrailEffects(ServerPlayerEntity target) {
        ServerWorld world = target.getServerWorld();
        Vec3d pos = target.getPos();
        world.spawnParticles(ParticleTypes.CLOUD,
                pos.x, pos.y + 0.5, pos.z,
                2, 0.2, 0.2, 0.2, 0.02);
    }
}