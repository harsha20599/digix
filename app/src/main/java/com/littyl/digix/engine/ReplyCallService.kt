package com.littyl.digix.engine

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telecom.CallAudioState
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.DisconnectCause
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import com.littyl.digix.BuildConfig
import com.littyl.digix.R
import com.littyl.digix.core.ActionType
import com.littyl.digix.core.AssistantConfig
import com.littyl.digix.core.CodeBinding
import com.littyl.digix.core.CodeRules
import com.littyl.digix.core.RunStatus
import com.littyl.digix.core.Store
import com.littyl.digix.ui.Readiness
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Answers a dialed code with a short private call: the watch sees a call connect right away, hears
 * the result, and the call hangs up by itself. It runs only inside the phone. Nothing goes over the
 * phone network, and Android keeps it out of the call log.
 */
class ReplyCallService : ConnectionService() {

    override fun onCreateOutgoingConnection(account: PhoneAccountHandle?, request: ConnectionRequest): Connection {
        val code = request.extras?.getString(EXTRA_CODE)
            ?: request.address?.schemeSpecificPart?.let(CodeRules::normalize)
        // If the safety net already ran the action, this call has nothing left to do but hang up.
        val binding = code?.takeIf(::claim)?.let(Store::find)
        val call = ReplyCall(applicationContext, binding)
        call.setAddress(request.address, TelecomManager.PRESENTATION_ALLOWED)
        call.setCallerDisplayName(getString(R.string.app_name), TelecomManager.PRESENTATION_ALLOWED)
        call.connectionProperties = Connection.PROPERTY_SELF_MANAGED
        // Declaring hold support is how Android asks this call to make way for a real one.
        call.connectionCapabilities = Connection.CAPABILITY_HOLD or Connection.CAPABILITY_SUPPORT_HOLD
        call.audioModeIsVoip = true
        // Telling Android the call is under way is what releases the watch from its dialing state.
        call.setDialing()
        call.begin()
        return call
    }

    override fun onCreateOutgoingConnectionFailed(account: PhoneAccountHandle?, request: ConnectionRequest) {
        // No call could start, such as during another call. The action still runs, just silently.
        val code = (request.extras?.getString(EXTRA_CODE) ?: request.address?.schemeSpecificPart?.let(CodeRules::normalize))
            ?.takeIf(::claim) ?: return
        Store.find(code)?.let { ActionService.dispatch(applicationContext, it) }
    }

