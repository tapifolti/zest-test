package com.tapifolti.emotiondetection.game;

/***
 * generates series of sec when shot will be taken
 * 1sec < interval < 5sec
 * max 20 shots per minute
 * 20sec < totalLength < 90sec
 */
public class ShotFrequency {

    public ShotFrequency() {
        reset();
    }

    private int[] mDelayArray = new int[100];
    private int mDelayPointer = 0;

    public void reset() {
        // TODO
    }

    public int getNextDelayMSec() {
        // TODO
        // return -1 if no more item available
        return 3000;
    }

    public int getTotalLengthSec() {
        // TODO
        return 90;
    }

    public boolean isFinished() {
        // TODO
        return false;
    }

}
