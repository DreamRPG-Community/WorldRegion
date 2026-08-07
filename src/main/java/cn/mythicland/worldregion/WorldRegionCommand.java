package cn.mythicland.worldregion;

import cn.mythicland.lib.api.LibApi;
import cn.mythicland.lib.bootstrap.BukkitCommandComponent;
import cn.mythicland.lib.bootstrap.annotation.CommandComponent;
import cn.mythicland.lib.bootstrap.annotation.InjectComponent;
import cn.mythicland.lib.command.CommandRouter;
import cn.mythicland.lib.command.CommandUsageException;
import cn.mythicland.lib.command.Subcommand;
import cn.mythicland.lib.command.VanillaCommandMessages;
import cn.mythicland.lib.location.LocationSnapper;
import cn.mythicland.lib.text.LegacyText;
import cn.mythicland.worldregion.api.LandmarkDefinition;
import cn.mythicland.worldregion.api.RegionBounds;
import cn.mythicland.worldregion.api.RegionDefinition;
import cn.mythicland.worldregion.api.WorldRegionApi;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Level;

/**
 * Registers WorldRegion's administrative command tree.
 */
@InjectComponent
@CommandComponent
final class WorldRegionCommand implements BukkitCommandComponent {

    private final CommandRouter router;

    WorldRegionCommand(
            JavaPlugin plugin,
            LibApi lib,
            WorldRegionPlugin worldRegionPlugin,
            WorldRegionApi service,
            WorldRegionDataStore dataStore,
            RegionSelectionService selections,
            WorldManagerIntegration worldManager
    ) {
        this.router = Objects.requireNonNull(lib, "lib").createCommandRouter(
                Objects.requireNonNull(plugin, "plugin"),
                "worldregion"
        );
        router.register(new PositionCommand(selections, true));
        router.register(new PositionCommand(selections, false));
        router.register(new ClearSelectionCommand(selections));
        router.register(new CreateRegionCommand(dataStore, selections, worldManager));
        router.register(new DeleteRegionCommand(dataStore));
        router.register(new ListRegionsCommand(service));
        router.register(new LandmarkCommand(dataStore, service, worldManager));
        router.register(new TeleportCommand(plugin, service, lib));
        router.register(new ReloadCommand(worldRegionPlugin));
    }

    @Override
    public String commandName() {
        return "worldregion";
    }

    @Override
    public CommandRouter executor() {
        return router;
    }

    @Override
    public CommandRouter tabCompleter() {
        return router;
    }

    private abstract static class AdminCommand implements Subcommand {

        @Override
        public String permission() {
            return "worldregion.admin";
        }
    }

    private static final class PositionCommand extends AdminCommand {

        private final RegionSelectionService selections;
        private final boolean first;

        private PositionCommand(RegionSelectionService selections, boolean first) {
            this.selections = selections;
            this.first = first;
        }

        @Override
        public String name() {
            return first ? "pos1" : "pos2";
        }

        @Override
        public String usage() {
            return "/worldregion " + name();
        }

        @Override
        public void execute(CommandSender sender, List<String> arguments) {
            if (!arguments.isEmpty()) throw new CommandUsageException(usage());
            if (!(sender instanceof Player player)) {
                sender.sendMessage(VanillaCommandMessages.red("该命令只能由玩家执行。"));
                return;
            }
            Location location = player.getLocation().getBlock().getLocation();
            if (first) selections.setFirst(player.getUniqueId(), location);
            else selections.setSecond(player.getUniqueId(), location);
            player.sendMessage(VanillaCommandMessages.green(
                    "已设置区域" + (first ? "第一" : "第二") + "角: " + format(location)
            ));
        }
    }

    private static final class ClearSelectionCommand extends AdminCommand {

        private final RegionSelectionService selections;

        private ClearSelectionCommand(RegionSelectionService selections) {
            this.selections = selections;
        }

        @Override
        public String name() {
            return "clear";
        }

        @Override
        public String usage() {
            return "/worldregion clear";
        }

        @Override
        public void execute(CommandSender sender, List<String> arguments) {
            if (!arguments.isEmpty()) throw new CommandUsageException(usage());
            if (sender instanceof Player player) selections.clear(player.getUniqueId());
            sender.sendMessage(VanillaCommandMessages.green("已清除当前区域选区。"));
        }
    }

    private static final class CreateRegionCommand extends AdminCommand {

