"""
AeroQuiz — Blender → GLB exporter
Usage:
  blender input.blend --background --python blend_to_glb.py -- output.glb

What this script does:
  1. Normalizes all materials:
     - Sets Material Output target = 'ALL' (fixes Blender EEVEE/CYCLES target omission)
     - Converts Diffuse BSDF / non-PBR shaders to Principled BSDF with original color
     - Preserves base colors, roughness (0.5), and metallic (0.0)
  2. Applies all object transforms (bakes rotations, scales, locations into vertices)
  3. Exports binary GLB with full PBR materials + normals
"""
import bpy
import sys

# ---------------------------------------------------------------------------
# Parse output path from command-line args after "--"
# ---------------------------------------------------------------------------
argv = sys.argv
try:
    sep = argv.index("--")
    output_path = argv[sep + 1]
except (ValueError, IndexError):
    print("Usage: blender file.blend --background --python blend_to_glb.py -- output.glb")
    sys.exit(1)

print(f"\n=== AeroQuiz Export ===")
print(f"Output : {output_path}")

# ---------------------------------------------------------------------------
# Step 1: Normalize Materials for glTF 2.0 Compatibility
# ---------------------------------------------------------------------------
for mat in bpy.data.materials:
    if not mat.node_tree:
        continue
    nodes = mat.node_tree.nodes
    links = mat.node_tree.links

    # Ensure Material Output target is 'ALL' so glTF exporter doesn't skip it
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

    # Check shader nodes
    principled = None
    diffuse = None
    for n in nodes:
        if n.type == 'BSDF_PRINCIPLED':
            principled = n
        elif n.type == 'BSDF_DIFFUSE':
            diffuse = n

    if diffuse and not principled:
        # Convert Diffuse BSDF -> Principled BSDF
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
        # Remove secondary diffuse if principled exists
        nodes.remove(diffuse)
        if primary_output:
            links.new(principled.outputs['BSDF'], primary_output.inputs['Surface'])

print("Materials normalized ✓")

# ---------------------------------------------------------------------------
# Step 2: Apply all object transforms (location, rotation, scale)
# ---------------------------------------------------------------------------
bpy.ops.object.select_all(action='SELECT')
try:
    bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
    print("Transforms applied ✓")
except Exception as e:
    print(f"Warning: could not apply transforms: {e}")

# ---------------------------------------------------------------------------
# Step 3: Export as GLB
# ---------------------------------------------------------------------------
export_ok = False

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
    export_ok = True
    print("Export: full parameter set ✓")
except Exception as e:
    print(f"Export attempt 1 failed: {e}")

if not export_ok:
    try:
        bpy.ops.export_scene.gltf(
            filepath=output_path,
            export_format='GLB',
            use_selection=False,
        )
        export_ok = True
        print("Export: fallback parameter set ✓")
    except Exception as e2:
        print(f"Export attempt 2 failed: {e2}")
        sys.exit(1)

print(f"=== Exported to: {output_path} ===\n")
