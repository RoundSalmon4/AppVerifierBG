package dev.soupslurpr.appverifier.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@Composable
fun CreditsScreen() {
    LazyColumn(
        modifier = Modifier
    ) {
        item {
            CreditsItem(
                dependencyName = "Verified Apps (Privacy Guides)",
                dependencyPackageName = "Crowdsourced Android app signature data",
                dependencyLicense = CC_BY_4_LICENSE,
            )
        }
        item {
            CreditsItem(
                dependencyName = "AppVerifier",
                dependencyPackageName = "dev.soupslurpr.appverifier (soupslurpr)",
                dependencyLicense = ISC_LICENSE,
            )
        }
        item {
            CreditsItem(
                dependencyName = "Core Ktx",
                dependencyPackageName = "androidx.core:core-ktx",
                dependencyLicense = APACHE2LICENSE,
            )
        }
        item {
            CreditsItem(
                dependencyName = "Activity Compose",
                dependencyPackageName = "androidx.activity:activity-compose",
                dependencyLicense = APACHE2LICENSE,
            )
        }
        item {
            CreditsItem(
                dependencyName = "Compose Navigation",
                dependencyPackageName = "androidx.navigation:navigation-compose",
                dependencyLicense = APACHE2LICENSE,
            )
        }
        item {
            CreditsItem(
                dependencyName = "Android Preferences DataStore",
                dependencyPackageName = "androidx.datastore:datastore-preferences",
                dependencyLicense = APACHE2LICENSE,
            )
        }
        item {
            CreditsItem(
                dependencyName = "Accompanist DrawablePainter",
                dependencyPackageName = "com.google.accompanist:accompanist-drawablepainter",
                dependencyLicense = APACHE2LICENSE,
            )
        }
        item {
            CreditsItem(
                dependencyName = "Jetpack Compose BOM",
                dependencyPackageName = "androidx.compose:compose-bom",
                dependencyLicense = APACHE2LICENSE,
            )
        }
        item {
            CreditsItem(
                dependencyName = "Compose Material3 Components",
                dependencyPackageName = "androidx.compose.material3:material3",
                dependencyLicense = APACHE2LICENSE,
            )
        }
        item {
            CreditsItem(
                dependencyName = "Material Symbols",
                dependencyPackageName = "androidx.compose.material:material-icons-extended",
                dependencyLicense = APACHE2LICENSE,
            )
        }
        item {
            CreditsItem(
                dependencyName = "Compose UI Preview Tooling",
                dependencyPackageName = "androidx.compose.ui:ui-tooling-preview",
                dependencyLicense = APACHE2LICENSE,
            )
        }
        item {
            CreditsItem(
                dependencyName = "Lifecycle ViewModel Ktx",
                dependencyPackageName = "androidx.lifecycle:lifecycle-viewmodel-ktx",
                dependencyLicense = APACHE2LICENSE,
            )
        }
        item {
            CreditsItem(
                dependencyName = "Kotlin Coroutines Android",
                dependencyPackageName = "org.jetbrains.kotlinx:kotlinx-coroutines-android",
                dependencyLicense = APACHE2LICENSE,
            )
        }

        item {
            Spacer(Modifier.padding(WindowInsets.navigationBars.asPaddingValues()))
        }
    }
}

@Composable
fun CreditsItem(
    dependencyName: String,
    dependencyPackageName: String,
    dependencyLicense: String,
) {
    var dropped by rememberSaveable { mutableStateOf(false) }

    ListItem(
        modifier = Modifier.clickable(
            onClickLabel = "View $dependencyName's license",
            role = Role.DropdownList,
            onClick = { dropped = !dropped },
        ),
        headlineContent = { Text(text = dependencyName) },
        supportingContent = { Text(text = dependencyPackageName) },
        trailingContent = {
            Icon(imageVector = Icons.Filled.Info, contentDescription = null)
        }
    )
    if (dropped) {
        Text(
            modifier = Modifier.padding(horizontal = 16.dp),
            style = typography.bodySmall,
            text = dependencyLicense,
        )
    }
}
