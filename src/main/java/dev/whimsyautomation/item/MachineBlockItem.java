package dev.whimsyautomation.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * Block item with a localized machine description.
 */
public final class MachineBlockItem extends BlockItem {
    private final String descriptionKey;

    public MachineBlockItem(Block block, Item.Properties properties, String descriptionKey) {
        super(block, properties);
        this.descriptionKey = descriptionKey;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        tooltipComponents.add(Component.translatable(descriptionKey));
    }
}
