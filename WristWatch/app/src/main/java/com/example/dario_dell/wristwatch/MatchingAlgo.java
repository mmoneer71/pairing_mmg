package com.example.dario_dell.wristwatch;

import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class MatchingAlgo {

    private static final String TAG = "MatchingAlgo";

    private static final int JUMP = 2;
    private static final float ACCEPTANCE_THRESHOLD = 0.4f;
    private static final float ZERO = 0.0f;


    private static final float NOISE_FACTOR = 4.0f;
    private static final float VEL_NOISE = 0.03f;
    private static final float ACC_NOISE = 0.45f;

    private static float accNoiseMax, accNoiseMin;
    private static boolean success = true;

    private static List<Float> xAcc, yAcc, xVel, yVel;

    private interface SignalTypes {
        int SIGNAL_VEL = 0;
        int SIGNAL_ACC = 1;
    }

    private static void init() {
        xAcc = new ArrayList<>();
        yAcc = new ArrayList<>();
        xVel = new ArrayList<>();
        yVel = new ArrayList<>();
    }

    private static void setAccNoiseRange(List<Float> accX, List<Float> accY) {

        float maxX = Collections.max(accX), maxY = Collections.max(accY);
        float minX = Collections.min(accX), minY = Collections.min(accY);

        accNoiseMax = maxX > maxY ? maxX / NOISE_FACTOR : maxY / NOISE_FACTOR;
        accNoiseMin = minX < minY ? minX / NOISE_FACTOR : minY / NOISE_FACTOR;
    }

    private static void addZeroesForSync(List<Float> input) {

        input.add(ZERO);
        input.add(ZERO);
        input.add(0, ZERO);
        input.add(0, ZERO);
    }


    private static void filterNoise(List<Float> xInput, List<Float> yInput, int signalType) {
        int n = xInput.size(), startIndex, endIndex;

        float upperLimit, lowerLimit;
        List<Integer> xIndices = new ArrayList<>(), yIndices = new ArrayList<>();

        if (signalType == SignalTypes.SIGNAL_ACC) {
            //setAccNoiseRange(xInput, yInput);
            upperLimit = ACC_NOISE;
            lowerLimit = -ACC_NOISE;
        }
        else if (signalType == SignalTypes.SIGNAL_VEL) {
            upperLimit = VEL_NOISE;
            lowerLimit = -VEL_NOISE;
        }
        else {
            Log.e(TAG, "Error in filterNoise(): Unknown signal type received");
            success = false;
            return;
        }

        for (int i = 0; i < n; ++i) {
            if (xInput.get(i) <= upperLimit && xInput.get(i) >= lowerLimit) {
                xInput.set(i, ZERO);
            }
            else {
                xIndices.add(i);
            }
            if (yInput.get(i) <= upperLimit && yInput.get(i) >= lowerLimit) {
                yInput.set(i, ZERO);
            }
            else {
                yIndices.add(i);
            }
        }

        if (xIndices.size() == 0 && yIndices.size() == 0) {
            Log.e(TAG, "No motion detected.");
            success = false;
            return;
        }
        else if (xIndices.size() == 0) {
            startIndex = yIndices.get(0);
            // endIndex = yIndices.get(yIndices.size() - 1);
        }
        else if (yIndices.size() == 0) {
            startIndex = xIndices.get(0);
            // endIndex = xIndices.get(xIndices.size() - 1);
        }
        else {
            startIndex = xIndices.get(0) < yIndices.get(0) ? xIndices.get(0) : yIndices.get(0);
            /*endIndex = xIndices.get(xIndices.size() - 1) > yIndices.get(yIndices.size() - 1)
                    ? xIndices.get(xIndices.size() - 1) : yIndices.get(yIndices.size() - 1);*/
        }


        if (signalType == SignalTypes.SIGNAL_VEL) {
            xVel = xInput.subList(startIndex, xInput.size());
            yVel = yInput.subList(startIndex, yInput.size());
        }

        if (signalType == SignalTypes.SIGNAL_ACC) {
            xAcc = xInput.subList(startIndex, startIndex + xVel.size());
            yAcc = yInput.subList(startIndex, startIndex + yVel.size());
        }

    }

    private static void sync() {

    }

    private static List<String> gen2bitGrayCode(List<Float> xInput, List<Float> yInput) {
        List<String> encodedBits = new ArrayList<>();
        int n = xInput.size(), index = 0;

        while (index + JUMP < n) {
            StringBuilder grayCodeBuilder = new StringBuilder();
            if (xInput.get(index + JUMP) - xInput.get(index) >= 0) {
                grayCodeBuilder.append("0");

                if (yInput.get(index + JUMP) - yInput.get(index) >= 0) {
                    grayCodeBuilder.append("0");
                }
                else {
                    grayCodeBuilder.append("1");
                }
            }
            else {
                grayCodeBuilder.append("1");

                if (yInput.get(index + JUMP) - yInput.get(index) >= 0) {
                    grayCodeBuilder.append("1");
                }
                else {
                    grayCodeBuilder.append("0");
                }
            }
            encodedBits.add(grayCodeBuilder.toString());
            ++index;
        }

        return encodedBits;
    }

    private static float compareEncodedStrings(List<String> encodedBitsPhone, List<String> encodedBitsWatch) {
        int matchingCodesCount = 0;
        int n = encodedBitsPhone.size();
        Log.i(TAG, encodedBitsPhone.size() + " " + encodedBitsWatch.size());

        for (int i = 0; i < n; ++i) {
            if (encodedBitsPhone.get(i).equals(encodedBitsWatch.get(i))) {
                ++matchingCodesCount;
            }
        }
        return (float)matchingCodesCount / (float)n;
    }

    static boolean pair(List<Float> xAccWatch,
                               List<Float> yAccWatch,
                               List<Float> xVelPhone,
                               List<Float> yVelPhone) {

        init();

        filterNoise(xVelPhone, yVelPhone, SignalTypes.SIGNAL_VEL);
        filterNoise(xAccWatch, yAccWatch, SignalTypes.SIGNAL_ACC);

        addZeroesForSync(xVel);
        addZeroesForSync(yVel);
        addZeroesForSync(xAcc);
        addZeroesForSync(yAcc);

        List<Float> xVelWatch = MathUtils.cumtrapz(xAcc);
        List<Float> yVelWatch = MathUtils.cumtrapz(yAcc);

        List<String> watchGrayCode = gen2bitGrayCode(xVelWatch, yVelWatch);
        List<String> phoneGrayCode = gen2bitGrayCode(xVel, yVel);

        float matchRatio = compareEncodedStrings(watchGrayCode, phoneGrayCode);
        Log.i(TAG, "Match ratio is: " + matchRatio);

        return matchRatio >= ACCEPTANCE_THRESHOLD && success;

    }

}
