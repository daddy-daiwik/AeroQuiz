package com.daddydaiwik.aeroquiz.game;

import com.jme3.app.SimpleApplication;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.asset.AssetKey;
import com.jme3.scene.Geometry;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;
import com.jme3.texture.Texture;
import com.jme3.util.SkyFactory;

import java.util.List;
import java.util.logging.Logger;

/**
 * AeroQuiz — main SimpleApplication class.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Probes for optional user assets via {@link AssetRegistry}.</li>
 *   <li>Sets up the scene (lighting, ground, sky colour).</li>
 *   <li>Attaches all AppStates in dependency order.</li>
 *   <li>Loads questions via {@link QuizLoader} and hands them to {@link GameState}.</li>
 *   <li>Exposes {@link #getAssetRegistry()} and {@link #getAudioFX()} for other states.</li>
 * </ul>
 */
public class AeroQuiz extends SimpleApplication {

    private static final Logger LOG = Logger.getLogger(AeroQuiz.class.getName());

    // Shared singletons accessible by all AppStates
    private AssetRegistry assetRegistry;
    private AudioFX       audioFX;
    private QuizDatabase  quizDatabase;

    // -------------------------------------------------------------------------
    // Entry point (direct IDE launch)
    // -------------------------------------------------------------------------

    public static void main(String[] args) {
        AeroQuiz app = new AeroQuiz();
        com.jme3.system.AppSettings settings = new com.jme3.system.AppSettings(true);
        settings.setTitle("AeroQuiz");
        settings.setResolution(1920, 1080);
        settings.setSamples(4);
        settings.setVSync(true);
        settings.setResizable(true);
        app.setSettings(settings);
        app.setShowSettings(false);
        app.start();
    }

    // -------------------------------------------------------------------------
    // SimpleApplication overrides
    // -------------------------------------------------------------------------

    @Override
    public void simpleInitApp() {
        flyCam.setEnabled(false);
        cam.setFrustumFar(2000f);

        // ── Asset detection ───────────────────────────────────────────────────
        // Step 1: Convert any .blend/.fbx/.dae → .glb using Blender CLI,
        //         and register filesystem locators for all scan directories.
        ModelConverter.convertAll(assetManager);

        // Step 2: Probe for all known optional assets (models, sounds, textures).
        assetRegistry = new AssetRegistry();
        assetRegistry.probe(assetManager);

        // ── Audio ────────────────────────────────────────────────────────
        audioFX = new AudioFX(assetManager, assetRegistry, rootNode);

        // ── Database ─────────────────────────────────────────────────────
        quizDatabase = new QuizDatabase();

        // ── Sky / atmosphere ─────────────────────────────────────────────
        buildSky();

        // ── Lighting ─────────────────────────────────────────────────────
        // Main overhead sunlight
        DirectionalLight sun = new DirectionalLight();
        sun.setDirection(new Vector3f(-0.5f, -1f, -0.5f).normalizeLocal());
        sun.setColor(ColorRGBA.White.mult(1.0f));
        rootNode.addLight(sun);

        // Chase-cam fill light (shines from camera onto plane's rear/top so PBR models aren't shadowed black)
        DirectionalLight camFill = new DirectionalLight();
        camFill.setDirection(new Vector3f(0.1f, -0.4f, -1.0f).normalizeLocal());
        camFill.setColor(ColorRGBA.White.mult(1.2f));
        rootNode.addLight(camFill);

        AmbientLight ambient = new AmbientLight();
        ambient.setColor(new ColorRGBA(0.45f, 0.45f, 0.45f, 1f));
        rootNode.addLight(ambient);

        // ── Settings ────────────────────────────────────────────────────
        QuizLoader.loadSettings();

        // ── Ground ───────────────────────────────────────────────────────
        buildGround();

        // ── AppStates (GameState first so others can find it) ────────────
        GameState       gameState       = new GameState(this);
        PlaneController planeController = new PlaneController(this);
        ObstacleSpawner obstacleSpawner = new ObstacleSpawner(this);
        HudState        hudState        = new HudState(this);

        stateManager.attach(gameState);
        stateManager.attach(planeController);
        stateManager.attach(obstacleSpawner);
        stateManager.attach(hudState);

        // ── Questions ────────────────────────────────────────────────────
        List<QuizQuestion> questions = QuizLoader.load("questions.txt");
        if (questions.isEmpty()) {
            LOG.severe("No questions loaded — check questions.txt on classpath or working dir.");
        }

        gameState.initGame(questions);
        updateGroundTerrain();
    }

    @Override
    public void simpleUpdate(float tpf) {
        PlaneController pc = stateManager.getState(PlaneController.class);
        if (pc != null && groundGeometry != null) {
            float pz = pc.getPlanePosition().z;
            groundGeometry.setLocalTranslation(0f, -2.5f, pz - 4500f);
        }
    }

    // -------------------------------------------------------------------------
    // Public accessors for AppStates
    // -------------------------------------------------------------------------

    /** @return the shared asset registry (populated at startup). */
    public AssetRegistry getAssetRegistry() { return assetRegistry; }

    /** @return the shared audio effects manager. */
    public AudioFX getAudioFX() { return audioFX; }

