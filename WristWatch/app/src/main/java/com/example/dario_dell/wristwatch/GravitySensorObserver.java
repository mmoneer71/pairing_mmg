package com.example.dario_dell.wristwatch;

public interface GravitySensorObserver {
    /**
     * Notify observers when new gravity measurements are available.
     *
     * @param gravity   the gravity values (x, y, z)
     * @param timeStamp the time of the sensor update.
     */
    public void onGravitySensorChanged(float[] gravity,
                                       long timeStamp);
}