package com.shadowhall.game

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.opengl.GLSurfaceView
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.TextView
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.media.AudioManager
import android.media.ToneGenerator
import android.media.SoundPool

class MainActivity : Activity() {
    private lateinit var game: HallSurface
    private lateinit var hud: TextView
    private lateinit var status: TextView
    private lateinit var tone: ToneGenerator
    private lateinit var sounds: SoundPool
    private var footstepSound = 0
    private var doorSound = 0
    private var chaseSound = 0
    private var lastFootstepAt = 0L
    private var lastFootstepSprint = false
    private val handler = Handler(Looper.getMainLooper())
    private var lastDangerLevel = 0
    private var lastDangerBeepAt = 0L
    private var lastChaseCueAt = 0L
    private var soundVolume = 0.75f
    private val updater = object : Runnable {
        override fun run() {
            if (::game.isInitialized && !isFinishing) {
                val currentStatus = game.renderer.statusText
                status.text = currentStatus
                status.setTextColor(when {
                    currentStatus.contains("مسكتك") || currentStatus.contains("اكتشفت مخبأك") -> 0xFFFF6666.toInt()
                    currentStatus.contains("نجحت") -> 0xFF80FF9A.toInt()
                    currentStatus.contains("مفتاح") || currentStatus.contains("محتاج مفتاح") -> 0xFFFFD56A.toInt()
                    else -> Color.WHITE
                })
                val now = android.os.SystemClock.elapsedRealtime()
                val dangerLevel = when {
                    currentStatus.contains("العجوز قريبة جدًا") -> 2
                    currentStatus.contains("في حركة قريبة") -> 1
                    else -> 0
                }
                // One immediate cue when entering danger, then a restrained pulse while very close.
                if (dangerLevel > 0 && (dangerLevel != lastDangerLevel ||
                        (dangerLevel == 2 && now - lastDangerBeepAt > 1800L))) {
                    tone.startTone(if (dangerLevel == 2) ToneGenerator.TONE_PROP_BEEP2 else ToneGenerator.TONE_PROP_BEEP, if (dangerLevel == 2) 120 else 65)
                    lastDangerBeepAt = now
                }
                // Procedural-style recorded WAV cues: footsteps while moving and a creak on interaction.
                val moving = kotlin.math.abs(game.renderer.moveX) + kotlin.math.abs(game.renderer.moveZ) > 0.12f && !game.renderer.hidden
                val sprinting = moving && (kotlin.math.abs(game.renderer.moveX) + kotlin.math.abs(game.renderer.moveZ) > 1.25f)
                val stepInterval = if (sprinting) 310L else 510L
                if (moving && now - lastFootstepAt >= stepInterval && footstepSound != 0) {
                    sounds.play(footstepSound, (if (sprinting) 0.82f else 0.48f) * soundVolume, (if (sprinting) 0.82f else 0.48f) * soundVolume, 1, 0, if (sprinting) 1.08f else 0.88f)
                    lastFootstepAt = now
                    lastFootstepSprint = sprinting
                }
                // A separate short cue when the monster starts a direct chase.
                val chasing = game.renderer.monsterState.contains("بيطاردك")
                if (chasing && now - lastChaseCueAt > 3500L) {
                    if (chaseSound != 0) {
                        // Lightweight stereo pan: position the chase sting toward the monster's side.
                        val dx = game.renderer.monsterWorldX - game.renderer.playerWorldX
                        val dz = game.renderer.monsterWorldZ - game.renderer.playerWorldZ
                        val yaw = Math.toRadians(game.renderer.cameraYaw.toDouble())
                        val side = (dx * kotlin.math.cos(yaw).toFloat() - dz * kotlin.math.sin(yaw).toFloat()).coerceIn(-8f, 8f) / 8f
                        val left = (0.7f * soundVolume * (1f - side * 0.55f)).coerceIn(0f, 1f)
                        val right = (0.7f * soundVolume * (1f + side * 0.55f)).coerceIn(0f, 1f)
                        sounds.play(chaseSound, left, right, 1, 0, 0.9f)
                    }
                    else tone.startTone(ToneGenerator.TONE_PROP_NACK, 100)
                    lastChaseCueAt = now
                }
                lastDangerLevel = dangerLevel
                handler.postDelayed(this, 200)
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tone = ToneGenerator(AudioManager.STREAM_MUSIC, 55)
        sounds = SoundPool.Builder().setMaxStreams(4).build()
        footstepSound = sounds.load(this, R.raw.footstep, 1)
        doorSound = sounds.load(this, R.raw.door_creak, 1)
        chaseSound = sounds.load(this, R.raw.chase_sting, 1)
        val root = FrameLayout(this)
        game = HallSurface(this)
        root.addView(game, FrameLayout.LayoutParams(-1, -1))
        hud = TextView(this).apply {
            text = "SHADOW HALL: الهروب من العجوز\nحرّك بإصبعك على اليسار، ولفّ الكاميرا بإصبع آخر على اليمين\nاضغط باستمرار على (جري) لتجري وتغيّر اتجاهك\nAlpha 11: بحث أذكى، منظور منخفض أثناء الاختباء، وتفاصيل إضافية للبيت"
            setTextColor(Color.WHITE); textSize = 13f; typeface = Typeface.DEFAULT_BOLD
            setBackgroundColor(0x990A0D18.toInt()); setPadding(18, 12, 18, 12)
        }
        root.addView(hud, FrameLayout.LayoutParams(-1, -2, Gravity.TOP or Gravity.START))
        status = TextView(this).apply { text = "اجمع 3 مفاتيح وافتح باب الهروب"; setTextColor(0xFFFFD56A.toInt()); textSize = 14f; setPadding(18, 8, 18, 8); setBackgroundColor(0x990A0D18.toInt()) }
        root.addView(status, FrameLayout.LayoutParams(-1, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = 88 })
        val hide = TextView(this).apply {
            text = "اختباء"; textSize = 16f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setTextColor(Color.WHITE); setBackgroundColor(0xCC6D2635.toInt()); setPadding(28, 18, 28, 18)
            setOnClickListener {
                val wasHidden = game.renderer.hidden
                game.renderer.toggleHide()
                if (game.renderer.hidden != wasHidden) tone.startTone(ToneGenerator.TONE_PROP_BEEP, 90)
                else tone.startTone(ToneGenerator.TONE_PROP_NACK, 100)
                text = if (game.renderer.hidden) "اخرج" else "اختباء"
            }
        }
        root.addView(hide, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply { setMargins(0,0,28,22) })
        val light = TextView(this).apply {
            text = "كشاف"; textSize = 14f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setTextColor(Color.WHITE); setBackgroundColor(0xCC31546B.toInt()); setPadding(20, 18, 20, 18)
            setOnClickListener { game.renderer.flashlightOn = !game.renderer.flashlightOn; text = if (game.renderer.flashlightOn) "كشاف" else "الكشاف مغلق" }
        }
        root.addView(light, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START).apply { setMargins(20,0,0,22) })
        val sprint = TextView(this).apply {
            text = "جري"; textSize = 15f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setTextColor(Color.WHITE); setBackgroundColor(0xCC7A421B.toInt()); setPadding(24, 18, 24, 18)
            setOnTouchListener { _, event ->
                val pressed = event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE
                game.renderer.setSprinting(pressed)
                if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) game.renderer.setSprinting(false)
                true
            }
        }
        root.addView(sprint, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = 22 })
        val settings = TextView(this).apply {
            text = "الإعدادات"; textSize = 13f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setTextColor(Color.WHITE); setBackgroundColor(0xCC39404D.toInt()); setPadding(18, 12, 18, 12)
            setOnClickListener { showGameSettings() }
        }
        root.addView(settings, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply { topMargin = 198; rightMargin = 16 })
        val restart = TextView(this).apply {
            text = "إعادة المحاولة"; textSize = 13f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setTextColor(Color.WHITE); setBackgroundColor(0xCC39404D.toInt()); setPadding(18, 12, 18, 12)
            setOnClickListener { game.renderer.restartGame(); hide.text = "اختباء" }
        }
        root.addView(restart, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply { topMargin = 145; rightMargin = 16 })
        val interact = TextView(this).apply {
            text = "تفاعل"; textSize = 16f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setTextColor(Color.WHITE); setBackgroundColor(0xCC53613A.toInt()); setPadding(24, 18, 24, 18)
            setOnClickListener { game.renderer.interact(); if (doorSound != 0) sounds.play(doorSound, 0.72f, 0.72f, 1, 0, 0.82f) else tone.startTone(ToneGenerator.TONE_PROP_BEEP, 80) }
        }
        root.addView(interact, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply { setMargins(0,0,28,100) })
        setContentView(root)
        handler.post(updater)
    }
    private fun showGameSettings() {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 12, 28, 8)
        }
        panel.addView(TextView(this).apply { text = "حساسية الكاميرا"; textSize = 16f })
        val sensitivity = SeekBar(this).apply {
            max = 300
            progress = (game.renderer.cameraSensitivity - 80f).toInt().coerceIn(0, 300)
        }
        panel.addView(sensitivity)
        panel.addView(TextView(this).apply { text = "جودة الرسوم"; textSize = 16f; setPadding(0, 14, 0, 4) })
        val quality = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("أداء أعلى", "متوازن", "تفاصيل أكثر"))
            setSelection(game.renderer.visualQuality.coerceIn(0, 2))
        }
        panel.addView(quality)
        panel.addView(TextView(this).apply { text = "مستوى الصوت"; textSize = 16f; setPadding(0, 14, 0, 4) })
        val volume = SeekBar(this).apply { max = 100; progress = (soundVolume * 100).toInt() }
        panel.addView(volume)
        AlertDialog.Builder(this)
            .setTitle("إعدادات Shadow Hall")
            .setView(panel)
            .setPositiveButton("حفظ") { _, _ ->
                game.renderer.cameraSensitivity = 80f + sensitivity.progress
                game.renderer.visualQuality = quality.selectedItemPosition.coerceIn(0, 2)
                soundVolume = volume.progress / 100f
                status.text = "تم حفظ الإعدادات"
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    override fun onPause() { handler.removeCallbacks(updater); super.onPause(); game.onPause() }
    override fun onResume() { super.onResume(); game.onResume(); handler.post(updater) }
    override fun onDestroy() { handler.removeCallbacks(updater); if (::tone.isInitialized) tone.release(); if (::sounds.isInitialized) sounds.release(); super.onDestroy() }
}

