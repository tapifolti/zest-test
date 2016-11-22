package com.tapifolti.emotiondetection.game;

import android.app.Activity;
import android.database.DataSetObserver;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.TextView;

import com.tapifolti.emotiondetection.R;

public class GridViewAdapter extends ArrayAdapter<String> {

    private static final String TAG = "Emotion_Adapter";

    Activity mActivity;
    int mLayoutResourceId;
    LayoutInflater mInflater;

    public GridViewAdapter(Activity activity) {
        super(activity, R.layout.grid_layout, PlayGame.emotions());
        mActivity = activity;
        mInflater = LayoutInflater.from(activity);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Log.i(TAG, "GridViewAdapter getView pos:" + position + " View:" + convertView);

        View ret = convertView;
        if (ret == null) {
            ret = mInflater.inflate(R.layout.grid_layout, parent, false);
            ret.setTag(R.id.grid_image, ret.findViewById(R.id.grid_image));
            ret.setTag(R.id.grid_text, ret.findViewById(R.id.grid_text));
        }
        ImageView picture = (ImageView)ret.getTag(R.id.grid_image);
        TextView name = (TextView)ret.getTag(R.id.grid_text);

        String emotion  = getItem(position);
        int picId = mActivity.getResources().getIdentifier(emotion, "drawable", mActivity.getPackageName());
        picture.setImageResource(picId);
        if (PlayGame.findItem(emotion) == PlayGame.MIRROR) {
            name.setText(capitalize(emotion));
        } else {
            name.setText(PlayGame.PLAY + " " + capitalize(emotion));
        }

        return ret;
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        String preFix  = str.substring(0, 1);
        String postFix = str.substring(1);
        return preFix.toUpperCase() + postFix;
    }
}
