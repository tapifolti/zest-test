package com.tapifolti.emotiondetection;

import android.app.Application;
import android.media.Image;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.SystemClock;
import android.util.Base64;
import android.util.Log;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AsyncTask to make REST api call for emotion detection
 */
public class EmotionApiCallAsyncTask extends AsyncTask<Image, Void, String> {

    // TODO make configurable ?
    private static final String mEmotionURL = "http://office.ultinous.com:11000/expr";
    private static AtomicInteger mSerial = new AtomicInteger(1);
    private TextView mTextView;
    private ConnectivityManager mConnMgr;
    private boolean mWifiOnly;

    public EmotionApiCallAsyncTask(TextView textView, ConnectivityManager connMgr, boolean wifiOnly) {
        mTextView = textView;
        mConnMgr = connMgr;
        mWifiOnly = wifiOnly;
    }

    @Override
    protected String doInBackground(Image... params) {
        Log.i(EmotionDetectionFragment.TAG, "doInBackground called");
        if (params == null || params.length != 1) {
            Log.e(EmotionDetectionFragment.TAG, "no image/too many images got to call API with");
            for (Image p : params) {
                p.close();
            }
            return insertNewLine("");
        }
        Image image = params[0];
        HttpURLConnection connection = null;
        DataOutputStream wr = null;
        BufferedReader reader = null;
        try {
            if (isConnected()) {
                // TODO call emotion API
                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                image.close();
                image = null;
                Log.i(EmotionDetectionFragment.TAG, "Start Base64.encode");
                // request: bytes -> base64 encode -> UTF-8 decode
                byte[] baseBuf = Base64.encode(bytes, Base64.DEFAULT);
                String requestStr = new String(baseBuf, "UTF-8");
                Log.i(EmotionDetectionFragment.TAG, "Finished UTF-8 String");
                JSONObject request = new JSONObject();
                request.put("image", requestStr);
                String requestJsonStr = request.toString();
                URL url = new URL(mEmotionURL);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Content-Length", "" + Integer.toString(requestJsonStr.length()));

                connection.setUseCaches(false);
                connection.setDoInput(true);
                connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(requestJsonStr.length());

                //Send request
                wr = new DataOutputStream(connection.getOutputStream());
                wr.writeBytes(requestJsonStr);
                wr.flush();
                wr.close();
                wr = null;

                connection.connect();

                int httpCode = connection.getResponseCode();
                String httpMsg = connection.getResponseMessage();

                Log.d(EmotionDetectionFragment.TAG, "HTTP Response: (" + Integer.toString(httpCode) + ") " + httpMsg);

                if (httpCode != HttpURLConnection.HTTP_OK) {
                    return insertNewLine("API ERROR");
                }

                // Get Response
                InputStream respStream = connection.getInputStream();
                reader = new BufferedReader(new InputStreamReader(respStream));
                String respStr = reader.readLine();
                reader.close();
                reader = null;

                Log.i(EmotionDetectionFragment.TAG, "JSon response: " + respStr);
                String retStr = parseJson(respStr);

                writeJpeg(bytes, requestJsonStr.length(), retStr);

                return insertNewLine(retStr);
            } else {
                return insertNewLine("NETWORK ERROR");
            }
        } catch (JSONException | IOException e) {
            e.printStackTrace();
        } finally {
            if (wr != null) {
                try {
                    wr.close();
                } catch(IOException e) {}
            }
            if (image != null) {
                image.close();
            }
            if (reader != null) {
                try {
                    reader.close();
                } catch(IOException e) {}
            }
            if (connection != null) {
                connection.disconnect();
            }
        }

        return Long.toString(SystemClock.uptimeMillis());
    }

    @Override
    protected void onPostExecute(String result) {
        mTextView.setText(result);
    }

    private void writeJpeg(byte[] bytes, int requestLen, String emotion) {

        FileOutputStream output = null;
        try {
            File outDir = mTextView.getContext().getExternalFilesDir(null);
            Log.i(EmotionDetectionFragment.TAG, "Output file path: " + outDir.getPath());
            File mFile = new File(outDir, "p_"+ Integer.toString(mSerial.	getAndAdd(1)) + "_" + Integer.toString(requestLen) + "_" + emotion  + ".jpg");
            output = new FileOutputStream(mFile);
            output.write(bytes);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (null != output) {
                try {
                    output.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static String parseJson(String respStr) {
        // {"result": {"face_expression": [{"expression": "fear", "confidence": "0.2736271023750305"}]}}
        // {"result": {"error": {"code": "5", "msg": "No face on the picture"}}}
        // {"result": {"error": {"code": "500", "msg": "Internal Error"}}}

        String retStr = "";
        JSONObject repJson = null;
        try {
            repJson = new JSONObject(respStr);
            JSONObject resultJson = repJson.getJSONObject("result");
            if(resultJson.has("face_expression")) {
                JSONArray exprJsonArray = resultJson.getJSONArray("face_expression");
                JSONObject exprJson = exprJsonArray.getJSONObject(0);
                retStr = exprJson.getString("expression");
                Log.i(EmotionDetectionFragment.TAG, "Expression: " + retStr);
            } else if(resultJson.has("error")) {
                JSONObject errorJson = resultJson.getJSONObject("error");
                retStr = errorJson.getString("msg").substring(0, 10).toUpperCase();
                Log.i(EmotionDetectionFragment.TAG, "Error: " + retStr);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return retStr;
    }

    private boolean isConnected() {
        NetworkInfo networkInfo = mConnMgr.getActiveNetworkInfo();
        if (networkInfo != null && networkInfo.isConnected()) {
            if (mWifiOnly) {
                return  (networkInfo.getType()  == ConnectivityManager.TYPE_WIFI);
            }
            return true;
        } else {
            Log.e(EmotionDetectionFragment.TAG, "There is no Internet connection");
            return false;
        }
    }


    private String insertNewLine(String in) {
        if (in == null || in.isEmpty())
            return "";
        if (mTextView.getWidth() > mTextView.getHeight()) {
            return in;
        }
        String replaced = in.replaceAll("(.{1})", "$1\n");
        return replaced.substring(0, replaced.length()-1);
    }
}
