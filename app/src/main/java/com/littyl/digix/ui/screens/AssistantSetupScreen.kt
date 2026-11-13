package com.littyl.digix.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.littyl.digix.BuildConfig
import com.littyl.digix.R
import com.littyl.digix.core.AssistantConfig
import com.littyl.digix.core.AssistantProvider
import com.littyl.digix.core.AssistantSettings
import com.littyl.digix.engine.AssistantClient
import com.littyl.digix.engine.AssistantException
import com.littyl.digix.engine.AssistantFailure
import com.littyl.digix.ui.ButtonKind
import com.littyl.digix.ui.DigixButton
import com.littyl.digix.ui.Group
import com.littyl.digix.ui.GroupDivider
import com.littyl.digix.ui.InlineMessage
import com.littyl.digix.ui.LargeTitle
import com.littyl.digix.ui.PinnedBar
import com.littyl.digix.ui.VSpace
import com.littyl.digix.ui.isTitleScrolledAway
import com.littyl.digix.ui.theme.Digix
import com.littyl.digix.ui.theme.DigixMotion
import com.littyl.digix.ui.theme.DigixShape
import com.littyl.digix.ui.theme.rememberHaptics
import kotlinx.coroutines.launch

/** Choose a provider, paste a key, done. The key is checked before it's saved. */
@Composable
fun AssistantSetupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val existing = remember { AssistantConfig.load(context) }

    var provider by rememberSaveable { mutableStateOf(existing?.provider ?: AssistantProvider.GEMINI) }
    var key by rememberSaveable { mutableStateOf(existing?.apiKey.orEmpty()) }
    var baseUrl by rememberSaveable { mutableStateOf(if (existing?.provider == AssistantProvider.CUSTOM) existing.baseUrl else "") }
    var chatModel by rememberSaveable { mutableStateOf(if (existing?.provider == AssistantProvider.CUSTOM) existing.chatModel else "") }
    var checking by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<Int?>(null) }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onBack() }

    val isCustom = provider == AssistantProvider.CUSTOM
    val needsHttps = BuildConfig.PLAY_EDITION && isCustom
    val validProvider = !needsHttps || baseUrl.startsWith("https://", ignoreCase = true)
    val canCheck = key.isNotBlank() && !checking && validProvider &&
        (!isCustom || (baseUrl.isNotBlank() && chatModel.isNotBlank()))
    val scroll = rememberScrollState()
    val title = stringResource(R.string.assistant_title)

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .navigationBarsPadding(),
    ) {
        PinnedBar(title, collapsed = scroll.isTitleScrolledAway(), onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(scroll),
        ) {
            LargeTitle(title)
            Column(Modifier.padding(horizontal = 16.dp)) {
                VSpace(6.dp)
                Text(stringResource(R.string.assistant_body), style = Digix.type.body, color = Digix.colors.secondary)
                VSpace(22.dp)
                Group {
                    AssistantProvider.entries.forEachIndexed { index, option ->
                        if (index > 0) GroupDivider()
                        ChoiceRow(
                            label = option.label,
                            body = stringResource(
                                when (option) {
                                    AssistantProvider.GEMINI -> R.string.assistant_gemini_body
                                    AssistantProvider.GROQ -> R.string.assistant_groq_body
                                    AssistantProvider.CUSTOM -> R.string.assistant_custom_body
                                },
                            ),
                            selected = option == provider,
                        ) {
                            haptics.press()
                            if (provider != option) key = ""
                            provider = option
                            problem = null
                        }
                    }
                }
                VSpace(22.dp)
                Field(
                    label = stringResource(R.string.assistant_key_label),
                    value = key,
                    hidden = true,
                    onChange = {
                        key = it.trim()
                        problem = null
                    },
                )
                val keyPage = provider.keyPage
                if (keyPage != null) {
                    VSpace(8.dp)
                    DigixButton(
                        stringResource(R.string.assistant_get_key, provider.label),
                        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(keyPage))) },
                        kind = ButtonKind.OUTLINE,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                AnimatedVisibility(visible = isCustom) {
                    Column {
                        VSpace(16.dp)
                        Field(stringResource(R.string.assistant_base_label), baseUrl, hint = "https://api.example.com/v1") {
                            baseUrl = it.trim()
                            problem = null
                        }
                        VSpace(16.dp)
                        Field(stringResource(R.string.assistant_model_label), chatModel) {
                            chatModel = it.trim()
                            problem = null
                        }
                    }
                }
                InlineMessage(text = when {
                    needsHttps && baseUrl.isNotBlank() && !validProvider -> stringResource(R.string.assistant_https_required)
                    else -> problem?.let { stringResource(it) }
                })
                VSpace(22.dp)
                Text(stringResource(R.string.assistant_privacy), style = Digix.type.subhead, color = Digix.colors.secondary)
                VSpace(16.dp)
            }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            DigixButton(
                stringResource(if (checking) R.string.assistant_checking else R.string.assistant_save),
                enabled = canCheck,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val settings = AssistantSettings(
                        provider = provider,
                        baseUrl = if (isCustom) baseUrl.trimEnd('/') else provider.baseUrl,
                        chatModel = if (isCustom) chatModel else provider.chatModel,
                        speechModel = provider.speechModel,
                        apiKey = key,
                    )
                    checking = true
                    problem = null
                    scope.launch {
                        try {
                            AssistantClient(settings).check()
                            AssistantConfig.save(context, settings)
                            haptics.confirm()
                            if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                onBack()
                            } else {
                                micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        } catch (e: AssistantException) {
                            haptics.reject()
                            problem = when (e.failure) {
                                AssistantFailure.BAD_KEY -> R.string.assistant_error_key
                                AssistantFailure.LIMIT_REACHED -> R.string.assistant_error_limit
                                AssistantFailure.NETWORK -> R.string.assistant_error_network
                                AssistantFailure.UNREADABLE -> R.string.assistant_error_reply
                            }
                        } finally {
                            checking = false
                        }
                    }
                },
            )
            if (existing != null) {
                DigixButton(
                    stringResource(R.string.assistant_remove),
                    onClick = {
                        AssistantConfig.clear(context)
                        onBack()
                    },
                    kind = ButtonKind.GHOST,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** One choice in a group where only one can be picked. */
@Composable
fun ChoiceRow(label: String, body: String?, selected: Boolean, onClick: () -> Unit) {
    val colors = Digix.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
            .heightIn(min = 56.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = Digix.type.bodyMedium, color = colors.foreground)
            if (body != null) Text(body, style = Digix.type.subhead, color = colors.secondary)
        }
        if (selected) {
            Icon(
                painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = colors.foreground,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun Field(label: String, value: String, hint: String? = null, hidden: Boolean = false, onChange: (String) -> Unit) {
    val colors = Digix.colors
    var focused by remember { mutableStateOf(false) }
    val border by animateColorAsState(
        targetValue = if (focused) colors.foreground else colors.border,
        animationSpec = DigixMotion.smooth(),
        label = "field border",
    )
    val shape = RoundedCornerShape(DigixShape.radius)
    Text(label, style = Digix.type.subheadMedium, color = colors.foreground)
    VSpace(6.dp)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (hidden) KeyboardType.Password else KeyboardType.Uri),
        visualTransformation = if (hidden) PasswordVisualTransformation() else VisualTransformation.None,
        textStyle = Digix.type.body.copy(color = colors.foreground),
        cursorBrush = SolidColor(colors.foreground),
        decorationBox = { inner ->
            if (value.isEmpty() && hint != null) {
                Text(hint, style = Digix.type.body, color = colors.secondary)
            }
            inner()
        },
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = label }
            .onFocusChanged { focused = it.isFocused }
            .border(DigixShape.hairline, border, shape)
            .padding(horizontal = 14.dp, vertical = 14.dp),
    )
}
