package cn.mythicland.worldregion;

import cn.mythicland.lib.api.LibApi;
import cn.mythicland.lib.bootstrap.annotation.CommandCompleter;
import cn.mythicland.lib.bootstrap.annotation.CommandComponent;
import cn.mythicland.lib.bootstrap.annotation.CommandHandler;
import cn.mythicland.lib.command.CommandContext;
import cn.mythicland.lib.command.CommandUsageException;
import cn.mythicland.lib.location.LocationSnapper;
import cn.mythicland.lib.text.LegacyText;
import cn.mythicland.worldregion.api.LandmarkDefinition;
import cn.mythicland.worldregion.api.RegionBounds;
import cn.mythicland.worldregion.api.RegionDefinition;
import cn.mythicland.worldregion.api.WorldRegionApi;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.logging.Level;

/**
 * Handles WorldRegion's administrative command tree.
 */
@CommandComponent(value = "worldregion", permission = "worldregion.admin")
final class WorldRegionCommand {

    private static final String ROOT = "/worldregion";
    private static final String LANDMARK_USAGE = ROOT + " landmark set <id> <显示名>\n"
            + ROOT + " landmark delete <id>";

    private final JavaPlugin plugin;
    private final LibApi lib;
    private final WorldRegionPlugin worldRegionPlugin;
    private final WorldRegionApi service;
    private final WorldRegionDataStore dataStore;
    private final RegionSelectionService selections;
    private final WorldManagerIntegration worldManager;

    WorldRegionCommand(
            JavaPlugin plugin,
            LibApi lib,
            WorldRegionPlugin worldRegionPlugin,
            WorldRegionApi service,
            WorldRegionDataStore dataStore,
            RegionSelectionService selections,
            WorldManagerIntegration worldManager
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.lib = Objects.requireNonNull(lib, "lib");
        this.worldRegionPlugin = Objects.requireNonNull(worldRegionPlugin, "worldRegionPlugin");
        this.service = Objects.requireNonNull(service, "service");
        this.dataStore = Objects.requireNonNull(dataStore, "dataStore");
        this.selections = Objects.requireNonNull(selections, "selections");
        this.worldManager = Objects.requireNonNull(worldManager, "worldManager");
    }

    private static ParsedDefinition parse(List<String> arguments) {
        String id = arguments.getFirst();
        List<String> nameParts = new ArrayList<>(arguments.subList(1, arguments.size()));
        int priority = 0;
        if (nameParts.size() > 1) {
            String last = nameParts.getLast();
            if (isIntegerLiteral(last)) {
                try {
                    priority = Integer.parseInt(last);
                    nameParts.removeLast();
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException("区域优先级超出整数范围: " + last, exception);
                }
            }
        }
        String displayName = String.join(" ", nameParts).trim();
        if (displayName.isBlank()) {
            throw new IllegalArgumentException(
                    "区域显示名不能为空。\n" + ROOT + " create <id> <显示名> [priority]"
            );
        }
        return new ParsedDefinition(id, displayName, priority);
    }

    private static boolean isIntegerLiteral(String value) {
        return value.matches("[+-]?\\d+");
    }

    private static String format(Location location) {
        return location.getWorld().getName()
                + " ("
                + location.getBlockX()
                + ", "
                + location.getBlockY()
                + ", "
                + location.getBlockZ()
                + ")";
    }

    @CommandHandler("pos1")
    void positionOne(CommandContext context) {
        setPosition(context, true);
    }

    @CommandHandler("pos2")
    void positionTwo(CommandContext context) {
        setPosition(context, false);
    }

    @CommandHandler("clear")
    void clear(CommandContext context) {
        context.requireArguments(0);
        if (context.sender() instanceof Player player) selections.clear(player.getUniqueId());
        context.sender().sendMessage(LegacyText.colorize("&a已清除当前区域选区。"));
    }

    @CommandHandler(value = "create", usage = ROOT + " create <id> <显示名> [priority]")
    void create(CommandContext context) {
        context.requireAtLeast(2);
        if (!(context.sender() instanceof Player player)) {
            context.sender().sendMessage(LegacyText.colorize("&c该命令只能由玩家执行。"));
            return;
        }
        RegionSelectionService.Selection selection = selections.find(player.getUniqueId())
                .orElseThrow(() -> new IllegalStateException("请先使用木铲设置两个区域角点。"));
        Location first = selection.first();
        Location second = selection.second();
        if (first == null || second == null) {
            throw new IllegalStateException("请先使用木铲设置两个区域角点。\n" + context.usage());
        }
        if (!Objects.requireNonNull(first.getWorld(), "first.world")
                .equals(Objects.requireNonNull(second.getWorld(), "second.world"))) {
            throw new IllegalStateException("区域两个角点必须位于同一世界。");
        }
        ParsedDefinition parsed = parse(context.arguments());
        RegionDefinition definition = new RegionDefinition(
                parsed.id(),
                parsed.displayName(),
                parsed.priority(),
                worldManager.logicalNameOrBukkitName(first.getWorld()),
                RegionBounds.between(first, second)
        );
        dataStore.createRegion(definition);
        selections.clear(player.getUniqueId());
        context.sender().sendMessage(LegacyText.colorize("&a区域已保存: " + definition.id()));
    }

    @CommandHandler(value = "delete", usage = ROOT + " delete <id>")
    void delete(CommandContext context) {
        context.requireArguments(1);
        String id = context.argument(0);
        dataStore.deleteRegion(id);
        context.sender().sendMessage(LegacyText.colorize("&a区域已删除: " + id));
    }

