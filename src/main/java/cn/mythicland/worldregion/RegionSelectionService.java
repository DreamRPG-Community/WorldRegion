package cn.mythicland.worldregion;

import cn.mythicland.lib.bootstrap.annotation.InjectComponent;
import org.bukkit.Location;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds temporary two-corner selections for administrators.
 */
@InjectComponent
final class RegionSelectionService {

    private final Map<UUID, Selection> selections = new ConcurrentHashMap<>();

    void setFirst(UUID playerId, Location location) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(location, "location");
        selections.compute(
                playerId,
                (ignored, current) -> new Selection(location, current == null ? null : current.second())
        );
    }

    void setSecond(UUID playerId, Location location) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(location, "location");
        selections.compute(
                playerId,
                (ignored, current) -> new Selection(current == null ? null : current.first(), location)
        );
    }

    Optional<Selection> find(UUID playerId) {
        return Optional.ofNullable(selections.get(playerId));
    }

    void clear(UUID playerId) {
        selections.remove(playerId);
    }

    void clearAll() {
        selections.clear();
    }

    /**
     * Immutable defensive selection snapshot.
     *
     * @param first  first selected location, if set
     * @param second second selected location, if set
     */
    record Selection(Location first, Location second) {

        Selection {
            first = first == null ? null : first.clone();
            second = second == null ? null : second.clone();
        }

        @Override
        public Location first() {
            return first == null ? null : first.clone();
        }

        @Override
        public Location second() {
            return second == null ? null : second.clone();
        }
    }
}
