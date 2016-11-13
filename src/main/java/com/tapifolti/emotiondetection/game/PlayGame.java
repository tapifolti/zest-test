package com.tapifolti.emotiondetection.game;


public enum PlayGame {
    PLAY_MIRROR(""),
    PLAY_HAPPINESS(Emotions.HAPPINESS),
    PLAY_NEUTRAL(Emotions.NEUTRAL),
    PLAY_ANGER(Emotions.ANGER),
    PLAY_SADNESS(Emotions.SADNESS),
    PLAY_SURPRISE(Emotions.SURPRISE),
    PLAY_FEAR(Emotions.FEAR),
    PLAY_DISGUST(Emotions.DISGUST),
    PLAY_CONTEMPT(Emotions.CONTEMPT);

    public static String PLAY = "Play";

    private final String text;

    private PlayGame(final String text) {
        this.text = text;
    }

    @Override
    public String toString() {
        return text;
    }

    public static PlayGame findItem(String item) {
        for (PlayGame game : PlayGame.values()) {
            if (item.toLowerCase().equals(game.text.toLowerCase())) {
                return game;
            }
        }
        return PLAY_MIRROR;
    }
}