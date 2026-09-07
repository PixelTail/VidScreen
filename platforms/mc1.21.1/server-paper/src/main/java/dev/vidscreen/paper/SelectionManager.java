package dev.vidscreen.paper;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import dev.vidscreen.domain.BlockPoint;
import dev.vidscreen.domain.DimensionKey;

final class SelectionManager {
    private final Map<UUID, Selection> selections = new ConcurrentHashMap<>();
    private final NamespacedKey wandKey;

    SelectionManager(Plugin plugin) {
        this.wandKey = new NamespacedKey(plugin, "screen_wand");
    }

    NamespacedKey wandKey() {
        return wandKey;
    }

    boolean isWand(ItemStack item) {
        return item != null
                && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE);
    }

    Selection setFirst(Player player, Block block) {
        DimensionKey dimension = dimension(block);
        return selections.compute(player.getUniqueId(), (ignored, current) ->
                new Selection(
                        point(block),
                        current != null && dimension.equals(current.dimension()) ? current.second() : null,
                        dimension));
    }

    Selection setSecond(Player player, Block block) {
        DimensionKey dimension = dimension(block);
        return selections.compute(player.getUniqueId(), (ignored, current) ->
                new Selection(
                        current != null && dimension.equals(current.dimension()) ? current.first() : null,
                        point(block),
                        dimension));
    }

    Selection get(Player player) {
        return selections.get(player.getUniqueId());
    }

    private static BlockPoint point(Block block) {
        return new BlockPoint(block.getX(), block.getY(), block.getZ());
    }

    private static DimensionKey dimension(Block block) {
        return new DimensionKey(block.getWorld().getKey().toString());
    }

    static final class Selection {
        private final BlockPoint first;
        private final BlockPoint second;
        private final DimensionKey dimension;

        Selection(BlockPoint first, BlockPoint second, DimensionKey dimension) {
            this.first = first;
            this.second = second;
            this.dimension = dimension;
        }

        BlockPoint first() {
            return first;
        }

        BlockPoint second() {
            return second;
        }

        DimensionKey dimension() {
            return dimension;
        }

        boolean complete() {
            return first != null && second != null;
        }
    }
}