    companion object {
        private const val EXTRA_CODE = "com.littyl.digix.CODE"
        private const val SAFETY_NET_MS = 3_000L
        private const val IDLE_POLL_MS = 50L
        private const val IDLE_WAIT_MS = 700L
        private const val SETTLE_MS = 800L

        private val handler = Handler(Looper.getMainLooper())
        private var pendingCode: String? = null
        private var safetyNet: Runnable? = null

        /** True for the first caller to take responsibility for running this code's action. */
        private fun claim(code: String): Boolean {
            if (pendingCode != code) return false
            pendingCode = null
            safetyNet?.let(handler::removeCallbacks)
            safetyNet = null
            return true
        }

        /**
         * Turns the dial itself into the reply call, so the watch never sees it fail. The safety net
         * still runs the action if Android doesn't start the call.
         */
        fun prepareRedirect(context: Context, binding: CodeBinding): PhoneAccountHandle {
            val appContext = context.applicationContext
            pendingCode = binding.code
            safetyNet = Runnable {
                if (claim(binding.code)) ActionService.dispatch(appContext, binding)
            }.also { handler.postDelayed(it, SAFETY_NET_MS) }
            return handle(context)
        }

        private fun handle(context: Context) =
            PhoneAccountHandle(ComponentName(context, ReplyCallService::class.java), "reply")

        fun registerAccount(context: Context) {
            val account = PhoneAccount.builder(handle(context), context.getString(R.string.app_name))
                .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
                .addSupportedUriScheme(PhoneAccount.SCHEME_TEL)
                .build()
            context.getSystemService(TelecomManager::class.java).registerPhoneAccount(account)
        }

        /**
         * Answers with a reply call, but only on an idle phone: starting one during a real call
         * would end that call. Android reports "in call" briefly while the dialed code is being
         * cancelled, so this watches for the phone to return to normal, which a real call or
         * ringing never does. If it doesn't, the action runs silently.
         */
        fun startWhenIdle(context: Context, binding: CodeBinding) {
            val appContext = context.applicationContext
            val audio = appContext.getSystemService(AudioManager::class.java)
            var waited = 0L
            val check = object : Runnable {
                override fun run() {
                    when {
                        audio.mode == AudioManager.MODE_NORMAL -> handler.postDelayed({
                            // The cancelled dial needs a moment to release the watch's audio link.
                            if (audio.mode != AudioManager.MODE_NORMAL || !start(appContext, binding)) {
                                ActionService.dispatch(appContext, binding)
                            }
                        }, SETTLE_MS)
                        waited >= IDLE_WAIT_MS -> ActionService.dispatch(appContext, binding)
                        else -> {
                            waited += IDLE_POLL_MS
                            handler.postDelayed(this, IDLE_POLL_MS)
                        }
                    }
                }
            }
            handler.postDelayed(check, IDLE_POLL_MS)
        }

        /**
         * True when the reply call was requested. On false, the caller runs the action silently.
         * Placing a call on this app's own account only needs MANAGE_OWN_CALLS, not CALL_PHONE.
         */
        @SuppressLint("MissingPermission")
        private fun start(context: Context, binding: CodeBinding): Boolean = try {
            val telecom = context.getSystemService(TelecomManager::class.java)
            val account = handle(context)
            if (telecom.isOutgoingCallPermitted(account)) {
                val extras = Bundle().apply {
                    putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, account)
                    putBundle(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS, Bundle().apply { putString(EXTRA_CODE, binding.code) })
                }
                // Android can accept this and then never start the call, such as during a real call.
                // The action must run either way, so it runs silently if the call hasn't started.
                val appContext = context.applicationContext
                pendingCode = binding.code
                safetyNet = Runnable {
                    if (claim(binding.code)) ActionService.dispatch(appContext, binding)
                }.also { handler.postDelayed(it, SAFETY_NET_MS) }
                telecom.placeCall(Uri.fromParts(PhoneAccount.SCHEME_TEL, binding.code, null), extras)
                true
            } else {
                false
            }
        } catch (_: RuntimeException) {
            // Not claimed, or the safety net would run the action a second time.
            claim(binding.code)
            false
        }
    }
}

