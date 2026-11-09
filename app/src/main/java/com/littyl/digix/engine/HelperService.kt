package com.littyl.digix.engine

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.littyl.digix.core.ActionType
import com.littyl.digix.core.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Taps a Quick Settings tile for people without ADB or Shizuku. Android offers normal apps no way
 * to control the hotspot or Wi-Fi, but a person can always tap the tile themselves.
 *
 * Tile names differ by language and manufacturer, so the person teaches Digix once by tapping
 * theirs, and it remembers the name their phone uses. The service only receives events from the
 * system UI, and only reads the Quick Settings panel.
 */
class HelperService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
        _running.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val action = teaching.value ?: return
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED || event.packageName != SYSTEM_UI) return
        val label = labelOf(event) ?: return
        Store.setTileLabel(action, label)
        teaching.value = null
        _learned.value = action
        scope.launch {
            closePanelAfterTeaching()
            closeShade()
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        instance = null
        _running.value = false
        super.onDestroy()
    }

    private fun closeShade() {
        performGlobalAction(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE else GLOBAL_ACTION_BACK,
        )
    }

    private fun labelOf(event: AccessibilityEvent): String? {
        val raw = event.contentDescription?.toString()
            ?: event.text.firstOrNull()?.toString()
            ?: event.source?.let { node -> node.contentDescription?.toString() ?: node.text?.toString() }
        return raw?.let(::tileName)
    }

    /** Tiles often read "Hotspot, off". Keeps the part that does not change with state. */
    private fun tileName(raw: String): String? =
        raw.split(',', '，', '،').first().trim().takeIf { it.isNotEmpty() }

    private suspend fun tap(label: String, action: ActionType): Boolean {
        performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
        delay(PANEL_OPEN_MS)
        repeat(MAX_PAGES) {
            val roots = systemRoots()
            val tile = roots.firstNotNullOfOrNull { findTile(it, label) }
            if (tile != null) {
                val tapped = clickableAncestor(tile)?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
                delay(AFTER_TAP_MS)
                if (action == ActionType.WIFI) flipWifiInPanel()
                closeShade()
                return tapped
            }
            val scrolled = roots.firstNotNullOfOrNull(::findScrollable)
                ?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) == true
            if (!scrolled) return@repeat
            delay(PAGE_TURN_MS)
        }
        closeShade()
        return false
    }

    /**
     * On many phones Wi-Fi sits behind an Internet tile, which opens a panel instead of switching.
     * That panel's views have stable ids in every language, so its switch can be found reliably.
     * Waits, because the panel can take a moment to open, and stops early if the tile switched
     * Wi-Fi by itself.
     */
    private suspend fun flipWifiInPanel() {
        val wifi = getSystemService(WifiManager::class.java)
        val before = wifi.isWifiEnabled
        repeat(PANEL_POLLS) {
            if (wifi.isWifiEnabled != before) return
            val toggle = findWifiToggle()
            if (toggle != null) {
                clickableAncestor(toggle)?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                delay(AFTER_TAP_MS)
                performGlobalAction(GLOBAL_ACTION_BACK)
                delay(AFTER_TAP_MS)
                return
            }
            delay(PANEL_POLL_MS)
        }
    }

    /** Teaching taps the tile for real, so an Internet panel may be left open. */
    private suspend fun closePanelAfterTeaching() {
        repeat(PANEL_POLLS) {
            if (findWifiToggle() != null) {
                performGlobalAction(GLOBAL_ACTION_BACK)
                delay(AFTER_TAP_MS)
                return
            }
            delay(PANEL_POLL_MS)
        }
    }

    private fun findWifiToggle(): AccessibilityNodeInfo? = systemRoots().firstNotNullOfOrNull { root ->
        root.findAccessibilityNodeInfosByViewId("$SYSTEM_UI:id/wifi_toggle").firstOrNull()
    }

    private suspend fun sendInWhatsApp(phone: String, message: String): Boolean {
        val number = phone.filter { it.isDigit() }
        val link = Uri.parse("https://wa.me/$number?text=${Uri.encode(message)}")
        for (app in WHATSAPP_APPS) {
            val opened = runCatching {
                startActivity(Intent(Intent.ACTION_VIEW, link).setPackage(app).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            }.isSuccess
            if (!opened) continue
            repeat(WHATSAPP_POLLS) {
                delay(WHATSAPP_POLL_MS)
                val send = windows.mapNotNull { it.root }.plus(listOfNotNull(rootInActiveWindow))
                    .filter { it.packageName == app }
                    .firstNotNullOfOrNull { root -> root.findAccessibilityNodeInfosByViewId("$app:id/send").firstOrNull() }
                if (send != null && clickableAncestor(send)?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) {
                    delay(AFTER_SEND_MS)
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    return true
                }
            }
            performGlobalAction(GLOBAL_ACTION_HOME)
            return false
        }
        return false
    }

    private fun systemRoots(): List<AccessibilityNodeInfo> {
        val fromWindows = windows.mapNotNull { it.root }.filter { it.packageName == SYSTEM_UI }
        return fromWindows.ifEmpty { listOfNotNull(rootInActiveWindow) }
    }

    private fun findTile(node: AccessibilityNodeInfo, label: String): AccessibilityNodeInfo? {
        val text = node.contentDescription?.toString() ?: node.text?.toString()
        if (text != null && tileName(text).equals(label, ignoreCase = true)) return node
        for (i in 0 until node.childCount) {
            val found = node.getChild(i)?.let { findTile(it, label) }
            if (found != null) return found
        }
        return null
    }

    private fun clickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable) return current
            current = current.parent
        }
        return null
    }

    private fun findScrollable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val found = node.getChild(i)?.let(::findScrollable)
            if (found != null) return found
        }
        return null
    }

    companion object {
        private const val SYSTEM_UI = "com.android.systemui"
        private const val PANEL_OPEN_MS = 700L
        private const val PAGE_TURN_MS = 450L
        private const val AFTER_TAP_MS = 350L
        private const val MAX_PAGES = 5
        private const val WHATSAPP_POLLS = 40
        private const val WHATSAPP_POLL_MS = 250L
        private const val AFTER_SEND_MS = 700L
        private val WHATSAPP_APPS = listOf("com.whatsapp", "com.whatsapp.w4b")
        private const val PANEL_POLLS = 16
        private const val PANEL_POLL_MS = 150L

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        private var instance: HelperService? = null
        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running
        val isRunning: Boolean get() = instance != null

        private val teaching = MutableStateFlow<ActionType?>(null)
        private val _learned = MutableStateFlow<ActionType?>(null)

        /** The action whose tile was most recently taught. */
        val learned: StateFlow<ActionType?> = _learned

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            val self = ComponentName(context, HelperService::class.java).flattenToString()
            return enabled?.split(':')?.any { it.equals(self, ignoreCase = true) } == true
        }

        fun isConfigured(action: ActionType): Boolean = isRunning && Store.tileLabels.value.containsKey(action)

        /** Opens Quick Settings and waits for the person to tap the tile for [action]. */
        fun teach(action: ActionType): Boolean {
            val service = instance ?: return false
            _learned.value = null
            teaching.value = action
            return service.performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
        }

        fun cancelTeaching() {
            teaching.value = null
        }

        suspend fun tapTile(action: ActionType): Boolean {
            val service = instance ?: return false
            val label = Store.tileLabels.value[action] ?: return false
            return service.tap(label, action)
        }

        /**
         * Opens the chat with the message filled in, then taps Send. WhatsApp offers no other way to
         * send, so this needs an unlocked phone and could break if WhatsApp changes its screens.
         */
        suspend fun sendWhatsApp(phone: String, message: String): Boolean {
            val service = instance ?: return false
            return service.sendInWhatsApp(phone, message)
        }

        fun lockScreen(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) == true
    }
}
