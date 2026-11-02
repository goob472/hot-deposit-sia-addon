package com.hotdeposit;

import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

public class HotDepositBlock extends HorizontalDirectionalBlock implements EntityBlock {
	public static final MapCodec<HotDepositBlock> CODEC = simpleCodec(HotDepositBlock::new);
	/** Eyes open and glowing: a nearby player has items that match nearby storage. */
	public static final BooleanProperty OPEN = BooleanProperty.create("open");
	// Same footprint as a vanilla chest
	private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 14, 15);
	// Holding right-click repeats the use action every few ticks; don't deposit for each repeat
	private static final long COOLDOWN_TICKS = 10;

	public HotDepositBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, false));
	}

	@Override
	protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, OPEN);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new HotDepositBlockEntity(pos, state);
	}

	/**
	 * Also reached when right-clicking with an item in hand (vanilla falls through to this unless the player is sneaking),
	 * so the player doesn't need an empty hand.
	 */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
			long now = level.getGameTime();
			if (HotDepositTracker.tryStartDeposit(player.getUUID(), now, COOLDOWN_TICKS)) {
				HotDepositLogic.deposit(serverLevel, serverPlayer, pos);
				// inventory and storage contents just changed: re-check the eyes on the next tick
				if (level.getBlockEntity(pos) instanceof HotDepositBlockEntity blockEntity) {
					blockEntity.invalidate(now);
				}
			}
		}
		return InteractionResult.sidedSuccess(level.isClientSide());
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("block.hotdeposit.hot_deposit.tooltip").withStyle(ChatFormatting.GRAY));
	}
}
