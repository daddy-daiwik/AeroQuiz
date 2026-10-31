package com.daddydaiwik.aeroquiz.game;

import com.jme3.app.Application;
import com.jme3.app.state.AbstractAppState;
import com.jme3.app.state.AppStateManager;
import com.jme3.bounding.BoundingBox;
import com.jme3.bounding.BoundingVolume;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.control.BillboardControl;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Quad;
import com.jme3.texture.Texture;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;

/**
 * AppState that manages the player's plane Node, follow-camera, lane-shifting in 2D,
 * and projectile ballistics (including ricochet bounce-back on wrong answers).
 */
public class PlaneController extends AbstractAppState {

    private static final Logger LOG = Logger.getLogger(PlaneController.class.getName());

    private static final float PLANE_SPEED         = 8f;
    private static final Vector3f CAM_OFFSET       = new Vector3f(0f, 4.6f, 16.5f);
    private static final float SHIFT_DURATION      = 0.20f;
    private static final float PROJECTILE_SPEED    = 100f;
    private static final float FIRE_PULSE_SPEED    = 6f;
    private static final float DEATH_SPIN_SPEED    = FastMath.DEG_TO_RAD * 180f;
    private static final float DEATH_DESCENT_SPEED = 8f;

    private static final int   SHARD_COUNT         = 14;
    private static final float SHARD_LIFETIME      = 0.65f;
    private static final float SHARD_SPEED         = 14f;

    /** Increased target wingspan for a larger, more prominent plane model. */
    private static final float TARGET_WINGSPAN     = 6.5f;

    /** Upward tilt angle (pitch up toward sky options). */
    private static final float DEFAULT_TILT_RAD    = -8f * FastMath.DEG_TO_RAD;

    private static final ColorRGBA[] DAMAGE_COLORS = {
        new ColorRGBA(0.78f, 0.78f, 0.78f, 1f),
        new ColorRGBA(0.55f, 0.55f, 0.55f, 1f),
        new ColorRGBA(0.38f, 0.38f, 0.38f, 1f),
        new ColorRGBA(0.22f, 0.22f, 0.22f, 1f),
        new ColorRGBA(0.12f, 0.10f, 0.08f, 1f),
        new ColorRGBA(0.05f, 0.04f, 0.03f, 1f)
    };

    // -------------------------------------------------------------------------
    // Projectile & Explosion types
    // -------------------------------------------------------------------------

    private static final class Projectile {
        Geometry geo;
        Geometry targetObs;
        boolean  destroyOnHit;
        ColorRGBA obsColor;

        boolean isRicocheting = false;
        Vector3f velocity = new Vector3f();
    }

    private static final class Explosion {
        final List<Geometry> shards = new ArrayList<>();
        final float[] vx, vy, vz;
        float timer = 0f;

        Explosion(int count) {
            vx = new float[count];
            vy = new float[count];
            vz = new float[count];
        }
    }

    private static final class SmokePuff {
        Spatial geo;
        Vector3f velocity;
        float age = 0f;
        float maxLifetime = 1.0f;
        float initialScale = 0.3f;
        float targetScale = 1.4f;
    }

    // -------------------------------------------------------------------------
    // State — plane & scene
    // -------------------------------------------------------------------------

    private AeroQuiz app;

    public PlaneController() {}

    public PlaneController(AeroQuiz app) {
        this.app = app;
    }

    private Node     planeNode;
    private Node     propellerPivot;

    private final List<Geometry> bodyParts = new ArrayList<>();
    private int damageLevel = 0;

    // Shift state (2D X and Y)
    private boolean shifting     = false;
    private float   shiftTimer   = 0f;
    private float   shiftStartX  = 0f;
    private float   shiftTargetX = 0f;
    private float   shiftStartY  = -0.2f;
    private float   shiftTargetY = -0.2f;

    // Pending shot state
    private boolean   hasPendingShot  = false;
    private Geometry  pendingTarget;
    private boolean   pendingDestroy;
    private ColorRGBA pendingObsColor = ColorRGBA.Orange;

    // Projectiles & FX
    private final List<Projectile> projectiles = new ArrayList<>();
    private final List<Explosion>  explosions  = new ArrayList<>();
    private final List<SmokePuff>  smokePuffs  = new ArrayList<>();
    private float                  smokeSpawnTimer = 0f;
    private float                  steamHissTimer  = 0f;
    private final Random           rng         = new Random();

    // Death state
    private Node    fireNode;
    private float   firePhase   = 0f;
    private boolean dying       = false;
    private float   deathTimer  = 0f;