    @CommandHandler("list")
    void list(CommandContext context) {
        context.requireArguments(0);
        Collection<RegionDefinition> regions = service.regions();
        if (regions.isEmpty()) {
            context.sender().sendMessage(LegacyText.colorize("&c当前没有保存区域。"));
            return;
        }
        for (RegionDefinition region : regions) {
            context.sender().sendMessage(LegacyText.colorize(
                    "&a" + region.id() + " - " + region.displayName()
                            + " &7(priority=" + region.priority() + ")"
            ));
        }
    }

    @CommandHandler(value = "landmark", usage = LANDMARK_USAGE)
    void landmark(CommandContext context) {
        context.requireAtLeast(1);
        switch (context.argument(0).toLowerCase(Locale.ROOT)) {
            case "set" -> setLandmark(context, context.arguments().subList(1, context.arguments().size()));
            case "delete" -> deleteLandmark(context, context.arguments().subList(1, context.arguments().size()));
            default -> throw context.invalidUsage();
        }
    }

    @CommandCompleter("landmark")
    List<String> completeLandmark(CommandContext context) {
        if (context.arguments().size() == 1) {
            String prefix = context.argument(0).toLowerCase(Locale.ROOT);
            return java.util.stream.Stream.of("set", "delete")
                    .filter(value -> value.startsWith(prefix))
                    .toList();
        }
        if (context.arguments().size() == 2 && context.argument(0).equalsIgnoreCase("delete")) {
            String prefix = context.argument(1).toLowerCase(Locale.ROOT);
            return service.landmarks().stream()
                    .map(LandmarkDefinition::id)
                    .filter(id -> id.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .toList();
        }
        return List.of();
    }

    @CommandHandler(
            value = "teleport",
            aliases = {"tp"},
            usage = ROOT + " teleport <地标id>"
    )
    void teleport(CommandContext context) {
        context.requireArguments(1);
        if (!(context.sender() instanceof Player player)) {
            context.sender().sendMessage(LegacyText.colorize("&c该命令只能由玩家执行。"));
            return;
        }
        String id = context.argument(0);
        context.sender().sendMessage(LegacyText.colorize("&a正在传送到地标: " + id));
        service.teleport(player, id).whenComplete((success, error) ->
                lib.runOnMain(() -> {
                    if (error != null) {
                        context.sender().sendMessage(LegacyText.colorize(
                                "&c地标传送失败: " + LibApi.rootCauseMessage(error)
                        ));
                        return;
                    }
                    context.sender().sendMessage(Boolean.TRUE.equals(success)
                            ? LegacyText.colorize("&a已传送到地标: " + id)
                            : LegacyText.colorize("&c地标传送被 Bukkit 拒绝: " + id));
                }).whenComplete((ignored, notificationFailure) -> {
                    if (notificationFailure != null) {
                        plugin.getLogger().log(
                                Level.SEVERE,
                                "Failed to report WorldRegion landmark teleport result: " + id,
                                notificationFailure
                        );
                    }
                })
        );
    }

    @CommandCompleter("teleport")
    List<String> completeTeleport(CommandContext context) {
        if (context.arguments().size() != 1) return List.of();
        String prefix = context.argument(0).toLowerCase(Locale.ROOT);
        return service.landmarks().stream()
                .map(LandmarkDefinition::id)
                .filter(id -> id.toLowerCase(Locale.ROOT).startsWith(prefix))
                .toList();
    }

    @CommandHandler("reload")
    void reload(CommandContext context) {
        context.requireArguments(0);
        worldRegionPlugin.reloadWorldRegion();
        context.sender().sendMessage(LegacyText.colorize("&aWorldRegion 配置已重载。"));
    }

    private void setPosition(CommandContext context, boolean first) {
        context.requireArguments(0);
        if (!(context.sender() instanceof Player player)) {
            context.sender().sendMessage(LegacyText.colorize("&c该命令只能由玩家执行。"));
            return;
        }
        Location location = player.getLocation().getBlock().getLocation();
        if (first) selections.setFirst(player.getUniqueId(), location);
        else selections.setSecond(player.getUniqueId(), location);
        player.sendMessage(LegacyText.colorize(
                "&a已设置区域" + (first ? "第一" : "第二") + "角: " + format(location)
        ));
    }

    private void setLandmark(CommandContext context, List<String> arguments) {
        if (!(context.sender() instanceof Player player)) {
            context.sender().sendMessage(LegacyText.colorize("&c该命令只能由玩家执行。"));
            return;
        }
        if (arguments.size() < 2) {
            throw new CommandUsageException(ROOT + " landmark set <id> <显示名>");
        }
        String displayName = String.join(" ", arguments.subList(1, arguments.size())).trim();
        Location location = LocationSnapper.snapBlockAndView(player.getLocation());
        LandmarkDefinition definition = new LandmarkDefinition(
                arguments.getFirst(),
                displayName,
                worldManager.logicalNameOrBukkitName(Objects.requireNonNull(location.getWorld(), "player.world")),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch()
        );
        dataStore.createLandmark(definition);
        context.sender().sendMessage(LegacyText.colorize("&a地标已保存: " + definition.id()));
    }

    private void deleteLandmark(CommandContext context, List<String> arguments) {
        if (arguments.size() != 1) {
            throw new CommandUsageException(ROOT + " landmark delete <id>");
        }
        String id = arguments.getFirst();
        dataStore.deleteLandmark(id);
        context.sender().sendMessage(LegacyText.colorize("&a地标已删除: " + id));
    }

    private record ParsedDefinition(String id, String displayName, int priority) {
    }
}
