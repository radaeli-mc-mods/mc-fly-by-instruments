package com.radaeli.aeronauticsflybyinstruments.client;

import com.radaeli.aeronauticsflybyinstruments.attitude.AttitudeMath;
import com.radaeli.aeronauticsflybyinstruments.client.AttitudeDisplayState.Status;

/**
 * The client's view of one Attitude Indicator: keeps the last sample the server sent and moves the displayed values
 * toward it every frame, so ten samples a second look continuous. Pure and clock-free (the caller passes the frame
 * time), which keeps it testable.
 *
 * <p>The first sample, and the first one after a loss of data, is taken as it is: the horizon should not sweep in
 * from level. A feed nobody has updated for {@link #STALE_SECONDS} goes to the ATT flag, as when the table is out
 * of range of the server or the server has stopped.
 */
final class AttitudeDisplayFeed {
    /** Time constant of the smoothing: the display closes about two thirds of the gap to a new sample in this time. */
    static final double SMOOTHING_SECONDS = 0.08;
    /** A tape takes this long to slide in or out. */
    static final double TAPE_SECONDS = 0.25;
    /** The server repeats an unchanged feed every second; three seconds without one is a lost feed. */
    static final double STALE_SECONDS = 3;

    private Status status = Status.NO_DATA;
    private double targetPitch, targetRoll, targetAgl = Double.NaN, targetSpeed = Double.NaN;
    private boolean altitudeWanted, speedWanted;
    private double pitch, roll, agl = Double.NaN, speed = Double.NaN;
    private float altitudeTape, speedTape;
    private double sinceSample = Double.POSITIVE_INFINITY;
    private boolean live;

    void accept(Status next, double pitch, double roll, double agl, boolean altitudeTape, double speed,
                boolean speedTape) {
        boolean snap = !live || status == Status.NO_DATA || sinceSample > STALE_SECONDS;
        status = next;
        targetPitch = pitch;
        targetRoll = roll;
        targetAgl = agl;
        targetSpeed = speed;
        altitudeWanted = altitudeTape;
        speedWanted = speedTape;
        sinceSample = 0;
        live = true;
        if (snap) {
            this.pitch = pitch;
            this.roll = roll;
            this.agl = agl;
            this.speed = speed;
            this.altitudeTape = altitudeTape ? 1 : 0;
            this.speedTape = speedTape ? 1 : 0;
        }
    }

    /** The state to draw after {@code seconds} more have passed. */
    AttitudeDisplayState advance(double seconds) {
        sinceSample += seconds;
        if (!live || status == Status.NO_DATA || sinceSample > STALE_SECONDS) return NO_DATA;
        double blend = 1 - Math.exp(-seconds / SMOOTHING_SECONDS);
        pitch += (targetPitch - pitch) * blend;
        roll = AttitudeMath.wrapDegrees(roll + AttitudeMath.shortestDelta(roll, targetRoll) * blend);
        agl = Double.isNaN(targetAgl) || Double.isNaN(agl) ? targetAgl : agl + (targetAgl - agl) * blend;
        speed = Double.isNaN(targetSpeed) || Double.isNaN(speed) ? targetSpeed : speed + (targetSpeed - speed) * blend;
        float step = (float) (seconds / TAPE_SECONDS);
        altitudeTape = altitudeWanted ? Math.min(1, altitudeTape + step) : Math.max(0, altitudeTape - step);
        speedTape = speedWanted ? Math.min(1, speedTape + step) : Math.max(0, speedTape - step);
        return new AttitudeDisplayState(status, pitch, roll, agl, speed, altitudeTape, speedTape);
    }

    static final AttitudeDisplayState NO_DATA = new AttitudeDisplayState(Status.NO_DATA, 0, 0, Double.NaN, Double.NaN, 0f, 0f);
}
