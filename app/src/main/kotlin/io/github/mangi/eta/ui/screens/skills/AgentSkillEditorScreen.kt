package io.github.mangi.eta.ui.screens.skills

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.skill.SkillRuntime
import io.github.mangi.eta.ui.components.AdaptiveTopAppBar
import io.github.mangi.eta.ui.components.MiuixBackButton
import io.github.mangi.eta.ui.components.MiuixDialogActions
import io.github.mangi.eta.ui.components.TopBarBackdrop
import io.github.mangi.eta.ui.components.rememberTopBarBackdrop
import io.github.mangi.eta.ui.components.topBarContainerColor
import io.github.mangi.eta.ui.layout.WidePageContent
import io.github.mangi.eta.ui.layout.horizontalCutoutPadding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun AgentSkillEditorScreen(
    skillId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var initialContent by remember { mutableStateOf<String?>(null) }
    var currentContent by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val hasUnsavedChanges = remember(initialContent, currentContent) {
        initialContent != null && currentContent != initialContent
    }

    LaunchedEffect(skillId) {
        val content = withContext(Dispatchers.IO) {
            val service = SkillRuntime.createIndexService(context)
            service.readSkillContent(skillId)
        }
        if (content != null) {
            initialContent = content
            currentContent = content
        } else {
            errorMessage = context.getString(R.string.skills_not_found)
        }
        isLoading = false
    }

    val handleBack = {
        if (hasUnsavedChanges) {
            showDiscardDialog = true
        } else {
            onBack()
        }
    }

    BackHandler(enabled = true) {
        handleBack()
    }

    val saveSkill = {
        if (!isSaving && hasUnsavedChanges) {
            isSaving = true
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    val service = SkillRuntime.createIndexService(context)
                    service.updateSkillContent(skillId, currentContent)
                }
                isSaving = false
                result.fold(
                    onSuccess = {
                        Toast.makeText(
                            context,
                            context.getString(R.string.skills_saved_success),
                            Toast.LENGTH_SHORT,
                        ).show()
                        initialContent = currentContent
                        onBack()
                    },
                    onFailure = { error ->
                        errorMessage = error.message ?: context.getString(R.string.skills_save_failed)
                    },
                )
            }
        }
    }

    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberTopBarBackdrop()
    val topBarColor = topBarContainerColor(backdrop)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopBarBackdrop(backdrop) {
                AdaptiveTopAppBar(
                    title = stringResource(R.string.route_skill_editor),
                    color = topBarColor,
                    navigationIcon = {
                        MiuixBackButton(onClick = handleBack)
                    },
                    actions = {
                        if (isSaving) {
                            InfiniteProgressIndicator(
                                modifier = Modifier
                                    .padding(end = 12.dp)
                                    .size(20.dp),
                            )
                        } else {
                            TextButton(
                                text = stringResource(R.string.skills_save),
                                enabled = !isLoading && hasUnsavedChanges,
                                onClick = saveSkill,
                                colors = ButtonDefaults.textButtonColorsPrimary(),
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
    ) { innerPadding ->
        WidePageContent { sidePadding ->
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    InfiniteProgressIndicator(size = 36.dp)
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = innerPadding.calculateTopPadding())
                        .horizontalCutoutPadding()
                        .padding(horizontal = sidePadding)
                        .imePadding()
                        .navigationBarsPadding(),
                ) {
                    SmallTitle("SKILL.md")
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(bottom = 12.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                        ) {
                            TextField(
                                value = currentContent,
                                onValueChange = { currentContent = it },
                                label = "SKILL.md",
                                useLabelAsPlaceholder = true,
                                enabled = !isSaving,
                                minLines = 10,
                                textStyle = MiuixTheme.textStyles.body2.copy(
                                    fontFamily = FontFamily.Monospace,
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState()),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = if (hasUnsavedChanges) {
                                        stringResource(R.string.skills_unsaved_changes)
                                    } else {
                                        ""
                                    },
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDiscardDialog) {
        WindowDialog(
            show = true,
            title = stringResource(R.string.skills_discard_changes_title),
            summary = stringResource(R.string.skills_discard_changes_summary),
            onDismissRequest = { showDiscardDialog = false },
        ) {
            MiuixDialogActions(
                confirmText = stringResource(R.string.skills_discard),
                destructive = true,
                onCancel = { showDiscardDialog = false },
                onConfirm = {
                    showDiscardDialog = false
                    onBack()
                },
            )
        }
    }

    errorMessage?.let { msg ->
        WindowDialog(
            show = true,
            title = stringResource(R.string.skills_save_failed),
            summary = msg,
            onDismissRequest = { errorMessage = null },
        ) {
            TextButton(
                text = stringResource(R.string.ui_knew_cb63c6),
                onClick = { errorMessage = null },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}
