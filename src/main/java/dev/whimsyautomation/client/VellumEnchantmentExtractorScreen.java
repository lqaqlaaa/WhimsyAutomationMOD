package dev.whimsyautomation.client;

import com.google.gson.JsonObject;
import dev.vellum.mod.client.VellumContainerScreen;
import dev.whimsyautomation.menu.EnchantmentExtractorMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Vellum-backed container screen; Minecraft still owns all slot interactions. */
public final class VellumEnchantmentExtractorScreen extends VellumContainerScreen<EnchantmentExtractorMenu> {
    public VellumEnchantmentExtractorScreen(EnchantmentExtractorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "whimsy_automation:vellum/enchantment_extractor.html",
                VellumEnchantmentExtractorScreen::pageData);
        driver().onMessage("page", value -> send(value.getAsInt() < 0 ? 3 : 4));
        driver().onMessage("auto", value -> send(0));
        driver().onMessage("split", value -> send(2));
        driver().onMessage("process", value -> send(1));
    }

    private static JsonObject pageData(EnchantmentExtractorMenu menu) {
        JsonObject data = new JsonObject();
        data.addProperty("page", menu.getPage());
        data.addProperty("pageCount", menu.getPageCount());
        data.addProperty("auto", menu.isAutoMode());
        data.addProperty("split", menu.isSplitLevels());
        data.addProperty("applyPreview", menu.getApplyPreview());
        data.addProperty("applying", menu.isApplying());
        return data;
    }

    private void send(int button) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
        }
    }
}
