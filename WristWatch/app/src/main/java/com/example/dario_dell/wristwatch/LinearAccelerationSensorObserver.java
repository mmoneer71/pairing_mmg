package com.example.dario_dell.wristwatch;


public interface LinearAccelerationSensorObserver
{

	public void onLinearAccelerationSensorChanged(float[] linearAcceleration, long timeStamp);
}
