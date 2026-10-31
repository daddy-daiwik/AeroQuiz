package com.daddydaiwik.aeroquiz.desktopmodule;

import com.daddydaiwik.aeroquiz.game.AeroQuiz;
import com.jme3.system.AppSettings;

/**
 * Used to launch a jme application in desktop environment
 *
 */
public class DesktopLauncher {
    public static void main(String[] args) {
        final AeroQuiz game = new AeroQuiz();

        final AppSettings appSettings = new AppSettings(true);
        appSettings.setTitle("AeroQuiz");
        appSettings.setResolution(1920, 1080);
        appSettings.setSamples(4);     // 4x MSAA anti-aliasing for smooth edges
        appSettings.setVSync(true);
        appSettings.setFrequency(60);
        appSettings.setResizable(true);

        game.setSettings(appSettings);
        game.setShowSettings(false); // Settings dialog not supported on mac
        game.start();
    }
}
