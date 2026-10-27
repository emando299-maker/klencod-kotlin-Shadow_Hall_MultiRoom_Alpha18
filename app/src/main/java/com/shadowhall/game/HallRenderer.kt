package com.shadowhall.game

import android.opengl.GLES20
import android.graphics.BitmapFactory
import android.opengl.GLUtils
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.sqrt

/** High-detail OpenGL ES 2.0 horror scene with layered architectural geometry and textured materials. */
class HallRenderer : GLSurfaceView.Renderer {
    @Volatile var moveX = 0f
    @Volatile var moveZ = 0f
    @Volatile var cameraYaw = 0f
    @Volatile var hidden = false
    @Volatile var hidingPlace = ""
    @Volatile var flashlightOn = true
    @Volatile var flashlightBattery = 100f
    @Volatile var stamina = 100f
    /** User-adjustable touch sensitivity and lightweight scene detail preset. */
    /** Read-only world positions exposed for lightweight stereo sound panning. */
    val playerWorldX: Float get() = px
    val playerWorldZ: Float get() = pz
    val monsterWorldX: Float get() = mx
    val monsterWorldZ: Float get() = mz
    @Volatile var cameraSensitivity = 220f
    @Volatile var visualQuality = 2 // 0=performance, 1=balanced, 2=ultra quality
    @Volatile var keysCount = 0
    @Volatile var statusText = "اجمع 3 مفاتيح"
    @Volatile var monsterState = "يتجول"
    @Volatile var gameWon = false
    @Volatile var exitDoorOpen = false
    @Volatile var drawerOpen = false
    @Volatile var hasFuse = false
    @Volatile var batteryPackCollected = false
    @Volatile var bedroomDoorOpen = false
    @Volatile var kitchenDoorOpen = false
    @Volatile var storageDoorOpen = false
    @Volatile var sideRoomDoorOpen = false
    private var program = 0
    private var posHandle = 0
    private var mvpHandle = 0
    private var colorHandle = 0
    private var mvHandle = 0
    private var modelHandle = 0
    private var flashPosHandle = 0
    private var flashDirHandle = 0
    private var flashOnHandle = 0
    private var flashPowerHandle = 0
    private var lampPos0Handle = 0
    private var lampPos1Handle = 0
    private var lampPos2Handle = 0
    private var lampStrengthHandle = 0
    private var textureHandle = 0
    private var useTextureHandle = 0
    private var fogHandle = 0
    private var timeHandle = 0
    private var viewportHandle = 0
    private var textureIds = IntArray(3)
    private var viewportWidth = 1f
    private var viewportHeight = 1f
    private lateinit var vertices: FloatBuffer
    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val model = FloatArray(16)
    private val vp = FloatArray(16)
    private val mvp = FloatArray(16)
    private var px = 0f; private var pz = 2.8f
    private var mx = 0f; private var mz = -3.2f
    private val keys = arrayOf(floatArrayOf(-6.0f, -2.9f), floatArrayOf(6.0f, -2.9f), floatArrayOf(0.2f, 3.5f))
    private val collected = BooleanArray(3)
    private var lastTime = System.nanoTime()
    private var gameOver = false
    private var alertTimer = 0f
    private var patrolClock = 0f
    private var investigateTimer = 0f
    private var hideDiscoveryTimer = 0f
    private var lastKnownX = 0f
    private var lastKnownZ = 0f
    private var searchPhase = 0f
    private var searchTargetTimer = 0f
    private var searchTargetX = 0f
    private var searchTargetZ = 0f
    private var lastSeenTimer = 0f
    private var patrolTargetX = -5.5f
    private var patrolTargetZ = 0f
    private val hideSpots = arrayOf(floatArrayOf(-2.8f, -0.4f), floatArrayOf(2.5f, 2.0f), floatArrayOf(0f, -3.8f))

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.025f, 0.035f, 0.07f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        // Alpha 12: textured materials, face-aware directional shading, distance fog and subtle film grain.
        val vertexShader = """
            attribute vec4 aPosition;
            uniform mat4 uMVP;
            uniform mat4 uMV;
            uniform mat4 uModel;
            varying vec3 vWorld;
            varying vec2 vUV;
            varying vec3 vLocal;
            varying float vDepth;
            void main(){
              vec3 p=aPosition.xyz;
              vec3 a=abs(p);
              if(a.x>a.y && a.x>a.z) vUV=vec2(p.z,p.y)+0.5;
              else if(a.y>a.x && a.y>a.z) vUV=vec2(p.x,p.z)+0.5;
              else vUV=vec2(p.x,p.y)+0.5;
              vLocal=p;
              vWorld=(uModel*aPosition).xyz;
              vec4 eye=uMV*aPosition;
              vDepth=-eye.z;
              gl_Position=uMVP*aPosition;
            }
        """.trimIndent()
        val fragmentShader = """
            precision mediump float;
            uniform vec4 uColor;
            uniform sampler2D uTexture;
            uniform int uUseTexture;
            uniform float uFog;
            uniform float uTime;
            uniform vec2 uViewport;
            uniform vec3 uFlashPos;
            uniform vec3 uFlashDir;
            uniform float uFlashOn;
            uniform float uFlashPower;
            uniform vec3 uLampPos0;
            uniform vec3 uLampPos1;
            uniform vec3 uLampPos2;
            uniform float uLampStrength;
            varying vec3 vWorld;
            varying vec2 vUV;
            varying vec3 vLocal;
            varying float vDepth;
            float hash(vec2 p){ return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453); }
            void main(){
              vec4 texel = uUseTexture == 1 ? texture2D(uTexture, vUV) : vec4(1.0);
              vec3 base=uColor.rgb*texel.rgb;
              vec3 a=abs(vLocal);
              vec3 normal;
              if(a.x>a.y && a.x>a.z) normal=vec3(sign(vLocal.x),0.0,0.0);
              else if(a.y>a.x && a.y>a.z) normal=vec3(0.0,sign(vLocal.y),0.0);
              else normal=vec3(0.0,0.0,sign(vLocal.z));
              float diffuse=max(dot(normal,normalize(vec3(-0.35,0.82,0.42))),0.0);
              float lighting=0.66+diffuse*0.34;
              // Three warm ceiling lights with distance falloff; this is a lightweight
              // per-fragment point-light approximation, not shadow mapping.
              vec3 l0=uLampPos0-vWorld; float d0=length(l0);
              vec3 l1=uLampPos1-vWorld; float d1=length(l1);
              vec3 l2=uLampPos2-vWorld; float d2=length(l2);
              float p0=max(dot(normal,normalize(l0)),0.0)/(1.0+0.45*d0+0.18*d0*d0);
              float p1=max(dot(normal,normalize(l1)),0.0)/(1.0+0.45*d1+0.18*d1*d1);
              float p2=max(dot(normal,normalize(l2)),0.0)/(1.0+0.45*d2+0.18*d2*d2);
              vec3 lampLight=vec3(1.0,0.72,0.43)*(p0+p1+p2)*uLampStrength*0.72;
              float grain=(hash(gl_FragCoord.xy+uTime*13.0)-0.5)*0.012;
              float broadMottle=0.94+0.06*sin(vUV.x*19.0+sin(vUV.y*13.0))*sin(vUV.y*31.0);
              float fineMottle=0.96+0.04*sin(vUV.x*127.0+sin(vUV.y*91.0));
              float pores=hash(floor(vUV*vec2(190.0,190.0)))-0.5;
              float roughnessDetail=clamp(broadMottle*fineMottle+pores*0.09,0.72,1.08);
              vec3 color=base*lighting*roughnessDetail+base*lampLight+grain;
              // Small restrained highlight gives wood/stone edges a less flat appearance.
              float highlight=pow(max(dot(normal,normalize(vec3(-0.25,0.9,0.35))),0.0),7.0)*0.075;
              color += highlight*texel.rgb;
              // Directional flashlight illumination: works on every rendered surface,
              // follows the first-person camera and fades naturally with distance.
              vec3 toSurface=vWorld-uFlashPos;
              float distToSurface=length(toSurface);
              vec3 ray=normalize(toSurface);
              float cone=dot(ray,normalize(uFlashDir));
              float spot=smoothstep(0.58,0.86,cone);
              float attenuation=1.0/(1.0+0.028*distToSurface+0.006*distToSurface*distToSurface);
              float flashlight=uFlashOn*spot*attenuation*uFlashPower;
              color += base*flashlight*2.55;
              float fog=clamp((vDepth-9.0)/28.0,0.0,0.38)*uFog;
              color=mix(color,vec3(0.035,0.045,0.065),fog);
              vec2 screen=gl_FragCoord.xy/uViewport-0.5;
              float vignette=1.0-0.10*clamp(dot(screen,screen)*1.7,0.0,1.0);
              color*=vignette;
              gl_FragColor=vec4(max(color,vec3(0.0)),uColor.a);
            }
        """.trimIndent()
        program = GLES20.glCreateProgram().also { p ->
            val vs = compile(GLES20.GL_VERTEX_SHADER, vertexShader); val fs = compile(GLES20.GL_FRAGMENT_SHADER, fragmentShader)
            GLES20.glAttachShader(p, vs); GLES20.glAttachShader(p, fs); GLES20.glLinkProgram(p)
        }
        posHandle = GLES20.glGetAttribLocation(program, "aPosition")
        mvpHandle = GLES20.glGetUniformLocation(program, "uMVP")
        colorHandle = GLES20.glGetUniformLocation(program, "uColor")
        mvHandle = GLES20.glGetUniformLocation(program, "uMV")
        modelHandle = GLES20.glGetUniformLocation(program, "uModel")
        flashPosHandle = GLES20.glGetUniformLocation(program, "uFlashPos")
        flashDirHandle = GLES20.glGetUniformLocation(program, "uFlashDir")
        flashOnHandle = GLES20.glGetUniformLocation(program, "uFlashOn")
        flashPowerHandle = GLES20.glGetUniformLocation(program, "uFlashPower")
        lampPos0Handle = GLES20.glGetUniformLocation(program, "uLampPos0")
        lampPos1Handle = GLES20.glGetUniformLocation(program, "uLampPos1")
        lampPos2Handle = GLES20.glGetUniformLocation(program, "uLampPos2")
        lampStrengthHandle = GLES20.glGetUniformLocation(program, "uLampStrength")
        textureHandle = GLES20.glGetUniformLocation(program, "uTexture")
        useTextureHandle = GLES20.glGetUniformLocation(program, "uUseTexture")
        fogHandle = GLES20.glGetUniformLocation(program, "uFog")
        timeHandle = GLES20.glGetUniformLocation(program, "uTime")
        viewportHandle = GLES20.glGetUniformLocation(program, "uViewport")
        loadMaterialTextures()
        val data = floatArrayOf(
            -0.5f,-0.5f,0.5f, 0.5f,-0.5f,0.5f, 0.5f,0.5f,0.5f, -0.5f,-0.5f,0.5f, 0.5f,0.5f,0.5f, -0.5f,0.5f,0.5f,
            0.5f,-0.5f,-0.5f, -0.5f,-0.5f,-0.5f, -0.5f,0.5f,-0.5f, 0.5f,-0.5f,-0.5f, -0.5f,0.5f,-0.5f, 0.5f,0.5f,-0.5f,
            -0.5f,-0.5f,-0.5f, -0.5f,-0.5f,0.5f, -0.5f,0.5f,0.5f, -0.5f,-0.5f,-0.5f, -0.5f,0.5f,0.5f, -0.5f,0.5f,-0.5f,
            0.5f,-0.5f,0.5f, 0.5f,-0.5f,-0.5f, 0.5f,0.5f,-0.5f, 0.5f,-0.5f,0.5f, 0.5f,0.5f,-0.5f, 0.5f,0.5f,0.5f,
            -0.5f,0.5f,0.5f, 0.5f,0.5f,0.5f, 0.5f,0.5f,-0.5f, -0.5f,0.5f,0.5f, 0.5f,0.5f,-0.5f, -0.5f,0.5f,-0.5f,
            -0.5f,-0.5f,-0.5f, 0.5f,-0.5f,-0.5f, 0.5f,-0.5f,0.5f, -0.5f,-0.5f,-0.5f, 0.5f,-0.5f,0.5f, -0.5f,-0.5f,0.5f
        )
        vertices = ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(data); position(0) }
    }
    private fun compile(type: Int, source: String): Int = GLES20.glCreateShader(type).also { s -> GLES20.glShaderSource(s, source); GLES20.glCompileShader(s) }

    private fun loadMaterialTextures() {
        GLES20.glGenTextures(3, textureIds, 0)
        val names = arrayOf("textures/plaster.png", "textures/old_wood.png", "textures/dirty_stone.png")
        for (i in names.indices) {
            try {
                val bitmap = activityAssets?.open(names[i])?.use { BitmapFactory.decodeStream(it) } ?: continue
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureIds[i])
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT)
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
                GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
                bitmap.recycle()
            } catch (_: Exception) { /* Material shader falls back to tint if an asset is missing. */ }
        }
    }

    // Supplied by MainActivity's context without retaining an Activity reference.
    @Volatile var activityAssets: android.content.res.AssetManager? = null

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportWidth = width.toFloat().coerceAtLeast(1f); viewportHeight = height.toFloat().coerceAtLeast(1f)
        GLES20.glViewport(0,0,width,height)
        Matrix.perspectiveM(projection,0,62f,width.toFloat()/height.coerceAtLeast(1),0.1f,60f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime(); val dt = ((now-lastTime)/1e9f).coerceIn(0f,0.04f); lastTime=now
        if (!gameOver) update(dt)
        // Flashlight battery makes light management part of the survival loop.
        if (flashlightOn) {
            flashlightBattery = (flashlightBattery - 2.2f * dt).coerceAtLeast(0f)
            if (flashlightBattery <= 0f) flashlightOn = false
        } else {
            flashlightBattery = (flashlightBattery + 1.25f * dt).coerceAtMost(100f)
        }
        // Danger feedback: the scene subtly pulses red as the monster closes in.
        // This is a cheap clear-color effect, so it does not add draw calls.
        val dangerDx = px - mx
        val dangerDz = pz - mz
        val dangerDistance = sqrt(dangerDx * dangerDx + dangerDz * dangerDz)
        val dangerPulse = (kotlin.math.sin(System.nanoTime() / 150000000.0).toFloat() + 1f) * 0.5f
        val danger = if (!hidden && !gameWon && !gameOver && dangerDistance < 4.2f)
            ((4.2f - dangerDistance) / 4.2f).coerceIn(0f, 1f) * (0.025f + dangerPulse * 0.035f)
        else 0f
        val fogPulse = if (visualQuality == 0) 0f else (kotlin.math.sin(System.nanoTime()/900000000.0).toFloat()+1f)*0.5f
        val endRed = if (gameOver) 0.12f else 0f
        val endGreen = if (gameWon) 0.08f else 0f
        GLES20.glClearColor(0.018f + danger + endRed + fogPulse*0.004f, 0.024f + endGreen, 0.045f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val yaw = Math.toRadians(cameraYaw.toDouble())
        // First-person camera, inspired by classic hide-and-escape horror games.
        val lookX = px + kotlin.math.sin(yaw).toFloat()
        val lookZ = pz - kotlin.math.cos(yaw).toFloat()
        val nearDanger = sqrt((px-mx)*(px-mx) + (pz-mz)*(pz-mz))
        val shake = if (!hidden && nearDanger < 2.5f && !gameOver && !gameWon) kotlin.math.sin(System.nanoTime()/36000000.0).toFloat() * ((2.5f-nearDanger)/2.5f) * 0.025f else 0f
        val eyeHeight = if (hidden && hidingPlace == "تحت السرير") 0.48f else if (hidden) 0.92f else 1.48f
        val lookHeight = if (hidden && hidingPlace == "تحت السرير") 0.46f else if (hidden) 0.90f else 1.38f
        Matrix.setLookAtM(view,0,px+shake,eyeHeight,pz-shake,lookX+shake,lookHeight,lookZ-shake,0f,1f,0f)
        Matrix.multiplyMM(vp,0,projection,0,view,0)
        // Multi-room house: central hall plus two side wings, kept low-poly for A23.
        box(0f,-0.22f,0f,8f,0.35f,12f,0.13f,0.15f,0.21f)
        box(-6f,-0.22f,0f,4.2f,0.35f,12f,0.12f,0.14f,0.19f)
        box(6f,-0.22f,0f,4.2f,0.35f,12f,0.12f,0.14f,0.19f)
        for (z in -5..5 step 2) box(0f,-0.035f,z.toFloat(),7.7f,0.012f,0.025f,0.22f,0.24f,0.30f)
        for (x in listOf(-6f, 6f)) for (z in -5..5 step 2) box(x,-0.035f,z.toFloat(),3.8f,0.012f,0.025f,0.18f,0.20f,0.26f)
        for (z in -4..4 step 4) {
            box(0f,2.18f,z.toFloat(),1.0f,0.04f,0.35f,0.52f,0.38f,0.16f)
            box(-1.8f,2.18f,z.toFloat(),0.16f,0.035f,0.3f,0.25f,0.38f,0.48f)
            box(1.8f,2.18f,z.toFloat(),0.16f,0.035f,0.3f,0.25f,0.38f,0.48f)
        }
        // Outer walls of the expanded house.
        box(-8.1f,1f,0f,0.3f,2.4f,12.5f,0.20f,0.22f,0.30f)
        box(8.1f,1f,0f,0.3f,2.4f,12.5f,0.20f,0.22f,0.30f)
        // Keep the escape doorway clear in the rear wall.
        box(-4.6f,1f,-6.25f,7.0f,2.4f,0.3f,0.16f,0.18f,0.25f)
        box(4.6f,1f,-6.25f,7.0f,2.4f,0.3f,0.16f,0.18f,0.25f)
        box(0f,1f,6.25f,16.2f,2.4f,0.3f,0.16f,0.18f,0.25f)
        // Side-hall partitions leave doorways at z=-2 and z=2.
        for (x in listOf(-4f, 4f)) {
            box(x,1f,-4.35f,0.18f,2.4f,3.8f,0.20f,0.22f,0.30f)
            box(x,1f,0f,0.18f,2.4f,2.0f,0.20f,0.22f,0.30f)
            box(x,1f,4.35f,0.18f,2.4f,3.8f,0.20f,0.22f,0.30f)
        }
        // Three named rooms: bedroom, kitchen and storage. Door panels slide aside when opened.
        box(-6.1f,0.72f,-3.9f,1.25f,1.35f,0.72f,0.22f,0.13f,0.12f)
        box(-6.1f,1.42f,-3.9f,1.28f,0.08f,0.74f,0.40f,0.25f,0.19f)
        box(-6.1f,0.20f,-3.9f,1.30f,0.35f,0.76f,0.15f,0.10f,0.10f)
        box(6.0f,0.48f,-3.9f,1.15f,0.92f,0.65f,0.26f,0.29f,0.31f)
        box(6.0f,0.98f,-3.9f,1.18f,0.08f,0.68f,0.48f,0.43f,0.30f)
        box(6.0f,0.30f,3.8f,1.05f,0.55f,0.72f,0.28f,0.23f,0.15f)
        box(6.0f,0.62f,3.8f,0.82f,0.08f,0.54f,0.42f,0.34f,0.21f)
        // Internal door between the right-side rooms.
        box(if(sideRoomDoorOpen) 7.35f else 6.0f,1f,0f,1.45f,2.0f,0.12f,if(sideRoomDoorOpen) 0.34f else 0.27f,if(sideRoomDoorOpen) 0.42f else 0.19f,0.12f)
        // Four doorway panels. Open doors are visibly slid along the wall.
        box(if(bedroomDoorOpen) -4.0f else -4.0f,1f,if(bedroomDoorOpen) -0.05f else -2f,0.12f,1.95f,1.35f,0.30f,0.18f,0.12f)
        box(if(kitchenDoorOpen) 4.0f else 4.0f,1f,if(kitchenDoorOpen) 0.05f else -2f,0.12f,1.95f,1.35f,0.22f,0.30f,0.24f)
        box(-4.0f,1f,if(storageDoorOpen) 3.95f else 2f,0.12f,1.95f,1.35f,0.28f,0.20f,0.13f)
        box(4.0f,1f,if(sideRoomDoorOpen) 3.95f else 2f,0.12f,1.95f,1.35f,0.25f,0.22f,0.15f)
        // Atmospheric pass: wall panels, ceiling fixtures and unsettling stains.
        // These simple meshes keep rendering inexpensive on mid-range Android phones.
        val flicker = if ((System.nanoTime() / 100000000L) % 19L == 0L) 0.12f else 1f
        if (visualQuality >= 1) for (z in -4..4 step 2) {
            box(-3.82f,1.05f,z.toFloat(),0.025f,1.55f,0.72f,0.10f,0.12f,0.17f)
            box(3.82f,1.05f,z.toFloat(),0.025f,1.55f,0.72f,0.10f,0.12f,0.17f)
            box(-3.79f,1.78f,z.toFloat(),0.035f,0.045f,0.66f,0.32f,0.12f,0.10f)
            box(3.79f,1.78f,z.toFloat(),0.035f,0.045f,0.66f,0.32f,0.12f,0.10f)
        }
        for (z in -4..4 step 4) {
            box(0f,2.27f,z.toFloat(),0.72f,0.035f,0.24f,0.72f*flicker,0.68f*flicker,0.42f*flicker)
            box(0f,2.22f,z.toFloat(),0.92f,0.035f,0.34f,0.12f,0.13f,0.18f)
        }
        box(-1.45f,0.005f,1.35f,0.62f,0.012f,0.34f,0.12f,0.045f,0.055f)
        box(1.75f,0.006f,-2.7f,0.38f,0.013f,0.52f,0.09f,0.035f,0.045f)
        box(-3.78f,1.10f,-2.0f,0.018f,0.52f,0.30f,0.18f,0.035f,0.045f)
        // Exposed ceiling pipes and utility conduits add depth to the abandoned building.
        if (visualQuality >= 2) {
            // Extra architectural trim: skirting, door casings and layered wall panels.
            for (x in -6..6 step 2) {
                box(x.toFloat(),0.10f, -4.92f,1.82f,0.16f,0.07f,0.25f,0.24f,0.22f)
                box(x.toFloat(),0.10f, 4.92f,1.82f,0.16f,0.07f,0.25f,0.24f,0.22f)
            }
            for (z in -4..4 step 2) {
                box(-3.91f,0.10f,z.toFloat(),0.07f,0.16f,1.72f,0.25f,0.24f,0.22f)
                box(3.91f,0.10f,z.toFloat(),0.07f,0.16f,1.72f,0.25f,0.24f,0.22f)
            }
            for (z in -4..4 step 2) {
                box(-3.88f,1.02f,z.toFloat(),0.045f,1.34f,0.92f,0.16f,0.17f,0.18f)
                box(3.88f,1.02f,z.toFloat(),0.045f,1.34f,0.92f,0.16f,0.17f,0.18f)
                box(-3.84f,1.72f,z.toFloat(),0.055f,0.045f,0.98f,0.30f,0.27f,0.22f)
                box(3.84f,1.72f,z.toFloat(),0.055f,0.045f,0.98f,0.30f,0.27f,0.22f)
            }
        }
        if (visualQuality >= 2) for (z in -4..4 step 2) {
            box(-2.65f,2.05f,z.toFloat(),0.055f,0.055f,1.05f,0.20f,0.22f,0.24f)
            box(2.65f,2.05f,z.toFloat(),0.055f,0.055f,1.05f,0.20f,0.22f,0.24f)
            box(-2.65f,2.00f,z.toFloat(),0.09f,0.025f,0.08f,0.31f,0.16f,0.10f)
            box(2.65f,2.00f,z.toFloat(),0.09f,0.025f,0.08f,0.31f,0.16f,0.10f)
        }
        // A damaged wall notice and rusty utility panel provide visual landmarks.
        box(3.78f,1.20f,0.25f,0.025f,0.48f,0.38f,0.30f,0.24f,0.16f)
        box(3.75f,1.20f,0.25f,0.025f,0.32f,0.25f,0.10f,0.12f,0.14f)
        box(3.72f,1.28f,0.17f,0.025f,0.035f,0.035f,0.65f,0.08f,0.06f)
        box(-3.78f,0.82f,2.4f,0.025f,0.25f,0.50f,0.11f,0.13f,0.15f)
        box(-3.75f,0.82f,2.4f,0.025f,0.15f,0.38f,0.34f,0.20f,0.10f)
        // Alpha 18: more visible light fixtures and layered environmental details.
        if (visualQuality >= 2) {
            for (z in listOf(-4f, 0f, 4f)) {
                // Recessed ceiling plate, warm luminous panel and metal rim.
                box(0f,2.245f,z,0.92f,0.045f,0.42f,0.12f,0.13f,0.16f)
                box(0f,2.205f,z,0.68f,0.022f,0.24f,0.95f,0.72f,0.42f)
                box(-0.39f,2.205f,z,0.035f,0.03f,0.30f,0.38f,0.30f,0.18f)
                box(0.39f,2.205f,z,0.035f,0.03f,0.30f,0.38f,0.30f,0.18f)
            }
            // Wall grime streaks and small chipped-plaster marks, placed as surface overlays.
            for (i in 0..7) {
                val z = -4.2f + i*1.15f
                val height = 0.18f + (i%3)*0.11f
                box(-3.785f,1.35f,z,0.012f,height,0.035f,0.22f,0.20f,0.17f)
                box(3.785f,1.20f,z+0.28f,0.012f,height*0.8f,0.045f,0.17f,0.16f,0.14f)
            }
            // Metal bolts on the utility panel add scale and definition.
            for (yy in listOf(1.08f,1.32f)) for (zz in listOf(0.10f,0.40f)) {
                box(3.715f,yy,zz,0.025f,0.025f,0.025f,0.62f,0.57f,0.43f)
            }
        }
        // Alpha 16: layered architectural details, door panels, floorboards and furniture construction.
        if (visualQuality >= 2) {
            // Floorboard seams across the central hall: varied lengths and alternating wood tones.
            for (row in 0..11) {
                val z = -5.45f + row * 0.95f
                val offset = if (row % 2 == 0) -0.8f else 0.2f
                var x = -3.55f + offset
                while (x < 3.6f) {
                    box(x, -0.035f, z, 1.62f, 0.009f, 0.012f,
                        if (row % 3 == 0) 0.25f else 0.19f,
                        if (row % 3 == 0) 0.14f else 0.105f,
                        if (row % 3 == 0) 0.075f else 0.065f)
                    x += 1.75f
                }
            }
            // Raised door casings and inset panels on the four interior doors.
            for (side in listOf(-4f, 4f)) {
                for (zDoor in listOf(-2f, 2f)) {
                    box(side + if (side < 0) 0.12f else -0.12f, 1.0f, zDoor-0.68f, 0.075f, 2.06f, 0.09f, 0.39f,0.24f,0.13f)
                    box(side + if (side < 0) 0.12f else -0.12f, 1.0f, zDoor+0.68f, 0.075f, 2.06f, 0.09f, 0.39f,0.24f,0.13f)
                    box(side + if (side < 0) 0.12f else -0.12f, 2.02f, zDoor, 0.075f, 0.09f, 1.45f, 0.43f,0.28f,0.16f)
                    box(side + if (side < 0) 0.12f else -0.12f, 0.22f, zDoor, 0.075f, 0.09f, 1.45f, 0.34f,0.20f,0.11f)
                    box(side + if (side < 0) 0.105f else -0.105f, 1.0f, zDoor, 0.035f, 1.35f, 0.78f, 0.18f,0.105f,0.06f)
                    box(side + if (side < 0) 0.08f else -0.08f, 1.52f, zDoor, 0.04f, 0.035f, 0.62f, 0.45f,0.29f,0.17f)
                    box(side + if (side < 0) 0.08f else -0.08f, 0.50f, zDoor, 0.04f, 0.035f, 0.62f, 0.45f,0.29f,0.17f)
                }
            }
            // Table construction and legs, rather than a single block.
            for (xLeg in listOf(-0.42f,0.42f)) for (zLeg in listOf(-0.27f,0.27f)) {
                box(-2.8f+xLeg,0.23f,-0.4f+zLeg,0.075f,0.42f,0.075f,0.20f,0.09f,0.055f)
            }
            // Books and clutter on the cabinet top.
            for (i in 0..5) {
                val bx = -3.16f + i*0.13f
                box(bx,0.99f,-0.43f,0.095f,0.18f,0.17f,
                    if (i%2==0) 0.32f else 0.13f,
                    if (i%3==0) 0.10f else 0.18f,
                    if (i%2==0) 0.055f else 0.20f)
            }
            // Extra cabinet drawer fronts and metallic pulls.
            box(-2.8f,0.69f,-1.015f,0.72f,0.20f,0.045f,0.33f,0.17f,0.08f)
            box(-2.8f,0.69f,-1.052f,0.12f,0.035f,0.025f,0.65f,0.53f,0.29f)
            // Bed frame and separated legs in the bedroom wing.
            for (xLeg in listOf(-0.48f,0.48f)) for (zLeg in listOf(-0.28f,0.28f)) {
                box(6.0f+xLeg,0.20f,-3.9f+zLeg,0.07f,0.35f,0.07f,0.19f,0.12f,0.08f)
            }
            box(6.0f,0.40f,-3.9f,1.28f,0.10f,0.78f,0.28f,0.17f,0.12f)
            box(6.0f,0.49f,-4.02f,0.55f,0.08f,0.34f,0.47f,0.42f,0.36f)
            // Ceiling beams and decorative wall rails.
            for (z in -5..5 step 2) {
                box(0f,2.29f,z.toFloat(),7.9f,0.07f,0.12f,0.19f,0.12f,0.075f)
                box(0f,1.86f,z.toFloat(),7.8f,0.035f,0.045f,0.34f,0.23f,0.13f)
            }
        }
        // Furniture construction is layered to avoid the single-block look.
        // cartoon furniture that can provide visual cover
        box(-2.8f,0.45f,-0.4f,1.0f,0.9f,1.2f,0.28f,0.12f,0.10f)
        box(2.5f,0.6f,2.0f,1.2f,1.2f,0.8f,0.18f,0.20f,0.27f)
        box(0f,0.5f,-3.8f,1.5f,1.0f,0.7f,0.25f,0.18f,0.11f)
        // Cabinet details, bed-like obstacle and scattered floor clutter.
        box(-2.8f,0.48f,-1.02f,0.68f,0.05f,0.025f,0.52f,0.30f,0.14f)
        // Searchable cabinet drawer; it slides outward after interaction.
        box(-2.8f,0.27f,if(drawerOpen) -1.30f else -0.94f,0.58f,0.20f,0.10f,0.36f,0.19f,0.09f)
        box(-2.8f,0.27f,if(drawerOpen) -1.355f else -0.995f,0.12f,0.035f,0.025f,0.72f,0.54f,0.28f)
        if(drawerOpen && !hasFuse) box(-2.8f,0.42f,-1.30f,0.16f,0.10f,0.12f,0.18f,0.88f,0.72f)
        box(-3.12f,0.50f,-0.4f,0.06f,0.74f,0.06f,0.42f,0.23f,0.11f)
        box(-2.48f,0.50f,-0.4f,0.06f,0.74f,0.06f,0.42f,0.23f,0.11f)
        box(2.5f,1.22f,2.0f,0.92f,0.08f,0.56f,0.35f,0.36f,0.43f)
        box(2.5f,0.18f,2.0f,1.0f,0.35f,0.62f,0.16f,0.13f,0.12f)
        box(1.0f,0.015f,0.8f,0.45f,0.025f,0.22f,0.32f,0.25f,0.18f)
        box(-1.1f,0.015f,-2.0f,0.28f,0.025f,0.35f,0.26f,0.27f,0.30f)
        // Alpha 11: additional room landmarks and puzzle props (decorative geometry, no extra textures).
        // Bathroom sink and cracked mirror in the far-right wing.
        box(6.25f,0.62f,4.65f,0.92f,0.16f,0.42f,0.42f,0.43f,0.40f)
        box(6.25f,0.88f,4.65f,0.72f,0.06f,0.30f,0.60f,0.63f,0.60f)
        box(6.25f,1.42f,4.88f,0.58f,0.58f,0.045f,0.16f,0.21f,0.25f)
        box(6.25f,1.42f,4.85f,0.42f,0.42f,0.025f,0.28f,0.34f,0.38f)
        // Wall-mounted fuse indicator: green when the fuse is installed, red otherwise.
        box(-3.72f,1.42f,-0.85f,0.035f,0.42f,0.30f,0.15f,0.14f,0.12f)
        box(-3.69f,1.48f,-0.85f,0.025f,0.09f,0.09f,if(hasFuse) 0.10f else 0.85f,if(hasFuse) 0.85f else 0.08f,0.05f)
        // Broken floorboards and warning stripes add readable landmarks without heavy effects.
        for (i in 0..3) box(-5.9f+i*0.34f,0.006f,1.55f,0.22f,0.012f,0.035f,0.20f,0.12f,0.09f)
        box(5.35f,0.012f,1.15f,0.75f,0.018f,0.045f,0.48f,0.24f,0.06f)
        box(5.35f,0.014f,1.25f,0.75f,0.018f,0.045f,0.12f,0.10f,0.08f)
        // The player is not drawn because this version uses a first-person camera.
        // Alpha 17: rebuilt pursuer silhouette from separated anatomical forms.
        // The front of the face points toward +Z, matching the existing eye placement.
        val monsterBob = kotlin.math.sin((System.nanoTime()/180000000.0).toFloat())*0.035f
        val mY = monsterBob
        // Legs, knees, ankles and broad feet; narrower parts avoid the old single-block silhouette.
        box(mx-0.19f,0.20f+mY,mz,0.19f,0.38f,0.23f,0.12f,0.025f,0.045f)
        box(mx+0.19f,0.20f+mY,mz,0.19f,0.38f,0.23f,0.12f,0.025f,0.045f)
        box(mx-0.19f,0.43f+mY,mz+0.015f,0.23f,0.16f,0.25f,0.17f,0.035f,0.055f)
        box(mx+0.19f,0.43f+mY,mz+0.015f,0.23f,0.16f,0.25f,0.17f,0.035f,0.055f)
        box(mx-0.20f,0.055f+mY,mz+0.075f,0.27f,0.10f,0.39f,0.075f,0.018f,0.035f)
        box(mx+0.20f,0.055f+mY,mz+0.075f,0.27f,0.10f,0.39f,0.075f,0.018f,0.035f)
        // Hips and ribbed torso.
        box(mx,0.62f+mY,mz,0.47f,0.34f,0.34f,0.19f,0.025f,0.055f)
        box(mx,0.88f+mY,mz+0.015f,0.56f,0.42f,0.31f,0.15f,0.022f,0.045f)
        box(mx,0.92f+mY,mz+0.185f,0.36f,0.27f,0.045f,0.24f,0.035f,0.06f)
        // Shoulder masses and two-piece arms.
        box(mx-0.34f,1.02f+mY,mz,0.25f,0.25f,0.28f,0.13f,0.02f,0.045f)
        box(mx+0.34f,1.02f+mY,mz,0.25f,0.25f,0.28f,0.13f,0.02f,0.045f)
        box(mx-0.43f,0.78f+mY,mz+0.015f,0.16f,0.34f,0.19f,0.10f,0.018f,0.035f)
        box(mx+0.43f,0.78f+mY,mz+0.015f,0.16f,0.34f,0.19f,0.10f,0.018f,0.035f)
        box(mx-0.45f,0.48f+mY,mz+0.055f,0.13f,0.31f,0.16f,0.075f,0.012f,0.025f)
        box(mx+0.45f,0.48f+mY,mz+0.055f,0.13f,0.31f,0.16f,0.075f,0.012f,0.025f)
        // Long fingers/claws read as separate shapes instead of square hands.
        for (i in 0..2) {
            val fx = mx-0.49f+i*0.055f
            val rx = mx+0.39f+i*0.055f
            box(fx,0.31f+mY,mz+0.11f,0.035f,0.16f,0.045f,0.22f,0.045f,0.045f)
            box(rx,0.31f+mY,mz+0.11f,0.035f,0.16f,0.045f,0.22f,0.045f,0.045f)
        }
        // Neck, skull, brow ridge, cheek plates and jaw.
        box(mx,1.16f+mY,mz+0.015f,0.20f,0.20f,0.19f,0.10f,0.012f,0.03f)
        box(mx,1.39f+mY,mz+0.035f,0.40f,0.39f,0.34f,0.105f,0.012f,0.028f)
        box(mx-0.15f,1.49f+mY,mz+0.205f,0.13f,0.075f,0.065f,0.055f,0.008f,0.018f)
        box(mx+0.15f,1.49f+mY,mz+0.205f,0.13f,0.075f,0.065f,0.055f,0.008f,0.018f)
        box(mx,1.27f+mY,mz+0.205f,0.24f,0.12f,0.09f,0.055f,0.008f,0.018f)
        // Glowing eyes, dark eye sockets, nose and a visible toothed mouth.
        box(mx-0.115f,1.405f+mY,mz+0.211f,0.13f,0.095f,0.035f,0.018f,0.002f,0.004f)
        box(mx+0.115f,1.405f+mY,mz+0.211f,0.13f,0.095f,0.035f,0.018f,0.002f,0.004f)
        box(mx-0.115f,1.408f+mY,mz+0.235f,0.062f,0.040f,0.018f,1f,0.012f,0.006f)
        box(mx+0.115f,1.408f+mY,mz+0.235f,0.062f,0.040f,0.018f,1f,0.012f,0.006f)
        box(mx,1.345f+mY,mz+0.239f,0.055f,0.075f,0.035f,0.045f,0.004f,0.012f)
        box(mx,1.292f+mY,mz+0.235f,0.19f,0.045f,0.035f,0.012f,0.002f,0.003f)
        for (i in 0..3) {
            box(mx-0.06f+i*0.04f,1.292f+mY,mz+0.258f,0.018f,0.025f,0.012f,0.82f,0.72f,0.56f)
        }
        // A spare battery pack is a small optional resource placed in the side corridor.
        if (!batteryPackCollected) {
            val batteryBob = 0.24f + kotlin.math.sin((System.nanoTime()/220000000.0).toFloat())*0.035f
            box(1.15f,batteryBob,-1.85f,0.28f,0.34f,0.18f,0.18f,0.72f,0.34f)
            box(1.15f,batteryBob+0.19f,-1.85f,0.12f,0.055f,0.10f,0.72f,0.82f,0.70f)
            box(1.15f,batteryBob,-1.745f,0.055f,0.18f,0.018f,0.78f,0.95f,0.72f)
        }
        for (i in keys.indices) if (!collected[i]) {
            val bob=0.28f+kotlin.math.sin((System.nanoTime()/250000000.0+i).toFloat())*0.04f
            box(keys[i][0],bob,keys[i][1],0.22f,0.22f,0.22f,1f,0.72f,0.08f)
        }
        // Exit gate and its frame; the door only opens after interaction with all keys.
        box(-1.0f,1f,-5.72f,0.18f,2.15f,0.22f,0.30f,0.22f,0.13f)
        box(1.0f,1f,-5.72f,0.18f,2.15f,0.22f,0.30f,0.22f,0.13f)
        box(0f,2.05f,-5.72f,2.15f,0.18f,0.22f,0.30f,0.22f,0.13f)
        // Exit gate: after unlocking, the heavy panel slides aside instead of vanishing.
        val gateX = if (exitDoorOpen) -2.05f else 0f
        if (exitDoorOpen) {
            box(0f,1.05f,-5.57f,1.7f,2.0f,0.06f,0.08f,0.42f,0.18f)
            box(0f,0.08f,-5.48f,1.45f,0.035f,0.035f,0.12f,0.65f,0.25f)
            box(-0.65f,1.25f,-5.48f,0.05f,1.25f,0.035f,0.22f,0.72f,0.32f)
            box(0.65f,1.25f,-5.48f,0.05f,1.25f,0.035f,0.22f,0.72f,0.32f)
        }
        box(gateX,1f,-5.68f,1.8f,1.95f,0.12f,if(keysCount>=3 && hasFuse) 0.12f else 0.24f,if(keysCount>=3 && hasFuse) 0.8f else 0.12f,0.18f)
        // Small status lamps on the gate frame make puzzle progress readable from a distance.
        val gateReady = keysCount >= 3 && hasFuse
        val lampPulse = if ((System.nanoTime()/350000000L)%2L==0L) 1f else 0.45f
        box(-0.78f,1.88f,-5.48f,0.12f,0.10f,0.035f,if(gateReady) 0.08f else lampPulse*0.8f,if(gateReady) 0.9f else 0.06f,0.04f)
        box(0.78f,1.88f,-5.48f,0.12f,0.10f,0.035f,if(gateReady) 0.08f else lampPulse*0.8f,if(gateReady) 0.9f else 0.06f,0.04f)
    }

    private fun update(dt: Float) {
        val yaw=Math.toRadians(cameraYaw.toDouble())
        val cy=kotlin.math.cos(yaw).toFloat(); val sy=kotlin.math.sin(yaw).toFloat()
        val moving=kotlin.math.abs(moveX)+kotlin.math.abs(moveZ)>0.08f
        val sprint=moving && stamina>1f && (sprintHeld || kotlin.math.abs(moveX)+kotlin.math.abs(moveZ)>1.25f)
        val speed=if(sprint) 3.25f else 2.3f
        stamina=if(sprint) (stamina-26f*dt).coerceAtLeast(0f) else (stamina+13f*dt).coerceAtMost(100f)
        val noise=if(sprint) 1f else if(moving) 0.45f else 0f
        val dx=(moveX*cy-moveZ*sy)*speed*dt; val dz=(moveZ*cy+moveX*sy)*speed*dt
        if(!hidden && !gameWon) {
            val nx=(px+dx).coerceIn(-7.55f,7.55f); val nz=(pz+dz).coerceIn(-5.72f,5.72f)
            val furnitureBlocked=(nx < -2.0f && nx > -3.55f && nz > -1.25f && nz < 0.45f) || (nx > 1.75f && nx < 3.25f && nz > 1.25f && nz < 2.75f) || (nx > -0.95f && nx < 0.95f && nz > -4.35f && nz < -3.25f)
            // Closed doors stop the player crossing the side-hall wall; open doors connect the rooms.
            val crossesLeftWall = (px > -4f && nx <= -4f) || (px < -4f && nx >= -4f)
            val crossesRightWall = (px < 4f && nx >= 4f) || (px > 4f && nx <= 4f)
            val doorZ = (nz > -2.75f && nz < -1.25f)
            val doorZ2 = (nz > 1.25f && nz < 2.75f)
            val blockedDoor = (crossesLeftWall && ((doorZ && !bedroomDoorOpen) || (doorZ2 && !storageDoorOpen) || (!doorZ && !doorZ2))) ||
                (crossesRightWall && ((doorZ && !kitchenDoorOpen) || (doorZ2 && !sideRoomDoorOpen) || (!doorZ && !doorZ2)))
            val outerFurniture = (nx < -5.25f && nx > -7.2f && nz > -4.8f && nz < -3.0f) || (nx > 5.2f && nx < 6.9f && nz > -4.8f && nz < -3.0f) || (nx > 5.0f && nx < 7.0f && nz > 3.0f && nz < 4.7f)
            if(!furnitureBlocked && !blockedDoor && !outerFurniture) { px=nx; pz=nz }
        }
        if(!hidden && !gameWon) {
            hideDiscoveryTimer = 0f
            val vx=px-mx; val vz=pz-mz; val d=sqrt(vx*vx+vz*vz).coerceAtLeast(0.001f)
            patrolClock+=dt
            // AI senses sprinting farther away than walking. A close player is spotted even when still.
            // Alpha 5: running is loud and gives away the player's position from farther away.
            // Walking is quieter, but can still be heard when the monster is nearby.
            val sprintNoise = moving && sprint && d < 10.5f
            val walkNoise = moving && !sprint && d < 3.4f
            val spotted = d < (if (visualQuality >= 2) 5.0f else 4.6f)
            if (spotted || sprintNoise || walkNoise) {
                lastKnownX=px; lastKnownZ=pz
                lastSeenTimer=if(spotted) 2.2f else 0.0f
                alertTimer=if(spotted) 2.8f else if(sprintNoise) 5.0f else 2.5f
                investigateTimer=0f
                searchTargetTimer=0f
                // The monster remembers the direction of the last sound and starts searching nearby corners.
                searchTargetX = (px + if (mx < px) 1.0f else -1.0f).coerceIn(-7.2f, 7.2f)
                searchTargetZ = (pz + if (mz < pz) 0.8f else -0.8f).coerceIn(-5.2f, 5.2f)
            } else {
                lastSeenTimer=(lastSeenTimer-dt).coerceAtLeast(0f)
                alertTimer=(alertTimer-dt).coerceAtLeast(0f)
            }
            if(alertTimer>0f && !hidden) {
                monsterState=if(lastSeenTimer>0f) "شافك وبيطاردك!" else "بيجري ناحية آخر صوت سمعه"
                val keyPressure = keysCount * 0.10f
                val chaseSpeed=(if(lastSeenTimer>0f) 1.52f else 1.27f) + keyPressure
                moveMonsterToward(lastKnownX,lastKnownZ,chaseSpeed,dt)
            } else {
                // Search several nearby points around the last known position instead of standing still.
                if(investigateTimer<8.0f) {
                    if(searchTargetTimer<=0f) {
                        val phase=searchPhase
                        searchTargetX=(lastKnownX+kotlin.math.cos(phase)*1.55f).coerceIn(-7.0f,7.0f)
                        searchTargetZ=(lastKnownZ+kotlin.math.sin(phase)*1.55f).coerceIn(-5.0f,5.0f)
                        searchPhase+=1.35f
                        searchTargetTimer=1.9f
                    }
                    val sx=searchTargetX-mx; val sz=searchTargetZ-mz
                    val sd=sqrt(sx*sx+sz*sz)
                    if(sd>0.28f) {
                        monsterState="بيفتش حوالين آخر مكان شافك فيه"
                        moveMonsterToward(searchTargetX,searchTargetZ,0.82f + keysCount*0.045f,dt)
                    } else {
                        searchTargetTimer=0f
                        investigateTimer+=dt
                        monsterState="بيفتش في المكان..."
                    }
                    searchTargetTimer-=dt
                } else {
                    // Small patrol loop with stable targets, rather than jittering in place.
                    if(kotlin.math.abs(mx-patrolTargetX)<0.45f && kotlin.math.abs(mz-patrolTargetZ)<0.45f || patrolClock>14f) {
                        patrolTargetX=when((patrolClock.toInt()/4)%4) { 0 -> -5.5f; 1 -> 5.5f; 2 -> 5.5f; else -> -5.5f }
                        patrolTargetZ=when((patrolClock.toInt()/4)%4) { 0 -> -3.7f; 1 -> -3.7f; 2 -> 3.7f; else -> 3.7f }
                    }
                    monsterState="بيتجول ويدور عليك"
                    moveMonsterToward(patrolTargetX,patrolTargetZ,0.48f,dt)
                }
            }
            // The monster can push open an interior door when actively chasing or investigating.
            // It only opens a door from the correct side of the partition, preventing random toggling.
            if (alertTimer > 0.15f || investigateTimer < 8.0f) {
                if (kotlin.math.abs(mx + 4f) < 0.48f) {
                    if (mz > -2.8f && mz < -1.2f) bedroomDoorOpen = true
                    if (mz > 1.2f && mz < 2.8f) storageDoorOpen = true
                }
                if (kotlin.math.abs(mx - 4f) < 0.48f) {
                    if (mz > -2.8f && mz < -1.2f) kitchenDoorOpen = true
                    if (mz > 1.2f && mz < 2.8f) sideRoomDoorOpen = true
                }
            }
            val cx=px-mx; val cz=pz-mz
            if(sqrt(cx*cx+cz*cz)<0.72f && !hidden) { gameOver=true; statusText="مسكتك العجوز! اضغط إعادة المحاولة"; monsterState="أمسكت بك" }
                } else if(hidden) {
            monsterState="بيدور عليك"
            // If the monster reaches the hiding area, it lingers and searches before leaving.
            val hx=px-mx; val hz=pz-mz
            if(hx*hx+hz*hz<3.4f) {
                investigateTimer=(investigateTimer+dt).coerceAtMost(6f)
                hideDiscoveryTimer += dt
                mx+=kotlin.math.sin(patrolClock*1.8f)*0.18f*dt
                mz+=kotlin.math.cos(patrolClock*1.6f)*0.18f*dt
                if(hideDiscoveryTimer >= (if (hidingPlace == "خلف الأثاث") 3.5f else 6.0f)) {
                    gameOver=true
                    statusText="العجوز اكتشفت مكان اختبائك! اضغط إعادة المحاولة"
                    monsterState="اكتشفت مخبأك!"
                }
            } else {
                hideDiscoveryTimer = (hideDiscoveryTimer - dt * 1.5f).coerceAtLeast(0f)
                mx+=(lastKnownX-mx)*dt*0.08f
                mz+=(lastKnownZ-mz)*dt*0.08f
            }
        }
        for(i in keys.indices) if(!collected[i]) { val kx=px-keys[i][0]; val kz=pz-keys[i][1]; if(kx*kx+kz*kz<0.55f) collected[i]=true }
        keysCount=collected.count { it }
        if(exitDoorOpen && pz < -5.15f && kotlin.math.abs(px)<1.15f) { gameWon=true; monsterState="نجوت!" }
        val nearHideSpotIndex = hideSpots.indices.firstOrNull { i -> val hx=px-hideSpots[i][0]; val hz=pz-hideSpots[i][1]; hx*hx+hz*hz<1.65f }
        val nearHideSpot = nearHideSpotIndex != null
        val hideLabel = when (nearHideSpotIndex) { 0 -> "تحت السرير"; 1 -> "داخل الدولاب"; 2 -> "خلف الأثاث"; else -> "" }
        val nearCabinet = (px+2.8f)*(px+2.8f)+(pz+0.4f)*(pz+0.4f)<2.2f
        val nearBatteryPack = !batteryPackCollected && (px-1.15f)*(px-1.15f)+(pz+1.85f)*(pz+1.85f)<1.45f
        // Recalculate the monster distance here: dangerDistance is local to the render frame.
        val warningDx = px - mx
        val warningDz = pz - mz
        val warningDistance = sqrt(warningDx * warningDx + warningDz * warningDz)
        val dangerWarning = if (!hidden && !gameWon && !gameOver && warningDistance < 2.8f) "⚠ العجوز قريبة جدًا!" else if (!hidden && !gameWon && !gameOver && warningDistance < 4.2f) "في حركة قريبة..." else ""
        val nearRoomDoor = kotlin.math.abs(kotlin.math.abs(px)-4f)<1.15f && (kotlin.math.abs(pz+2f)<1.25f || kotlin.math.abs(pz-2f)<1.25f)
        val mainStatus = when { gameOver -> "مسكتك العجوز! اضغط إعادة المحاولة"; gameWon -> "نجحت في الهروب!"; hidden -> "مختبئ $hidingPlace — العجوز بتفتش عنك"; nearCabinet && !drawerOpen -> "درج قريب • اضغط تفاعل للبحث"; nearCabinet && drawerOpen && !hasFuse -> "وجدت قطعة كهربائية! خذها بالتفاعل"; nearBatteryPack -> "بطارية احتياطية قريبة • اضغط تفاعل"; keysCount>=3 && hasFuse && pz < -3.8f -> "اضغط تفاعل لفتح بوابة الهروب"; keysCount>=3 && !hasFuse -> "المفاتيح كاملة! ابحث عن القطعة الكهربائية في الدرج"; keysCount>=3 && hasFuse -> "المفاتيح والقطعة جاهزان — اذهب إلى بوابة الهروب"; nearHideSpot -> "مكان اختباء قريب ($hideLabel) • اضغط اختباء"; nearRoomDoor -> "باب غرفة قريب • اضغط تفاعل للفتح أو الإغلاق"; px < -4.1f -> "غرفة النوم / المخزن • المفاتيح: $keysCount/3"; px > 4.1f -> "المطبخ / الغرفة الداخلية • المفاتيح: $keysCount/3"; else -> "المفاتيح: $keysCount/3 • $monsterState" }
        val batteryLabel = "البطارية: ${flashlightBattery.toInt()}%"
        statusText = listOf(mainStatus, dangerWarning, batteryLabel).filter { it.isNotBlank() }.joinToString(" • ")
    }

    fun interact() {
        if (gameOver || gameWon) return
        // Door interaction is proximity-based, so it works with the existing single interact button.
        if (kotlin.math.abs(kotlin.math.abs(px) - 4.0f) < 1.15f && kotlin.math.abs(pz + 2.0f) < 1.25f) {
            if (px < 0f) bedroomDoorOpen = !bedroomDoorOpen else kitchenDoorOpen = !kitchenDoorOpen
            statusText = if (px < 0f) (if (bedroomDoorOpen) "باب غرفة النوم اتفتح" else "باب غرفة النوم اتقفل") else (if (kitchenDoorOpen) "باب المطبخ اتفتح" else "باب المطبخ اتقفل")
            return
        }
        if (kotlin.math.abs(kotlin.math.abs(px) - 4.0f) < 1.15f && kotlin.math.abs(pz - 2.0f) < 1.25f) {
            if (keysCount < 1) { statusText = "الباب مقفول — محتاج مفتاح واحد"; return }
            if (px < 0f) storageDoorOpen = !storageDoorOpen else sideRoomDoorOpen = !sideRoomDoorOpen
            statusText = if (px < 0f) (if (storageDoorOpen) "باب المخزن اتفتح" else "باب المخزن اتقفل") else (if (sideRoomDoorOpen) "باب الغرفة الداخلية اتفتح" else "باب الغرفة الداخلية اتقفل")
            return
        }
        if (kotlin.math.abs(px - 6.0f) < 1.3f && kotlin.math.abs(pz) < 1.15f) {
            if (keysCount < 2) { statusText = "القفل الصدئ محتاج مفتاحين ($keysCount/2)"; return }
            sideRoomDoorOpen = !sideRoomDoorOpen
            statusText = if (sideRoomDoorOpen) "فتحت الباب الداخلي بين الغرف" else "قفلت الباب الداخلي"
            return
        }
        val batteryDistance=(px-1.15f)*(px-1.15f)+(pz+1.85f)*(pz+1.85f)
        if(!batteryPackCollected && batteryDistance<1.45f) {
            batteryPackCollected=true
            flashlightBattery=(flashlightBattery+42f).coerceAtMost(100f)
            statusText="جمعت بطارية احتياطية! شحن الكشاف ${flashlightBattery.toInt()}%"
            return
        }
        val cabinetDistance=(px+2.8f)*(px+2.8f)+(pz+0.4f)*(pz+0.4f)
        if(cabinetDistance<2.2f) {
            if(!drawerOpen) { drawerOpen=true; statusText="فتحت الدرج — ابحث عن الأداة"; return }
            if(!hasFuse) { hasFuse=true; statusText="جمعت القطعة الكهربائية!"; return }
        }
        val nearExit = pz < -3.8f && kotlin.math.abs(px) < 1.6f
        if (!nearExit) { statusText = "اقترب من الباب أو ابحث عن المفاتيح"; return }
        if (keysCount < 3) { statusText = "الباب مقفول — تحتاج 3 مفاتيح ($keysCount/3)"; return }
        if (!hasFuse) { statusText = "لوحة الباب بلا كهرباء — ابحث عن القطعة الكهربائية في الدرج"; return }
        exitDoorOpen = true
        statusText = "انفتح الباب! اركض للخارج بسرعة"
    }

    fun setSprinting(on: Boolean) { sprintHeld = on }
    @Volatile private var sprintHeld = false

    fun toggleHide() {
        if(hidden) { hidden=false; hidingPlace=""; hideDiscoveryTimer=0f; statusText="خرجت من مكان الاختباء"; return }
        val idx=hideSpots.indices.firstOrNull { i -> val dx=px-hideSpots[i][0]; val dz=pz-hideSpots[i][1]; dx*dx+dz*dz<1.65f }
        if(idx != null) {
            hidden=true
            hidingPlace=when(idx) { 0 -> "تحت السرير"; 1 -> "داخل الدولاب"; else -> "خلف الأثاث" }
            statusText="اختبأت $hidingPlace — خليك ساكت!"
        } else statusText="لازم تقرب من السرير أو الدولاب عشان تختبئ"
    }

    fun restartGame() {
        px=0f; pz=2.8f; mx=0f; mz=-3.2f
        for(i in collected.indices) collected[i]=false
        hidden=false; hidingPlace=""; hideDiscoveryTimer=0f; flashlightOn=true; flashlightBattery=100f; stamina=100f; keysCount=0; gameWon=false; exitDoorOpen=false; drawerOpen=false; hasFuse=false; batteryPackCollected=false; bedroomDoorOpen=false; kitchenDoorOpen=false; storageDoorOpen=false; sideRoomDoorOpen=false; gameOver=false; alertTimer=0f; investigateTimer=0f; lastKnownX=0f; lastKnownZ=0f; searchPhase=0f; searchTargetTimer=0f; searchTargetX=0f; searchTargetZ=0f; lastSeenTimer=0f; patrolTargetX=-5.5f; patrolTargetZ=0f; patrolClock=0f; sprintHeld=false
        statusText="اجمع المفاتيح والقطعة الكهربائية وابحث عن بطارية احتياطية — خلي بالك من صوت خطواتك"
    }

    /** Moves the monster in small axis-separated steps so furniture and closed room doors are less likely to trap it. */
    private fun moveMonsterToward(tx: Float, tz: Float, speed: Float, dt: Float) {
        val dx=tx-mx; val dz=tz-mz
        val d=sqrt(dx*dx+dz*dz).coerceAtLeast(0.001f)
        val step=(speed*dt).coerceAtMost(d)
        val nx=(mx+dx/d*step).coerceIn(-7.25f,7.25f)
        val nz=(mz+dz/d*step).coerceIn(-5.35f,5.35f)
        fun blocked(x:Float,z:Float):Boolean {
            val furniture=(x < -2.0f && x > -3.55f && z > -1.25f && z < 0.45f) ||
                (x > 1.75f && x < 3.25f && z > 1.25f && z < 2.75f) ||
                (x > -0.95f && x < 0.95f && z > -4.35f && z < -3.25f)
            val doorBlock=(kotlin.math.abs(x+4f)<0.22f && ((z>-2.75f && z< -1.25f && !bedroomDoorOpen) || (z>1.25f && z<2.75f && !storageDoorOpen))) ||
                (kotlin.math.abs(x-4f)<0.22f && ((z>-2.75f && z< -1.25f && !kitchenDoorOpen) || (z>1.25f && z<2.75f && !sideRoomDoorOpen)))
            return furniture || doorBlock
        }
        // Try the direct path first, then slide along one axis to escape corners.
        if(!blocked(nx,nz)) { mx=nx; mz=nz }
        else if(!blocked(nx,mz)) mx=nx
        else if(!blocked(mx,nz)) mz=nz
        else {
            val sideX=(mx+(-dz/d)*step).coerceIn(-7.25f,7.25f)
            val sideZ=(mz+(dx/d)*step).coerceIn(-5.35f,5.35f)
            if(!blocked(sideX,mz)) mx=sideX else if(!blocked(mx,sideZ)) mz=sideZ
        }
    }

    private fun box(x:Float,y:Float,z:Float,sx:Float,sy:Float,sz:Float,r:Float,g:Float,b:Float) {
        Matrix.setIdentityM(model,0); Matrix.translateM(model,0,x,y,z); Matrix.scaleM(model,0,sx,sy,sz)
        Matrix.multiplyMM(mvp,0,vp,0,model,0)
        val mv = FloatArray(16)
        Matrix.multiplyMM(mv,0,view,0,model,0)
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(mvpHandle,1,false,mvp,0)
        GLES20.glUniformMatrix4fv(mvHandle,1,false,mv,0)
        GLES20.glUniformMatrix4fv(modelHandle,1,false,model,0)
        val yaw = Math.toRadians(cameraYaw.toDouble())
        val flicker = (flashlightBattery < 22f && (System.nanoTime() / 85000000L) % 5L == 0L) ||
            (sqrt((px-mx)*(px-mx) + (pz-mz)*(pz-mz)) < 2.7f && (System.nanoTime() / 115000000L) % 4L == 0L)
        val beamEnabled = flashlightOn && !hidden && !flicker && flashlightBattery > 0f
        GLES20.glUniform3f(flashPosHandle, px, if (hidden && hidingPlace == "تحت السرير") 0.48f else if (hidden) 0.92f else 1.48f, pz)
        GLES20.glUniform3f(flashDirHandle, kotlin.math.sin(yaw).toFloat(), -0.10f, -kotlin.math.cos(yaw).toFloat())
        GLES20.glUniform1f(flashOnHandle, if (beamEnabled) 1f else 0f)
        val batteryPower = if (flashlightBattery < 12f) 0.40f else if (flashlightBattery < 28f) 0.62f else 1f
        GLES20.glUniform1f(flashPowerHandle, batteryPower)
        val lampFlicker = if ((System.nanoTime() / 115000000L) % 37L == 0L) 0.35f else 1.0f
        GLES20.glUniform3f(lampPos0Handle, 0f, 2.08f, -4f)
        GLES20.glUniform3f(lampPos1Handle, 0f, 2.08f, 0f)
        GLES20.glUniform3f(lampPos2Handle, 0f, 2.08f, 4f)
        GLES20.glUniform1f(lampStrengthHandle, if (visualQuality >= 2) lampFlicker else 0.45f)
        GLES20.glUniform4f(colorHandle,r,g,b,1f)
        GLES20.glUniform1f(fogHandle, if (visualQuality >= 2) 1.0f else if (visualQuality == 1) 0.62f else 0.0f)
        GLES20.glUniform1f(timeHandle, (System.nanoTime() / 1.0e9).toFloat())
        GLES20.glUniform2f(viewportHandle, viewportWidth, viewportHeight)
        val material = when {
            r > g * 1.23f && r > b * 1.18f -> 1 // worn timber
            r < 0.17f && b > r * 1.18f -> 2 // dirty stone floor
            else -> 0 // aged plaster
        }
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureIds.getOrElse(material) { 0 })
        GLES20.glUniform1i(textureHandle, 0)
        GLES20.glUniform1i(useTextureHandle, if (textureIds.getOrElse(material) { 0 } != 0) 1 else 0)
        vertices.position(0); GLES20.glEnableVertexAttribArray(posHandle)
        GLES20.glVertexAttribPointer(posHandle,3,GLES20.GL_FLOAT,false,0,vertices)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,36)
        GLES20.glDisableVertexAttribArray(posHandle)
    }
}
