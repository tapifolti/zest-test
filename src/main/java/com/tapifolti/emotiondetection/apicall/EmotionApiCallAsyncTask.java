package com.tapifolti.emotiondetection.apicall;

import android.media.Image;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.util.Base64;
import android.util.Log;
import android.widget.TextView;

import com.tapifolti.emotiondetection.game.Emotions;

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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AsyncTask to make REST api call for emotion detection
 */
public class EmotionApiCallAsyncTask extends AsyncTask<Image, Void, String> {
    public static final String TAG = "Emotion_Api";

    // TODO make configurable HOW? make it obfuscated
    private static final String mEmotionURL = "http://office.ultinous.com:11000/expr";
    private static AtomicInteger mSerial = new AtomicInteger(1);

    private TextView mTextView;
    private ConnectivityManager mConnMgr;
    private boolean mWifiOnly;
    private byte[] bytes; // TODO remove it from class level, when JPEG file is not needed

    public EmotionApiCallAsyncTask(TextView textView, ConnectivityManager connMgr, boolean wifiOnly) {
        mTextView = textView;
        mConnMgr = connMgr;
        mWifiOnly = wifiOnly;
    }

    @Override
    protected String doInBackground(Image... params) {
        Log.i(TAG, "doInBackground called");
//        if (params == null || params.length != 1) {
//            Log.e(TAG, "no image/too many images got to call API with");
//            for (Image p : params) {
//                p.close();
//            }
//            return "NO PICTURE";
//        }
//        String retStr = "";
//        if (!isConnected()) {
//            params[0].close();
//            retStr = "NETWORK ERROR";
//            return retStr;
//        }
//        HttpURLConnection connection = null;
//        String requestJsonStr = "";
//        try {
//            // call emotion API
//            requestJsonStr = createRequestBody(params[0]);
//            connection = (HttpURLConnection) new URL(mEmotionURL).openConnection();
//            if (requestJsonStr != null && requestJsonStr.length() > 0) {
//                writeRequest(connection, requestJsonStr);
//            }
//
//            connection.connect();
//
//            int httpCode = connection.getResponseCode();
//            String httpMsg = connection.getResponseMessage();
//
//            Log.d(TAG, "HTTP Response: (" + httpCode + ") " + httpMsg);
//
//            if (httpCode != HttpURLConnection.HTTP_OK) {
//                retStr = "API ERROR";
//                return retStr;
//            }
//
//            String respStr = readResponse(connection);
//            connection.disconnect();
//            connection = null;
//
//            Log.i(TAG, "JSon response: " + respStr);
//            retStr = parseJson(respStr);
//
//            return retStr;
//        } catch (IOException e) {
//            Log.e(TAG, "Exception while calling emotion API");
//            e.printStackTrace();
//            retStr = "ERROR";
//        } finally {
//            if (connection != null) {
//                connection.disconnect();
//            }
//            writeJpeg(bytes, requestJsonStr.length(), retStr);
//        }
//
        for (Image p : params) {
            p.close();
        }
       return "Happy"; // retStr;
        // return Long.toString(SystemClock.uptimeMillis());
    }

    @Override
    protected void onPostExecute(String result) {
        if (result != null && !result.isEmpty()) {
            mTextView.setText(insertNewLine(result));
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

    private static void writeRequest(HttpURLConnection connection, String requestJsonStr) {
        DataOutputStream wr = null;
        try {
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Content-Length", "" + requestJsonStr.length());
            connection.setUseCaches(false);
            connection.setDoInput(true);
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(requestJsonStr.length());

            //Send request
            wr = new DataOutputStream(connection.getOutputStream());
            wr.writeBytes(requestJsonStr);
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


    private String createRequestBody(Image image) {
        try {
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            image.close(); // close asap
            image = null;
            // Log.i(EmotionDetectionFragment.TAG, "Start Base64.encode");
            // request: bytes -> base64 encode -> UTF-8 decode
            byte[] baseBuf = Base64.encode(bytes, Base64.DEFAULT);
            String requestStr = null;
                requestStr = new String(baseBuf, "UTF-8");
            // Log.i(EmotionDetectionFragment.TAG, "Finished UTF-8 String");
            JSONObject request = new JSONObject();
            request.put("image",requestStr);
            return request.toString();
        } catch (JSONException | UnsupportedEncodingException e) {
            Log.e(TAG, "Exception while creating request from image");
            e.printStackTrace();
        } finally {
            if (image != null) {
                image.close(); // close always
            }
        }
        return null;
    }

    private void writeJpeg(byte[] bytes, int requestLen, String emotion) {

        FileOutputStream output = null;
        try {
            File outDir = mTextView.getContext().getExternalFilesDir(null);
            if (mSerial.get() == 1) {
                Log.i(TAG, "Output file path: " + outDir.getPath());
                for(File file: outDir.listFiles())
                    if (!file.isDirectory())
                        file.delete();
            }
            File mFile = new File(outDir, "p_"+ mSerial.getAndAdd(1) + "_" +
                        requestLen + "_" + emotion  + ".jpg");
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

    private static String parseJson(String respStr) {
        // sample responses
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
            } else if(resultJson.has("error")) {
                JSONObject errorJson = resultJson.getJSONObject("error");
                retStr = errorJson.getString("msg").substring(0, 10).toUpperCase();
            }
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
