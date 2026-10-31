package com.daddydaiwik.aeroquiz.android;

import android.os.Bundle;
import com.jme3.app.AndroidHarness;
import java.util.logging.Level;
import java.util.logging.LogManager;

public class MainActivity extends AndroidHarness {

    public MainActivity() {
        // Set the application class to run
        appClass = "com.daddydaiwik.aeroquiz.game.AeroQuiz";

        // Display configuration
        screenFullScreen = true;
        screenShowTitle = false;

        // OpenGL configuration
        eglBitsPerPixel = 24;
        eglAlphaBits = 0;
        eglDepthBits = 16;
        eglSamples = 0;

        // Input configuration
        mouseEventsEnabled = true;
        keyEventsEnabled = true;

        splashPicID = 0;
        exitDialogTitle = "Exit AeroQuiz?";
        exitDialogMessage = "Are you sure you want to exit?";

        try {
            LogManager.getLogManager().getLogger("").setLevel(Level.INFO);
        } catch (Exception ignored) {}
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }
}
