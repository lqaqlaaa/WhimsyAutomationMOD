package dev.whimsyautomation.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipContext;

import java.util.List;

/**
 * Block item with a localized, stable machine description.
 * The description is supplied by the language file rather than hard-coded text.
 */
public final class MachineBlockItem extends BlockItem {
    private final String descriptionKey;

    public MachineBlockItem(Block block, Item.Properties properties, String descriptionKey) {
        super(block, properties);
        this.descriptionKey = descriptionKey;
    }

    @Override
    protected void appendHoverText(ItemStack stack, TooltipContext context,
                                   List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        tooltipComponents.add(Component.translatable(descriptionKey));
    }
}
