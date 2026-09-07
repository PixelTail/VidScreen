package dev.vidscreen.client;

import dev.vidscreen.media.MediaPlayer;

public interface MediaPlayerFactory {
    MediaPlayer create(int width, int height);
}