private class ReplyCall(private val context: Context, private val binding: CodeBinding?) : Connection() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val speaker = Speaker(context)
    private val route = MutableStateFlow<CallAudioState?>(null)
    private var job: Job? = null
    @Volatile
    private var ended = false

    fun begin() {
        job = scope.launch {
            delay(ANSWER_DELAY_MS)
            setActive()
            val limit = if (binding?.action == ActionType.ASSISTANT) MAX_CONVERSATION_MS else MAX_CALL_MS
            val spoken = withTimeoutOrNull(limit) {
                awaitWatchAudio()
                reply()
            }
            if (spoken == null) finish()
        }
    }

    /** Waits for the watch's audio link, so the first word isn't lost while it connects. */
    private suspend fun awaitWatchAudio() {
        val state = withTimeoutOrNull(ROUTE_STATE_MS) { route.first { it != null } } ?: return
        if (state.supportedRouteMask and CallAudioState.ROUTE_BLUETOOTH == 0) return
        if (state.route != CallAudioState.ROUTE_BLUETOOTH) requestBluetooth(state)
        withTimeoutOrNull(BLUETOOTH_ROUTE_MS) { route.first { it?.route == CallAudioState.ROUTE_BLUETOOTH } }
            ?: useBluetoothDirectly()
        delay(AUDIO_LINK_MS)
    }

    private fun requestBluetooth(state: CallAudioState) {
        val device = state.supportedBluetoothDevices.firstOrNull()
        if (device != null) {
            requestBluetoothAudio(device)
        } else {
            @Suppress("DEPRECATION")
            setAudioRoute(CallAudioState.ROUTE_BLUETOOTH)
        }
    }

    /** Last resort when Android hasn't moved the call: asks the audio system for the watch itself. */
    private fun useBluetoothDirectly() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val audio = context.getSystemService(AudioManager::class.java)
        audio.availableCommunicationDevices
            .firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
            ?.let { audio.setCommunicationDevice(it) }
    }

    private suspend fun reply() {
        if (binding == null) {
            finish()
            return
        }
        // Silent on purpose. The alert started when the code was caught, and no sound must give it away.
        if (binding.action == ActionType.SOS) {
            delay(SOS_CALL_MS)
            finish()
            return
        }
        if (binding.action == ActionType.ASSISTANT && Store.enabled.value) {
            converse(binding)
            return
        }
        val afterCall = spokenBeforeRunning(binding)
        val message = afterCall ?: runAction(binding)
        speaker.speak(message)
        delay(SPEECH_TAIL_MS)
        finish()
        if (afterCall != null) {
            // The call must be fully gone first, and paused media needs a moment to resume.
            delay(AFTER_CALL_MS)
            ActionService.dispatch(context, binding)
        }
    }

    /**
     * What to say now, for actions that can't run during a call. Android sends media keys to the
     * call while one is up, and ringing can't share the line with speech. Null means run it now.
     */
    private fun spokenBeforeRunning(binding: CodeBinding): String? {
        if (!Store.enabled.value) return null
        return when (binding.action) {
            ActionType.PLAY_PAUSE -> context.getString(R.string.spoken_play_pause)
            ActionType.NEXT_TRACK -> context.getString(R.string.spoken_next_track)
            ActionType.FIND_PHONE -> if (ActionService.isFinderRinging) null else context.getString(R.string.spoken_find_phone)
            // The helper taps the screen, and the phone's call screen would be in its way.
            ActionType.LOCK_PHONE -> context.getString(R.string.spoken_assistant_lock)
            ActionType.WHATSAPP -> context.getString(R.string.spoken_whatsapp)
            ActionType.HOTSPOT -> context.getString(R.string.spoken_assistant_hotspot).takeIf { hotspotNeedsHelper() }
            else -> null
        }
    }

    private fun hotspotNeedsHelper() =
        !ShizukuBridge.isReady() && !Readiness.canSwitchHotspotDirectly(context) && HelperService.isConfigured(ActionType.HOTSPOT)

    /** Listen, answer, repeat. Ends when the person hangs up, says goodbye, or stops talking. */
    private suspend fun converse(binding: CodeBinding) {
        val settings = AssistantConfig.load(context)
        val blocker = when {
            settings == null -> R.string.spoken_assistant_setup
            !Readiness.hasMicrophone(context) -> R.string.spoken_assistant_mic
            !AssistantService.start(context) -> R.string.spoken_assistant_mic
            else -> null
        }
        if (blocker != null || settings == null) {
            speaker.speak(context.getString(blocker ?: R.string.spoken_assistant_setup))
            delay(SPEECH_TAIL_MS)
            finish()
            AssistantService.stop(context)
            return
        }
        val client = AssistantClient(settings)
        val listener = VoiceListener(AssistantConfig.usesTestAudio(context))
        val afterCall = mutableListOf<CodeBinding>()
        val history = mutableListOf<AssistantTurn>()
        val instructions = assistantInstructions(binding)
        speaker.speak(context.getString(R.string.spoken_assistant_hello))

        var silences = 0
        while (!ended && silences < MAX_SILENCES && history.size < MAX_TURNS) {
            val audio = try {
                listener.listen()
            } catch (_: SecurityException) {
                speaker.speak(context.getString(R.string.spoken_assistant_mic))
                break
            }
            if (ended) break
            if (audio == null) {
                silences++
                continue
            }
            silences = 0
            val reply = try {
                client.reply(audio, history.takeLast(MAX_REMEMBERED_TURNS), instructions)
            } catch (e: AssistantException) {
                speaker.speak(context.getString(failureMessage(e.failure)))
                break
            }
            reply.action?.let { name -> Store.codes.value.firstOrNull { it.action.name == name && assistantCanRun(it.action) } }
                ?.let { requested ->
                    if (requested.action in AFTER_CALL_ACTIONS) afterCall += requested else ActionService.dispatch(context, requested)
                }
            if (reply.say.isNotBlank()) speaker.speak(reply.say, keep = false)
            history += AssistantTurn(reply.heard.ifBlank { "..." }, reply.say)
            if (reply.end) break
        }
        if (!ended && silences >= MAX_SILENCES) speaker.speak(context.getString(R.string.spoken_assistant_goodbye))
        delay(SPEECH_TAIL_MS)
        finish()
        AssistantService.stop(context)
        ActionService.report(context, binding, ActionOutcome(RunStatus.DONE, context.getString(R.string.result_assistant_done)))
        if (afterCall.isNotEmpty()) {
            delay(AFTER_CALL_MS)
            afterCall.forEach { ActionService.dispatch(context, it) }
        }
    }

    // Play permits the helper for explicit dial-code actions, not model-selected actions.
    private fun assistantCanRun(action: ActionType): Boolean =
        action in ASSISTANT_ACTIONS && (!BuildConfig.PLAY_EDITION || action !in HELPER_CAPABLE_ACTIONS)

    private fun assistantInstructions(binding: CodeBinding): String {
        // Only things that are safe to run on a misheard word, so never SOS, calls or messages.
        val actions = Store.codes.value.map { it.action }.filter(::assistantCanRun).joinToString(", ") { it.name }
        return """
            You are a voice assistant heard through a small watch speaker during a phone call.
            Speak briefly and naturally, in one or two short sentences. Use no markdown, lists or symbols.
            Reply in the language the person speaks.
            Reply only with JSON: {"heard": what they said, "say": your spoken reply, "action": null or one action name, "end": true if they are finished}.
            Set "action" only when they clearly ask for it. These are the phone actions you can run: $actions.
            Set "end" to true when they say goodbye or that they're done.
        """.trimIndent()
    }

    private fun failureMessage(failure: AssistantFailure) = when (failure) {
        AssistantFailure.BAD_KEY -> R.string.spoken_assistant_key
        AssistantFailure.LIMIT_REACHED -> R.string.spoken_assistant_limit
        AssistantFailure.NETWORK, AssistantFailure.UNREADABLE -> R.string.spoken_assistant_network
    }

    private suspend fun runAction(binding: CodeBinding): String {
        // Listens before starting, so even an instant result can't be missed.
        val result = scope.async(start = CoroutineStart.UNDISPATCHED) {
            withTimeoutOrNull(ACTION_TIMEOUT_MS) { Store.entries.first { it.code == binding.code } }
        }
        ActionService.dispatch(context, binding)
        return result.await()?.message ?: context.getString(R.string.result_failed)
    }

    override fun onCallAudioStateChanged(state: CallAudioState) {
        route.value = state
    }

    /** The person hung up from the watch. Any action already started keeps going. */
    override fun onDisconnect() = finish()

    override fun onAbort() = finish()

    /** Android needs the line for a real call. A real call always wins, so this one simply ends. */
    override fun onHold() = finish()

    /** Ends as canceled because Android leaves canceled calls out of the call history. */
    private fun finish() {
        if (ended) return
        ended = true
        AssistantService.stop(context)
        speaker.stop()
        setDisconnected(DisconnectCause(DisconnectCause.CANCELED))
        destroy()
    }

    private companion object {
        const val ANSWER_DELAY_MS = 100L
        const val ROUTE_STATE_MS = 800L
        const val BLUETOOTH_ROUTE_MS = 1_500L
        const val AUDIO_LINK_MS = 600L
        const val SPEECH_TAIL_MS = 350L
        const val AFTER_CALL_MS = 900L
        const val ACTION_TIMEOUT_MS = 30_000L
        const val MAX_CALL_MS = 45_000L
        const val SOS_CALL_MS = 500L
        val ASSISTANT_ACTIONS = setOf(
            ActionType.FLASHLIGHT, ActionType.FIND_PHONE, ActionType.BATTERY, ActionType.PLAY_PAUSE,
            ActionType.NEXT_TRACK, ActionType.VIBRATE_MODE, ActionType.DO_NOT_DISTURB, ActionType.WIFI,
            ActionType.HOTSPOT, ActionType.WEBHOOK,
        )
        const val MAX_CONVERSATION_MS = 600_000L
        const val MAX_SILENCES = 2
        const val MAX_TURNS = 40
        const val MAX_REMEMBERED_TURNS = 10
        val HELPER_CAPABLE_ACTIONS = setOf(ActionType.WIFI, ActionType.HOTSPOT)

        val AFTER_CALL_ACTIONS = setOf(ActionType.PLAY_PAUSE, ActionType.NEXT_TRACK, ActionType.FIND_PHONE, ActionType.LOCK_PHONE)
    }
}
