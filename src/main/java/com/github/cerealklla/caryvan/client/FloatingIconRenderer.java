package com.github.cerealklla.caryvan.client;

import java.util.function.Function;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Floats a purely-visual, non-interactive item above a custom structure block -- explicit user
 * request, 2026-10-28, for Wainwright Station ("should have a wheel floating over it like our
 * other crafting tables"). Direct copy of Lyfe's own `client.FloatingIconRenderer` (which Lyfe's
 * own crafting/cooking/research/fish-cleaning structures all already use for this exact purpose) --
 * generic, no Lyfe-specific logic, copied rather than pulled in through Caryvan's existing
 * `compileOnly` optional dependency on Lyfe (that dependency is for server-side bridge classes only;
 * a client renderer referenced unconditionally from {@code CaryvanModClient} must never hard-depend
 * on an optional mod's classes, same established convention this suite already follows everywhere
 * else -- a Lyfe-less server must never crash Caryvan's own rendering).
 */
public class FloatingIconRenderer<T extends BlockEntity> implements BlockEntityRenderer<T, FloatingIconRenderState> {

    private static final double FLOAT_HEIGHT = 1.3;
    private static final float SCALE = 0.5F;
    private static final float DEGREES_PER_TICK = 1.0F;

    // Same full-bright sentinel Lyfe's own identical renderer uses -- see its own class doc for the
    // real live bug this works around (the structure's real ambient light rendered the icon solid
    // black outdoors).
    private static final int FULL_BRIGHT = 15728880;

    private final Function<T, ItemStack> iconResolver;

    public FloatingIconRenderer(final Function<T, ItemStack> iconResolver) {
        this.iconResolver = iconResolver;
    }

    @Override
    public FloatingIconRenderState createRenderState() {
        return new FloatingIconRenderState();
    }

    @Override
    public void extractRenderState(final T blockEntity, final FloatingIconRenderState state,
                                    final float partialTicks, final Vec3 cameraPosition, final ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(blockEntity, state, breakProgress);
        final ItemStack icon = iconResolver.apply(blockEntity);
        final Level level = blockEntity.getLevel();
        final long gameTime = level != null ? level.getGameTime() : 0L;
        state.yRotDegrees = (gameTime + partialTicks) * DEGREES_PER_TICK;
        Minecraft.getInstance().getItemModelResolver()
                .updateForTopItem(state.itemRenderState, icon, ItemDisplayContext.GROUND, level, null, 0);
    }

    @Override
    public void submit(final FloatingIconRenderState state, final PoseStack poseStack, final SubmitNodeCollector submitNodeCollector, final CameraRenderState camera) {
        if (state.itemRenderState.isEmpty()) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0.5, FLOAT_HEIGHT, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees((float) state.yRotDegrees));
        poseStack.scale(SCALE, SCALE, SCALE);
        // outlineColor=0 -- "no outline," not -1 (opaque white silhouette); same real bug Lyfe's own
        // identical renderer already found and documented.
        state.itemRenderState.submit(poseStack, submitNodeCollector, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }
}
