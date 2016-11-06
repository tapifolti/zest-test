package com.tapifolti.emotiondetection;

import android.media.Image;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.util.Base64;
import android.util.Log;
import android.view.View;
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
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AsyncTask to make REST api call for emotion detection
 */
public class CSEmotionCallAsyncTask extends AsyncTask<Image, Void, String> {

    private static final String mEmotionURL = "https://api.projectoxford.ai/emotion/v1.0/recognize";
    private static AtomicInteger mSerial = new AtomicInteger(1);
    private static boolean doCall = false; // TODO remove it

    private TextView mTextView;
    private ConnectivityManager mConnMgr;
    private boolean mWifiOnly;
    private String mKeyCSEmotion;

    public CSEmotionCallAsyncTask(TextView textView, ConnectivityManager connMgr, boolean wifiOnly, String keyCSEmotion) {
        mTextView = textView;
        mConnMgr = connMgr;
        mWifiOnly = wifiOnly;
        mKeyCSEmotion = keyCSEmotion;
    }

    @Override
    protected String doInBackground(Image... params) {
        Log.i(EmotionDetectionFragment.TAG, "doInBackground called");
        if (params == null || params.length != 1) {
            Log.e(EmotionDetectionFragment.TAG, "no image/too many images got to call API with");
            for (Image p : params) {
                p.close();
            }
            return "NO PICTURE";
        }
        String retStr = "";
        if (!isConnected()) {
            params[0].close();
            retStr = "NETWORK ERROR";
            return retStr;
        }
        HttpURLConnection connection = null;
        byte[] requestJPEG = null;
        try {
            // call emotion API
            ByteBuffer buffer = params[0].getPlanes()[0].getBuffer();
            requestJPEG = new byte[buffer.remaining()];
            buffer.get(requestJPEG);
            params[0].close(); // close asap
            params[0] = null;

            String respStr = "[]";
            if (doCall) {
                connection = (HttpURLConnection) new URL(mEmotionURL).openConnection();
                if (requestJPEG.length > 0) {
                    writeRequest(connection, requestJPEG);
                }
                long beforeConnectTime = System.currentTimeMillis();
                connection.connect();

                int httpCode = connection.getResponseCode();
                String httpMsg = connection.getResponseMessage();

                long afterConnectTime = System.currentTimeMillis();
                Log.d(EmotionDetectionFragment.TAG, "HTTP Response: (" + httpCode + ") " + httpMsg + " [Took for: " + (afterConnectTime-beforeConnectTime) + "msec]");

                if (httpCode != HttpURLConnection.HTTP_OK) {
                    retStr = "API ERROR";
                    return retStr;
                }

                respStr = readResponse(connection);
                connection.disconnect();
                connection = null;
            }

            Log.i(EmotionDetectionFragment.TAG, "JSon response: " + respStr);
            retStr = parseJson(respStr);

            return retStr;
        } catch (IOException e) {
            Log.e(EmotionDetectionFragment.TAG, "Exception while calling emotion API");
            e.printStackTrace();
            retStr = "ERROR";
        } finally {
            if (params[0] != null) {
                params[0].close();
            }
            if (connection != null) {
                connection.disconnect();
            }
            writeJpeg(requestJPEG, retStr);
        }

        return retStr;
    }

    @Override
    protected void onPostExecute(String result) {
        if (result != null && !result.isEmpty()) {
            mTextView.setText(result); // insertNewLine(result));
        }
    }

