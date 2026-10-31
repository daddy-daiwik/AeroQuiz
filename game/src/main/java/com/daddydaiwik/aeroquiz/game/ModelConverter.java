package com.daddydaiwik.aeroquiz.game;

import com.jme3.asset.AssetManager;
import com.jme3.asset.plugins.FileLocator;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Startup model converter — auto-converts non-JME model formats to {@code .glb}
 * using the locally installed Blender, then registers filesystem asset locators
 * so JME can load them without a build step.
 *
 * <hr>
 * <h3>Supported input formats</h3>
 * <table>
 *   <tr><th>Extension</th><th>How handled</th></tr>
 *   <tr><td>{@code .blend}</td><td>Opened by Blender, exported as GLB</td></tr>
 *   <tr><td>{@code .fbx}</td>  <td>Imported into Blender, exported as GLB</td></tr>
 *   <tr><td>{@code .dae}</td>  <td>Collada — imported into Blender, exported as GLB</td></tr>
 *   <tr><td>{@code .obj}</td>  <td>Read directly by JME (no conversion needed)</td></tr>
 *   <tr><td>{@code .gltf/.glb}</td><td>Read directly by JME (no conversion needed)</td></tr>
 *   <tr><td>{@code .j3o}</td>  <td>JME native — read directly (no conversion needed)</td></tr>
 * </table>
 *
 * <h3>Where to drop your files</h3>
 * <pre>
 *   game/src/main/resources/Models/   ← development (Gradle run)
 *   assets/Models/                    ← next to the game JAR (release)
 * </pre>
 *
 * <h3>Requirements for .blend / .fbx / .dae</h3>
 * Blender 3.0+ must be installed.  The converter searches common OS paths
 * automatically; if Blender is in a custom location set the environment
 * variable {@code BLENDER_PATH} to the Blender executable.
 */
public class ModelConverter {

    private static final Logger LOG = Logger.getLogger(ModelConverter.class.getName());

    /** Formats that need Blender to convert → GLB. */
    private static final Set<String> BLENDER_NEEDED =
            new HashSet<>(Arrays.asList(".blend", ".fbx", ".dae"));

    /**
     * All model directories the converter will scan.
     * Written in priority order — dev path first, release path second.
     */
    private static final String[] SCAN_DIRS = {
        "game/src/main/resources/Models",   // Gradle dev layout
        "../game/src/main/resources/Models",
        "assets/Models",                     // next to released JAR
        "../assets/Models",
        "Models",                            // current-working-dir fallback
        "../Models"
    };

    // -------------------------------------------------------------------------
    // Public entry point
    // -------------------------------------------------------------------------

    /**
     * Scans known asset directories, converts any unprocessed
     * {@code .blend / .fbx / .dae} files to {@code .glb} via Blender,
     * then registers all scan directories as JME {@link FileLocator}s so
     * the engine can load the resulting models without a rebuild.
     *
     * <p>Call this <em>before</em> {@link AssetRegistry#probe(AssetManager)}.
     *
     * @param am the application's AssetManager
     */
    public static void convertAll(AssetManager am) {
        // Register filesystem locators for every scan dir (dev + release paths).
        // This lets JME find newly-converted .glb files and any .obj/.gltf files
        // the user dropped in without needing them inside the classpath JAR.
        registerLocators(am);

        // Find Blender executable
        String blender = findBlender();
        if (blender == null) {
            LOG.info("ModelConverter: Blender not found — .blend/.fbx/.dae conversion skipped.\n" +
                     "  ► Install Blender 3.0+ from https://blender.org  OR\n" +
                     "  ► Set env var BLENDER_PATH=/path/to/blender       OR\n" +
                     "  ► Use .obj / .gltf / .glb files directly (no Blender needed).");
            return;
        }
        LOG.info("ModelConverter: using Blender at: " + blender);

        // Scan and convert
        int converted = 0;
        int skipped   = 0;
        for (String dirStr : SCAN_DIRS) {
            Path dir = Paths.get(dirStr);
            if (!Files.isDirectory(dir)) continue;

            try (Stream<Path> files = Files.list(dir)) {
                List<Path> sources = files
                        .filter(p -> BLENDER_NEEDED.contains(ext(p)))
                        .filter(ModelConverter::needsConversion)
                        .sorted()
                        .toList();

                for (Path src : sources) {
                    if (convert(src, blender)) converted++;
                    else                       skipped++;
                }
            } catch (IOException e) {
                LOG.warning("ModelConverter: error scanning " + dir + ": " + e.getMessage());
            }
        }

        if (converted > 0)
            LOG.info("ModelConverter: converted " + converted + " model(s) to GLB.");
        if (skipped > 0)
            LOG.warning("ModelConverter: " + skipped + " model(s) failed to convert — check logs above.");
    }

