package com.example

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.ScaleAnimation
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {

    private var isOverlayRunning = false
    private var isLocked = false
    private var defaultTriggerSizeDp = 54
    private var defaultOpacityPercent = 75

    // References to dashboard UI
    private lateinit var tvOverlayStatus: TextView
    private lateinit var tvTouchStatus: TextView
    private lateinit var btnGrantOverlay: Button
    private lateinit var btnEnableAccessibility: Button
    private lateinit var btnToggleOverlay: Button
    private lateinit var sliderButtonSize: SeekBar
    private lateinit var sliderOpacity: SeekBar
    private lateinit var tvValueButtonSize: TextView
    private lateinit var tvValueOpacity: TextView

    // Interactive In-App Simulator View Elements
    private var overlayContainer: FrameLayout? = null
    private var floatingTriggerView: View? = null
    private var floatingTargetView: View? = null
    private var floatingMenuView: View? = null

    // Target coordinates
    private var targetCoordX = 180f
    private var targetCoordY = 380f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.main)

        initViews()
        setupListeners()
        updatePermissionBadges()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionBadges()
    }

    private fun initViews() {
        tvOverlayStatus = findViewById(R.id.badge_overlay_status)
        tvTouchStatus = findViewById(R.id.badge_touch_status)
        btnGrantOverlay = findViewById(R.id.btn_grant_overlay)
        btnEnableAccessibility = findViewById(R.id.btn_enable_accessibility)
        btnToggleOverlay = findViewById(R.id.btn_toggle_overlay)
        sliderButtonSize = findViewById(R.id.slider_button_size)
        sliderOpacity = findViewById(R.id.slider_opacity)
        tvValueButtonSize = findViewById(R.id.tv_value_button_size)
        tvValueOpacity = findViewById(R.id.tv_value_opacity)
    }

    private fun setupListeners() {
        // Overlay Permission Button
        btnGrantOverlay.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
                Toast.makeText(this, "Enable 'Display over other apps' for Keymapper", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Overlay permission is already ACTIVE!", Toast.LENGTH_SHORT).show()
                updatePermissionBadges()
            }
        }

        // Accessibility Service Button
        btnEnableAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "Turn on 'Keymapper' in Accessibility Settings", Toast.LENGTH_LONG).show()
        }

        // Start / Stop Keymapper Overlay
        btnToggleOverlay.setOnClickListener {
            if (isOverlayRunning) {
                stopKeymapperOverlay()
            } else {
                startKeymapperOverlay()
            }
        }

        // Size Slider (36dp to 96dp)
        sliderButtonSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val size = 36 + ((progress / 100f) * 60).toInt()
                defaultTriggerSizeDp = size
                tvValueButtonSize.text = "$size dp"
                updateTriggerSize(size)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Opacity Slider (20% to 100%)
        sliderOpacity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val alphaPercent = (20 + (progress * 0.8f)).toInt()
                defaultOpacityPercent = alphaPercent
                tvValueOpacity.text = "$alphaPercent%"
                val alphaFloat = alphaPercent / 100f
                floatingTriggerView?.alpha = alphaFloat
                floatingTargetView?.alpha = alphaFloat
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun updatePermissionBadges() {
        val hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }

        if (hasOverlay) {
            tvOverlayStatus.text = "ACTIVE"
            tvOverlayStatus.setBackgroundResource(R.drawable.bg_status_badge_active)
            tvOverlayStatus.setTextColor(resources.getColor(R.color.neon_green, theme))
        } else {
            tvOverlayStatus.text = "INACTIVE"
            tvOverlayStatus.setBackgroundResource(R.drawable.bg_status_badge_inactive)
            tvOverlayStatus.setTextColor(resources.getColor(R.color.neon_red, theme))
        }

        val a11yActive = KeymapperAccessibilityService.isRunning
        if (a11yActive) {
            tvTouchStatus.text = "ACTIVE"
            tvTouchStatus.setBackgroundResource(R.drawable.bg_status_badge_active)
            tvTouchStatus.setTextColor(resources.getColor(R.color.neon_green, theme))
        } else {
            tvTouchStatus.text = "INACTIVE"
            tvTouchStatus.setBackgroundResource(R.drawable.bg_status_badge_inactive)
            tvTouchStatus.setTextColor(resources.getColor(R.color.neon_red, theme))
        }
    }

    private fun startKeymapperOverlay() {
        val hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }

        isOverlayRunning = true
        btnToggleOverlay.text = "🛑 STOP KEYMAPPER OVERLAY"
        btnToggleOverlay.setBackgroundResource(R.drawable.bg_btn_toggle_running)
        btnToggleOverlay.setTextColor(Color.WHITE)

        // If system overlay permission is granted, also launch the real background system service!
        if (hasOverlay) {
            val serviceIntent = Intent(this, KeymapperOverlayService::class.java).apply {
                putExtra(KeymapperOverlayService.EXTRA_OPACITY, defaultOpacityPercent / 100f)
                putExtra(KeymapperOverlayService.EXTRA_SIZE_DP, defaultTriggerSizeDp)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        }

        // Always show live interactive simulator directly in the activity for immediate testing
        showInAppOverlayPreview()
        Toast.makeText(this, "HUD is ACTIVE! Drag trigger or target freely.", Toast.LENGTH_SHORT).show()
    }

    private fun stopKeymapperOverlay() {
        isOverlayRunning = false
        btnToggleOverlay.text = "⚡ START KEYMAPPER OVERLAY"
        btnToggleOverlay.setBackgroundResource(R.drawable.bg_btn_accent_toggle)
        btnToggleOverlay.setTextColor(Color.parseColor("#050B14"))

        // Stop background service if running
        stopService(Intent(this, KeymapperOverlayService::class.java))
        removeInAppOverlayPreview()
        Toast.makeText(this, "Keymapper HUD Stopped", Toast.LENGTH_SHORT).show()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showInAppOverlayPreview() {
        removeInAppOverlayPreview()

        val decorView = window.decorView as ViewGroup
        val container = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        overlayContainer = container

        val inflater = LayoutInflater.from(this)

        // 1. Floating Target Marker (floating_target.xml)
        val targetView = inflater.inflate(R.layout.floating_target, container, false)
        floatingTargetView = targetView
        val targetParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            leftMargin = dpToPx(190)
            topMargin = dpToPx(400)
        }
        container.addView(targetView, targetParams)
        makeDraggable(targetView, isTarget = true)

        // 2. Floating Trigger Button (floating_trigger.xml)
        val triggerView = inflater.inflate(R.layout.floating_trigger, container, false)
        floatingTriggerView = triggerView
        val triggerPx = dpToPx(defaultTriggerSizeDp)
        val triggerParams = FrameLayout.LayoutParams(triggerPx, triggerPx).apply {
            leftMargin = dpToPx(36)
            topMargin = dpToPx(410)
        }
        container.addView(triggerView, triggerParams)
        makeDraggable(triggerView, isTarget = false)

        // Trigger Click Action -> Pulses target and executes real tap or simulation
        val triggerSurface = triggerView.findViewById<View>(R.id.btn_trigger_surface) ?: triggerView
        triggerSurface.setOnClickListener {
            pulseView(targetView)
            
            // Execute tap injection via Accessibility Service if active
            if (KeymapperAccessibilityService.isRunning) {
                KeymapperAccessibilityService.injectTap(targetCoordX, targetCoordY)
                Toast.makeText(this, "Tap injected at (${targetCoordX.toInt()}, ${targetCoordY.toInt()}) via Accessibility!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Action triggered: [A] fired -> Target (${targetCoordX.toInt()}, ${targetCoordY.toInt()})", Toast.LENGTH_SHORT).show()
            }
        }

        // 3. Floating Menu Toolbar (floating_menu.xml)
        val menuView = inflater.inflate(R.layout.floating_menu, container, false)
        floatingMenuView = menuView
        val menuParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = dpToPx(60)
        }
        container.addView(menuView, menuParams)
        setupMenuControls(menuView)

        // Set opacity
        val alpha = defaultOpacityPercent / 100f
        triggerView.alpha = alpha
        targetView.alpha = alpha

        decorView.addView(container)
    }

    private fun setupMenuControls(menuView: View) {
        val btnLock = menuView.findViewById<ImageButton>(R.id.btn_menu_lock)
        val btnSave = menuView.findViewById<ImageButton>(R.id.btn_menu_save)
        val btnClose = menuView.findViewById<ImageButton>(R.id.btn_menu_close)
        val btnAdd = menuView.findViewById<ImageButton>(R.id.btn_menu_add)

        btnLock.setOnClickListener {
            isLocked = !isLocked
            if (isLocked) {
                btnLock.setImageResource(R.drawable.ic_lock)
                Toast.makeText(this, "HUD Layout LOCKED: Position secured", Toast.LENGTH_SHORT).show()
            } else {
                btnLock.setImageResource(R.drawable.ic_lock_open)
                Toast.makeText(this, "HUD Layout UNLOCKED: Drag to position", Toast.LENGTH_SHORT).show()
            }
        }

        btnSave.setOnClickListener {
            val coordText = floatingTargetView?.findViewById<TextView>(R.id.tv_target_coordinates)?.text ?: "Target saved"
            Toast.makeText(this, "Mapping Saved! Button [A] linked to $coordText", Toast.LENGTH_LONG).show()
        }

        btnClose.setOnClickListener {
            stopKeymapperOverlay()
        }

        btnAdd.setOnClickListener {
            Toast.makeText(this, "Key [A] is currently mapped. Ready to add [B] or [X]", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun makeDraggable(view: View, isTarget: Boolean) {
        var startX = 0f
        var startY = 0f
        var initialX = 0
        var initialY = 0
        var isClick = true

        view.setOnTouchListener { v, event ->
            if (isLocked) {
                if (isTarget) return@setOnTouchListener false
                if (event.action == MotionEvent.ACTION_UP) {
                    val clickableSurface = v.findViewById<View>(R.id.btn_trigger_surface) ?: v
                    clickableSurface.performClick()
                }
                return@setOnTouchListener true
            }

            val params = v.layoutParams as FrameLayout.LayoutParams

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    initialX = params.leftMargin
                    initialY = params.topMargin
                    isClick = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - startX).toInt()
                    val dy = (event.rawY - startY).toInt()

                    if (Math.abs(dx) > 8 || Math.abs(dy) > 8) {
                        isClick = false
                    }

                    params.leftMargin = initialX + dx
                    params.topMargin = initialY + dy
                    v.layoutParams = params

                    if (isTarget) {
                        val centerX = (params.leftMargin + v.width / 2).toFloat()
                        val centerY = (params.topMargin + v.height / 2).toFloat()
                        targetCoordX = centerX
                        targetCoordY = centerY
                        val coordTextView = v.findViewById<TextView>(R.id.tv_target_coordinates)
                        coordTextView?.text = "X:${centerX.toInt()}  Y:${centerY.toInt()}"
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isClick && !isTarget) {
                        val clickableSurface = v.findViewById<View>(R.id.btn_trigger_surface) ?: v
                        clickableSurface.performClick()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun pulseView(view: View) {
        val anim = ScaleAnimation(
            1.0f, 1.35f, 1.0f, 1.35f,
            ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
            ScaleAnimation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = 100
            repeatCount = 1
            repeatMode = ScaleAnimation.REVERSE
        }
        view.startAnimation(anim)
    }

    private fun updateTriggerSize(sizeDp: Int) {
        floatingTriggerView?.let { trigger ->
            val px = dpToPx(sizeDp)
            val params = trigger.layoutParams as? FrameLayout.LayoutParams ?: return
            params.width = px
            params.height = px
            trigger.layoutParams = params
        }
    }

    private fun removeInAppOverlayPreview() {
        overlayContainer?.let { container ->
            (container.parent as? ViewGroup)?.removeView(container)
        }
        overlayContainer = null
        floatingTriggerView = null
        floatingTargetView = null
        floatingMenuView = null
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        super.onDestroy()
        removeInAppOverlayPreview()
    }
}
