package com.tapifolti.emotiondetection.tests;

import com.tapifolti.emotiondetection.CameraActivity;

import android.test.ActivityInstrumentationTestCase2;

/**
* Tests for EmotionDetection sample.
*/
public class SampleTests extends ActivityInstrumentationTestCase2<CameraActivity> {

    private CameraActivity mTestActivity;

    public SampleTests() {
        super(CameraActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        // Starts the activity under test using the default Intent with:
        // action = {@link Intent#ACTION_MAIN}
        // flags = {@link Intent#FLAG_ACTIVITY_NEW_TASK}
        // All other fields are null or empty.
        mTestActivity = getActivity();
    }

    /**
    * Test if the test fixture has been set up correctly.
    */
    public void testPreconditions() {
        //Try to add a message to add context to your assertions. These messages will be shown if
        //a tests fails and make it easy to understand why a test failed
        assertNotNull("mTestActivity is null", mTestActivity);
    }

    /**
    * Add more tests below.
    */

}