    // Camera shake
    private float shakeTrauma = 0f;
    private static final float SHAKE_DECAY      = 1.6f;
    private static final float MAX_SHAKE_OFFSET = 1.4f;

    @Override
    public void initialize(AppStateManager stateManager, Application application) {
        super.initialize(stateManager, application);
        this.app = (AeroQuiz) application;
        buildPlane();
    }

    @Override
    public void update(float tpf) {
        if (!isEnabled()) return;

        GameState gs = app.getStateManager().getState(GameState.class);
        boolean gameOver  = gs != null && gs.isGameOver();
        boolean nameInput = gs != null && gs.getPhase() == GameState.Phase.NAME_INPUT;

        if (dying) {
            updateDeath(tpf);
        } else if (!gameOver && !nameInput) {
            planeNode.move(0f, 0f, -PLANE_SPEED * tpf);
        }

        // Continuously rotate propeller around its forward axis
        if (propellerPivot != null) {
            float spinSpeed = dying ? 8f : 45f;
            propellerPivot.rotate(0f, 0f, spinSpeed * tpf);
        }

        if (shifting)               updateShift(tpf);
        if (!projectiles.isEmpty()) updateProjectiles(tpf);
        if (!explosions.isEmpty())  updateExplosions(tpf);
        if (fireNode != null)       updateFire(tpf);
        updateSmoke(tpf);

        updateCamera(tpf);
    }

