package com.tapifolti.emotiondetection;

import android.app.Fragment;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v13.app.FragmentCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.TextView;

import com.tapifolti.emotiondetection.game.GridViewAdapter;
import com.tapifolti.emotiondetection.game.PlayGame;


public class RootFragment extends Fragment
        implements FragmentCompat.OnRequestPermissionsResultCallback {
    // TODO move permission management here
    // Root activity
    // - with App bar
    // - with tiles of the possible different sessions:
    //   - mirror
    //   - Play Angry
    //   - Play Neutral

    private static final String TAG = "Emotion_Root";
    private TextView mPermText;
    CameraPermission mCameraPermission = new CameraPermission(this);

    public static RootFragment newInstance() {
        return new RootFragment();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (!mCameraPermission.onRequestPermissionsResult(requestCode, permissions, grantResults)) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Log.i(TAG, "onCreateView(...) called");
        return inflater.inflate(R.layout.fragment_root, container, false);
    }

    private GridView mGridView;

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        Log.i(TAG, "onViewCreated(...) called");
        mPermText = (TextView) view.findViewById(R.id.permisson);
        mGridView = (GridView) view.findViewById(R.id.gridview);
        GridViewAdapter gridAdapter = new GridViewAdapter(getActivity());
        mGridView.setAdapter(gridAdapter);
        mGridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View v,
                                    int position, long id) {
                String emotion = (String)parent.getItemAtPosition(position);
                Log.i(TAG, "GridItem selected: " + emotion);
                Intent myIntent = new Intent(getActivity(), CameraActivity.class);
                myIntent.putExtra(PlayGame.PLAY, PlayGame.findItem(emotion));
                startActivity(myIntent);
            }
        });        if (!isInLayout()) {
            mGridView.getParent().requestLayout();
        }

    }

    @Override
    public void onResume() {
        super.onResume();
        Log.i(TAG, "onResume() called");
        if (mCameraPermission.showPermissionMessage()) {
            mPermText.setVisibility(View.VISIBLE);
        } else {
            mCameraPermission.setCanUseCamera(true);
            mPermText.setVisibility(View.INVISIBLE);

            // TODO test when permission should be asked
            if (!mCameraPermission.hasPermission()) {
                mCameraPermission.requestCameraPermission();
                return; // onResume will be called if permission granted
            }
        }
    }
}