    // -------------------------------------------------------------------------
    // Locator registration
    // -------------------------------------------------------------------------

    private static void registerLocators(AssetManager am) {
        String[] rootPaths = {
            "game/src/main/resources",
            "../game/src/main/resources",
            "assets",
            "../assets",
            ".",
            ".."
        };
        for (String p : rootPaths) {
            Path path = Paths.get(p).toAbsolutePath().normalize();
            if (Files.isDirectory(path)) {
                try {
                    am.registerLocator(path.toString() + File.separator, FileLocator.class);
                    LOG.info("ModelConverter: registered asset locator root: " + path);
                } catch (Exception e) {
                    LOG.finest("ModelConverter: could not register locator for " + path);
                }
            }
        }

        for (String dirStr : SCAN_DIRS) {
            Path dir = Paths.get(dirStr).toAbsolutePath().normalize();
            // Register the parent (e.g. "game/src/main/resources/") so paths like
            // "Models/plane.glb" resolve correctly through JME's asset system.
            Path parent = dir.getParent();
            if (parent != null && Files.isDirectory(parent)) {
                try {
                    am.registerLocator(parent.toString() + File.separator, FileLocator.class);
                } catch (Exception e) {
                    LOG.finest("ModelConverter: could not register locator for " + parent);
                }
            }
            // Also register the models dir itself (for files at the root of Models/)
            if (Files.isDirectory(dir)) {
                try {
                    am.registerLocator(dir.toString() + File.separator, FileLocator.class);
                } catch (Exception e) {
                    LOG.finest("ModelConverter: could not register locator for " + dir);
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Conversion logic
    // -------------------------------------------------------------------------

    /**
     * @return {@code true} if {@code sourcePath} has no corresponding {@code .glb} sibling.
     */
    private static boolean needsConversion(Path sourcePath) {
        Path glb = siblingWithExt(sourcePath, ".glb");
        return !Files.exists(glb);
    }

    /**
     * Converts {@code source} to a {@code .glb} sibling using Blender's headless mode.
     *
     * @return {@code true} on success
     */
    private static boolean convert(Path source, String blenderExe) {
        String srcExt = ext(source);
        Path   output = siblingWithExt(source, ".glb");
        LOG.info("ModelConverter: converting " + source.getFileName() + " → " + output.getFileName() + " …");

        try {
            // Write appropriate Python script to a temp file
            Path pyScript = writePythonScript(srcExt);

            // Build Blender command
            List<String> cmd = new ArrayList<>();
            cmd.add(blenderExe);

            if (".blend".equals(srcExt)) {
                // Open the .blend file directly as the scene to export
                cmd.add(source.toAbsolutePath().toString());
            }
            // else: start with empty Blender scene (import script handles loading)

            cmd.addAll(Arrays.asList("--background", "--python",
                                     pyScript.toAbsolutePath().toString(), "--"));

            if (!".blend".equals(srcExt)) {
                // Import scripts need source path as first arg
                cmd.add(source.toAbsolutePath().toString());
            }
            cmd.add(output.toAbsolutePath().toString());

            LOG.fine("ModelConverter: command: " + String.join(" ", cmd));

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            pb.directory(source.getParent().toFile());

            Process proc = pb.start();
            // Capture output for logging
            StringBuilder out = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                         new InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) out.append(line).append('\n');
            }
            int exit = proc.waitFor();

            if (exit == 0 && Files.exists(output)) {
                LOG.info("ModelConverter: ✓ " + output.getFileName() + " (" +
                         Files.size(output) / 1024 + " KB)");
                // Clean up temp script
                Files.deleteIfExists(pyScript);
                return true;
            } else {
                LOG.warning("ModelConverter: ✗ Blender exited " + exit +
                            " for " + source.getFileName() + "\n" + out);
                Files.deleteIfExists(pyScript);
                return false;
            }

        } catch (Exception e) {
            LOG.warning("ModelConverter: exception converting " + source.getFileName() +
                        ": " + e.getMessage());
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Python script generation
    // -------------------------------------------------------------------------

    /**
     * Writes the appropriate Blender Python export script to a temp file.
     *
     * @param srcExt source extension, e.g. {@code ".blend"}
     * @return path to the temp {@code .py} file
     */
    private static Path writePythonScript(String srcExt) throws IOException {
        String script = switch (srcExt) {
            case ".blend" -> BLEND_EXPORT_PY;
            case ".fbx"   -> fbxImportExportPy();
            case ".dae"   -> daeImportExportPy();
            default -> throw new IllegalArgumentException("Unsupported: " + srcExt);
        };
        Path tmp = Files.createTempFile("aeroquiz_convert_", ".py");
        Files.writeString(tmp, script);
        return tmp;
    }

    // ─── .blend → .glb ────────────────────────────────────────────────────────
    private static final String BLEND_EXPORT_PY = """
import bpy, sys

args = sys.argv[sys.argv.index("--") + 1:]
output_path = args[0]

# 1. Normalize materials
for mat in bpy.data.materials:
    if not mat.node_tree:
        continue
    nodes = mat.node_tree.nodes
    links = mat.node_tree.links

    outputs = [n for n in nodes if n.type == 'OUTPUT_MATERIAL']
    primary_output = None
    for n in outputs:
        if getattr(n, 'is_active_output', False) or getattr(n, 'target', None) == 'EEVEE':
            primary_output = n
            break
    if not primary_output and outputs:
        primary_output = outputs[0]

    for n in outputs:
        if n != primary_output:
            nodes.remove(n)
        else:
            n.target = 'ALL'

    principled = None
    diffuse = None
    for n in nodes:
        if n.type == 'BSDF_PRINCIPLED':
            principled = n
        elif n.type == 'BSDF_DIFFUSE':
            diffuse = n

    if diffuse and not principled:
        col = diffuse.inputs['Color'].default_value[:]
        principled = nodes.new('ShaderNodeBsdfPrincipled')
        principled.location = diffuse.location
        principled.inputs['Base Color'].default_value = col
        principled.inputs['Roughness'].default_value = 0.5
        principled.inputs['Metallic'].default_value = 0.0
        nodes.remove(diffuse)
        if primary_output:
            links.new(principled.outputs['BSDF'], primary_output.inputs['Surface'])
    elif diffuse and principled:
        nodes.remove(diffuse)
        if primary_output:
            links.new(principled.outputs['BSDF'], primary_output.inputs['Surface'])

# 2. Apply transforms
bpy.ops.object.select_all(action='SELECT')
try:
    bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
except Exception:
    pass

# 3. Export GLB
try:
    bpy.ops.export_scene.gltf(
        filepath=output_path,
        export_format='GLB',
        use_selection=False,
        export_animations=True,
        export_cameras=False,
        export_lights=False,
        export_materials='EXPORT',
        export_normals=True,
        export_tangents=True,
        export_texcoords=True,
    )
except Exception:
    bpy.ops.export_scene.gltf(filepath=output_path, export_format='GLB', use_selection=False)

print("AeroQuiz: exported to " + output_path)
""";

    // ─── .fbx → .glb ──────────────────────────────────────────────────────────
    private static String fbxImportExportPy() {
        return """
import bpy, sys, os

args   = sys.argv[sys.argv.index("--") + 1:]
source = args[0]
output = args[1]

# Clear the default Blender scene
bpy.ops.object.select_all(action='SELECT')
bpy.ops.object.delete()
for block in bpy.data.meshes:   bpy.data.meshes.remove(block)
for block in bpy.data.materials: bpy.data.materials.remove(block)

bpy.ops.import_scene.fbx(filepath=source)

bpy.ops.export_scene.gltf(
    filepath=output,
    export_format='GLB',
    use_selection=False,
    export_animations=True,
)
print("AeroQuiz: " + os.path.basename(source) + " → " + os.path.basename(output))
""";
    }

    // ─── .dae (Collada) → .glb ────────────────────────────────────────────────
    private static String daeImportExportPy() {
        return """
import bpy, sys, os

args   = sys.argv[sys.argv.index("--") + 1:]
source = args[0]
output = args[1]

bpy.ops.object.select_all(action='SELECT')
bpy.ops.object.delete()
for block in bpy.data.meshes:    bpy.data.meshes.remove(block)
for block in bpy.data.materials: bpy.data.materials.remove(block)

bpy.ops.wm.collada_import(filepath=source)

bpy.ops.export_scene.gltf(
    filepath=output,
    export_format='GLB',
    use_selection=False,
    export_animations=True,
)
print("AeroQuiz: " + os.path.basename(source) + " → " + os.path.basename(output))
""";
    }

    // -------------------------------------------------------------------------
    // Blender executable discovery
    // -------------------------------------------------------------------------

    private static String findBlender() {
        // 1. Environment variable override
        String envPath = System.getenv("BLENDER_PATH");
        if (envPath != null && Files.isExecutable(Paths.get(envPath))) return envPath;

        // 2. Common executable names on PATH
        for (String name : new String[]{"blender", "blender3", "blender4"}) {
            String found = findOnPath(name);
            if (found != null) return found;
        }

        // 3. Common fixed install locations
        List<String> candidates = new ArrayList<>();

        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("linux")) {
            candidates.addAll(Arrays.asList(
                "/usr/bin/blender",
                "/usr/local/bin/blender",
                "/snap/bin/blender",
                "/opt/blender/blender",
                "/opt/blender-3/blender",
                "/opt/blender-4/blender",
                System.getProperty("user.home") + "/blender/blender"
            ));
        } else if (os.contains("win")) {
            String pf  = System.getenv("ProgramFiles");
            String pf86 = System.getenv("ProgramFiles(x86)");
            for (String base : new String[]{pf, pf86}) {
                if (base == null) continue;
                // Glob-like: check Blender Foundation/Blender X.Y/blender.exe
                Path bf = Paths.get(base, "Blender Foundation");
                if (Files.isDirectory(bf)) {
                    try (Stream<Path> dirs = Files.list(bf)) {
                        dirs.filter(Files::isDirectory)
                            .map(d -> d.resolve("blender.exe"))
                            .filter(Files::isExecutable)
                            .findFirst()
                            .ifPresent(p -> candidates.add(p.toString()));
                    } catch (IOException ignored) {}
                }
            }
        } else if (os.contains("mac")) {
            candidates.addAll(Arrays.asList(
                "/Applications/Blender.app/Contents/MacOS/Blender",
                "/Applications/Blender.app/Contents/MacOS/blender",
                System.getProperty("user.home") +
                    "/Applications/Blender.app/Contents/MacOS/Blender"
            ));
        }

        for (String c : candidates) {
            if (c != null && Files.isExecutable(Paths.get(c))) return c;
        }

        return null; // not found
    }

    /** Checks if {@code name} is on the system PATH by trying to run {@code name --version}. */
    private static String findOnPath(String name) {
        try {
            Process p = new ProcessBuilder(name, "--version")
                    .redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes());
            p.waitFor();
            if (out.toLowerCase().contains("blender")) return name;
        } catch (Exception ignored) {}
        return null;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String ext(Path p) {
        String name = p.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot).toLowerCase() : "";
    }

    private static Path siblingWithExt(Path p, String newExt) {
        String name = p.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String stem = dot >= 0 ? name.substring(0, dot) : name;
        return p.getParent().resolve(stem + newExt);
    }
}
