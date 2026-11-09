package com.littyl.digix.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.littyl.digix.R
import com.littyl.digix.core.ActionType
import com.littyl.digix.core.CodeBinding
import com.littyl.digix.engine.GoogleHome
import com.littyl.digix.ui.Chevron
import com.littyl.digix.ui.Group
import com.littyl.digix.ui.GroupDivider
import com.littyl.digix.ui.GroupRow
import com.littyl.digix.ui.Readiness
import com.littyl.digix.ui.LargeTitle
import com.littyl.digix.ui.PinnedBar
import com.littyl.digix.ui.VSpace
import com.littyl.digix.ui.isTitleScrolledAway
import com.littyl.digix.ui.rememberResumeTick
import com.littyl.digix.ui.SectionLabel

@Composable
fun PickerScreen(onBack: () -> Unit, onPick: (ActionType) -> Unit) {
    val context = LocalContext.current
    val tick = rememberResumeTick()
    val (ready, setup) = remember(tick) {
        // Only in builds that include Google's library, which isn't public yet.
        ActionType.entries.filter { it != ActionType.GOOGLE_HOME || GoogleHome.available }.partition { Readiness.isReady(context, CodeBinding("", it)) }
    }
    val list = rememberLazyListState()
    val title = stringResource(R.string.picker_title)
    Column(Modifier.fillMaxSize()) {
    PinnedBar(title, collapsed = list.isTitleScrolledAway(), onBack = onBack)
    LazyColumn(
        state = list,
        modifier = Modifier.weight(1f),
        contentPadding = WindowInsets.navigationBars.asPaddingValues(),
    ) {
        item { LargeTitle(title) }
        if (ready.isNotEmpty()) item { Section(stringResource(R.string.picker_ready), ready, onPick) }
        if (setup.isNotEmpty()) item { Section(stringResource(R.string.picker_setup), setup, onPick) }
        item { VSpace(32.dp) }
    }
    }
}

@Composable
private fun Section(title: String, actions: List<ActionType>, onPick: (ActionType) -> Unit) {
    Column(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SectionLabel(title)
        Group {
            actions.forEachIndexed { index, action ->
                if (index > 0) GroupDivider()
                GroupRow(
                    title = stringResource(action.title),
                    body = stringResource(action.summary),
                    icon = action.icon,
                    onClick = { onPick(action) },
                    trailing = { Chevron() },
                )
            }
        }
    }
}
