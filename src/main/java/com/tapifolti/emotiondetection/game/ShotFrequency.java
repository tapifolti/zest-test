package com.tapifolti.emotiondetection.game;

import android.util.Log;

import java.util.ArrayList;
import java.util.Random;

/***
 * generates series of sec when shot will be taken
 * 1.5sec <= interval <= 5sec
 * max 20 shots per minute
 * 20sec <= mTotalLengthSec <= 90sec
 */
public class ShotFrequency {

    public static final String TAG = "Emotion_Freq";
    private static final int MIN_INTERVAL_MSEC = 1500;
    private static final int MAX_INTERVAL_MSEC = 5000;
    private static final int INTERVAL_LENGTH = MAX_INTERVAL_MSEC -MIN_INTERVAL_MSEC +1; // 3501
    private static final int EXTRA_MSEC = 100;
    private static final int MAX_SHOT_PER_MINUTE = 20;
    private static final int MIN_DURATION_SEC = 20;
    private static final int MAX_DURATION_SEC = 90;
    private static final int DURATION_LENGTH_SEC = MAX_DURATION_SEC -MIN_DURATION_SEC +1; // 71
    private static final int MINUTE_MSEC = 60*1000;

    private Random mRrand;
    private int mTotalLengthSec = (MIN_DURATION_SEC + MAX_DURATION_SEC)/2;
    private int mCurrentLengthMsec = 0;
    private int mPrevLengthMsec = 0;
    private ArrayList<Integer> mTicks = new ArrayList<>();

    public ShotFrequency() {
        mRrand = new Random(System.currentTimeMillis());
        mTotalLengthSec = mRrand.nextInt(DURATION_LENGTH_SEC) + MIN_DURATION_SEC; // 71sec
        Log.i(TAG, "mTotalLengthSec: " + mTotalLengthSec);
    }

    public void reset() {
        Log.i(TAG, "reset");
        mCurrentLengthMsec = 0;
        mPrevLengthMsec = 0;
    }

    private int checkTicksFor60sec(int msec) {
        // max 20 shot per min
        int summaMsec = msec;
        int count = 1;
        for (int i=mTicks.size()-1; i>=0; i--) {
            summaMsec += mTicks.get(i);
            count++;
            if (count == MAX_SHOT_PER_MINUTE) {
                if (summaMsec > MINUTE_MSEC) {
                    return msec;
                } else {
                    int longerMsec = MINUTE_MSEC-(summaMsec-msec)+EXTRA_MSEC;
                    Log.i(TAG, "checkTicksFor60sec made it longer:" + msec + "msec -> " + longerMsec + "msec");
                    return longerMsec;
                }
            }
        }
        return msec;
    }

    public int getNextDelayMSec() {
        int msec = mRrand.nextInt(INTERVAL_LENGTH) +MIN_INTERVAL_MSEC;
        msec = checkTicksFor60sec(msec);
        mPrevLengthMsec = mCurrentLengthMsec;
        mCurrentLengthMsec += msec;
        mTicks.add(msec);
        Log.i(TAG, "prevLength:" + mPrevLengthMsec + "msec, next delay:" + msec + "msec, currentLength:" + mCurrentLengthMsec + "msec");
        return msec;
    }

    public int getTotalLengthSec() {
        return mTotalLengthSec;
    }

    public int getPrevLengthSec() {
        return mPrevLengthMsec/1000;
    }

    public boolean isFinished() {
        return mCurrentLengthMsec/1000 >= mTotalLengthSec;
    }

}
