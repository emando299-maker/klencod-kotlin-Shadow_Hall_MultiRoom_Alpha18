# Shadow Hall - Alpha 8

Android Kotlin/OpenGL ES 2.0 prototype. Import the `ShadowHallKotlin` project into KlencodIDE.

## Alpha 7 update
- Adds short audio cues when the danger status changes to nearby/very nearby.
- Repeats a restrained warning beep while the monster is very close, with an 1.8-second cooldown.
- Adds a separate short cue while the monster is actively chasing the player, with a 3.5-second cooldown.
- Existing two-finger movement/look controls, sprint button, hiding, flashlight, keys, doors and enemy AI are preserved.

Audio cues use Android `ToneGenerator` tones; they are simple prototype cues, not recorded footsteps or spatial 3D audio.

## Build note
This source archive has not been built or run in KlencodIDE during this update. Please build on-device and share the complete error text or screenshot if Gradle/Kotlin reports a problem.


## Alpha 8 update
- Adds short generated WAV footstep sounds while the player moves; running steps are faster and louder than walking steps.
- Adds a short creaking-door/interaction sound on the Interact button.
- Uses a short suspense sting during an active chase, while retaining the existing proximity warning tones.
- Audio is lightweight and uses bundled mono WAV files under `app/src/main/res/raw`.

## Build note
This source archive has not been built or run in KlencodIDE during this update. Please build on-device and share the complete error text or screenshot if Gradle/Kotlin reports a problem.

## Alpha 9 update
- Interior storage/side-room doors now require at least one key, and the inner rusty lock requires two keys.
- Hiding gives a distinct Arabic location label (under the bed, inside the wardrobe, or behind furniture); leaving the hiding spot resets discovery progress.
- Monster chase speed increases slightly as keys are collected, adding adaptive pressure without expensive pathfinding.
- Adds a very subtle camera shake when the monster is close and a small red/green scene tint for loss/escape feedback.
- The multi-room layout, existing three-key escape gate, fuse puzzle, flashlight battery, touch movement/look, and sound assets remain in the project.

## Alpha 9 build status
- Source archive and ZIP integrity were checked in this environment.
- Android Gradle build and on-device gameplay were **not** run here. Build in KlencodIDE before replacing your working copy.


## Alpha 10 update (source changes)
- Adds an in-game Settings dialog with adjustable camera sensitivity, three lightweight visual-detail presets, and sound-effect volume.
- Low visual preset skips the extra wall-panel/atmosphere pass; high preset adds the ceiling pipe detail pass. This is not true dynamic shadow mapping or volumetric fog.
- Slightly increases the monster's hearing radius for sprinting and walking. Existing multi-room layout, keys/fuse/exit flow, hiding spots, chase/loss/win state, and WAV assets remain.

## Alpha 10 verification
- The source archive was extracted and inspected; the output ZIP is checked for archive integrity and expected Kotlin/resource files.
- Android Gradle/Kotlin build and gameplay were not run in this environment. Import into KlencodIDE and build before replacing your working copy.


## Alpha 11 (source update)
- Improved enemy search pressure and memory of the last noisy/visible player position.
- Hiding changes the first-person eye height (lower under the bed, partially lowered in the wardrobe); furniture hiding is easier to discover than dedicated hide spots.
- Added bathroom-like sink/mirror landmarks, fuse status indicator, and floor warning/debris details.
- Kept the Alpha 10 quality and camera settings.
- Validation: ZIP archive integrity checked. Android build/install/gameplay were not run in this environment. The new room pieces are visual additions inside the existing house footprint, not a new navigable floorplan.

- Alpha 11 also pans the existing chase sting left/right based on the monster's position relative to the camera (stereo cue, not full 3D audio).


## Alpha 12 — Graphics Upgrade (OpenGL ES 2.0)

- Added three locally bundled 256×256 material textures: aged plaster, worn wood, and dirty stone.
- The renderer loads texture assets on the GL thread and selects a material based on the existing object tint, so the current scene geometry and gameplay remain in place.
- Added face-aware directional shading, subtle material mottling/grain, distance fog, and screen-edge vignette in the fragment shader. Fog strength follows the selected quality preset.
- The effect is designed for OpenGL ES 2.0 and avoids shadow-map render passes; **real-time cast shadows and a physically accurate flashlight cone are not implemented in this alpha**.
- Build status: source/ZIP validation only. No APK build or on-device test was performed as part of this package.


## Alpha 13 — Flashlight Fix (source update)
- Replaced the old tiny fixed-position floor bar with shader-based directional flashlight illumination.
- The beam now follows camera yaw and lights surfaces in a forward cone with distance attenuation; low battery reduces power and the existing flicker/hiding behavior is retained.
- This is a lightweight OpenGL ES 2.0 spotlight approximation, not shadow mapping or physically accurate volumetric lighting.
- Validation: ZIP integrity and source-level changes checked. Android Gradle build and on-device gameplay were not run in this environment.


## Alpha 14 — Visual pass
- Replaced the three material maps with 512x512 procedural detailed plaster, aged wood, and dirty stone textures.
- Improved ambient visibility and softened the heavy fog/vignette that made rooms excessively dark.
- Widened and strengthened the approximate flashlight cone.
- Validation: archive integrity and asset/source presence checked. No Android build or device test was available, so in-game appearance still needs verification on the A23.


## Alpha 15 — Ultra Quality preset
- High/ultra visual quality is now the default preset.
- Added shader-level material micro-detail and restrained surface highlights.
- Added layered wall paneling, skirting/baseboards, and additional architectural trim when high quality is selected.
- Increased flashlight reach and reduced excessive fog/vignette darkening.
- This is still a cube-based OpenGL ES scene; the update does not turn it into a photorealistic AAA renderer. ZIP/source checks only; no APK build or device test was available.


## Alpha 16 — High-detail scene pass
- Added layered door casings and inset panels, floorboard seams, cabinet trim/drawer pull, table legs, books, bed frame/pillow, ceiling beams and wall rails in the ultra-quality scene.
- Adjusted shader lighting/grain to be less flat and less noisy.
- Existing gameplay state, controls, and room/door interactions were retained in source.
- Validation: ZIP integrity and source edits checked; no Android build or device run was available. Geometry is still composed of boxes and does not yet match a full high-poly asset pipeline.


## Alpha 17 — Monster silhouette redesign
- Replaced the old large rectangular monster body/head with separated torso, hips, legs, feet, shoulders, upper/lower arms, fingers, neck, skull, cheek/brow pieces, sockets, glowing eyes, nose, jaw, and teeth.
- Existing monster position and AI movement variables remain unchanged; this is a visual-only replacement.
- Validation: source block and ZIP integrity checked. No Android build or device run was available.


## Alpha 18 — Lighting and environment detail pass
- Added three warm point-light approximations in the fragment shader, aligned with the hall ceiling fixtures, including distance falloff and subtle flicker.
- Increased ambient visibility slightly and added fixture trim, warm lamp panels, wall grime streaks, and utility-panel bolts in ultra quality.
- Existing gameplay logic and controls were not intentionally changed.
- Validation: ZIP integrity and source checks only; no Android build or device test was available. Lighting uses an approximate shader model and does not cast real shadows.
