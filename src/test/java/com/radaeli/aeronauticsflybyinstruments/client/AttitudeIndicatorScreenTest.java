package com.radaeli.aeronauticsflybyinstruments.client;

import com.radaeli.aeronauticsflybyinstruments.network.AttitudeIndicatorActionPayload;
import com.radaeli.aeronauticsflybyinstruments.network.AttitudeIndicatorScreenPayload;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AttitudeIndicatorScreenTest {
    private static final UUID TOKEN = UUID.randomUUID();

    private static AttitudeIndicatorScreenPayload payload(boolean tape) {
        return new AttitudeIndicatorScreenPayload(new BlockPos(3, 64, -2), 1, 2, TOKEN, "ok", "ok", "ok",
                5, -12, 40, tape);
    }

    @Test
    void theTapeButtonAsksForTheOppositeOfWhatTheServerReported() {
        var sent = new ArrayList<AttitudeIndicatorActionPayload>();
        var screen = new AttitudeIndicatorScreen(payload(true), sent::add);
        screen.toggleAltitudeTape();
        screen.accept(payload(false));
        screen.toggleAltitudeTape();

        assertEquals(2, sent.size());
        assertEquals("altitude_tape", sent.get(0).action());
        assertFalse(sent.get(0).value());
        assertTrue(sent.get(1).value());
        for (var action : sent) {
            assertEquals(new BlockPos(3, 64, -2), action.position());
            assertEquals(1, action.column());
            assertEquals(2, action.row());
            assertEquals(TOKEN, action.interactionToken(), "Actions carry the module's token");
        }
    }

    @Test
    void anOpenScreenIsOnlyUpdatedByItsOwnModule() {
        var screen = new AttitudeIndicatorScreen(payload(true), ignored -> { });
        assertTrue(screen.isFor(payload(false)));
        assertFalse(screen.isFor(new AttitudeIndicatorScreenPayload(new BlockPos(3, 64, -2), 0, 2, TOKEN, "ok", "ok",
                "ok", 0, 0, 0, true)));
        assertFalse(screen.isFor(new AttitudeIndicatorScreenPayload(new BlockPos(4, 64, -2), 1, 2, TOKEN, "ok", "ok",
                "ok", 0, 0, 0, true)));
    }

    @Test
    void refreshAsksForTheLatestStateEveryHalfSecond() {
        List<AttitudeIndicatorActionPayload> sent = new ArrayList<>();
        var screen = new AttitudeIndicatorScreen(payload(true), sent::add);
        for (int i = 0; i < 9; i++) screen.tick();
        assertTrue(sent.isEmpty());
        screen.tick();
        assertEquals(1, sent.size());
        assertEquals("refresh", sent.getFirst().action());
    }
}
