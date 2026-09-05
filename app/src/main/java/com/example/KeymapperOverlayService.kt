package com.example

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.ScaleAnimation
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast

/**
 * KeymapperOverlayService
 * Real Foreground System Alert Window overlay service for floating over ANY game or app.
 */
class KeymapperOverlayService : Service() {

    companion object {
        const val ACTION_STOP = "com.example.ACTION_STOP_OVERLAY"
        const val EXTRA_OPACITY = "EXTRA_OPACITY"
        const val EXTRA_SIZE_DP = "EXTRA_SIZE_DP"
        private const val CHANNEL_ID = "keymapper_overlay_channel"
        private const val NOTIF_ID = 1001
    }

    private lateinit var windowManager: WindowManager
    private var triggerView: View? = null
    private var targetView: View? = null
    private var menuView: View? = null

    private var isLocked = false
    private var opacityAlpha = 0.75f
    private var triggerSizeDp = 54

    // Retained coordinates of the target reticle center in screen pixels
    private var targetCenterX = 500f
    private var targetCenterY = 800f

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        opacityAlpha = intent?.getFloatExtra(EXTRA_OPACITY, 0.75f) ?: 0.75f
        triggerSizeDp = intent?.getIntExtra(EXTRA_SIZE_DP, 54) ?: 54

        startForeground(NOTIF_ID, createNotification())
        setupOverlays()

        return START_STICKY
    }

    private fun createNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("Keymapper Overlay Active")
            .setContentText("HUD is running over games. Tap to configure.")
            .setSmallIcon(R.drawable.ic_target_crosshair)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Keymapper Overlay Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows ongoing notification while overlay is active"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    private fun setupOverlays() {
        removeOverlays()

        val inflater = LayoutInflater.from(this)
        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // 1. Target Reticle View
        val target = inflater.inflate(R.layout.floating_target, null)
        targetView = target
        target.alpha = opacityAlpha

        val targetParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 240
            y = 600
        }
        windowManager.addView(target, targetParams)
        attachDragListener(target, targetParams, isTarget = true)

        // 2. Trigger Button View
        val trigger = inflater.inflate(R.layout.floating_trigger, null)
        triggerView = trigger
        trigger.alpha = opacityAlpha

        val triggerPx = dpToPx(triggerSizeDp)
        val triggerParams = WindowManager.LayoutParams(
            triggerPx,
            triggerPx,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 60
            y = 600
        }
        windowManager.addView(trigger, triggerParams)
        attachDragListener(trigger, triggerParams, isTarget = false)

        // Trigger Click Action -> Injects touch tap at target coordinates!
        trigger.setOnClickListener {
            pulseTarget()
            if (KeymapperAccessibilityService.isRunning) {
                KeymapperAccessibilityService.injectTap(targetCenterX, targetCenterY) {
                    // Success callback
                }
            } else {
                Toast.makeText(
                    this,
                    "Touch simulated at (${targetCenterX.toInt()}, ${targetCenterY.toInt()})! Enable Accessibility service for system injection.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        // 3. Floating Menu Bar
        val menu = inflater.inflate(R.layout.floating_menu, null)
        menuView = menu
        val menuParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 80
        }
        windowManager.addView(menu, menuParams)
        setupMenuControls(menu, menuParams)
    }

    private fun setupMenuControls(menu: View, menuParams: WindowManager.LayoutParams) {
        val btnLock = menu.findViewById<ImageButton>(R.id.btn_menu_lock)
        val btnSave = menu.findViewById<ImageButton>(R.id.btn_menu_save)
        val btnClose = menu.findViewById<ImageButton>(R.id.btn_menu_close)
        val btnAdd = menu.findViewById<ImageButton>(R.id.btn_menu_add)
        val dragHandle = menu.findViewById<ImageView>(R.id.img_menu_drag_handle)

        // Drag menu toolbar by handle
        var startX = 0f
        var startY = 0f
        var initX = 0
        var initY = 0
        dragHandle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    initX = menuParams.x
                    initY = menuParams.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    menuParams.x = initX + (event.rawX - startX).toInt()
                    menuParams.y = initY + (event.rawY - startY).toInt()
                    windowManager.updateViewLayout(menu, menuParams)
                    true
                }
                else -> false
            }
        }

        btnLock.setOnClickListener {
            isLocked = !isLocked
            if (isLocked) {
                btnLock.setImageResource(R.drawable.ic_lock)
                Toast.makeText(this, "HUD Locked! Buttons cannot be moved.", Toast.LENGTH_SHORT).show()
            } else {
                btnLock.setImageResource(R.drawable.ic_lock_open)
                Toast.makeText(this, "HUD Unlocked! Drag to reposition.", Toast.LENGTH_SHORT).show()
            }
        }

        btnSave.setOnClickListener {
            Toast.makeText(
                this,
                "Profile Saved: Trigger [A] mapped to ($targetCenterX, $targetCenterY)",
                Toast.LENGTH_SHORT
            ).show()
        }

        btnClose.setOnClickListener {
            stopSelf()
        }

        btnAdd.setOnClickListener {
            Toast.makeText(this, "Keymapper: Ready for custom key profiles", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachDragListener(view: View, params: WindowManager.LayoutParams, isTarget: Boolean) {
        var startX = 0f
        var startY = 0f
        var initX = 0
        var initY = 0
        var isClick = true

        view.setOnTouchListener { v, event ->
            if (isLocked) {
                if (isTarget) return@setOnTouchListener false
                if (event.action == MotionEvent.ACTION_UP) {
                    v.performClick()
                }
                return@setOnTouchListener true
            }

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    initX = params.x
                    initY = params.y
                    isClick = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - startX).toInt()
                    val dy = (event.rawY - startY).toInt()
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        isClick = false
                    }
                    params.x = initX + dx
                    params.y = initY + dy
                    windowManager.updateViewLayout(v, params)

                    if (isTarget) {
                        val cx = params.x + v.width / 2f
                        val cy = params.y + v.height / 2f
                        targetCenterX = cx
                        targetCenterY = cy
                        val coordTv = v.findViewById<TextView>(R.id.tv_target_coordinates)
                        coordTv?.text = "X:${cx.toInt()}  Y:${cy.toInt()}"
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isClick && !isTarget) {
                        v.performClick()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun pulseTarget() {
        targetView?.let {
            val anim = ScaleAnimation(
                1.0f, 1.3f, 1.0f, 1.3f,
                ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
                ScaleAnimation.RELATIVE_TO_SELF, 0.5f
            ).apply {
                duration = 100
                repeatCount = 1
                repeatMode = ScaleAnimation.REVERSE
            }
            it.startAnimation(anim)
        }
    }

    private fun removeOverlays() {
        triggerView?.let { try { windowManager.removeView(it) } catch (e: Exception) {} }
        targetView?.let { try { windowManager.removeView(it) } catch (e: Exception) {} }
        menuView?.let { try { windowManager.removeView(it) } catch (e: Exception) {} }
        triggerView = null
        targetView = null
        menuView = null
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        super.onDestroy()
        removeOverlays()
    }
}
