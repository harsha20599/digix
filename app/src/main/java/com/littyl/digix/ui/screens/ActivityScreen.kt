package com.littyl.digix.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.littyl.digix.R
import com.littyl.digix.core.RunStatus
import com.littyl.digix.core.Store
import com.littyl.digix.ui.ButtonKind
import com.littyl.digix.ui.CodeBadge
import com.littyl.digix.ui.DigixButton
import com.littyl.digix.ui.EmptyState
import com.littyl.digix.ui.Group
import com.littyl.digix.ui.GroupDivider
import com.littyl.digix.ui.GroupRow
import com.littyl.digix.ui.LargeTitle
import com.littyl.digix.ui.PinnedBar
import com.littyl.digix.ui.VSpace
import com.littyl.digix.ui.isTitleScrolledAway
import com.littyl.digix.ui.theme.Digix

@Composable
fun ActivityScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val log by Store.log.collectAsStateWithLifecycle()
    val list = rememberLazyListState()
    val title = stringResource(R.string.activity_title)
    Column(Modifier.fillMaxSize()) {
    PinnedBar(
        title,
        collapsed = list.isTitleScrolledAway(),
        onBack = onBack,
        trailing = {
            if (log.isNotEmpty()) {
                DigixButton(stringResource(R.string.activity_clear), { Store.clearLog() }, kind = ButtonKind.GHOST)
            }
        },
    )
    LazyColumn(
        state = list,
        modifier = Modifier.weight(1f),
        contentPadding = WindowInsets.navigationBars.asPaddingValues(),
    ) {
        item { LargeTitle(title) }
        if (log.isEmpty()) {
            item {
                EmptyState(
                    stringResource(R.string.activity_empty_title),
                    stringResource(R.string.activity_empty_body),
                    icon = R.drawable.ic_history,
                )
            }
        } else {
            item {
                Group(Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp)) {
                    log.forEachIndexed { index, entry ->
                        if (index > 0) GroupDivider()
                        GroupRow(
                            title = entry.message,
                            body = relativeTime(context, entry.timeMillis),
                            icon = when (entry.status) {
                                RunStatus.DONE -> entry.action.icon
                                RunStatus.STOPPED_OFF -> R.drawable.ic_x
                                RunStatus.NEEDS_SETUP, RunStatus.FAILED -> R.drawable.ic_circle_alert
                            },
                            trailing = { CodeBadge(entry.code) },
                        )
                    }
                }
            }
        }
        item { VSpace(32.dp) }
    }
    }
}
