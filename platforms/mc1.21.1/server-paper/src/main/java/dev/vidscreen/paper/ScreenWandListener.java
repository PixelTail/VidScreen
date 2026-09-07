package dev.vidscreen.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

final class ScreenWandListener implements Listener {
    private final SelectionManager selections;

    ScreenWandListener(SelectionManager selections) {
        this.selections = selections;
    }

    ItemStack createWand() {
        ItemStack wand = new ItemStack(Material.STICK);
        ItemMeta meta = wand.getItemMeta();
        meta.displayName(Component.text("VidScreen Screen Wand", NamedTextColor.AQUA));
        meta.lore(java.util.List.of(
                Component.text("Left-click: first corner", NamedTextColor.GRAY),
                Component.text("Right-click: second corner", NamedTextColor.GRAY)));
        meta.getPersistentDataContainer().set(selections.wandKey(), PersistentDataType.BYTE, (byte) 1);
        wand.setItemMeta(meta);
        return wand;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || event.getClickedBlock() == null
                || !selections.isWand(event.getItem())) {
            return;
        }

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            selections.setFirst(event.getPlayer(), event.getClickedBlock());
            event.getPlayer().sendMessage(Component.text(
                    "First screen corner: " + format(event.getClickedBlock()), NamedTextColor.GREEN));
            event.setCancelled(true);
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            selections.setSecond(event.getPlayer(), event.getClickedBlock());
            event.getPlayer().sendMessage(Component.text(
                    "Second screen corner: " + format(event.getClickedBlock()), NamedTextColor.GREEN));
            event.setCancelled(true);
        }
    }

    private static String format(org.bukkit.block.Block block) {
        return block.getX() + ", " + block.getY() + ", " + block.getZ();
    }
}
