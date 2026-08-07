package cn.mythicland.worldregion;

import cn.mythicland.lib.bootstrap.annotation.ListenerComponent;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

/**
 * Records administrator region corners from wooden shovel interactions.
 */
@ListenerComponent
final class RegionSelectionListener implements Listener {

    private final RegionSelectionService selections;
    private final RegionParticleRenderer particles;

    RegionSelectionListener(
            RegionSelectionService selections,
            RegionParticleRenderer particles
    ) {
        this.selections = Objects.requireNonNull(selections, "selections");
        this.particles = Objects.requireNonNull(particles, "particles");
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        if (!player.hasPermission("worldregion.admin")) return;
        ItemStack heldItem = player.getInventory().getItemInMainHand();
        if (heldItem == null || heldItem.getType() != Material.WOOD_SPADE) return;
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) return;

        event.setCancelled(true);
        if (action == Action.LEFT_CLICK_BLOCK) {
            selections.setFirst(player.getUniqueId(), clickedBlock.getLocation());
            player.sendMessage(ChatColor.GREEN + "已设置区域第一角: " + format(clickedBlock));
            particles.render(player);
            return;
        }
        selections.setSecond(player.getUniqueId(), clickedBlock.getLocation());
        player.sendMessage(ChatColor.GREEN + "已设置区域第二角: " + format(clickedBlock));
        particles.render(player);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        selections.clear(event.getPlayer().getUniqueId());
    }

    private static String format(Block block) {
        return block.getWorld().getName()
                + " ("
                + block.getX()
                + ", "
                + block.getY()
                + ", "
                + block.getZ()
                + ")";
    }
}
