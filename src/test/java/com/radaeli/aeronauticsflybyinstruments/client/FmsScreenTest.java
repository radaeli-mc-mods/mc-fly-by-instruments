package com.radaeli.aeronauticsflybyinstruments.client;

import com.radaeli.aeronauticsflybyinstruments.fms.FmsModuleData;
import com.radaeli.aeronauticsflybyinstruments.fms.FmsWaypoint;
import com.radaeli.aeronauticsflybyinstruments.network.FmsActionPayload;
import com.radaeli.aeronauticsflybyinstruments.network.FmsScreenPayload;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FmsScreenTest {
    private static final String OVERWORLD = "minecraft:overworld";
    private final List<FmsActionPayload> sent = new ArrayList<>();

    @Test
    void typedFractionalCoordinatesBecomeTheContainingBlock() {
        assertEquals(3.0, FmsScreen.parseBlockCoordinate("3.25"), 0.0);
        assertEquals(-2.0, FmsScreen.parseBlockCoordinate("-1.3278908072976996"), 0.0);
        assertEquals(-11.0, FmsScreen.parseBlockCoordinate("-10.671499778056285"), 0.0);
        assertNull(FmsScreen.parseBlockCoordinate("NaN"));
        assertNull(FmsScreen.parseBlockCoordinate("not a coordinate"));
        assertNull(FmsScreen.parseBlockCoordinate("40000000"), "Beyond the world border the server refuses it");
    }

    @Test
    void clickingAWaypointFliesDirectToItAndClickingTheActiveOneClearsIt() {
        var data = new FmsModuleData();
        var landing = waypoint("Landing", 10, 64, 0);
        data.add(landing);
        var screen = screen(data, -1, "");
        screen.toggleDestination(landing);
        assertEquals("select", sent.getLast().action());
        assertEquals(landing.id().toString(), sent.getLast().waypointId());

        data.select(landing.id());
        screen.acceptServerState(payload(data, -1, ""));
        screen.toggleDestination(landing);
        assertEquals("clear_target", sent.getLast().action());
    }

    @Test
    void destinationShowsDistanceAndBearingFromThePlayer() {
        var screen = screen(new FmsModuleData(), -1, "");
        // The player stands at 12.6 70.2 -3.4; waypoints are stored as whole blocks.
        var east = waypoint("East", 312, 70, -4);
        var far = waypoint("Far", 12, 70, -2504);
        assertEquals("299 m", screen.distanceLabel(east));
        assertEquals("090°", screen.bearingLabel(east));
        assertEquals("2.5 km", screen.distanceLabel(far));
        assertEquals("000°", screen.bearingLabel(far), "North is -Z");
        assertEquals("300 64 0", FmsScreen.positionLabel(waypoint("Block", 300, 64, 0)));
        assertFalse(screen.sameDimension(new FmsWaypoint(UUID.randomUUID(), "Nether", "minecraft:the_nether", 0, 0, 0)));
    }

    @Test
    void longListsScrollAndOpenWithTheDestinationInView() {
        var data = new FmsModuleData();
        var waypoints = new ArrayList<FmsWaypoint>();
        for (int i = 0; i < FmsScreen.VISIBLE_WAYPOINTS * 2; i++) {
            waypoints.add(waypoint("WPT " + i, i, 64, 0));
            data.add(waypoints.getLast());
        }
        assertEquals(0, FmsScreen.scrollToShow(waypoints, null));
        assertEquals(0, FmsScreen.scrollToShow(waypoints, waypoints.get(FmsScreen.VISIBLE_WAYPOINTS - 1).id()),
                "A destination already in the first rows keeps the list at the top");
        data.select(waypoints.getLast().id());
        var screen = screen(data, -1, "");
        assertEquals(FmsScreen.VISIBLE_WAYPOINTS, screen.scroll(), "The last waypoint is the bottom row");
        screen.scrollTo(2);
        assertEquals(2, screen.scroll());
    }

    @Test
    void scrollbarDragMapsTheTrackToTheRows() {
        var rows = new java.util.concurrent.atomic.AtomicInteger(-1);
        var bar = new HudPanel.ScrollBar(0, 0, 2, 70, 14, 7, rows::set);
        bar.dragTo(-50);
        assertEquals(0, rows.get());
        bar.dragTo(500);
        assertEquals(7, rows.get(), "The bottom of the track shows the last rows");
        bar.dragTo(35);
        assertEquals(4, rows.get(), "The middle of the track is about halfway");
        assertEquals(35, HudPanel.thumbHeight(70, 14, 7));
        assertEquals(6, HudPanel.thumbHeight(70, 1000, 7), "The thumb never gets too small to grab");
    }

    @Test
    void compassLinkIsOneClickAndARelinkCarriesTheConfirmation() {
        var data = new FmsModuleData();
        assertEquals("missing", screen(data, -1, "").compassState());
        assertEquals("linked", screen(data, 4, data.id().toString()).compassState());

        var unlinked = screen(data, 4, "");
        assertEquals("unlinked", unlinked.compassState());
        unlinked.linkCompass(false);
        assertEquals("bind_navigator", sent.getLast().action());
        assertEquals(4, sent.getLast().navigatorDisplaySlot());
        assertFalse(sent.getLast().confirmRebind());

        var elsewhere = screen(data, 7, UUID.randomUUID().toString());
        assertEquals("other", elsewhere.compassState());
        elsewhere.linkCompass(true);
        assertTrue(sent.getLast().confirmRebind(), "The Relink button is the confirmation");
    }

    @Test
    void newWaypointStartsAtThePlayersBlockAndAddsOnlyWhenNamed() {
        var parent = screen(new FmsModuleData(), -1, "");
        var editor = new FmsWaypointScreen(parent, null);
        assertEquals("12", editor.value(FmsWaypointScreen.X));
        assertEquals("70", editor.value(FmsWaypointScreen.Y));
        assertEquals("-4", editor.value(FmsWaypointScreen.Z), "Floor, also for negatives");
        assertEquals(FmsWaypointScreen.NAME, editor.focused(), "The name is ready to type");
        assertEquals("name", editor.problem());

        editor.submit();
        assertTrue(sent.isEmpty(), "Nothing is added without a name");
        editor.paste("Base");
        editor.submit();
        assertEquals("create", sent.getLast().action());
        assertEquals("Base", sent.getLast().name());
        assertEquals(-4, sent.getLast().z());
    }

    @Test
    void coordinateFieldsTakeOnlyNumbersAndFlagUnreadableValues() {
        var editor = new FmsWaypointScreen(screen(new FmsModuleData(), -1, ""), null);
        editor.paste("Hangar");
        editor.focus(FmsWaypointScreen.X);
        editor.paste("abc-");
        assertEquals("12-", editor.value(FmsWaypointScreen.X), "Letters are ignored");
        assertEquals("coordinates", editor.problem());
        editor.useCurrentPosition();
        assertNull(editor.problem());
    }

    @Test
    void editingSendsOnlyRealChangesAndDeleteNeedsASecondClick() {
        var data = new FmsModuleData();
        var landing = waypoint("Landing", 3, 82, -10);
        data.add(landing);
        var parent = screen(data, -1, "");

        new FmsWaypointScreen(parent, landing).submit();
        assertTrue(sent.isEmpty(), "Done without changes sends nothing");

        var rename = new FmsWaypointScreen(parent, landing);
        rename.paste(" 2");
        rename.submit();
        assertEquals("update", sent.getLast().action());
        assertEquals("Landing 2", sent.getLast().name());

        var remove = new FmsWaypointScreen(parent, landing);
        int before = sent.size();
        remove.delete();
        assertEquals(before, sent.size(), "The first click only arms the delete");
        remove.delete();
        assertEquals("delete", sent.getLast().action());
        assertEquals(landing.id().toString(), sent.getLast().waypointId());
    }

    @Test
    void namesStopAtTheWaypointLimit() {
        var editor = new FmsWaypointScreen(screen(new FmsModuleData(), -1, ""), null);
        editor.paste("x".repeat(FmsWaypoint.MAX_NAME_LENGTH + 10));
        assertEquals(FmsWaypoint.MAX_NAME_LENGTH, editor.value(FmsWaypointScreen.NAME).length());
    }

    private FmsScreen screen(FmsModuleData data, int compassSlot, String compassBound) {
        return new FmsScreen(payload(data, compassSlot, compassBound), sent::add);
    }

    private static final UUID TOKEN = UUID.randomUUID();

    private static FmsScreenPayload payload(FmsModuleData data, int compassSlot, String compassBound) {
        return new FmsScreenPayload(BlockPos.ZERO, 0, 0, data, OVERWORLD, 12.6, 70.2, -3.4,
                compassSlot, compassBound, TOKEN);
    }

    private static FmsWaypoint waypoint(String name, double x, double y, double z) {
        return new FmsWaypoint(UUID.randomUUID(), name, OVERWORLD, x, y, z);
    }
}
