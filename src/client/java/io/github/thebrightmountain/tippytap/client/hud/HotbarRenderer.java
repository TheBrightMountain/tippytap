package io.github.thebrightmountain.tippytap.client.hud;

import io.github.thebrightmountain.tippytap.client.TippytapConfig;
import io.github.thebrightmountain.tippytap.mixin.client.InventoryAccessor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

public final class HotbarRenderer {
    private HotbarRenderer() {}

    public static void render(GuiGraphics gfx, Minecraft client,
                               TippytapConfig config, boolean dimmed) {
        if (!config.showCustomHotbar) return;

        int screenWidth  = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();
        int slotSize     = config.hotbarSlotSize;
        int slotGap      = config.hotbarGap;
        int hotbarWidth  = 9 * slotSize + 8 * slotGap;
        int hotbarLeft   = (screenWidth - hotbarWidth) / 2;
        int hotbarTop    = screenHeight - config.hotbarOffsetY - slotSize;

        // Main hotbar background
        gfx.fill(hotbarLeft - HudConst.BG_PAD, hotbarTop - HudConst.BG_PAD,
                 hotbarLeft + hotbarWidth + HudConst.BG_PAD, hotbarTop + slotSize + HudConst.BG_PAD,
                 HudConst.C_PANEL_BG);

        int selectedSlot = ((InventoryAccessor) client.player.getInventory()).getSelected();

        for (int slot = 0; slot < 9; slot++) {
            int     slotLeft = hotbarLeft + slot * (slotSize + slotGap);
            boolean selected = slot == selectedSlot;

            gfx.fill(slotLeft, hotbarTop, slotLeft + slotSize, hotbarTop + slotSize,
                     selected ? HudConst.C_SLOT_SELECTED_BG : HudConst.C_SLOT_IDLE_BG);
            DrawUtils.drawBorder(gfx, slotLeft, hotbarTop, slotSize, slotSize,
                                 selected ? HudConst.C_SLOT_SELECTED_BORDER : HudConst.C_SLOT_IDLE_BORDER);

            ItemStack stack = client.player.getInventory().getItem(slot);
            if (!stack.isEmpty()) {
                int itemX = slotLeft  + (slotSize - HudConst.ITEM_SIZE) / 2;
                int itemY = hotbarTop + (slotSize - HudConst.ITEM_SIZE) / 2;
                gfx.renderItem(stack, itemX, itemY);
                gfx.renderItemDecorations(client.font, stack, itemX, itemY);
            }

            DrawUtils.drawSmallLabel(gfx, client, slotLeft + 1.5f, hotbarTop + 1.5f,
                                     keyLabel(client.options.keyHotbarSlots[slot]),
                                     selected ? HudConst.C_SLOT_LABEL_SELECTED : HudConst.C_SLOT_LABEL_IDLE);
        }

        if (dimmed) gfx.fill(hotbarLeft - HudConst.BG_PAD, hotbarTop - HudConst.BG_PAD,
                             hotbarLeft + hotbarWidth + HudConst.BG_PAD, hotbarTop + slotSize + HudConst.BG_PAD,
                             HudConst.C_PANEL_DIM);

        renderArmorBar(gfx, client, config, hotbarLeft, hotbarTop, slotSize, slotGap, hotbarWidth, dimmed);
        renderOffhandSlot(gfx, client, hotbarLeft, hotbarTop, slotSize, dimmed);
    }

    private static void renderArmorBar(GuiGraphics gfx, Minecraft client, TippytapConfig config,
                                        int hotbarLeft, int hotbarTop,
                                        int slotSize, int slotGap, int hotbarWidth, boolean dimmed) {
        if (!config.showArmorBar) return;

        EquipmentSlot[] slots = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
        java.util.List<ItemStack> wornPieces = new java.util.ArrayList<>();
        for (EquipmentSlot slot : slots) {
            ItemStack piece = client.player.getItemBySlot(slot);
            if (!piece.isEmpty()) wornPieces.add(piece);
        }
        if (wornPieces.isEmpty()) return;

        int armorLeft  = hotbarLeft + hotbarWidth + HudConst.OFFHAND_GAP;
        int armorWidth = wornPieces.size() * slotSize + (wornPieces.size() - 1) * slotGap;

        gfx.fill(armorLeft - HudConst.BG_PAD, hotbarTop - HudConst.BG_PAD,
                 armorLeft + armorWidth + HudConst.BG_PAD, hotbarTop + slotSize + HudConst.BG_PAD,
                 HudConst.C_PANEL_BG);

        for (int i = 0; i < wornPieces.size(); i++) {
            int slotLeft = armorLeft + i * (slotSize + slotGap);
            gfx.fill(slotLeft, hotbarTop, slotLeft + slotSize, hotbarTop + slotSize, HudConst.C_SLOT_IDLE_BG);
            DrawUtils.drawBorder(gfx, slotLeft, hotbarTop, slotSize, slotSize, HudConst.C_SLOT_IDLE_BORDER);
            int itemX = slotLeft  + (slotSize - HudConst.ITEM_SIZE) / 2;
            int itemY = hotbarTop + (slotSize - HudConst.ITEM_SIZE) / 2;
            gfx.renderItem(wornPieces.get(i), itemX, itemY);
            gfx.renderItemDecorations(client.font, wornPieces.get(i), itemX, itemY);
        }

        if (dimmed) gfx.fill(armorLeft - HudConst.BG_PAD, hotbarTop - HudConst.BG_PAD,
                             armorLeft + armorWidth + HudConst.BG_PAD, hotbarTop + slotSize + HudConst.BG_PAD,
                             HudConst.C_PANEL_DIM);
    }

    private static void renderOffhandSlot(GuiGraphics gfx, Minecraft client,
                                           int hotbarLeft, int hotbarTop, int slotSize, boolean dimmed) {
        ItemStack offhand = client.player.getOffhandItem();
        if (offhand.isEmpty()) return;

        int offhandLeft = hotbarLeft - HudConst.OFFHAND_GAP - slotSize;

        gfx.fill(offhandLeft - HudConst.BG_PAD, hotbarTop - HudConst.BG_PAD,
                 offhandLeft + slotSize + HudConst.BG_PAD, hotbarTop + slotSize + HudConst.BG_PAD,
                 HudConst.C_PANEL_BG);
        gfx.fill(offhandLeft, hotbarTop, offhandLeft + slotSize, hotbarTop + slotSize, HudConst.C_SLOT_IDLE_BG);
        DrawUtils.drawBorder(gfx, offhandLeft, hotbarTop, slotSize, slotSize, HudConst.C_SLOT_IDLE_BORDER);

        int itemX = offhandLeft + (slotSize - HudConst.ITEM_SIZE) / 2;
        int itemY = hotbarTop   + (slotSize - HudConst.ITEM_SIZE) / 2;
        gfx.renderItem(offhand, itemX, itemY);
        gfx.renderItemDecorations(client.font, offhand, itemX, itemY);

        if (dimmed) gfx.fill(offhandLeft - HudConst.BG_PAD, hotbarTop - HudConst.BG_PAD,
                             offhandLeft + slotSize + HudConst.BG_PAD, hotbarTop + slotSize + HudConst.BG_PAD,
                             HudConst.C_PANEL_DIM);
    }

    /** Extracts a short display label from a key mapping (max 3 chars, upper-cased). */
    private static String keyLabel(KeyMapping key) {
        String s = key.saveString();
        s = s.substring(s.lastIndexOf('.') + 1).toUpperCase();
        return s.length() > 3 ? s.substring(0, 3) : s;
    }
}
