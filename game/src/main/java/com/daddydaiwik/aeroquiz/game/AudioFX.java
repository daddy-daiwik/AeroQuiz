package com.daddydaiwik.aeroquiz.game;

import com.jme3.asset.AssetManager;
import com.jme3.audio.AudioData;
import com.jme3.audio.AudioNode;
import com.jme3.scene.Node;

import java.util.logging.Logger;

/**
 * Manages background music and one-shot sound effects for AeroQuiz.
 *
 * <p>Every method is a no-op when the corresponding asset was not detected
 * by {@link AssetRegistry} — no exceptions, no crash, just silence.
 *
 * <p>SFX nodes are created fresh on each call and self-clean once finished.
 */
public class AudioFX {

    private static final Logger LOG = Logger.getLogger(AudioFX.class.getName());

    private final AssetManager  am;
    private final AssetRegistry registry;
    private final Node          rootNode;

    private AudioNode musicNode;

    public AudioFX(AssetManager am, AssetRegistry registry, Node rootNode) {
        this.am       = am;
        this.registry = registry;
        this.rootNode = rootNode;
    }

    // -------------------------------------------------------------------------
    // Background music
    // -------------------------------------------------------------------------

    /** Starts looping background music (streamed). */
    public void startMusic() {
        if (!registry.has(AssetRegistry.SFX_MUSIC)) return;
        stopMusic();
        try {
            musicNode = new AudioNode(am, registry.path(AssetRegistry.SFX_MUSIC),
                                     AudioData.DataType.Stream);
            musicNode.setLooping(true);
            musicNode.setVolume(0.55f);
            musicNode.setPositional(false);
            rootNode.attachChild(musicNode);
            musicNode.play();
        } catch (Exception e) {
            LOG.warning("AudioFX: could not start music — " + e.getMessage());
        }
    }

    /** Stops background music. */
    public void stopMusic() {
        if (musicNode != null) {
            musicNode.stop();
            musicNode.removeFromParent();
            musicNode = null;
        }
    }

    // -------------------------------------------------------------------------
    // One-shot SFX
    // -------------------------------------------------------------------------

    public void playCorrect() {
        if (registry.has(AssetRegistry.SFX_EXPLOSION_CORRECT)) {
            playOnce(AssetRegistry.SFX_EXPLOSION_CORRECT, 1.0f);
        } else if (registry.has(AssetRegistry.SFX_CORRECT)) {
            playOnce(AssetRegistry.SFX_CORRECT, 1.0f);
        } else {
            playOnce(AssetRegistry.SFX_EXPLOSION, 1.0f);
        }
    }

    public void playWrong() {
        if (registry.has(AssetRegistry.SFX_EXPLOSION_INCORRECT)) {
            playOnce(AssetRegistry.SFX_EXPLOSION_INCORRECT, 1.0f);
        } else if (registry.has(AssetRegistry.SFX_WRONG)) {
            playOnce(AssetRegistry.SFX_WRONG, 1.0f);
        } else {
            playOnce(AssetRegistry.SFX_EXPLOSION, 1.0f);
        }
    }

    public void playExplosion() {
        if (registry.has(AssetRegistry.SFX_EXPLOSION)) {
            playOnce(AssetRegistry.SFX_EXPLOSION, 1.0f);
        } else if (registry.has(AssetRegistry.SFX_EXPLOSION_CORRECT)) {
            playOnce(AssetRegistry.SFX_EXPLOSION_CORRECT, 1.0f);
        } else if (registry.has(AssetRegistry.SFX_EXPLOSION_INCORRECT)) {
            playOnce(AssetRegistry.SFX_EXPLOSION_INCORRECT, 1.0f);
        }
    }

    public void playExplosionCorrect() {
        playCorrect();
    }

    public void playExplosionIncorrect() {
        playWrong();
    }

    public void playSteamHiss() {
        String[] hissKeys = {
            AssetRegistry.SFX_STEAM_HISS_1,
            AssetRegistry.SFX_STEAM_HISS_2,
            AssetRegistry.SFX_STEAM_HISS_3,
            AssetRegistry.SFX_STEAM_HISS_4,
            AssetRegistry.SFX_STEAM_HISS_5,
            AssetRegistry.SFX_STEAM_HISS
        };
        java.util.List<String> valid = new java.util.ArrayList<>();
        for (String k : hissKeys) {
            if (registry.has(k)) valid.add(k);
        }
        if (!valid.isEmpty()) {
            String selected = valid.get((int) (Math.random() * valid.size()));
            playOnce(selected, 0.9f);
        }
    }

    // -------------------------------------------------------------------------
    // Private
    // -------------------------------------------------------------------------

    private void playOnce(String key) {
        playOnce(key, 1f);
    }

    private void playOnce(String key, float volume) {
        if (!registry.has(key)) return;
        try {
            AudioNode node = new AudioNode(am, registry.path(key), AudioData.DataType.Buffer);
            node.setLooping(false);
            node.setVolume(volume);
            node.setPositional(false);
            rootNode.attachChild(node);
            node.playInstance();
        } catch (Exception e) {
            LOG.warning("AudioFX: could not play " + key + " — " + e.getMessage());
        }
    }
}
