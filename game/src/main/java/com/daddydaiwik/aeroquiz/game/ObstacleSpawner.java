package com.daddydaiwik.aeroquiz.game;

import com.jme3.app.Application;
import com.jme3.app.state.AbstractAppState;
import com.jme3.app.state.AppStateManager;
import com.jme3.collision.CollisionResults;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Ray;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Cylinder;
import com.jme3.scene.shape.Sphere;
import com.jme3.util.BufferUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * AppState that manages spawning the options boxes in a 2x2 rectangular grid
 * (or 2-row grid for 2 to 6 options), with the option text written directly inside
 * each box, and customizable 3D shapes (BOX, SPHERE, PYRAMID, CYLINDER).
 */
public class ObstacleSpawner extends AbstractAppState {

    private static final Logger LOG = Logger.getLogger(ObstacleSpawner.class.getName());

    private static final float SPAWN_AHEAD    = 32f;
    private static final float OBSTACLE_SPEED = 8f;
    private static final float COOLDOWN_SECS  = 0.7f;

    public static final String[] COLOR_KEYS = {
        "RED", "BLUE", "GREEN", "YELLOW", "PURPLE", "CYAN"
    };

    public static final ColorRGBA[] COLORS = {
        new ColorRGBA(0.95f, 0.08f, 0.08f, 1f), // Solid vivid RED
        new ColorRGBA(0.08f, 0.45f, 0.98f, 1f), // Solid vivid BLUE
        new ColorRGBA(0.05f, 0.82f, 0.18f, 1f), // Solid vivid GREEN
        new ColorRGBA(0.98f, 0.78f, 0.02f, 1f), // Solid vivid GOLD / YELLOW
        new ColorRGBA(0.75f, 0.12f, 0.92f, 1f), // Solid vivid PURPLE
        new ColorRGBA(0.02f, 0.82f, 0.88f, 1f)  // Solid vivid CYAN
    };

    private AeroQuiz  app;

    public ObstacleSpawner() {}

    public ObstacleSpawner(AeroQuiz app) {
        this.app = app;
    }

    private GameState gameState;
    private Node      obstacleRoot;

    /** Currently active obstacle nodes (each holds a box geometry + 3D label). */
    private final List<Node> currentObstacleNodes = new ArrayList<>();
    private final List<Geometry> currentGeometries = new ArrayList<>();

    private boolean waitingToSpawn = false;
    private float   cooldownTimer  = 0f;

    @Override
    public void initialize(AppStateManager stateManager, Application application) {
        super.initialize(stateManager, application);
        this.app       = (AeroQuiz) application;
        this.gameState = stateManager.getState(GameState.class);

        obstacleRoot = new Node("Obstacles");
        app.getRootNode().attachChild(obstacleRoot);
    }

    @Override
    public void update(float tpf) {
        if (!isEnabled()) return;

        // Handle cooldown between questions
        if (waitingToSpawn) {
            cooldownTimer += tpf;
            if (cooldownTimer >= COOLDOWN_SECS) {
                waitingToSpawn = false;
                cooldownTimer  = 0f;
                spawnObstacles();
            }
            return;
        }

        // Dynamic approach speed based on timePerQuestion:
        // Spawn distance is fixed (SPAWN_AHEAD = 48f).
        // If timePerQuestion > 0, closing speed is SPAWN_AHEAD / timePerQuestion.
        // Relative velocity along Z: vObsZ = closingSpeed - planeSpeed.
        // If timePerQuestion <= 0 (infinite time), closing speed is 0 -> vObsZ = -planeSpeed.
        QuizSettings settings = QuizLoader.getSettings();
        float timePerQuestion = settings.getTimePerQuestion();

        PlaneController pc = app.getStateManager().getState(PlaneController.class);
        float planeSpeed = (pc != null) ? pc.getPlaneSpeed() : 8f;

        float vObsZ;
        if (timePerQuestion > 0f) {
            float closingSpeed = SPAWN_AHEAD / timePerQuestion;
            vObsZ = closingSpeed - planeSpeed;
        } else {
            vObsZ = -planeSpeed;
        }

        // Move obstacle nodes along Z
        for (Node node : currentObstacleNodes) {
            node.move(0f, 0f, vObsZ * tpf);
        }

        // Cull obstacles that have flown past the plane
        if (pc != null) {
            float planeZ = pc.getPlanePosition().z;
            currentObstacleNodes.removeIf(node -> {
                if (node.getWorldTranslation().z > planeZ + 12f) {
                    node.removeFromParent();
                    return true;
                }
                return false;
            });

            // If all obstacles flew past without being answered -> auto-advance
            if (currentObstacleNodes.isEmpty() && !waitingToSpawn
                    && gameState != null && !gameState.isGameOver()
                    && gameState.getPhase() == GameState.Phase.PLAYING) {
                gameState.autoSkipQuestion();
                scheduleNextSpawn();
            }
        }
    }

