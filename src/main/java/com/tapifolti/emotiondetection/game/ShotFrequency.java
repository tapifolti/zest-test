package com.tapifolti.emotiondetection.game;

import android.util.Log;

import java.util.Random;

/***
 * generates series of sec when shot will be taken
 * 1sec <= interval <= 5sec
 * max 20 shots per minute
 * 20sec <= mTotalLength <= 90sec
 */
public class ShotFrequency {

    public static final String TAG = "Emotion_Freq";
    private Random mRrand;
    private static int MAX_INTERVAL = 4001;
    private int mTotalLength = 30;
    private int mCurrentLengthMsec = 0;
    private int[] mTicks = new int[100];
    private int mTickCounter = 0;

    public ShotFrequency() {
        mRrand = new Random(System.currentTimeMillis());
        mTotalLength = mRrand.nextInt(71) +20;
        Log.i(TAG, "mTotalLength: " + mTotalLength);
    }

    public void reset() {
        Log.i(TAG, "reset");
        mCurrentLengthMsec = 0;
    }

    private int checkTicksfor60sec(int msec) {
        // max 20 shot per min
        int summaMsec = msec;
        int count = 1;
        for (int i=mTickCounter-1; i>=0; i--) {
            summaMsec += mTicks[i];
            count++;
            if (count == 20) {
                if (summaMsec > 60*1000) {
                    return msec;
                } else {
                    int longerMsec = 60*1000-summaMsec+100;
                    Log.i(TAG, "checkTicksfor60sec made it longer:" + msec + "msec -> " + longerMsec + "msec");
                    return longerMsec;
                }
            }
        }
        return msec;
    }

    public int getNextDelayMSec() {
        int msec = mRrand.nextInt(MAX_INTERVAL) +1000;
        msec = checkTicksfor60sec(msec);
        mCurrentLengthMsec += msec;
        mTicks[mTickCounter++] = msec;
        Log.i(TAG, "next delay:" + msec + "msec, currentLength:" + mCurrentLengthMsec + "msec");
        return msec;
    }

    public int getTotalLengthSec() {
        return mTotalLength;
    }

    public boolean isFinished() {
        return mCurrentLengthMsec/1000 >= mTotalLength;
    }

}
