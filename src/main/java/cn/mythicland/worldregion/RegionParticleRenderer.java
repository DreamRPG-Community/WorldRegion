package cn.mythicland.worldregion;

import cn.mythicland.lib.bootstrap.annotation.InjectComponent;
import cn.mythicland.worldregion.api.RegionBounds;
import cn.mythicland.worldregion.api.RegionDefinition;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Objects;

/**
 * Renders saved region cuboid edges as white single redstone particles for administrators holding the wand.
 */
@InjectComponent
final class RegionParticleRenderer {

    private static final double EDGE_PARTICLE_SPACING = 0.5D;
    private static final double EDGE_PARTICLE_SIZE = 1.0D;
    private static final int MAX_EDGE_STEPS = 128;

    private final WorldRegionSettings settings;
    private final WorldRegionDataStore dataStore;
    private final RegionSelectionService selections;

    RegionParticleRenderer(
            WorldRegionSettings settings,
            WorldRegionDataStore dataStore,
            RegionSelectionService selections
    ) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.dataStore = Objects.requireNonNull(dataStore, "dataStore");
        this.selections = Objects.requireNonNull(selections, "selections");
    }

    private static void drawParticle(
            Player player,
            RegionParticleGeometry.Point point,
            Color color,
            double maxDistanceSquared
    ) {
        Location location = new Location(player.getWorld(), point.x(), point.y(), point.z());
        if (location.distanceSquared(player.getLocation()) > maxDistanceSquared) return;
        Particle.REDSTONE.builder()
                .location(location)
                // Paper 1.12.2 encodes the exact colored redstone particle with count zero.
                .count(0)
                .receivers(player)
                .force(true)
                .extra(EDGE_PARTICLE_SIZE)
                .color(color)
                .spawn();
    }

    static Color colorFor(String displayName) {
        String translated = ChatColor.translateAlternateColorCodes('&', displayName);
        for (int index = 0; index + 1 < translated.length(); index++) {
            if (translated.charAt(index) != ChatColor.COLOR_CHAR) continue;
            ChatColor color = ChatColor.getByChar(translated.charAt(index + 1));
            if (color == null || !color.isColor()) continue;
            return toBukkitColor(color);
        }
        return Color.WHITE;
    }

    @SuppressWarnings("DuplicateBranchesInSwitch")
    private static Color toBukkitColor(ChatColor color) {
        return switch (color) {
            case BLACK -> Color.fromRGB(0, 0, 0);
            case DARK_BLUE -> Color.fromRGB(0, 0, 170);
            case DARK_GREEN -> Color.fromRGB(0, 170, 0);
            case DARK_AQUA -> Color.fromRGB(0, 170, 170);
            case DARK_RED -> Color.fromRGB(170, 0, 0);
            case DARK_PURPLE -> Color.fromRGB(170, 0, 170);
            case GOLD -> Color.fromRGB(255, 170, 0);
            case GRAY -> Color.fromRGB(170, 170, 170);
            case DARK_GRAY -> Color.fromRGB(85, 85, 85);
            case BLUE -> Color.fromRGB(85, 85, 255);
            case GREEN -> Color.fromRGB(85, 255, 85);
            case AQUA -> Color.fromRGB(85, 255, 255);
            case RED -> Color.fromRGB(255, 85, 85);
            case LIGHT_PURPLE -> Color.fromRGB(255, 85, 255);
            case YELLOW -> Color.fromRGB(255, 255, 85);
            case WHITE -> Color.WHITE;
            default -> Color.WHITE;
        };
    }

    void renderAll() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Region particles must render on the primary thread");
        }
        WorldRegionSettings.SettingsSnapshot current = settings.snapshot();
        if (!current.particlesEnabled()) return;
        for (Player player : Bukkit.getOnlinePlayers()) renderPlayer(player, current);
    }

    void render(Player player) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Region particles must render on the primary thread");
        }
        renderPlayer(Objects.requireNonNull(player, "player"), settings.snapshot());
    }

    private void renderPlayer(Player player, WorldRegionSettings.SettingsSnapshot current) {
        if (!player.hasPermission("worldregion.admin")) return;
        ItemStack heldItem = player.getInventory().getItemInMainHand();
        if (heldItem == null || heldItem.getType() != Material.WOOD_SPADE) return;
        Location viewerLocation = player.getLocation();
        double maxDistanceSquared = (double) current.renderDistance() * current.renderDistance();
        renderSelection(player, selections.find(player.getUniqueId()).orElse(null), maxDistanceSquared);
        List<RegionDefinition> regions = dataStore.visibleRegions(
                viewerLocation,
                current.renderDistance(),
                current.maxRegionsPerPlayer()
        );
        for (RegionDefinition region : regions) {
            renderRegion(player, region, maxDistanceSquared);
        }
    }

    private void renderSelection(
            Player player,
            RegionSelectionService.Selection selection,
            double maxDistanceSquared
    ) {
        if (selection == null || selection.first() == null) return;
        Location first = Objects.requireNonNull(selection.first(), "selection.first");
        if (!Objects.requireNonNull(first.getWorld(), "selection.first.world").equals(player.getWorld())) return;
        Location second = selection.second();
        if (second == null) {
            renderCornerMarker(player, first, maxDistanceSquared);
            return;
        }
        if (!Objects.requireNonNull(second.getWorld(), "selection.second.world").equals(player.getWorld())) return;
        renderBounds(player, RegionBounds.between(first, second), Color.WHITE, maxDistanceSquared);
    }

    private void renderRegion(Player player, RegionDefinition region, double maxDistanceSquared) {
        renderBounds(player, region.bounds(), colorFor(region.displayName()), maxDistanceSquared);
    }

    private void renderBounds(
            Player player,
            RegionBounds bounds,
            Color color,
            double maxDistanceSquared
    ) {
        for (RegionParticleGeometry.Edge edge : RegionParticleGeometry.outline(bounds)) {
            for (RegionParticleGeometry.Point point : RegionParticleGeometry.sample(
                    edge,
                    EDGE_PARTICLE_SPACING,
                    MAX_EDGE_STEPS
            )) {
                drawParticle(player, point, color, maxDistanceSquared);
            }
        }
    }

    private void renderCornerMarker(Player player, Location corner, double maxDistanceSquared) {
        double x = corner.getBlockX();
        double y = corner.getBlockY();
        double z = corner.getBlockZ();
        double size = 0.75D;
        drawEdge(player, x, y, z, x + size, y, z, maxDistanceSquared);
        drawEdge(player, x, y, z, x, y + size, z, maxDistanceSquared);
        drawEdge(player, x, y, z, x, y, z + size, maxDistanceSquared);
    }

    private void drawEdge(
            Player player,
            double startX,
            double startY,
            double startZ,
            double endX,
            double endY,
            double endZ,
            double maxDistanceSquared
    ) {
        RegionParticleGeometry.Edge edge = new RegionParticleGeometry.Edge(
                new RegionParticleGeometry.Point(startX, startY, startZ),
                new RegionParticleGeometry.Point(endX, endY, endZ)
        );
        for (RegionParticleGeometry.Point point : RegionParticleGeometry.sample(
                edge,
                EDGE_PARTICLE_SPACING,
                MAX_EDGE_STEPS
        )) {
            drawParticle(player, point, Color.WHITE, maxDistanceSquared);
        }
    }
}
