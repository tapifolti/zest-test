package com.tapifolti.emotiondetection.apicall;

import android.media.Image;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.TextView;

import com.tapifolti.emotiondetection.game.PlayGame;
import com.tapifolti.emotiondetection.game.ShotFrequency;

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
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AsyncTask to make REST api call for emotion detection
 */
public class CSEmotionCallAsyncTask extends AsyncTask<Image, Void, String> {
    public static final String TAG = "Emotion_CSApi";

    private static final String mEmotionURL = "https://api.projectoxford.ai/emotion/v1.0/recognize"; // TODO config
    private static AtomicInteger mSerial = new AtomicInteger(1);
    private static boolean doCall = true; // TODO remove it
    private static boolean writeJpeg = true; // TODO remove it

    private TextView mTextView;
    private ConnectivityManager mConnMgr;
    private boolean mWifiOnly;
    private String mKeyCSEmotion;
    private PlayGame mGame;
    private ShotFrequency mFreq;

    public CSEmotionCallAsyncTask(TextView textView, PlayGame game, ShotFrequency freq, ConnectivityManager connMgr, boolean wifiOnly, String keyCSEmotion) {
        mTextView = textView;
        mConnMgr = connMgr;
        mWifiOnly = wifiOnly;
        mKeyCSEmotion = keyCSEmotion;
        mGame = game;
        mFreq = freq;
    }

    private static final String NO_PICTURE_ERROR = "NO PICTURE";
    private static final String NETWORK_ERROR = "NETWORK ERROR";
    private static final String API_ERROR = "API ERROR";
    private static final String PROTOCOL_ERROR = "PROTOCOL ERROR";
    private static final String PARSE_ERROR = "PARSE ERROR";
    private static final String NOT_FACE_ERROR = "NO FACE";
    @Override
    protected String doInBackground(Image... params) {
        Log.i(TAG, "doInBackground called");
//        if (params == null || params.length != 1) {
//            Log.e(TAG, "no image/too many images got to call API with");
//            for (Image p : params) {
//                p.close();
//            }
//            return NO_PICTURE_ERROR;
//        }
//        String retStr = "";
//        if (!isConnected()) {
//            params[0].close();
//            retStr = NETWORK_ERROR;
//            return retStr;
//        }
//        HttpURLConnection connection = null;
//        byte[] requestJPEG = null;
//        try {
//            // call emotion API
//            ByteBuffer buffer = params[0].getPlanes()[0].getBuffer();
//            requestJPEG = new byte[buffer.remaining()];
//            buffer.get(requestJPEG);
//            params[0].close(); // close asap
//            params[0] = null;
//
//            String respStr = "[]";
//            if (doCall) {
//                connection = (HttpURLConnection) new URL(mEmotionURL).openConnection();
//                if (requestJPEG.length > 0) {
//                    writeRequest(connection, requestJPEG);
//                }
//                long beforeConnectTime = System.currentTimeMillis();
//                connection.connect();
//                int httpCode = connection.getResponseCode();
//                long afterConnectTime = System.currentTimeMillis();
//                String httpMsg = connection.getResponseMessage();
//                Log.d(TAG, "HTTP Response: (" + httpCode + ") " + httpMsg + " [Took for: " + (afterConnectTime-beforeConnectTime) + "msec]");
//                if (httpCode != HttpURLConnection.HTTP_OK) {
//                    retStr = API_ERROR;
//                    return retStr;
//                }
//
//                respStr = readResponse(connection);
//                connection.disconnect();
//                connection = null;
//            }
//
//            Log.i(TAG, "JSon response: " + respStr);
//            retStr = parseJson(respStr);
//            return retStr;
//        } catch (IOException e) {
//            Log.e(TAG, "Exception while calling emotion API");
//            e.printStackTrace();
//            retStr = PROTOCOL_ERROR;
//        } finally {
//            if (params[0] != null) {
//                params[0].close();
//            }
//            if (connection != null) {
//                connection.disconnect();
//            }
//            if (writeJpeg) {
//                writeJpeg(requestJPEG, retStr);
//            }
//        }
        for (Image p : params) {
            p.close();
        }
        return "Happy"; // retStr;
    }

    @Override
    protected void onPostExecute(String result) {
        if (result == null) {
            result = "";
        }
        if (!PlayGame.isOk(result, mGame)) {
            mFreq.reset();
        }
        mTextView.setText(result.toUpperCase() + " " + String.format("%02d", mFreq.getPrevLengthSec())+"/"+mFreq.getTotalLengthSec()+"s");
    }

    private static String readResponse(HttpURLConnection connection) {
        BufferedReader reader = null;
        try {
            InputStream respStream = connection.getInputStream();
            reader = new BufferedReader(new InputStreamReader(respStream));
            String respStr = reader.readLine();
            return respStr;
        } catch (IOException e) {
            Log.e(TAG, "Exception while reading reponse");
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
            Log.e(TAG, "Exception while sending request");
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
                Log.i(TAG, "Output file path: " + outDir.getPath());
                for(File file: outDir.listFiles())
                    if (!file.isDirectory())
                        file.delete();
            }
            File mFile = new File(outDir, "p_"+ mSerial.getAndAdd(1) + "_" + emotion  + ".jpg");
            output = new FileOutputStream(mFile);
            output.write(bytes);
        } catch (IOException e) {
            Log.e(TAG, "Exception while writing JPEG file");
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
        //                    "anger": 0.00300731952,
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
        String retStr = PARSE_ERROR;
        try {
            JSONArray itemsArray = new JSONArray(respStr);
            if (itemsArray.length() != 1) {
                return NOT_FACE_ERROR;
            }
            TreeMap<Double, String> maxMap = new TreeMap<>();

            JSONObject item = itemsArray.getJSONObject(0);
            JSONObject scores = item.getJSONObject("scores");
//            maxMap.put(scores.getDouble(Actions.ANGER), Actions.ANGER);
//            maxMap.put(scores.getDouble(Actions.CONTEMPT), Actions.CONTEMPT);
//            maxMap.put(scores.getDouble(Actions.DISGUST), Actions.DISGUST);
//            maxMap.put(scores.getDouble(Actions.FEAR), Actions.FEAR);
//            maxMap.put(scores.getDouble(Actions.HAPPINESS), Actions.HAPPINESS);
//            maxMap.put(scores.getDouble(Actions.NEUTRAL), Actions.NEUTRAL);
//            maxMap.put(scores.getDouble(Actions.SADNESS), Actions.SADNESS);
//            maxMap.put(scores.getDouble(Actions.SURPRISE), Actions.SURPRISE);

            Map.Entry<Double, String> maxEntry = maxMap.lastEntry();
            Log.i(TAG, "MAX Score: " + maxEntry.getValue() + " : " + maxEntry.getKey().toString());
            retStr = maxEntry.getValue();

        } catch (JSONException e) {
            Log.e(TAG, "Exception when parsing JSon response");
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
                    Log.d(TAG, "There is no WIFI connection");
                }
                return isWifi;
            }
            return true;
        } else {
            Log.d(TAG, "There is no Internet connection");
            return false;
        }
    }

    private static String readKeyCSEmotion() {
        // TODO: reads in the CS key from local file resource which is ecluded from source control
        return "";
    }
}
