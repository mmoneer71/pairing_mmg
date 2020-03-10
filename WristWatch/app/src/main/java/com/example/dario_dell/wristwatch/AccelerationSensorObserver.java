package com.example.dario_dell.wristwatch;

public interface AccelerationSensorObserver
{
    /**
     * Notify observers when new acceleration measurements are available.
     * @param acceleration the acceleration values (x, y, z)
     * @param timeStamp the time of the sensor update.
     */
    public void onAccelerationSensorChanged(float[] acceleration,
                                            long timeStamp);
}