    private static String readResponse(HttpURLConnection connection) {
        BufferedReader reader = null;
        try {
            InputStream respStream = connection.getInputStream();
            reader = new BufferedReader(new InputStreamReader(respStream));
            String respStr = reader.readLine();
            return respStr;
        } catch (IOException e) {
            Log.e(EmotionDetectionFragment.TAG, "Exception while reading reponse");
            e.printStackTrace();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return "";
    }

    private void writeRequest(HttpURLConnection connection, byte[] requestJPEG) {
        DataOutputStream wr = null;
        try {
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/octet-stream");
            connection.setRequestProperty("Content-Length", "" + requestJPEG.length);
            connection.setRequestProperty("Ocp-Apim-Subscription-Key", mKeyCSEmotion);

            connection.setUseCaches(false);
            connection.setDoInput(true);
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(requestJPEG.length);

            //Send request
            wr = new DataOutputStream(connection.getOutputStream());
            wr.write(requestJPEG);
            wr.flush();
        } catch (IOException e) {
            Log.e(EmotionDetectionFragment.TAG, "Exception while sending request");
            e.printStackTrace();
        } finally {
            if (wr != null) {
                try {
                    wr.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void writeJpeg(byte[] bytes, String emotion) {

        FileOutputStream output = null;
        try {
            File outDir = mTextView.getContext().getExternalFilesDir(null);
            if (mSerial.get() == 1) {
                Log.i(EmotionDetectionFragment.TAG, "Output file path: " + outDir.getPath());
                for(File file: outDir.listFiles())
                    if (!file.isDirectory())
                        file.delete();
            }
            File mFile = new File(outDir, "p_"+ mSerial.getAndAdd(1) + "_" + emotion  + ".jpg");
            output = new FileOutputStream(mFile);
            output.write(bytes);
        } catch (IOException e) {
            Log.e(EmotionDetectionFragment.TAG, "Exception while writing JPEG file");
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

    public static String parseJson(String respStr) {
        // sample response
        //        [
        //        {
        //            "faceRectangle": {
        //            "left": 68,
        //                    "top": 97,
        //                    "width": 64,
        //                    "height": 97
        //        },
        //            "scores": {
        //            "anger": 0.00300731952,
        //                    "contempt": 5.14648448E-08,
        //                    "disgust": 9.180124E-06,
        //                    "fear": 0.0001912825,
        //                    "happiness": 0.9875571,
        //                    "neutral": 0.0009861537,
        //                    "sadness": 1.889955E-05,
        //                    "surprise": 0.008229999
        //        }
        //        }
        //        ]
        String retStr = "";
        try {
            JSONArray itemsArray = new JSONArray(respStr);
            if (itemsArray.length() != 1) {
                return "NOT ONE FACE";
            }
            TreeMap<Double, String> maxMap = new TreeMap<>();

            JSONObject item = itemsArray.getJSONObject(0);
            JSONObject scores = item.getJSONObject("scores");
            maxMap.put(scores.getDouble("anger"), "anger");
            maxMap.put(scores.getDouble("contempt"), "contempt");
            maxMap.put(scores.getDouble("disgust"), "disgust");
            maxMap.put(scores.getDouble("fear"), "fear");
            maxMap.put(scores.getDouble("happiness"), "happiness");
            maxMap.put(scores.getDouble("neutral"), "neutral");
            maxMap.put(scores.getDouble("sadness"), "sadness");
            maxMap.put(scores.getDouble("surprise"), "surprise");

            Map.Entry<Double, String> maxEntry = maxMap.lastEntry();
            Log.i(EmotionDetectionFragment.TAG, "MAX Score: " + maxEntry.getValue() + " : " + maxEntry.getKey().toString());
            retStr = maxEntry.getValue();

        } catch (JSONException e) {
            Log.e(EmotionDetectionFragment.TAG, "Exception when parsing JSon response");
            e.printStackTrace();
        }
        return retStr;
    }

    private boolean isConnected() {
        NetworkInfo networkInfo = mConnMgr.getActiveNetworkInfo();
        if (networkInfo != null && networkInfo.isConnected()) {
            if (mWifiOnly) {
                boolean isWifi = (networkInfo.getType()  == ConnectivityManager.TYPE_WIFI);
                if (!isWifi) {
                    Log.d(EmotionDetectionFragment.TAG, "There is no WIFI connection");
                }
                return isWifi;
            }
            return true;
        } else {
            Log.d(EmotionDetectionFragment.TAG, "There is no Internet connection");
            return false;
        }
    }


//    private String insertNewLine(String in) {
//        if (in == null || in.isEmpty())
//            return "";
//        if (mTextView.getWidth() > mTextView.getHeight()) {
//            return in;
//        }
//        String replaced = in.replaceAll("(.{1})", "$1\n");
//        return replaced.substring(0, replaced.length()-1);
//    }

    private static String readKeyCSEmotion() {
        // TODO: reads in the CS key from local file resource which is ecluded from source control

        return "";
    }
}