        private final WorldRegionDataStore dataStore;
        private final RegionSelectionService selections;
        private final WorldManagerIntegration worldManager;

        private CreateRegionCommand(
                WorldRegionDataStore dataStore,
                RegionSelectionService selections,
                WorldManagerIntegration worldManager
        ) {
            this.dataStore = dataStore;
            this.selections = selections;
            this.worldManager = worldManager;
        }

        @Override
        public String name() {
            return "create";
        }

        @Override
        public String usage() {
            return "/worldregion create <id> <显示名> [priority]";
        }

        @Override
        public void execute(CommandSender sender, List<String> arguments) {
            if (arguments.size() < 2) throw new CommandUsageException(usage());
            if (!(sender instanceof Player player)) {
                sender.sendMessage(VanillaCommandMessages.red("该命令只能由玩家执行。"));
                return;
            }
            RegionSelectionService.Selection selection = selections.find(player.getUniqueId())
                    .orElseThrow(() -> new IllegalStateException("请先使用木铲设置两个区域角点。"));
            Location first = selection.first();
            Location second = selection.second();
            if (first == null || second == null) {
                throw new IllegalStateException("请先使用木铲设置两个区域角点。\n" + usage());
            }
            if (!Objects.requireNonNull(first.getWorld(), "first.world")
                    .equals(Objects.requireNonNull(second.getWorld(), "second.world"))) {
                throw new IllegalStateException("区域两个角点必须位于同一世界。");
            }
            ParsedDefinition parsed = parse(arguments);
            RegionDefinition definition = new RegionDefinition(
                    parsed.id(),
                    parsed.displayName(),
                    parsed.priority(),
                    worldManager.logicalNameOrBukkitName(first.getWorld()),
                    RegionBounds.between(first, second)
            );
            dataStore.createRegion(definition);
            selections.clear(player.getUniqueId());
            sender.sendMessage(VanillaCommandMessages.green("区域已保存: " + definition.id()));
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
                        "区域显示名不能为空。\n" + "/worldregion create <id> <显示名> [priority]"
                );
            }
            return new ParsedDefinition(id, displayName, priority);
        }

        private static boolean isIntegerLiteral(String value) {
            return value.matches("[+-]?\\d+");
        }

        private record ParsedDefinition(String id, String displayName, int priority) {
        }
    }

    private static final class DeleteRegionCommand extends AdminCommand {

        private final WorldRegionDataStore dataStore;

        private DeleteRegionCommand(WorldRegionDataStore dataStore) {
            this.dataStore = dataStore;
        }

        @Override
        public String name() {
            return "delete";
        }

        @Override
        public String usage() {
            return "/worldregion delete <id>";
        }

        @Override
        public void execute(CommandSender sender, List<String> arguments) {
            if (arguments.size() != 1) throw new CommandUsageException(usage());
            dataStore.deleteRegion(arguments.getFirst());
            sender.sendMessage(VanillaCommandMessages.green("区域已删除: " + arguments.getFirst()));
        }
    }

    private static final class ListRegionsCommand extends AdminCommand {

        private final WorldRegionApi service;

        private ListRegionsCommand(WorldRegionApi service) {
            this.service = service;
        }

        @Override
        public String name() {
            return "list";
        }

        @Override
        public String usage() {
            return "/worldregion list";
        }

        @Override
        public void execute(CommandSender sender, List<String> arguments) {
            if (!arguments.isEmpty()) throw new CommandUsageException(usage());
            Collection<RegionDefinition> regions = service.regions();
            if (regions.isEmpty()) {
                sender.sendMessage(VanillaCommandMessages.red("当前没有保存区域。"));
                return;
            }
            for (RegionDefinition region : regions) {
                sender.sendMessage(VanillaCommandMessages.green(
                        region.id() + " - " + LegacyText.colorize(region.displayName())
                                + " &7(priority=" + region.priority() + ")"
                ));
            }
        }
    }

    private static final class LandmarkCommand extends AdminCommand {

        private static final String USAGE = "/worldregion landmark set <id> <显示名>\n"
                + "/worldregion landmark delete <id>";

        private final WorldRegionDataStore dataStore;
        private final WorldRegionApi service;
        private final WorldManagerIntegration worldManager;

        private LandmarkCommand(
                WorldRegionDataStore dataStore,
                WorldRegionApi service,
                WorldManagerIntegration worldManager
        ) {
            this.dataStore = dataStore;
            this.service = service;
            this.worldManager = worldManager;
        }

        @Override
        public String name() {
            return "landmark";
        }

        @Override
        public String usage() {
            return USAGE;
        }

        @Override
        public void execute(CommandSender sender, List<String> arguments) {
            if (arguments.isEmpty()) throw new CommandUsageException(usage());
            switch (arguments.getFirst().toLowerCase(Locale.ROOT)) {
                case "set" -> set(sender, arguments.subList(1, arguments.size()));
                case "delete" -> delete(sender, arguments.subList(1, arguments.size()));
                default -> throw new CommandUsageException(usage());
            }
        }

        @Override
        public List<String> tabComplete(CommandSender sender, List<String> arguments) {
            if (arguments.size() == 1) {
                String prefix = arguments.getFirst().toLowerCase(Locale.ROOT);
                return List.of("set", "delete").stream()
                        .filter(value -> value.startsWith(prefix))
                        .toList();
            }
            if (arguments.size() == 2 && arguments.getFirst().equalsIgnoreCase("delete")) {
                String prefix = arguments.getLast().toLowerCase(Locale.ROOT);
                return service.landmarks().stream()
                        .map(LandmarkDefinition::id)
                        .filter(id -> id.toLowerCase(Locale.ROOT).startsWith(prefix))
                        .toList();
            }
            return List.of();
        }

        private void set(CommandSender sender, List<String> arguments) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(VanillaCommandMessages.red("该命令只能由玩家执行。"));
                return;
            }
            if (arguments.size() < 2) throw new CommandUsageException("/worldregion landmark set <id> <显示名>");
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
            sender.sendMessage(VanillaCommandMessages.green("地标已保存: " + definition.id()));
        }

        private void delete(CommandSender sender, List<String> arguments) {
            if (arguments.size() != 1) throw new CommandUsageException("/worldregion landmark delete <id>");
            dataStore.deleteLandmark(arguments.getFirst());
            sender.sendMessage(VanillaCommandMessages.green("地标已删除: " + arguments.getFirst()));
        }

    }

    private static final class TeleportCommand extends AdminCommand {

        private final JavaPlugin plugin;
        private final WorldRegionApi service;
        private final LibApi lib;

        private TeleportCommand(
                JavaPlugin plugin,
                WorldRegionApi service,
                LibApi lib
        ) {
            this.plugin = Objects.requireNonNull(plugin, "plugin");
            this.service = service;
            this.lib = lib;
        }

        @Override
        public String name() {
            return "teleport";
        }

        @Override
        public Collection<String> aliases() {
            return List.of("tp");
        }

        @Override
        public String usage() {
            return "/worldregion teleport <地标id>";
        }

        @Override
        public void execute(CommandSender sender, List<String> arguments) {
            if (arguments.size() != 1) throw new CommandUsageException(usage());
            if (!(sender instanceof Player player)) {
                sender.sendMessage(VanillaCommandMessages.red("该命令只能由玩家执行。"));
                return;
            }
            String id = arguments.getFirst();
            sender.sendMessage(VanillaCommandMessages.green("正在传送到地标: " + id));
            service.teleport(player, id).whenComplete((success, error) ->
                    lib.runOnMain(() -> {
                        if (error != null) {
                            sender.sendMessage(VanillaCommandMessages.red(
                                    "地标传送失败: " + LibApi.rootCauseMessage(error)
                            ));
                            return;
                        }
                        sender.sendMessage(Boolean.TRUE.equals(success)
                                ? VanillaCommandMessages.green("已传送到地标: " + id)
                                : VanillaCommandMessages.red("地标传送被 Bukkit 拒绝: " + id));
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
    }

    private static final class ReloadCommand extends AdminCommand {

        private final WorldRegionPlugin plugin;

        private ReloadCommand(WorldRegionPlugin plugin) {
            this.plugin = plugin;
        }

        @Override
        public String name() {
            return "reload";
        }

        @Override
        public String usage() {
            return "/worldregion reload";
        }

        @Override
        public void execute(CommandSender sender, List<String> arguments) {
            if (!arguments.isEmpty()) throw new CommandUsageException(usage());
            plugin.reloadWorldRegion();
            sender.sendMessage(VanillaCommandMessages.green("WorldRegion 配置已重载。"));
        }
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
}
