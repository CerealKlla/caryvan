package com.github.cerealklla.caryvan.wainwright;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Wainwright Station -- a single, untiered crafting structure (Wainwright Station Part 2, see the
 * root {@code Minecraft\} project's approved plan file) where a player crafts wood-species-specific
 * Wheels/Cart Body/Wagon Body parts ("Parts" tab) and assembles a finished, mixed-wood cart/wagon
 * ("Body" tab). Same shape as Lyfe's own {@code CraftingStructureBlock} (right-click opens a
 * {@link WainwrightStationBlockEntity}'s menu) -- a parallel copy, not a shared base class, matching
 * this suite's established "no shared crafting-structure base exists" precedent.
 */
public class WainwrightStationBlock extends HorizontalDirectionalBlock implements EntityBlock {

    private final MapCodec<WainwrightStationBlock> instanceCodec = simpleCodec(WainwrightStationBlock::new);

    public WainwrightStationBlock(final Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return instanceCodec;
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new WainwrightStationBlockEntity(pos, state);
    }

    @Override
    public InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos, final Player player, final BlockHitResult hit) {
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)
                || !(level.getBlockEntity(pos) instanceof WainwrightStationBlockEntity station)) {
            return InteractionResult.SUCCESS;
        }
        serverPlayer.openMenu(station);
        return InteractionResult.SUCCESS_SERVER;
    }
}
