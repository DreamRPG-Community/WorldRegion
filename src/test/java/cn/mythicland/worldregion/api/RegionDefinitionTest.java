package cn.mythicland.worldregion.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class RegionDefinitionTest {

    @Test
    void regionIdRejectsCommandAmbiguousCharacters() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new RegionDefinition(
                        "主城 区域",
                        "&a主城",
                        10,
                        "world",
                        new RegionBounds(0, 0, 0, 1, 1, 1)
                )
        );
    }
}