    /** @return the database manager for scores and pilot records. */
    public QuizDatabase getQuizDatabase() { return quizDatabase; }

    /** Ground geometry reference. */
    private Geometry groundGeometry;

    /**
     * Updates ground texture and color to match the currently configured terrain in settings.
     */
    public void updateGroundTerrain() {
        if (groundGeometry == null) return;

        QuizSettings settings = QuizLoader.getSettings();
        String terrain = (settings != null && settings.getTerrain() != null)
                ? settings.getTerrain().toUpperCase().trim() : "GRASS";

        String targetKey = switch (terrain) {
            case "SAND", "DESERT" -> AssetRegistry.TEX_SAND;
            case "SNOW", "ARCTIC", "WINTER", "ICE" -> AssetRegistry.TEX_SNOW;
            case "WATER", "OCEAN", "SEA" -> AssetRegistry.TEX_WATER;
            case "GRASS" -> AssetRegistry.TEX_GRASS;
            default -> AssetRegistry.TEX_GRASS;
        };

        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        if (assetRegistry != null && assetRegistry.has(targetKey)) {
            try {
                Texture terrainTex = assetManager.loadTexture(assetRegistry.path(targetKey));
                terrainTex.setWrap(Texture.WrapMode.Repeat);
                mat.setTexture("ColorMap", terrainTex);
                mat.setColor("Color", ColorRGBA.White);
                LOG.info("Ground: applied " + terrain + " texture: " + assetRegistry.path(targetKey));
            } catch (Exception e) {
                LOG.warning("Ground: failed to load " + terrain + " texture: " + e.getMessage());
                mat.setColor("Color", getTerrainFallbackColor(terrain));
            }
        } else {
            LOG.info("Ground: using fallback color for terrain " + terrain);
            mat.setColor("Color", getTerrainFallbackColor(terrain));
        }

        groundGeometry.setMaterial(mat);
    }

    private ColorRGBA getTerrainFallbackColor(String terrain) {
        return switch (terrain) {
            case "SAND", "DESERT" -> new ColorRGBA(0.85f, 0.72f, 0.45f, 1f);
            case "SNOW", "ARCTIC", "WINTER", "ICE" -> new ColorRGBA(0.92f, 0.96f, 1.0f, 1f);
            case "WATER", "OCEAN", "SEA" -> new ColorRGBA(0.10f, 0.35f, 0.70f, 1f);
            default -> new ColorRGBA(0.15f, 0.55f, 0.15f, 1f);
        };
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void buildGround() {
        Box groundMesh = new Box(200f, 0.5f, 5000f);
        groundMesh.scaleTextureCoordinates(new Vector2f(50f, 1250f));
        groundGeometry = new Geometry("Ground", groundMesh);
        groundGeometry.setLocalTranslation(0f, -2.5f, -4800f);
        updateGroundTerrain();
        rootNode.attachChild(groundGeometry);
    }

    private void buildSky() {
        try {
            // Check for 6-face undistorted cubemap first (zero projection stretch)
            if (hasAsset("Textures/sky_08_2k/sky_08_cubemap_2k/px.png")) {
                Texture west  = assetManager.loadTexture("Textures/sky_08_2k/sky_08_cubemap_2k/nx.png");
                Texture east  = assetManager.loadTexture("Textures/sky_08_2k/sky_08_cubemap_2k/px.png");
                Texture north = assetManager.loadTexture("Textures/sky_08_2k/sky_08_cubemap_2k/nz.png");
                Texture south = assetManager.loadTexture("Textures/sky_08_2k/sky_08_cubemap_2k/pz.png");
                Texture up    = assetManager.loadTexture("Textures/sky_08_2k/sky_08_cubemap_2k/py.png");
                Texture down  = assetManager.loadTexture("Textures/sky_08_2k/sky_08_cubemap_2k/ny.png");
                Spatial sky = SkyFactory.createSky(assetManager, west, east, north, south, up, down);
                rootNode.attachChild(sky);
                LOG.info("AeroQuiz: loaded 6-sided undistorted cubemap sky.");
                return;
            }

            // Fallback to Equirectangular 2k sky textures
            String skyPath = null;
            if (hasAsset("Textures/sky_08_2k/sky_08_2k.png")) {
                skyPath = "Textures/sky_08_2k/sky_08_2k.png";
            } else if (hasAsset("Textures/sky_12_2k/sky_12_2k.png")) {
                skyPath = "Textures/sky_12_2k/sky_12_2k.png";
            }

            if (skyPath != null) {
                Texture skyTex = assetManager.loadTexture(skyPath);
                Spatial sky = SkyFactory.createSky(assetManager, skyTex, SkyFactory.EnvMapType.EquirectMap);
                rootNode.attachChild(sky);
                LOG.info("AeroQuiz: loaded equirectangular sky from " + skyPath);
                return;
            }
        } catch (Exception e) {
            LOG.warning("AeroQuiz: failed to load skybox: " + e.getMessage());
        }
        viewPort.setBackgroundColor(new ColorRGBA(0.5f, 0.7f, 1.0f, 1.0f));
    }

    private boolean hasAsset(String path) {
        try {
            return assetManager.locateAsset(new AssetKey<>(path)) != null;
        } catch (Exception e) {
            return false;
        }
    }
}