class HallSurface(activity: Activity) : GLSurfaceView(activity) {
    val renderer = HallRenderer()
    private enum class TouchRole { MOVE, LOOK }
    private data class TouchTrack(
        val role: TouchRole,
        val downX: Float,
        val downY: Float,
        var lastX: Float,
        var lastY: Float
    )
    private val touches = mutableMapOf<Int, TouchTrack>()

    init {
        renderer.activityAssets = activity.assets
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
        isFocusable = true
    }

    private fun beginPointer(e: MotionEvent, index: Int) {
        val id = e.getPointerId(index)
        val x = e.getX(index)
        val y = e.getY(index)
        val role = if (x < width * 0.48f) TouchRole.MOVE else TouchRole.LOOK
        touches[id] = TouchTrack(role, x, y, x, y)
    }

    private fun endPointer(id: Int) {
        touches.remove(id)
        updateMovement()
    }

    private fun updateMovement() {
        val move = touches.values.firstOrNull { it.role == TouchRole.MOVE }
        if (move == null) {
            renderer.moveX = 0f
            renderer.moveZ = 0f
        } else {
            val w = width.toFloat().coerceAtLeast(1f)
            val h = height.toFloat().coerceAtLeast(1f)
            renderer.moveX = ((move.lastX - move.downX) / (w * 0.18f)).coerceIn(-1f, 1f)
            renderer.moveZ = ((move.lastY - move.downY) / (h * 0.18f)).coerceIn(-1f, 1f)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> beginPointer(e, e.actionIndex)
            MotionEvent.ACTION_POINTER_DOWN -> beginPointer(e, e.actionIndex)
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) {
                    val id = e.getPointerId(i)
                    val track = touches[id] ?: continue
                    val x = e.getX(i)
                    val y = e.getY(i)
                    if (track.role == TouchRole.LOOK) {
                        val dx = x - track.lastX
                        renderer.cameraYaw += (dx / width.toFloat().coerceAtLeast(1f)) * renderer.cameraSensitivity
                    }
                    track.lastX = x
                    track.lastY = y
                }
                updateMovement()
            }
            MotionEvent.ACTION_POINTER_UP -> endPointer(e.getPointerId(e.actionIndex))
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                touches.clear()
                renderer.moveX = 0f
                renderer.moveZ = 0f
            }
        }
        return true
    }
}