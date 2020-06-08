package com.example.dario_dell.smartphone;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;

class MatchingAlgo {

    private static final String TAG = "MatchingAlgo";

    private static final int JUMP = 2;
    private static final float ACCEPTANCE_THRESHOLD = 0.7f;
    private static final float EPSILON = 0.4f;
    private static final float ZERO = 0.0f;
    private static final float VEL_NOISE = 0.02f;
    private static final float ACC_NOISE = 0.2f;
    private static final float WINDOW_RANGE = 0.15f;


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
            endIndex = yIndices.get(yIndices.size() - 1);
        }
        else if (yIndices.size() == 0) {
            startIndex = xIndices.get(0);
            endIndex = xIndices.get(xIndices.size() - 1);
        }
        else {
            startIndex = xIndices.get(0) < yIndices.get(0) ? xIndices.get(0) : yIndices.get(0);
            endIndex = xIndices.get(xIndices.size() - 1) > yIndices.get(yIndices.size() - 1)
                    ? xIndices.get(xIndices.size() - 1) : yIndices.get(yIndices.size() - 1);
        }


        if (signalType == SignalTypes.SIGNAL_VEL) {
            xVel = xInput.subList(startIndex, xInput.size());
            yVel = yInput.subList(startIndex, yInput.size());
        }

        if (signalType == SignalTypes.SIGNAL_ACC) {
            xAcc = xInput.subList(startIndex, endIndex + 1);
            yAcc = yInput.subList(startIndex, endIndex + 1);
        }

    }


    private static String gen2bitGrayCode(List<Float> xInput, List<Float> yInput) {
        StringBuilder grayCodeBuilder = new StringBuilder();
        int n = xInput.size(), index = 0;

        while (index + JUMP < n) {
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
            ++index;
        }

        return grayCodeBuilder.toString();
    }

    private static float compareEncodedStrings(String encodedBitsPhone, String encodedBitsWatch) {
        int phoneBitsSize = encodedBitsPhone.length(), watchBitsSize = encodedBitsWatch.length();
        float matchResult = 0.0f;
        boolean watchHasMoreSamples = watchBitsSize > phoneBitsSize;
        int n = watchHasMoreSamples ? phoneBitsSize : watchBitsSize;
        int window = Math.abs(phoneBitsSize - watchBitsSize), walker = 0;
        Log.i(TAG, "Parameters are: " + n + " " + window);

        if (window > n * WINDOW_RANGE)
            return ZERO;

        while (walker <= window) {
            int matchingCodesCount = 0;
            for (int i = 0; i < n; ++i) {
                if (watchHasMoreSamples) {
                    if (encodedBitsPhone.charAt(i) == encodedBitsWatch.charAt(i + walker)) {
                        ++matchingCodesCount;
                    }
                }
                else if (encodedBitsPhone.charAt(i + walker) == encodedBitsWatch.charAt(i)) {
                    ++matchingCodesCount;
                }
            }

            float currMatchResult = (float) matchingCodesCount / (float) n;
            Log.i(TAG, "Current match result: " + currMatchResult);

            if (currMatchResult >= ACCEPTANCE_THRESHOLD || currMatchResult < EPSILON)
                return currMatchResult;

            if (currMatchResult > matchResult) {
                matchResult = currMatchResult;
            }
            ++walker;
        }
        return matchResult;

    }

    static boolean pair(List<Float> xAccWatch,
                        List<Float> yAccWatch,
                        List<Float> xVelPhone,
                        List<Float> yVelPhone) {

        init();

        filterNoise(xVelPhone, yVelPhone, SignalTypes.SIGNAL_VEL);
        filterNoise(xAccWatch, yAccWatch, SignalTypes.SIGNAL_ACC);

        if (!success)
            return false;

        addZeroesForSync(xVel);
        addZeroesForSync(yVel);
        addZeroesForSync(xAcc);
        addZeroesForSync(yAcc);

        List<Float> xVelWatch = MathUtils.cumtrapz(xAcc);
        List<Float> yVelWatch = MathUtils.cumtrapz(yAcc);

        String watchGrayCode = gen2bitGrayCode(xVelWatch, yVelWatch);
        String phoneGrayCode = gen2bitGrayCode(xVel, yVel);

        float matchRatio = compareEncodedStrings(phoneGrayCode, watchGrayCode);
        Log.i(TAG, "Match ratio is: " + matchRatio);

        return matchRatio >= ACCEPTANCE_THRESHOLD && success;

    }

}
