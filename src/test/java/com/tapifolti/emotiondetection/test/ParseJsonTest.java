package com.tapifolti.emotiondetection.test;

import com.tapifolti.emotiondetection.apicall.CSEmotionCallAsyncTask;

import org.junit.Test;


import static junit.framework.Assert.assertTrue;

public class ParseJsonTest {

    private static final String sampleMulti = "[\n" +
            "{\n" +
            "\"faceRectangle\": {\n" +
            "\"left\": 89,\n" +
            "\"top\": 120,\n" +
            "\"width\": 71,\n" +
            "\"height\": 71\n" +
            "},\n" +
            "\"scores\": {\n" +
            "\"anger\": 3.44697156e-7,\n" +
            "\"contempt\": 6.8442256e-7,\n" +
            "\"disgust\": 0.00009313403,\n" +
            "\"fear\": 2.90478118e-13,\n" +
            "\"happiness\": 0.999748647,\n" +
            "\"neutral\": 0.000157187445,\n" +
            "\"sadness\": 4.30214253e-10,\n" +
            "\"surprise\": 1.04141549e-8\n" +
            "}\n" +
            "},\n" +
            "{\n" +
            "\"faceRectangle\": {\n" +
            "\"left\": 371,\n" +
            "\"top\": 84,\n" +
            "\"width\": 68,\n" +
            "\"height\": 68\n" +
            "},\n" +
            "\"scores\": {\n" +
            "\"anger\": 0.00182586757,\n" +
            "\"contempt\": 0.0002984629,\n" +
            "\"disgust\": 0.02091086,\n" +
            "\"fear\": 0.00000180568077,\n" +
            "\"happiness\": 0.975053847,\n" +
            "\"neutral\": 0.00117619825,\n" +
            "\"sadness\": 0.000502721639,\n" +
            "\"surprise\": 0.000230238249\n" +
            "}\n" +
            "},\n" +
            "{\n" +
            "\"faceRectangle\": {\n" +
            "\"left\": 13,\n" +
            "\"top\": 115,\n" +
            "\"width\": 65,\n" +
            "\"height\": 65\n" +
            "},\n" +
            "\"scores\": {\n" +
            "\"anger\": 0.00000699793964,\n" +
            "\"contempt\": 7.99774966e-7,\n" +
            "\"disgust\": 0.00006691713,\n" +
            "\"fear\": 2.63261342e-7,\n" +
            "\"happiness\": 0.9998963,\n" +
            "\"neutral\": 0.000002192277,\n" +
            "\"sadness\": 0.00002027727,\n" +
            "\"surprise\": 0.000006275641\n" +
            "}\n" +
            "},\n" +
            "{\n" +
            "\"faceRectangle\": {\n" +
            "\"left\": 292,\n" +
            "\"top\": 126,\n" +
            "\"width\": 61,\n" +
            "\"height\": 61\n" +
            "},\n" +
            "\"scores\": {\n" +
            "\"anger\": 4.0647123e-8,\n" +
            "\"contempt\": 1.64457253e-10,\n" +
            "\"disgust\": 4.61994972e-7,\n" +
            "\"fear\": 4.95065239e-11,\n" +
            "\"happiness\": 0.999999464,\n" +
            "\"neutral\": 1.56707947e-9,\n" +
            "\"sadness\": 1.28522437e-9,\n" +
            "\"surprise\": 8.867549e-9\n" +
            "}\n" +
            "},\n" +
            "{\n" +
            "\"faceRectangle\": {\n" +
            "\"left\": 176,\n" +
            "\"top\": 119,\n" +
            "\"width\": 59,\n" +
            "\"height\": 59\n" +
            "},\n" +
            "\"scores\": {\n" +
            "\"anger\": 0.000264266157,\n" +
            "\"contempt\": 0.00000851838,\n" +
            "\"disgust\": 0.000587589748,\n" +
            "\"fear\": 0.000454976573,\n" +
            "\"happiness\": 0.9972821,\n" +
            "\"neutral\": 0.00000680189351,\n" +
            "\"sadness\": 0.000008809628,\n" +
            "\"surprise\": 0.001386942\n" +
            "}\n" +
            "},\n" +
            "{\n" +
            "\"faceRectangle\": {\n" +
            "\"left\": 244,\n" +
            "\"top\": 123,\n" +
            "\"width\": 52,\n" +
            "\"height\": 52\n" +
            "},\n" +
            "\"scores\": {\n" +
            "\"anger\": 3.79380879e-7,\n" +
            "\"contempt\": 1.50238613e-8,\n" +
            "\"disgust\": 1.15522427e-8,\n" +
            "\"fear\": 3.231413e-9,\n" +
            "\"happiness\": 0.9999981,\n" +
            "\"neutral\": 0.00000133733238,\n" +
            "\"sadness\": 1.35564209e-8,\n" +
            "\"surprise\": 1.64349089e-7\n" +
            "}\n" +
            "}\n"  + "]";

    private static final String sampleSingle1 = "[\n" +
            "{\n" +
            "\"faceRectangle\": {\n" +
            "\"left\": 89,\n" +
            "\"top\": 120,\n" +
            "\"width\": 71,\n" +
            "\"height\": 71\n" +
            "},\n" +
            "\"scores\": {\n" +
            "\"anger\": 3.44697156e-7,\n" +
            "\"contempt\": 6.8442256e-7,\n" +
            "\"disgust\": 0.00009313403,\n" +
            "\"fear\": 2.90478118e-13,\n" +
            "\"happiness\": 0.999748647,\n" +
            "\"neutral\": 0.000157187445,\n" +
            "\"sadness\": 4.30214253e-10,\n" +
            "\"surprise\": 1.04141549e-8\n" +
            "}\n" +
            "} " + "]";

    private static final String sampleSingle2 = "[\n" +
            "{\n" +
            "\"faceRectangle\": {\n" +
            "\"left\": 89,\n" +
            "\"top\": 120,\n" +
            "\"width\": 71,\n" +
            "\"height\": 71\n" +
            "},\n" +
            "\"scores\": {\n" +
            "\"anger\": 3.44697156e-7,\n" +
            "\"contempt\": 6.8442256e-7,\n" +
            "\"disgust\": 0.00009313403,\n" +
            "\"fear\": 2.90478118e-13,\n" +
            "\"happiness\": 0.0999748647,\n" +
            "\"neutral\": 0.9157187445,\n" +
            "\"sadness\": 4.30214253e-10,\n" +
            "\"surprise\": 1.04141549e-8\n" +
            "}\n" +
            "} " + "]";

    @Test
    public void parseSingle() {

        String res = CSEmotionCallAsyncTask.parseJson(sampleSingle1);
        assertTrue(res.equals("happiness"));
        res = CSEmotionCallAsyncTask.parseJson(sampleSingle2);
        assertTrue(res.equals("neutral"));
    }


    @Test
    public void parseMulti() {
        String res = CSEmotionCallAsyncTask.parseJson(sampleMulti);
        assertTrue(res.equals("NOT ONE FACE"));
    }

}
