package cn.mythicland.worldregion.api;

import java.util.Objects;

/**
 * Immutable public definition of one named world region.
 *
 * @param id          stable internal identifier
 * @param displayName colored name shown to players
 * @param priority    matching priority, where larger values win
 * @param worldName   WorldManager logical name or Bukkit world name
 * @param bounds      inclusive three-dimensional region bounds
 */
public record RegionDefinition(
        String id,
        String displayName,
        int priority,
        String worldName,
        RegionBounds bounds
) {

    /**
     * Validates the public region model.
     */
    public RegionDefinition {
        id = requireIdentifier(id);
        displayName = requireText(displayName, "displayName");
        worldName = requireText(worldName, "worldName");
        Objects.requireNonNull(bounds, "bounds");
    }

    private static String requireIdentifier(String value) {
        String identifier = requireText(value, "id");
        if (!identifier.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("id contains unsupported characters: " + identifier);
        }
        return identifier;
    }

    private static String requireText(String value, String fieldName) {
        String text = Objects.requireNonNull(value, fieldName).trim();
        if (text.isBlank()) throw new IllegalArgumentException(fieldName + " cannot be blank");
        return text;
    }
}
