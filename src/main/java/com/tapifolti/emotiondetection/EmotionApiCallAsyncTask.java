package com.tapifolti.emotiondetection;

import android.media.Image;
import android.os.AsyncTask;
import android.os.SystemClock;
import android.util.Log;
import android.widget.TextView;

/**
 * AsyncTask to make REST api call for emotion detection
 */
public class EmotionApiCallAsyncTask extends AsyncTask<Image, Void, String> {

    private TextView mTextView;
    public EmotionApiCallAsyncTask(TextView textView) {
        mTextView = textView;
    }

    @Override
    protected String doInBackground(Image... params) {
        // TODO call emotion API
        if (params == null || params.length == 0) {
            Log.i(EmotionDetectionFragment.TAG, "no image got to call API with");
            return "";
        }

        try {
        } finally {
            params[0].close();
        }

        Log.i(EmotionDetectionFragment.TAG, "doInBackground called");
        return Long.toString(SystemClock.uptimeMillis());
    }

    @Override
    protected void onPostExecute(String result) {
        mTextView.setText(result);
    }
}
