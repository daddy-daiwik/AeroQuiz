package com.daddydaiwik.aeroquiz.game;

import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetManager;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Probes the JME asset manager at startup to detect which optional
 * user-supplied assets are present. Other states call {@link #has(String)}
 * and {@link #path(String)} to decide whether to load a 3-D model / sound,
 * or fall back to the built-in primitive shapes / silence.
 *
 * <hr>
 * <h3>Where to put your asset files</h3>
 *
 * Drop files into {@code game/src/main/resources/} while developing, or
 * place them next to the game JAR in production:
 *
 * <pre>
 * ┌─ Models/
 * │    plane.j3o             ← player plane model
 * │    obstacle.j3o          ← shared obstacle (used for all 4 colours)
 * │    obstacle_red.j3o      ← per-colour obstacle (overrides shared)
 * │    obstacle_blue.j3o
 * │    obstacle_green.j3o
 * │    obstacle_yellow.j3o
 * │    (also accepts .obj / .gltf / .glb)
 * │
 * ├─ Sounds/
 * │    bg_music.ogg          ← looping background music
 * │    correct.ogg           ← correct-answer SFX
 * │    wrong.ogg             ← wrong-answer SFX
 * │    explosion.ogg         ← obstacle-hit SFX
 * │    (also accepts .wav)
 * │
 * └─ Textures/
 *      sky.png               ← sky/backdrop  (also .jpg)
 * </pre>
 */
public class AssetRegistry {

    private static final Logger LOG = Logger.getLogger(AssetRegistry.class.getName());

    // ── model base-keys ──────────────────────────────────────────────────────
    public static final String PLANE_MODEL     = "Models/plane.j3o";
    public static final String PLANE_NOPROP    = "Models/plane-without-propeller.j3o";
    public static final String PROPELLER_MODEL = "Models/propeller.j3o";
    public static final String HELI_MODEL      = "Models/attack_heli.j3o";
    public static final String HELI_ROTOR      = "Models/heli_propeller.j3o";
    public static final String CAR_MODEL       = "Models/flying_car.j3o";
    public static final String OBS_SHARED      = "Models/obstacle.j3o";
    public static final String OBS_RED      = "Models/obstacle_red.j3o";
    public static final String OBS_BLUE     = "Models/obstacle_blue.j3o";
    public static final String OBS_GREEN    = "Models/obstacle_green.j3o";
    public static final String OBS_YELLOW   = "Models/obstacle_yellow.j3o";

    private static final String[] MODEL_EXTS = {".j3o", ".obj", ".gltf", ".glb"};

    // ── sound base-keys (extension-less) ────────────────────────────────────
    public static final String SFX_MUSIC               = "Sounds/bg_music";
    public static final String SFX_CORRECT             = "Sounds/correct";
    public static final String SFX_WRONG               = "Sounds/wrong";
    public static final String SFX_EXPLOSION           = "Sounds/explosion";
    public static final String SFX_EXPLOSION_CORRECT   = "Sounds/explosion_correct";
    public static final String SFX_EXPLOSION_INCORRECT = "Sounds/explosion_incorrect";
    public static final String SFX_STEAM_HISS          = "Sounds/steam_hiss";
    public static final String SFX_STEAM_HISS_1        = "Sounds/steam_hiss_1";
    public static final String SFX_STEAM_HISS_2        = "Sounds/steam_hiss_2";
    public static final String SFX_STEAM_HISS_3        = "Sounds/steam_hiss_3";
    public static final String SFX_STEAM_HISS_4        = "Sounds/steam_hiss_4";
    public static final String SFX_STEAM_HISS_5        = "Sounds/steam_hiss_5";

    private static final String[] SOUND_EXTS = {".ogg", ".wav"};

    // ── texture base-keys ───────────────────────────────────────────────────
    public static final String TEX_SKY   = "Textures/sky";
    public static final String TEX_GRASS = "Textures/grass";
    public static final String TEX_SAND  = "Textures/sand";
    public static final String TEX_SNOW  = "Textures/snow";
    public static final String TEX_WATER = "Textures/water";
    public static final String TEX_SMOKE = "Textures/smoke";
    public static final String TEX_FLAME = "Textures/flame";
    public static final String TEX_SPARK = "Textures/spark";

    private static final String[] TEX_EXTS = {".png", ".jpg", ".jpeg"};

    // ── resolved map: base-key → full path with extension ───────────────────
    private final Map<String, String> resolved = new HashMap<>();

    private AssetManager am;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Scans the asset manager for all known optional assets.
     * Call once during {@code simpleInitApp}.
     */
    public void probe(AssetManager assetManager) {
        this.am = assetManager;

        probeModel(PLANE_NOPROP);
        probeModel(PROPELLER_MODEL);
        probeModel(PLANE_MODEL);
        probeModel(HELI_MODEL);
        probeModel(HELI_ROTOR);
        probeModel(CAR_MODEL);
        probeModel(OBS_SHARED);
        probeModel(OBS_RED);
        probeModel(OBS_BLUE);
        probeModel(OBS_GREEN);
        probeModel(OBS_YELLOW);

        probeSound(SFX_MUSIC);
        probeSound(SFX_CORRECT);
        probeSound(SFX_WRONG);
        probeSound(SFX_EXPLOSION);
        probeSound(SFX_EXPLOSION_CORRECT);
        probeSound(SFX_EXPLOSION_INCORRECT);
        probeSound(SFX_STEAM_HISS);
        probeSound(SFX_STEAM_HISS_1);
        probeSound(SFX_STEAM_HISS_2);
        probeSound(SFX_STEAM_HISS_3);
        probeSound(SFX_STEAM_HISS_4);
        probeSound(SFX_STEAM_HISS_5);

        // Fallbacks
        if (!has(SFX_CORRECT) && has(SFX_EXPLOSION_CORRECT)) {
            resolved.put(SFX_CORRECT, resolved.get(SFX_EXPLOSION_CORRECT));
        }
        if (!has(SFX_WRONG) && has(SFX_EXPLOSION_INCORRECT)) {
            resolved.put(SFX_WRONG, resolved.get(SFX_EXPLOSION_INCORRECT));
        }
        if (!has(SFX_EXPLOSION) && has(SFX_EXPLOSION_CORRECT)) {
            resolved.put(SFX_EXPLOSION, resolved.get(SFX_EXPLOSION_CORRECT));
        }

        probeTexture(TEX_SKY);
        probeTexture(TEX_GRASS);
        probeTexture(TEX_SAND);
        probeTexture(TEX_SNOW);
        probeTexture(TEX_WATER);

        // Preferred: the flat pastel set produced by tools/TextureGen.java
        String[][] aero = {
            {TEX_GRASS, "Textures/Aero/ground_grass.png"}, {TEX_SAND, "Textures/Aero/ground_sand.png"},
            {TEX_SNOW, "Textures/Aero/ground_snow.png"},   {TEX_WATER, "Textures/Aero/ground_water.png"}
        };
        boolean aeroGround = true;
        for (String[] a : aero) aeroGround &= hasAsset(a[1]);
        if (aeroGround) {
            for (String[] a : aero) resolved.put(a[0], a[1]);
        }
        if (!aeroGround) {
            // Fallback: the original photographic texture set
            if (hasAsset("Textures/Grass004_1K-JPG_Color.jpg")) {
                resolved.put(TEX_GRASS, "Textures/Grass004_1K-JPG_Color.jpg");
            }
            if (hasAsset("Textures/Ground037_1K-JPG/Ground037_1K-JPG_Color.jpg")) {
                resolved.put(TEX_SAND, "Textures/Ground037_1K-JPG/Ground037_1K-JPG_Color.jpg");
            }
            if (hasAsset("Textures/Snow005_1K-JPG/Snow005_1K-JPG_Color.jpg")) {
                resolved.put(TEX_SNOW, "Textures/Snow005_1K-JPG/Snow005_1K-JPG_Color.jpg");
            }
            if (hasAsset("Textures/Water/Water_Color.jpg")) {
                resolved.put(TEX_WATER, "Textures/Water/Water_Color.jpg");
            } else if (hasAsset("Textures/water.jpg")) {
                resolved.put(TEX_WATER, "Textures/water.jpg");
            } else if (hasAsset("Textures/water.png")) {
                resolved.put(TEX_WATER, "Textures/water.png");
            }
        }

        // Kenney Particle Pack textures
        if (hasAsset("Textures/kenney_particle-pack/PNG (Transparent)/smoke_01.png")) {
            resolved.put(TEX_SMOKE, "Textures/kenney_particle-pack/PNG (Transparent)/smoke_01.png");
        }
        if (hasAsset("Textures/kenney_particle-pack/PNG (Transparent)/flame_01.png")) {
            resolved.put(TEX_FLAME, "Textures/kenney_particle-pack/PNG (Transparent)/flame_01.png");
        }
        if (hasAsset("Textures/kenney_particle-pack/PNG (Transparent)/spark_01.png")) {
            resolved.put(TEX_SPARK, "Textures/kenney_particle-pack/PNG (Transparent)/spark_01.png");
        }

        if (resolved.isEmpty()) {
            LOG.info("AssetRegistry: no optional user assets found — using built-in primitives.");
        } else {
            LOG.info("AssetRegistry: detected " + resolved.size() + " asset(s): " + resolved.keySet());
        }
    }

    /** @return {@code true} if an asset was detected for the given key. */
    public boolean has(String key) {
        return resolved.containsKey(key);
    }

    /**
     * @return the resolved path (including extension) for {@code key},
     *         or {@code null} if not detected.
     */
    public String path(String key) {
        return resolved.get(key);
    }

    /**
     * Resolves the best obstacle model path for a given colour key
     * ({@code "RED"}, {@code "BLUE"}, {@code "GREEN"}, {@code "YELLOW"}).
     * Prefers colour-specific model; falls back to shared obstacle model.
     *
     * @return resolved path, or {@code null} if no obstacle model found at all
     */
    public String obstacleModelPath(String colorKey) {
        String specificKey = "Models/obstacle_" + colorKey.toLowerCase() + ".j3o";
        if (resolved.containsKey(specificKey)) return resolved.get(specificKey);
        if (resolved.containsKey(OBS_SHARED))  return resolved.get(OBS_SHARED);
        return null;
    }

    // -------------------------------------------------------------------------
    // Probing helpers
    // -------------------------------------------------------------------------

    private void probeModel(String baseKey) {
        // Strip the .j3o from the canonical key and try every extension
        String stem = baseKey.endsWith(".j3o")
                ? baseKey.substring(0, baseKey.length() - 4) : baseKey;
        for (String ext : MODEL_EXTS) {
            String full = stem + ext;
            if (hasAsset(full)) { resolved.put(baseKey, full); return; }
        }
    }

    private void probeSound(String baseKey) {
        for (String ext : SOUND_EXTS) {
            String full = baseKey + ext;
            if (hasAsset(full)) { resolved.put(baseKey, full); return; }
        }
    }

    private void probeTexture(String baseKey) {
        for (String ext : TEX_EXTS) {
            String full = baseKey + ext;
            if (hasAsset(full)) { resolved.put(baseKey, full); return; }
        }
    }

    private boolean hasAsset(String path) {
        try {
            return am.locateAsset(new AssetKey<>(path)) != null;
        } catch (Exception e) {
            return false;
        }
    }
}
