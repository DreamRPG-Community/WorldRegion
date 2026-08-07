package cn.mythicland.worldregion;

import cn.mythicland.worldregion.api.RegionBounds;
import cn.mythicland.worldregion.api.RegionDefinition;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies Actionbar transitions for region entry, exit, and direct region changes.
 */
class RegionTransitionTrackerTest {

    private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void enteringFromOutsideEmitsOnlyAnEntryTransition() {
        RegionTransitionTracker tracker = new RegionTransitionTracker();
        RegionDefinition region = region("town", "&a城镇");

        assertTrue(tracker.update(PLAYER_ID, Optional.empty()).isEmpty());
        RegionTransitionTracker.Transition transition = tracker.update(
                PLAYER_ID,
                Optional.of(region)
        ).orElseThrow();

        assertTrue(transition.exited().isEmpty());
        assertEquals(region, transition.entered().orElseThrow());
    }

    @Test
    void leavingToOutsideEmitsOnlyTheRegionThatWasLeft() {
        RegionTransitionTracker tracker = new RegionTransitionTracker();
        RegionDefinition region = region("town", "&a城镇");
        tracker.update(PLAYER_ID, Optional.of(region));

        RegionTransitionTracker.Transition transition = tracker.update(
                PLAYER_ID,
                Optional.empty()
        ).orElseThrow();

        assertEquals(region, transition.exited().orElseThrow());
        assertTrue(transition.entered().isEmpty());
    }

    @Test
    void movingDirectlyBetweenRegionsEmitsBothSidesOfTheChange() {
        RegionTransitionTracker tracker = new RegionTransitionTracker();
        RegionDefinition first = region("first", "&a第一区域");
        RegionDefinition second = region("second", "&b第二区域");
        tracker.update(PLAYER_ID, Optional.of(first));

        RegionTransitionTracker.Transition transition = tracker.update(
                PLAYER_ID,
                Optional.of(second)
        ).orElseThrow();

        assertEquals(first, transition.exited().orElseThrow());
        assertEquals(second, transition.entered().orElseThrow());
    }

    @Test
    void remainingInsideTheSameRegionDoesNotRepeatTheActionbar() {
        RegionTransitionTracker tracker = new RegionTransitionTracker();
        RegionDefinition region = region("town", "&a城镇");
        tracker.update(PLAYER_ID, Optional.of(region));

        assertTrue(tracker.update(PLAYER_ID, Optional.of(region)).isEmpty());
    }

    @Test
    void enteringNestedRegionDoesNotAnnounceLeavingTheOuterRegion() {
        RegionTransitionTracker tracker = new RegionTransitionTracker();
        RegionDefinition outer = region(
                "outer",
                "&a外层",
                new RegionBounds(0, 0, 0, 10, 10, 10)
        );
        RegionDefinition inner = region(
                "inner",
                "&b内层",
                new RegionBounds(2, 2, 2, 4, 4, 4)
        );
        tracker.update(PLAYER_ID, Optional.of(outer));

        RegionTransitionTracker.Transition transition = tracker.update(
                PLAYER_ID,
                Optional.of(inner)
        ).orElseThrow();
        Location from = new Location(null, 1.5D, 1.0D, 1.0D);
        Location to = new Location(null, 2.5D, 2.0D, 2.0D);

        assertFalse(RegionTransitionListener.shouldAnnounceLeave(transition, from, to));
        assertTrue(RegionTransitionListener.shouldAnnounceEnter(transition, from, to));
    }

    @Test
    void leavingNestedRegionDoesNotAnnounceEnteringTheOuterRegion() {
        RegionTransitionTracker tracker = new RegionTransitionTracker();
        RegionDefinition outer = region(
                "outer",
                "&a外层",
                new RegionBounds(0, 0, 0, 10, 10, 10)
        );
        RegionDefinition inner = region(
                "inner",
                "&b内层",
                new RegionBounds(2, 2, 2, 4, 4, 4)
        );
        tracker.update(PLAYER_ID, Optional.of(inner));

        RegionTransitionTracker.Transition transition = tracker.update(
                PLAYER_ID,
                Optional.of(outer)
        ).orElseThrow();
        Location from = new Location(null, 2.5D, 2.0D, 2.0D);
        Location to = new Location(null, 1.5D, 1.0D, 1.0D);

        assertTrue(RegionTransitionListener.shouldAnnounceLeave(transition, from, to));
        assertFalse(RegionTransitionListener.shouldAnnounceEnter(transition, from, to));
    }

    private static RegionDefinition region(String id, String displayName) {
        return region(id, displayName, new RegionBounds(0, 0, 0, 1, 1, 1));
    }

    private static RegionDefinition region(
            String id,
            String displayName,
            RegionBounds bounds
    ) {
        return new RegionDefinition(
                id,
                displayName,
                0,
                "world",
                bounds
        );
    }
}
