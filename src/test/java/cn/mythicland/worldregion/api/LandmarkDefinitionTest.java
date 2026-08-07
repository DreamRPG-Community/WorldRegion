package cn.mythicland.worldregion.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class LandmarkDefinitionTest {

    @Test
    void landmarkRejectsNonFiniteCoordinates() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new LandmarkDefinition(
                        "spawn",
                        "主城",
                        "world",
                        Double.NaN,
                        7.0D,
                        2.5D,
                        0.0F,
                        0.0F
                )
        );
    }
}
