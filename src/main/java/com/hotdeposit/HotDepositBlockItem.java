package com.hotdeposit;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

import java.util.function.Consumer;

/** Adds the usage tooltip; since 1.21.5 tooltips live on the item, not the block. */
public class HotDepositBlockItem extends BlockItem {
	public HotDepositBlockItem(Block block, Properties properties) {
		super(block, properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay tooltipDisplay, Consumer<Component> tooltip, TooltipFlag flag) {
		tooltip.accept(Component.translatable("block.hotdeposit.hot_deposit.tooltip").withStyle(ChatFormatting.GRAY));
	}
}