    @Override
    public void cleanup() {
        if (planeNode != null) {
            planeNode.removeFromParent();
        }
        for (Projectile pd : projectiles) pd.geo.removeFromParent();
        projectiles.clear();
        for (Explosion ex : explosions) {
            ex.shards.forEach(Spatial::removeFromParent);
        }
        explosions.clear();
        for (SmokePuff puff : smokePuffs) {
            if (puff.geo != null) puff.geo.removeFromParent();
        }
        smokePuffs.clear();
        super.cleanup();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public Vector3f getPlanePosition() {
        return planeNode.getWorldTranslation().clone();
    }

    public float getPlaneSpeed() {
        return PLANE_SPEED;
    }

    /**
     * Shifts plane toward the target option (X, Y) and fires a projectile.
     */
    public void shiftThenFire(float targetX, float targetY, Geometry targetObs, boolean destroyOnHit) {
        shiftStartX  = planeNode.getLocalTranslation().x;
        shiftTargetX = targetX;
        shiftStartY  = planeNode.getLocalTranslation().y;
        shiftTargetY = Math.max(0.1f, targetY - 0.4f); // aim slightly below box
        shiftTimer   = 0f;
        shifting     = true;

        hasPendingShot = true;
        pendingTarget  = targetObs;
        pendingDestroy = destroyOnHit;

        if (targetObs != null && targetObs.getMaterial() != null) {
            ColorRGBA c = null;
            if (targetObs.getMaterial().getParam("Color") != null) {
                c = (ColorRGBA) targetObs.getMaterial().getParam("Color").getValue();
            }
            pendingObsColor = c != null ? c.clone() : ColorRGBA.Orange;
        } else {
            pendingObsColor = ColorRGBA.Orange;
        }
    }

    public void shiftThenFire(float targetX, Geometry targetObs, boolean destroyOnHit) {
        shiftThenFire(targetX, 1.8f, targetObs, destroyOnHit);
    }

    /**
     * Darkens plane body material by one level.
     */
    public void damageBody() {
        damageLevel = Math.min(damageLevel + 1, DAMAGE_COLORS.length - 1);
        ColorRGBA col = DAMAGE_COLORS[damageLevel];
        for (Geometry g : bodyParts) {
            if (g.getMaterial() != null) {
                if (g.getMaterial().getParam("BaseColor") != null) {
                    ColorRGBA cur = (ColorRGBA) g.getMaterial().getParam("BaseColor").getValue();
                    if (cur != null) {
                        g.getMaterial().setColor("BaseColor", cur.mult(0.72f));
                    }
                } else if (g.getMaterial().getParam("Color") != null) {
                    g.getMaterial().setColor("Color", col);
                }
            }
        }
        addCameraShake(0.65f);
        steamHissTimer = 0f;
        AudioFX audio = (app != null) ? app.getAudioFX() : null;
        if (audio != null) {
            audio.playSteamHiss();
        }
    }

    public void addCameraShake(float amount) {
        shakeTrauma = Math.min(1.0f, shakeTrauma + amount);
    }

    /**
     * Resets the plane and all associated FX, projectiles, and camera trauma to
     * a clean starting state ready for takeoff.
     */
    public void resetPlane() {
        dying       = false;
        deathTimer  = 0f;
        shakeTrauma = 0f;

        shifting     = false;
        shiftTimer   = 0f;
        shiftStartX  = 0f;
        shiftTargetX = 0f;
        shiftStartY  = -0.2f;
        shiftTargetY = -0.2f;

        hasPendingShot  = false;
        pendingTarget   = null;
        pendingDestroy  = false;

        damageLevel     = 0;
        smokeSpawnTimer = 0f;
        steamHissTimer  = 0f;
        firePhase       = 0f;

        // Clean up active projectiles
        for (Projectile p : projectiles) {
            if (p.geo != null) p.geo.removeFromParent();
        }
        projectiles.clear();

        // Clean up active explosions
        for (Explosion ex : explosions) {
            if (ex.shards != null) {
                ex.shards.forEach(Spatial::removeFromParent);
            }
        }
        explosions.clear();

        // Clean up active smoke puffs
        for (SmokePuff puff : smokePuffs) {
            if (puff.geo != null) {
                puff.geo.removeFromParent();
            }
        }
        smokePuffs.clear();

        // Clean up fire node
        if (fireNode != null) {
            fireNode.removeFromParent();
            fireNode = null;
        }

        // Clean up and rebuild plane node
        if (planeNode != null) {
            planeNode.removeFromParent();
            bodyParts.clear();
            propellerPivot = null;
        }
        buildPlane();

        // Snap camera back to initial takeoff position
        updateCamera(0.01f);
        LOG.info("PlaneController: resetPlane() completed — plane restored to pristine state at (0, 0.2, 0).");
    }

    public void triggerDeath() {
        if (dying) return;
        dying      = true;
        deathTimer = 0f;
        addCameraShake(1.0f);
        attachFireNode();

        AudioFX audio = app.getAudioFX();
        if (audio != null) {
            audio.playExplosion();
            audio.playSteamHiss();
        }

        // Violent multi-stage explosive blast centered at the plane
        Vector3f planePos = planeNode.getWorldTranslation();
        for (int i = 0; i < 4; i++) {
            Vector3f blastOffset = new Vector3f(
                (rng.nextFloat() - 0.5f) * 1.8f,
                (rng.nextFloat() - 0.5f) * 1.2f,
                (rng.nextFloat() - 0.5f) * 2.4f
            );
            spawnExplosion(planePos.add(blastOffset), new ColorRGBA(1.0f, 0.35f + i * 0.15f, 0.05f, 1f));
        }
    }

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    private void buildPlane() {
        planeNode = new Node("Plane");
        // Start below options grid
        planeNode.setLocalTranslation(0f, -0.2f, 0f);

        // Apply default upward tilt
        Quaternion tilt = new Quaternion().fromAngleAxis(DEFAULT_TILT_RAD, Vector3f.UNIT_X);
        planeNode.setLocalRotation(tilt);

        AssetRegistry reg = app.getAssetRegistry();
        Spatial planeBody = null;
        Spatial propModel = null;

        // Check if separate plane-without-propeller and propeller models are available
        if (reg != null && reg.has(AssetRegistry.PLANE_NOPROP)) {
            try {
                planeBody = app.getAssetManager().loadModel(reg.path(AssetRegistry.PLANE_NOPROP));
                LOG.info("PlaneController: loaded plane-without-propeller model.");
            } catch (Exception e) {
                LOG.warning("PlaneController: failed loading plane-without-propeller: " + e.getMessage());
            }
        }

        if (reg != null && reg.has(AssetRegistry.PROPELLER_MODEL)) {
            try {
                propModel = app.getAssetManager().loadModel(reg.path(AssetRegistry.PROPELLER_MODEL));
                LOG.info("PlaneController: loaded propeller model.");
            } catch (Exception e) {
                LOG.warning("PlaneController: failed loading propeller: " + e.getMessage());
            }
        }

        // Fallback to unified plane model if plane-without-propeller wasn't found
        if (planeBody == null && reg != null && reg.has(AssetRegistry.PLANE_MODEL)) {
            try {
                planeBody = app.getAssetManager().loadModel(reg.path(AssetRegistry.PLANE_MODEL));
                LOG.info("PlaneController: fallback to unified plane model.");
            } catch (Exception e) {
                LOG.warning("PlaneController: failed loading unified plane: " + e.getMessage());
            }
        }

        if (planeBody != null) {
            try {
                Node modelRoot = new Node("PlaneModelRoot");
                modelRoot.attachChild(planeBody);

                // If propeller model is loaded, assemble it at the plane's nose shaft stub
                if (propModel != null) {
                    propellerPivot = new Node("PropellerPivot");
                    // Center the propeller hub (hub was at Y=0.9 in GLTF space)
                    Node propCentering = new Node("PropCentering");
                    propModel.setLocalTranslation(0f, -0.9f, 0f);
                    propCentering.attachChild(propModel);
                    propCentering.setLocalScale(1.85f); // Enlarged prominent propeller
                    propellerPivot.attachChild(propCentering);

                    // Position at nose shaft stub: X=0, Y=1.6, Z=3.88
                    propellerPivot.setLocalTranslation(0f, 1.6f, 3.88f);
                    modelRoot.attachChild(propellerPivot);
                    LOG.info("PlaneController: attached enlarged spinning propeller (scale 1.85) to plane nose.");
                }

                // 180 deg Y flip so nose points forward in -Z of the game world
                Quaternion fixYaw = new Quaternion().fromAngleAxis(FastMath.PI, Vector3f.UNIT_Y);
                modelRoot.setLocalRotation(fixYaw);

                planeNode.attachChild(modelRoot);
                planeNode.updateGeometricState();

                BoundingVolume bv = planeNode.getWorldBound();
                if (bv instanceof BoundingBox) {
                    BoundingBox bb = (BoundingBox) bv;
                    float halfSpan = Math.max(bb.getXExtent(), bb.getZExtent());
                    float currentSpan = halfSpan * 2f;
                    if (currentSpan > 0.001f) {
                        float scale = TARGET_WINGSPAN / currentSpan;
                        modelRoot.setLocalScale(scale);
                        LOG.info(String.format("PlaneController: model span %.2f -> scaled x%.3f -> %.2f (Enlarged)",
                                currentSpan, scale, TARGET_WINGSPAN));
                    }
                }

                modelRoot.depthFirstTraversal(sp -> {
                    if (sp instanceof Geometry) {
                        bodyParts.add((Geometry) sp);
                    }
                });

                app.getRootNode().attachChild(planeNode);
                LOG.info("PlaneController: assembled 3D plane with spinning propeller.");
                return;
            } catch (Exception e) {
                LOG.warning("PlaneController: model load failed, using primitives: " + e.getMessage());
                planeNode.detachAllChildren();
            }
        }

        // Primitive fallback (scaled up)
        ColorRGBA col = DAMAGE_COLORS[0];
        addBodyPart("Fuselage",  0.6f,  0.45f, 2.2f,  0f,    0f,    0f,    col);
        addBodyPart("LeftWing",  2.4f,  0.15f, 0.8f, -2.4f,  0f,    0f,    col);
        addBodyPart("RightWing", 2.4f,  0.15f, 0.8f,  2.4f,  0f,    0f,    col);
        addBodyPart("TailFin",   0.15f, 0.75f, 0.5f,  0f,    0.6f, -1.8f,  col);

        app.getRootNode().attachChild(planeNode);
    }

    private void addBodyPart(String name, float xE, float yE, float zE,
                             float lx, float ly, float lz, ColorRGBA color) {
        Geometry geo = new Geometry(name, new Box(xE, yE, zE));
        geo.setLocalTranslation(lx, ly, lz);
        Material mat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", color);
        geo.setMaterial(mat);
        planeNode.attachChild(geo);
        bodyParts.add(geo);
    }

    private void attachFireNode() {
        fireNode = new Node("FireNode");
        float[][] offsets = {{0f, 0.7f, 0f}, {0.3f, 0.5f, 0.2f}, {-0.3f, 0.5f, -0.2f}};
        ColorRGBA[] cols  = {
            new ColorRGBA(1f, 0.5f, 0f, 1f),
            new ColorRGBA(1f, 0.2f, 0f, 1f),
            new ColorRGBA(1f, 0.8f, 0f, 1f)
        };
        for (int i = 0; i < 3; i++) {
            Geometry g = new Geometry("Fire_" + i, new Box(0.45f, 0.45f, 0.45f));
            g.setLocalTranslation(offsets[i][0], offsets[i][1], offsets[i][2]);
            Material mat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
            mat.setColor("Color", cols[i]);
            g.setMaterial(mat);
            fireNode.attachChild(g);
        }
        planeNode.attachChild(fireNode);
    }

    // -------------------------------------------------------------------------
    // Updates
    // -------------------------------------------------------------------------

    private void updateShift(float tpf) {
        shiftTimer += tpf;
        float progress = Math.min(shiftTimer / SHIFT_DURATION, 1f);
        float t = progress * progress * (3f - 2f * progress); // smoothstep

        float curX = FastMath.interpolateLinear(t, shiftStartX, shiftTargetX);
        float curY = FastMath.interpolateLinear(t, shiftStartY, shiftTargetY);
        Vector3f cur = planeNode.getLocalTranslation();
        planeNode.setLocalTranslation(curX, curY, cur.z);

        // Bank roll while shifting laterally
        float bankAngle = -FastMath.clamp((shiftTargetX - shiftStartX) * 0.1f, -0.35f, 0.35f) * (1f - progress);
        Quaternion bank = new Quaternion().fromAngleAxis(bankAngle, Vector3f.UNIT_Z);
        Quaternion tilt = new Quaternion().fromAngleAxis(DEFAULT_TILT_RAD, Vector3f.UNIT_X);
        planeNode.setLocalRotation(tilt.mult(bank));

        if (shiftTimer >= SHIFT_DURATION) {
            shifting = false;
            planeNode.setLocalTranslation(shiftTargetX, shiftTargetY, cur.z);
            planeNode.setLocalRotation(tilt);
            if (hasPendingShot) {
                hasPendingShot = false;
                doFireProjectile(pendingTarget, pendingDestroy, pendingObsColor);
            }
        }
    }

    private void doFireProjectile(Geometry targetObs, boolean destroyOnHit, ColorRGBA obsColor) {
        Geometry proj = new Geometry("Projectile_" + System.nanoTime(), new Box(0.1f, 0.1f, 1.8f));
        Material mat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", new ColorRGBA(1f, 0.95f, 0.35f, 1f));
        proj.setMaterial(mat);

        Vector3f nose = planeNode.getWorldTranslation().add(0f, 0.2f, -2.6f);
        proj.setLocalTranslation(nose);
        app.getRootNode().attachChild(proj);

        Projectile pd = new Projectile();
        pd.geo          = proj;
        pd.targetObs    = targetObs;
        pd.destroyOnHit = destroyOnHit;
        pd.obsColor     = obsColor;
        pd.velocity.set(0f, 0f, -PROJECTILE_SPEED);
        pd.isRicocheting = false;
        projectiles.add(pd);
    }

    private void updateProjectiles(float tpf) {
        ObstacleSpawner spawner = app.getStateManager().getState(ObstacleSpawner.class);
        GameState gameState     = app.getStateManager().getState(GameState.class);
        HudState hudState       = app.getStateManager().getState(HudState.class);
        AudioFX audio           = app.getAudioFX();

        Iterator<Projectile> it = projectiles.iterator();
        while (it.hasNext()) {
            Projectile pd = it.next();
            pd.geo.move(pd.velocity.mult(tpf));

            float projZ  = pd.geo.getWorldTranslation().z;
            Vector3f planePos = planeNode.getWorldTranslation();

            if (!pd.isRicocheting) {
                // Moving forward toward obstacle
                float obsZ = (pd.targetObs != null && pd.targetObs.getParent() != null)
                        ? pd.targetObs.getWorldTranslation().z
                        : (planePos.z - 45f);

                if (projZ <= obsZ + 1.2f) {
                    Vector3f impactPos = (pd.targetObs != null && pd.targetObs.getParent() != null)
                            ? pd.targetObs.getWorldTranslation().clone()
                            : new Vector3f(planePos.x, planePos.y + 2f, obsZ);

                    if (pd.destroyOnHit) {
                        // Correct answer: obstacle destroyed!
                        if (spawner != null && pd.targetObs != null) {
                            spawner.removeObstacleGeometry(pd.targetObs);
                        }
                        spawnExplosion(impactPos, pd.obsColor);
                        if (audio != null) audio.playExplosionCorrect();
                        addCameraShake(0.4f);

                        pd.geo.removeFromParent();
                        it.remove();

                        if (gameState != null) {
                            gameState.onCorrectHit();
                        }
                        continue;
                    } else {
                        // WRONG answer: bullet BOUNCES OFF obstacle and heads back to plane!
                        spawnExplosion(impactPos, new ColorRGBA(1.0f, 0.85f, 0.2f, 1f)); // metal sparks
                        addCameraShake(0.25f);
                        if (audio != null) audio.playExplosionIncorrect();

                        // Reverse velocity back toward current plane position
                        Vector3f dirBack = planePos.subtract(impactPos).normalizeLocal();
                        pd.velocity.set(dirBack.mult(135f));
                        pd.isRicocheting = true;

                        // Glowing red-hot ricochet projectile
                        pd.geo.getMaterial().setColor("Color", new ColorRGBA(1.0f, 0.25f, 0.1f, 1f));
                    }
                }
            } else {
                // Ricocheting bullet heading back to hit the plane!
                float dist = pd.geo.getWorldTranslation().distance(planePos);
                if (dist < 3.2f || projZ >= planePos.z - 0.2f) {
                    // DIRECT HIT ON THE PLANE!
                    spawnExplosion(planePos, ColorRGBA.Red);
                    addCameraShake(0.85f);
                    if (hudState != null) hudState.triggerRedFlash();
                    damageBody();
                    if (audio != null) audio.playExplosionIncorrect();

                    if (gameState != null) {
                        gameState.registerWrongHit();
                    }

                    pd.geo.removeFromParent();
                    it.remove();
                    continue;
                }
            }

            // Cleanup if projectile flies away
            if (projZ < planePos.z - 160f || projZ > planePos.z + 10f) {
                if (pd.isRicocheting) {
                    // Bullet passed plane — trigger guaranteed hit
                    spawnExplosion(planePos, ColorRGBA.Red);
                    addCameraShake(0.85f);
                    if (hudState != null) hudState.triggerRedFlash();
                    damageBody();
                    if (audio != null) audio.playExplosionIncorrect();
                    if (gameState != null) {
                        gameState.registerWrongHit();
                    }
                }
                pd.geo.removeFromParent();
                it.remove();
            }
        }
    }

    private void spawnExplosion(Vector3f pos, ColorRGBA color) {
        Explosion ex = new Explosion(SHARD_COUNT);
        Material mat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", color != null ? color : ColorRGBA.Orange);

        for (int i = 0; i < SHARD_COUNT; i++) {
            Geometry shard = new Geometry("Shard_" + i, new Box(0.18f, 0.18f, 0.18f));
            shard.setMaterial(mat);
            shard.setLocalTranslation(pos);
            app.getRootNode().attachChild(shard);
            ex.shards.add(shard);

            float theta = rng.nextFloat() * FastMath.TWO_PI;
            float phi   = (rng.nextFloat() - 0.5f) * FastMath.PI;
            float spd   = SHARD_SPEED * (0.6f + 0.8f * rng.nextFloat());
            ex.vx[i] = spd * FastMath.cos(phi) * FastMath.cos(theta);
            ex.vy[i] = spd * FastMath.sin(phi) + 2f;
            ex.vz[i] = spd * FastMath.cos(phi) * FastMath.sin(theta);
        }
        explosions.add(ex);
    }

    private void updateExplosions(float tpf) {
        Iterator<Explosion> it = explosions.iterator();
        while (it.hasNext()) {
            Explosion ex = it.next();
            ex.timer += tpf;
            float life = ex.timer / SHARD_LIFETIME;
            for (int i = 0; i < ex.shards.size(); i++) {
                ex.vy[i] -= 18f * tpf;
                ex.vz[i] *= 0.92f;
                ex.shards.get(i).move(ex.vx[i] * tpf, ex.vy[i] * tpf, ex.vz[i] * tpf);
                float scale = Math.max(0f, 1f - life);
                ex.shards.get(i).setLocalScale(scale);
            }
            if (ex.timer >= SHARD_LIFETIME) {
                ex.shards.forEach(Spatial::removeFromParent);
                it.remove();
            }
        }
    }

    private void updateDeath(float tpf) {
        deathTimer += tpf;
        float spinAngle = DEATH_SPIN_SPEED * deathTimer * 1.5f;
        Quaternion spinX = new Quaternion().fromAngleAxis(spinAngle, Vector3f.UNIT_X);
        Quaternion spinZ = new Quaternion().fromAngleAxis(spinAngle * 0.7f, Vector3f.UNIT_Z);
        planeNode.setLocalRotation(spinX.mult(spinZ));
        planeNode.move(0f, -DEATH_DESCENT_SPEED * tpf, -PLANE_SPEED * 0.4f * tpf);

        // Continuous trail of fiery blasts during death spiral
        if (deathTimer < 3.0f && rng.nextFloat() < 0.35f) {
            Vector3f p = planeNode.getWorldTranslation();
            spawnExplosion(p.add((rng.nextFloat() - 0.5f) * 1.6f,
                                 (rng.nextFloat() - 0.5f) * 1.2f,
                                 (rng.nextFloat() - 0.5f) * 1.6f),
                    rng.nextBoolean() ? ColorRGBA.Orange : ColorRGBA.Red);
        }
    }

    private void updateSmoke(float tpf) {
        GameState gs = (app != null && app.getStateManager() != null)
                ? app.getStateManager().getState(GameState.class) : null;
        int wrongHits = (gs != null) ? gs.getWrongHits() : damageLevel;
        if (damageLevel > wrongHits) wrongHits = damageLevel;

        // If plane is damaged or dying, spawn progressive smoke puffs
        if (wrongHits > 0 || dying) {
            smokeSpawnTimer += tpf;
            float spawnInterval;
            if (dying) {
                spawnInterval = 0.025f;
            } else if (wrongHits == 1) {
                spawnInterval = 0.14f;
            } else if (wrongHits == 2) {
                spawnInterval = 0.07f;
            } else {
                spawnInterval = 0.035f;
            }

            if (smokeSpawnTimer >= spawnInterval) {
                smokeSpawnTimer = 0f;
                spawnSmokePuff(wrongHits);
                if (dying) {
                    spawnFireFlamePuff();
                }
            }

            // Periodic steam hissing sound as steam/smoke gas vents
            steamHissTimer += tpf;
            float hissInterval = dying ? 0.85f : (wrongHits == 1 ? 2.8f : (wrongHits == 2 ? 1.8f : 1.1f));
            if (steamHissTimer >= hissInterval) {
                steamHissTimer = 0f;
                AudioFX audio = (app != null) ? app.getAudioFX() : null;
                if (audio != null) {
                    audio.playSteamHiss();
                }
            }
        }

        // Update active smoke puffs
        Iterator<SmokePuff> it = smokePuffs.iterator();
        while (it.hasNext()) {
            SmokePuff puff = it.next();
            puff.age += tpf;
            puff.geo.move(puff.velocity.mult(tpf));
            float progress = puff.age / puff.maxLifetime;
            float currentScale = FastMath.interpolateLinear(progress, puff.initialScale, puff.targetScale);
            puff.geo.setLocalScale(currentScale);

            if (puff.age >= puff.maxLifetime) {
                puff.geo.removeFromParent();
                it.remove();
            }
        }
    }

    private void spawnSmokePuff(int wrongHits) {
        Vector3f planePos = planeNode.getWorldTranslation();
        Vector3f spawnPos = planePos.add(
                (rng.nextFloat() - 0.5f) * 0.5f,
                0.2f + (rng.nextFloat() - 0.5f) * 0.3f,
                1.4f + rng.nextFloat() * 0.5f
        );

        ColorRGBA smokeColor;
        float initScale;
        float targetScale;
        float lifetime;

        if (dying) {
            // Billowing dense pitch black soot smoke
            smokeColor  = new ColorRGBA(0.08f, 0.08f, 0.08f, 0.95f);
            initScale   = 0.5f;
            targetScale = 2.4f;
            lifetime    = 1.1f;
        } else if (wrongHits == 1) {
            // Light grey wispy smoke
            smokeColor  = new ColorRGBA(0.82f, 0.82f, 0.82f, 0.70f);
            initScale   = 0.25f;
            targetScale = 1.1f;
            lifetime    = 0.85f;
        } else if (wrongHits == 2) {
            // Medium dark charcoal grey smoke
            smokeColor  = new ColorRGBA(0.38f, 0.38f, 0.40f, 0.85f);
            initScale   = 0.35f;
            targetScale = 1.6f;
            lifetime    = 0.95f;
        } else {
            // Thick dark smoke with dense black plumes
            smokeColor  = new ColorRGBA(0.12f, 0.12f, 0.12f, 0.95f);
            initScale   = 0.45f;
            targetScale = 2.0f;
            lifetime    = 1.05f;
        }

        Spatial geo;
        AssetRegistry reg = app.getAssetRegistry();
        if (reg != null && reg.has(AssetRegistry.TEX_SMOKE)) {
            float s = 0.8f;
            Quad q = new Quad(s, s);
            Geometry g = new Geometry("SmokePuff_" + System.nanoTime(), q);
            g.setLocalTranslation(-s / 2f, -s / 2f, 0f);
            Material mat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
            Texture smokeTex = app.getAssetManager().loadTexture(reg.path(AssetRegistry.TEX_SMOKE));
            mat.setTexture("ColorMap", smokeTex);
            mat.setColor("Color", smokeColor);
            mat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
            mat.getAdditionalRenderState().setDepthWrite(false);
            g.setMaterial(mat);
            g.setQueueBucket(RenderQueue.Bucket.Transparent);

            Node bb = new Node("SmokeBillboard");
            bb.attachChild(g);
            bb.addControl(new BillboardControl());
            bb.setLocalTranslation(spawnPos);
            app.getRootNode().attachChild(bb);
            geo = bb;
        } else {
            Geometry g = new Geometry("SmokePuff_" + System.nanoTime(), new Box(0.18f, 0.18f, 0.18f));
            Material mat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
            mat.setColor("Color", smokeColor);
            g.setMaterial(mat);
            g.setLocalTranslation(spawnPos);
            app.getRootNode().attachChild(g);
            geo = g;
        }

        SmokePuff puff = new SmokePuff();
        puff.geo = geo;
        puff.initialScale = initScale;
        puff.targetScale  = targetScale;
        puff.maxLifetime  = lifetime;
        puff.velocity     = new Vector3f(
                (rng.nextFloat() - 0.5f) * 1.2f,
                0.8f + rng.nextFloat() * 1.5f,
                4f + rng.nextFloat() * 3f
        );
        smokePuffs.add(puff);
    }

    private void spawnFireFlamePuff() {
        Vector3f planePos = planeNode.getWorldTranslation();
        Vector3f spawnPos = planePos.add(
                (rng.nextFloat() - 0.5f) * 0.8f,
                (rng.nextFloat() - 0.5f) * 0.6f,
                (rng.nextFloat() - 0.5f) * 1.2f
        );
        ColorRGBA fireColor = (rng.nextFloat() > 0.4f)
                ? new ColorRGBA(1.0f, 0.45f, 0.05f, 1f)
                : new ColorRGBA(1.0f, 0.85f, 0.1f, 1f);

        Spatial geo;
        AssetRegistry reg = app.getAssetRegistry();
        if (reg != null && reg.has(AssetRegistry.TEX_FLAME)) {
            float s = 0.9f;
            Quad q = new Quad(s, s);
            Geometry g = new Geometry("FlamePuff_" + System.nanoTime(), q);
            g.setLocalTranslation(-s / 2f, -s / 2f, 0f);
            Material mat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
            Texture flameTex = app.getAssetManager().loadTexture(reg.path(AssetRegistry.TEX_FLAME));
            mat.setTexture("ColorMap", flameTex);
            mat.setColor("Color", fireColor);
            mat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
            mat.getAdditionalRenderState().setDepthWrite(false);
            g.setMaterial(mat);
            g.setQueueBucket(RenderQueue.Bucket.Transparent);

            Node bb = new Node("FlameBillboard");
            bb.attachChild(g);
            bb.addControl(new BillboardControl());
            bb.setLocalTranslation(spawnPos);
            app.getRootNode().attachChild(bb);
            geo = bb;
        } else {
            Geometry g = new Geometry("FlamePuff_" + System.nanoTime(), new Box(0.25f, 0.25f, 0.25f));
            Material mat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
            mat.setColor("Color", fireColor);
            g.setMaterial(mat);
            g.setLocalTranslation(spawnPos);
            app.getRootNode().attachChild(g);
            geo = g;
        }

        SmokePuff puff = new SmokePuff();
        puff.geo = geo;
        puff.initialScale = 0.35f;
        puff.targetScale  = 1.3f;
        puff.maxLifetime  = 0.45f;
        puff.velocity     = new Vector3f(
                (rng.nextFloat() - 0.5f) * 2.5f,
                1.5f + rng.nextFloat() * 2f,
                3f + rng.nextFloat() * 4f
        );
        smokePuffs.add(puff);
    }

    private void updateFire(float tpf) {
        firePhase += tpf * FIRE_PULSE_SPEED;
        fireNode.setLocalScale(1f + 0.5f * FastMath.sin(firePhase));
    }

    private void updateCamera(float tpf) {
        if (shakeTrauma > 0f) {
            shakeTrauma = Math.max(0f, shakeTrauma - SHAKE_DECAY * tpf);
        }
        float shakeAmount = shakeTrauma * shakeTrauma;

        Vector3f planePos = planeNode.getWorldTranslation();
        Vector3f camPos   = planePos.add(CAM_OFFSET);

        if (shakeAmount > 0.001f) {
            float rx = (rng.nextFloat() * 2f - 1f) * MAX_SHAKE_OFFSET * shakeAmount;
            float ry = (rng.nextFloat() * 2f - 1f) * MAX_SHAKE_OFFSET * shakeAmount;
            float rz = (rng.nextFloat() * 2f - 1f) * (MAX_SHAKE_OFFSET * 0.4f) * shakeAmount;
            camPos.addLocal(rx, ry, rz);
        }

        app.getCamera().setLocation(camPos);
        app.getCamera().lookAt(planePos.add(0f, 0.9f, -5f), Vector3f.UNIT_Y);
    }
}
