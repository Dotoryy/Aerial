package cc.aerial.client.render;

import cc.aerial.client.mixin.EntityAccessor;
import cc.aerial.client.mixin.ItemStackRenderStateAccessor;
import cc.aerial.client.mixin.LayerRenderStateAccessor;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

public final class ItemPhysicsUtility {
    private ItemPhysicsUtility() {
    }

    public static boolean needsOffset(BlockPos pos, Level level) {
        var state = level.getBlockState(pos);
        return state.is(Blocks.SNOW) || state.is(Blocks.SOUL_SAND) || state.is(Blocks.MUD);
    }

    public static void calculateRotation(ItemEntity entity, ItemEntityRenderState state, float realtimeDeltaTicks, boolean paused) {
        float rotateBy = realtimeDeltaTicks * 0.25f;
        if (paused) {
            rotateBy = 0.0f;
        }

        Vec3 motionMultiplier = ((EntityAccessor) (Entity) entity).aerial$getStuckSpeedMultiplier();
        if (motionMultiplier != null && motionMultiplier.lengthSqr() > 0.0) {
            rotateBy = (float) (rotateBy * motionMultiplier.x * 0.2);
        }

        boolean gui3d = ((ItemEntityRenderStateExtender) state).aerial$isBlock();
        if (gui3d) {
            if (!entity.onGround()) {
                rotateBy *= 2.0f;
                Fluid fluid = calculateFluid(entity, false);
                if (fluid == null) {
                    fluid = calculateFluid(entity, true);
                }
                if (fluid != null) {
                    rotateBy /= 1.0f + getViscosity(fluid, entity.level());
                }
                entity.setXRot(entity.getXRot() + rotateBy);
            }
        } else if (!Double.isNaN(entity.getX()) && !Double.isNaN(entity.getY()) && !Double.isNaN(entity.getZ()) && entity.level() != null) {
            if (entity.onGround()) {
                entity.setXRot(0.0f);
            } else {
                rotateBy *= 2.0f;
                Fluid fluid = calculateFluid(entity, false);
                if (fluid != null) {
                    rotateBy /= 1.0f + getViscosity(fluid, entity.level());
                }
                entity.setXRot(entity.getXRot() + rotateBy);
            }
        }
    }

    public static boolean submit(ItemEntityRenderState state, PoseStack pose, SubmitNodeCollector collector, RandomSource rand) {
        if (state.ageInTicks < 1.0f) {
            return false;
        }

        pose.pushPose();
        rand.setSeed(state.seed);
        int count = getModelCount(state.count);
        boolean gui3d = ((ItemEntityRenderStateExtender) state).aerial$isBlock();
        ItemTransform transform = ((LayerRenderStateAccessor) ((ItemStackRenderStateAccessor) (Object) state.item).aerial$callFirstLayer()).aerial$getItemTransform();

        pose.mulPose(Axis.XP.rotation((float) (Math.PI / 2.0)));
        pose.mulPose(Axis.ZP.rotation(((ItemEntityRenderStateExtender) state).aerial$getYRot()));

        if (state.ageInTicks != 0.0f) {
            if (gui3d) {
                pose.translate(0.0, -0.2, -0.08);
            } else if (((ItemEntityRenderStateExtender) state).aerial$hasAdditionalOffset()) {
                pose.translate(0.0, 0.0, -0.14 - state.bobOffset * 0.007957747154594767);
            } else {
                pose.translate(0.0, 0.0, -0.04 - state.bobOffset * 0.007957747154594767);
            }

            double height = transform.scale().y();
            if (gui3d) {
                pose.translate(0.0, height, 0.0);
            }
            pose.mulPose(Axis.YP.rotation(((ItemEntityRenderStateExtender) state).aerial$getXRot()));
            if (gui3d) {
                pose.translate(0.0, -height, 0.0);
            }
        }

        float sx = transform.scale().x();
        float sy = transform.scale().y();
        float sz = transform.scale().z();

        for (int i = 0; i < count; i++) {
            pose.pushPose();
            if (i > 0 && gui3d) {
                pose.translate((rand.nextFloat() * 2.0f - 1.0f) * sx, (rand.nextFloat() * 2.0f - 1.0f) * sy, (rand.nextFloat() * 2.0f - 1.0f) * sz);
            }
            state.item.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
            pose.popPose();
        }

        pose.popPose();
        return true;
    }

    public static int getModelCount(int count) {
        if (count > 48) return 5;
        if (count > 32) return 4;
        if (count > 16) return 3;
        return count > 1 ? 2 : 1;
    }

    public static Fluid calculateFluid(ItemEntity item, boolean below) {
        if (item.level() == null) {
            return null;
        }
        double posY = item.position().y;
        BlockPos pos = below ? item.blockPosition().below() : item.blockPosition();
        FluidState fluidState = item.level().getFluidState(pos);
        Fluid fluid = fluidState.getType();
        if (fluid != null && fluid.getTickDelay(item.level()) != 0) {
            if (below) {
                return fluid;
            }
            double filled = fluidState.getHeight(item.level(), pos);
            return posY - pos.getY() - 0.2 <= filled ? fluid : null;
        }
        return null;
    }

    public static float getViscosity(Fluid fluid, Level level) {
        return fluid == null ? 0.0f : fluid.getTickDelay(level) / 5.0f;
    }
}
