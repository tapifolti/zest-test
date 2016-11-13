package com.tapifolti.emotiondetection;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.tapifolti.emotiondetection.game.PlayGame;

public class RootActivity extends Activity {
    private static final String TAG = "Emotion_RootActivity";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.root_activity);
        if (null == savedInstanceState) {
            getFragmentManager().beginTransaction()
                    .replace(R.id.container, RootFragment.newInstance())
                    .commit();
        }
    }

    public boolean onCreateOptionsMenu(Menu menu) {

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.root_menu, menu);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        Log.i(TAG, "MenuItem selected: " + item.toString());
        Intent myIntent = new Intent(this, CameraActivity.class);
        switch (item.getItemId()) {
            case R.id.mirror:
                myIntent.putExtra(PlayGame.PLAY, PlayGame.PLAY_MIRROR.toString()); //Optional parameters
                startActivity(myIntent);
                break;
            case R.id.happy:
            case R.id.neutral:
            case R.id.anger:
            case R.id.sadness:
            case R.id.surprise:
            case R.id.fear:
            case R.id.disgust:
            case R.id.contempt:
                int space = item.toString().indexOf(" ");
                myIntent.putExtra(PlayGame.PLAY, PlayGame.findItem(item.toString().substring(space+1)).toString());
                startActivity(myIntent);
                break;
            case R.id.setkey:
                // TODO set CS key
                break;
            case R.id.howto:
                // TODO how to register to CS
                break;
            case R.id.pref:
                // TODO Preferences as it should be implemented: wifi, timer speed
                break;
            case R.id.help:
                // TODO Help
                break;
            case R.id.about:
                // TODO about
                break;
        }
        return true;
    }

}