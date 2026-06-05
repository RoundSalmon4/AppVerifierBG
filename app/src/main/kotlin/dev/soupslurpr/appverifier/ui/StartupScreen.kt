package dev.soupslurpr.appverifier.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import androidx.compose.foundation.Image
import dev.soupslurpr.appverifier.R

@Composable
fun StartupScreen(
    modifier: Modifier,
    onSettingsButtonClicked: () -> Unit,
    onPrivacyPolicyButtonClicked: () -> Unit,
    onAppListButtonClicked: () -> Unit,
    onVerifyApkFileButtonClicked: () -> Unit,
    onPasteFromClipboard: () -> Unit,
) {
    val context = LocalContext.current
    val icon = remember { context.packageManager.getApplicationIcon(context.packageName) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.padding(top = 8.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Image(
                painter = rememberDrawablePainter(drawable = icon),
                contentDescription = null,
                modifier = Modifier.size(96.dp),
            )
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                ActionItem(
                    icon = Icons.AutoMirrored.Filled.List,
                    title = stringResource(R.string.app_list),
                    description = "Browse installed apps and verify their signatures",
                    onClick = onAppListButtonClicked,
                )
                HorizontalDivider()
                ActionItem(
                    icon = Icons.Filled.ContentPaste,
                    title = "Paste from clipboard",
                    description = "Verify an app from clipboard text",
                    onClick = onPasteFromClipboard,
                )
                HorizontalDivider()
                ActionItem(
                    icon = Icons.Filled.FileOpen,
                    title = "Verify APK File",
                    description = "Select and verify an APK file",
                    onClick = onVerifyApkFileButtonClicked,
                )
                HorizontalDivider()
                ActionItem(
                    icon = Icons.Filled.Settings,
                    title = stringResource(R.string.settings),
                    description = "Configure app preferences and manage databases",
                    onClick = onSettingsButtonClicked,
                )
                HorizontalDivider()
                ActionItem(
                    icon = Icons.Filled.Info,
                    title = "Privacy policy",
                    description = "View AppVerifier BG's privacy policy",
                    onClick = onPrivacyPolicyButtonClicked,
                )
            }
        }

        Spacer(Modifier.padding(WindowInsets.navigationBars.asPaddingValues()))
    }
}

@Composable
private fun ActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
