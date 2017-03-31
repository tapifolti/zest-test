package com.tapifolti.emotiondetection.game;

import java.io.Serializable;

public enum PlayGame implements Serializable {
    MIRROR(Emotions.MIRROR, "Mirrors your emotions"),
    PLAY_HAPPINESS(Emotions.HAPPINESS, "Be Happy"),
    PLAY_NEUTRAL(Emotions.NEUTRAL, "Stay Neutral"),
    PLAY_ANGER(Emotions.ANGER, "Show Anger"),
    PLAY_SADNESS(Emotions.SADNESS, "Show Sadness"),
    PLAY_SURPRISE(Emotions.SURPRISE, "Be Surprised"),
    PLAY_FEAR(Emotions.FEAR, "Show Fear"),
    PLAY_DISGUST(Emotions.DISGUST, "Be Disgusted"),
    PLAY_CONTEMPT(Emotions.CONTEMPT, "Show Contempt");

    public static String PLAY = "Play";

    private final String mText;
    private final String mDesc;

    PlayGame(final String text, final String desc) {
        this.mText = text;
        this.mDesc = desc;
    }

    @Override
    public String toString() {return mText; }

    public String getDesc() {return mDesc; }

    public static PlayGame findItem(String item) {
        for (PlayGame game : PlayGame.values()) {
            if (item.toLowerCase().equals(game.mText.toLowerCase())) {
                return game;
            }
        }
        return MIRROR;
    }

    public static String[] emotions() {
        String[] ret = new String[PlayGame.values().length];
        int i = 0;
        for (PlayGame game : PlayGame.values()) {
            ret[i++] = game.mText;
        }
        return ret;
    }

    public static boolean isOk(String result, PlayGame game) {
        if (game.mText.equals(Emotions.MIRROR)) {
            return true;
        }
        return game.mText.equals(result);
    }

}