    @Override
    public void cleanup() {
        removeAllObstacles();
        if (obstacleRoot != null) {
            obstacleRoot.removeFromParent();
        }
        super.cleanup();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public void spawnObstacles() {
        removeAllObstacles();

        PlaneController pc = app.getStateManager().getState(PlaneController.class);
        if (pc == null) return;

        QuizQuestion q = gameState.getCurrentQuestion();
        if (q == null) return;

        int count = q.getOptionCount();
        if (count < 2) count = 2;
        if (count > 6) count = 6;

        float spawnZ = pc.getPlanePosition().z - SPAWN_AHEAD;
        BitmapFont font = app.getAssetManager().loadFont("Interface/Fonts/Default.fnt");
        float textScale = 0.90f;

        // Auto-calculate box dimensions with padding based on max length of option words
        float maxOptionWidth = 0f;
        for (int i = 0; i < count; i++) {
            String fullText = "[" + (i + 1) + "] " + q.getOptionAt(i);
            BitmapText tempText = new BitmapText(font);
            tempText.setSize(textScale);
            tempText.setText(fullText);
            float w = tempText.getLineWidth();
            if (w > maxOptionWidth) {
                maxOptionWidth = w;
            }
        }

        float paddingX = 0.95f;
        float boxHalfWidth  = Math.max(2.6f, (maxOptionWidth / 2f) + paddingX);
        float boxHalfHeight = 1.08f;
        float boxHalfDepth  = 0.22f;

        Mesh boxMesh = new Box(boxHalfWidth, boxHalfHeight, boxHalfDepth);
        List<Vector2f> gridCoords = computeGridCoords(count, boxHalfWidth);

        for (int i = 0; i < count; i++) {
            String colorKey = q.getKeyAt(i);
            String optionText = q.getOptionAt(i);
            Vector2f coord = gridCoords.get(i);
            ColorRGBA color = getColorForKey(colorKey, i);

            Node obsNode = new Node("Obstacle_" + colorKey);
            obsNode.setLocalTranslation(coord.x, coord.y, spawnZ);
            obsNode.setUserData("colorKey", colorKey);
            obsNode.setUserData("optionIndex", i);

            Geometry geo = new Geometry("ObstacleGeo_" + colorKey, boxMesh);
            Material mat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
            mat.setColor("Color", color);
            geo.setMaterial(mat);

            // Store metadata on geometry
            geo.setUserData("colorKey", colorKey);
            geo.setUserData("optionIndex", i);
            geo.setUserData("gridX", coord.x);
            geo.setUserData("gridY", coord.y);

            obsNode.attachChild(geo);
            currentGeometries.add(geo);

            String textContent = "[" + (i + 1) + "] " + optionText;

            // 8-directional crisp black text outline
            float ox = 0.024f;
            float oy = 0.024f;
            float[][] offsets = {
                {-ox, 0f}, {ox, 0f}, {0f, -oy}, {0f, oy},
                {-ox, -oy}, {ox, oy}, {-ox, oy}, {ox, -oy}
            };
            for (float[] off : offsets) {
                BitmapText outline = new BitmapText(font);
                outline.setSize(textScale);
                outline.setText(textContent);
                outline.setColor(ColorRGBA.Black);
                outline.setQueueBucket(RenderQueue.Bucket.Transparent);
                float ow = outline.getLineWidth();
                float oh = outline.getLineHeight();
                outline.setLocalTranslation(-ow / 2f + off[0], oh / 2f + off[1], boxHalfDepth + 0.016f);
                outline.depthFirstTraversal(sp -> {
                    if (sp instanceof Geometry) {
                        Geometry g = (Geometry) sp;
                        Material m = g.getMaterial();
                        if (m != null) {
                            RenderState rs = m.getAdditionalRenderState();
                            rs.setDepthTest(false);
                            rs.setFaceCullMode(RenderState.FaceCullMode.Off);
                        }
                    }
                });
                obsNode.attachChild(outline);
            }

            // 3D text on the front face of the box (bright white on top)
            BitmapText label = new BitmapText(font);
            label.setSize(textScale);
            label.setText(textContent);
            label.setColor(ColorRGBA.White);
            label.setQueueBucket(RenderQueue.Bucket.Transparent);

            float tw = label.getLineWidth();
            float th = label.getLineHeight();
            label.setLocalTranslation(-tw / 2f, th / 2f, boxHalfDepth + 0.022f);

            // Ensure label is never occluded by the box and font quads are never culled
            label.depthFirstTraversal(sp -> {
                if (sp instanceof Geometry) {
                    Geometry g = (Geometry) sp;
                    Material m = g.getMaterial();
                    if (m != null) {
                        RenderState rs = m.getAdditionalRenderState();
                        rs.setDepthTest(false);
                        rs.setFaceCullMode(RenderState.FaceCullMode.Off);
                    }
                }
            });

            obsNode.attachChild(label);
            obstacleRoot.attachChild(obsNode);
            currentObstacleNodes.add(obsNode);
        }

        LOG.info("Spawned " + count + " option boxes in grid ahead at Z=" + spawnZ);
        if (gameState != null) {
            gameState.onQuestionSpawned();
        }
    }

    public Geometry getObstacleByColor(String colorKey) {
        if (colorKey == null) return null;
        for (Geometry g : currentGeometries) {
            String k = g.getUserData("colorKey");
            if (colorKey.equalsIgnoreCase(k)) {
                return g;
            }
        }
        return null;
    }

    public Vector3f getObstacleTarget(String colorKey) {
        Geometry g = getObstacleByColor(colorKey);
        if (g != null) {
            Float gx = g.getUserData("gridX");
            Float gy = g.getUserData("gridY");
            float z = g.getWorldTranslation().z;
            return new Vector3f(gx != null ? gx : 0f, gy != null ? gy : 1.8f, z);
        }
        return null;
    }

    public void removeObstacleGeometry(Geometry geo) {
        if (geo == null) return;
        currentGeometries.remove(geo);
        Node parent = geo.getParent();
        if (parent != null) {
            currentObstacleNodes.remove(parent);
            parent.removeFromParent();
        } else {
            geo.removeFromParent();
        }
    }

    public void removeAllObstacles() {
        for (Node node : currentObstacleNodes) {
            node.removeFromParent();
        }
        currentObstacleNodes.clear();
        currentGeometries.clear();
    }

    public void reset() {
        removeAllObstacles();
        waitingToSpawn = false;
        cooldownTimer  = 0f;
    }

    public void scheduleNextSpawn() {
        waitingToSpawn = true;
        cooldownTimer  = 0f;
    }

    /**
     * Raycasts from screen coordinates (touch/tap or click) into the 3D scene
     * to identify which obstacle box was targeted.
     *
     * @param screenX screen touch X in pixels
     * @param screenY screen touch Y in pixels
     * @return the color key (e.g. "RED", "BLUE") of the tapped obstacle, or null if none
     */
    public String pickObstacleColorAt(float screenX, float screenY) {
        if (app == null || app.getCamera() == null || obstacleRoot == null) return null;
        try {
            com.jme3.math.Vector2f click2d = new com.jme3.math.Vector2f(screenX, screenY);
            Vector3f click3d = app.getCamera().getWorldCoordinates(click2d, 0f).clone();
            Vector3f dir = app.getCamera().getWorldCoordinates(click2d, 1f).subtractLocal(click3d).normalizeLocal();
            Ray ray = new Ray(click3d, dir);
            CollisionResults results = new CollisionResults();
            obstacleRoot.collideWith(ray, results);
            if (results.size() > 0) {
                for (int i = 0; i < results.size(); i++) {
                    Spatial sp = results.getCollision(i).getGeometry();
                    while (sp != null && sp != obstacleRoot) {
                        String colorKey = sp.getUserData("colorKey");
                        if (colorKey != null) {
                            LOG.info("Touch Raycast: tapped obstacle [" + colorKey + "]");
                            return colorKey;
                        }
                        sp = sp.getParent();
                    }
                }
            }
        } catch (Exception e) {
            LOG.fine("Raycast pick error: " + e.getMessage());
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------------

    private static class Vector2f {
        float x, y;
        Vector2f(float x, float y) { this.x = x; this.y = y; }
    }

    private List<Vector2f> computeGridCoords(int count, float boxHalfWidth) {
        List<Vector2f> list = new ArrayList<>();
        float topY = 3.0f;
        float botY = 0.65f;

        // Ensure spacing adapts dynamically so wide option boxes never overlap
        float colX = Math.max(3.8f, boxHalfWidth + 0.6f);

        switch (count) {
            case 2:
                list.add(new Vector2f(-colX, 1.8f));
                list.add(new Vector2f( colX, 1.8f));
                break;
            case 3:
                list.add(new Vector2f(-colX, topY));
                list.add(new Vector2f( colX, topY));
                list.add(new Vector2f( 0.0f, botY));
                break;
            case 4: // 2x2 rectangular grid
                list.add(new Vector2f(-colX, topY));
                list.add(new Vector2f( colX, topY));
                list.add(new Vector2f(-colX, botY));
                list.add(new Vector2f( colX, botY));
                break;
            case 5:
                list.add(new Vector2f(-colX * 1.55f, topY));
                list.add(new Vector2f( 0.0f,         topY));
                list.add(new Vector2f( colX * 1.55f, topY));
                list.add(new Vector2f(-colX * 0.9f,  botY));
                list.add(new Vector2f( colX * 0.9f,  botY));
                break;
            case 6: // 2x3 grid
                list.add(new Vector2f(-colX * 1.55f, topY));
                list.add(new Vector2f( 0.0f,         topY));
                list.add(new Vector2f( colX * 1.55f, topY));
                list.add(new Vector2f(-colX * 1.55f, botY));
                list.add(new Vector2f( 0.0f,         botY));
                list.add(new Vector2f( colX * 1.55f, botY));
                break;
            default:
                for (int i = 0; i < count; i++) {
                    list.add(new Vector2f(-3f + i * 2f, 1.8f));
                }
                break;
        }
        return list;
    }

    public static ColorRGBA getColorForKey(String key, int fallbackIndex) {
        for (int i = 0; i < COLOR_KEYS.length; i++) {
            if (COLOR_KEYS[i].equalsIgnoreCase(key)) {
                return COLORS[i];
            }
        }
        return COLORS[fallbackIndex % COLORS.length];
    }
}
