package dev.whimsyautomation.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.vellum.mod.client.VellumContainerScreen;
import dev.whimsyautomation.menu.DeconstructionTableMenu;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** HTML/CSS page with native Minecraft slots and container click/drag behavior. */
public final class VellumDeconstructionTableScreen extends VellumContainerScreen<DeconstructionTableMenu> {
    public VellumDeconstructionTableScreen(DeconstructionTableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "whimsy_automation:vellum/deconstruction_table.html",
                VellumDeconstructionTableScreen::pageData);
        driver().onMessage("recipe", value -> {
            int delta = value.getAsInt() < 0 ? -1 : 1;
            menu.cyclePreview(delta);
            send(delta < 0 ? 0 : 1);
        });
        driver().onMessage("page", value -> send(value.getAsInt() < 0 ? 4 : 5));
        driver().onMessage("auto", value -> send(2));
        driver().onMessage("lock", value -> send(6));
        driver().onMessage("process", value -> send(3));
    }

    private static JsonObject pageData(DeconstructionTableMenu menu) {
        JsonObject data = new JsonObject();
        data.addProperty("page", menu.getPage());
        data.addProperty("pageCount", menu.getPageCount());
        data.addProperty("auto", menu.isAutoMode());
        data.addProperty("locked", menu.isLocked());
        data.addProperty("recipeStatus", menu.getRecipeStatus());
        data.addProperty("recipeId", menu.getPreviewRecipeId());
        data.addProperty("candidateCount", menu.getCandidateCount());

        ItemStack result = menu.getPreviewResult();
        data.addProperty("resultId", result.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(result.getItem()).toString());
        data.addProperty("resultCount", result.getCount());

        JsonArray preview = new JsonArray();
        for (ItemStack stack : menu.getPreviewStacks()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            entry.addProperty("count", stack.getCount());
            entry.addProperty("name", stack.isEmpty() ? "" : stack.getHoverName().getString());
            preview.add(entry);
        }
        data.add("preview", preview);
        return data;
    }

    private void send(int button) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
        }
    }
}
