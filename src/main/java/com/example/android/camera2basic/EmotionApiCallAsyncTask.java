package com.example.android.camera2basic;

import android.media.Image;
import android.os.AsyncTask;
import android.os.SystemClock;
import android.util.Log;
import android.widget.TextView;

import java.io.FileOutputStream;
import java.io.IOException;

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
            Log.i(Camera2BasicFragment.TAG, "no image got to call API with");
            return "";
        }

        try {
        } finally {
            params[0].close();
        }

        Log.i(Camera2BasicFragment.TAG, "doInBackground called");
        return Long.toString(SystemClock.uptimeMillis());
    }

    @Override
    protected void onPostExecute(String result) {
        mTextView.setText(result);
    }
}
