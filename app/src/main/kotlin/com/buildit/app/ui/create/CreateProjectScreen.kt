package com.buildit.app.ui.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buildit.core.model.ProjectConfig
import com.buildit.core.model.ProjectId
import com.buildit.core.model.ProjectTemplate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateProjectScreen(
    onBack: () -> Unit,
    onCreated: (ProjectId) -> Unit,
    vm: CreateProjectViewModel = hiltViewModel(),
) {
    val form by vm.form.collectAsStateWithLifecycle()
    LaunchedEffect(vm) { vm.created.collect { onCreated(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New project") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 640.dp).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = form.appName,
                onValueChange = vm::onAppNameChange,
                label = { Text("App name") },
                singleLine = true,
                isError = form.appNameError != null,
                supportingText = form.appNameError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.packageName,
                onValueChange = vm::onPackageChange,
                label = { Text("Package name") },
                singleLine = true,
                isError = form.packageError != null,
                supportingText = { Text(form.packageError ?: "Unique app identifier. Cannot be changed after publishing.") },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SdkPicker("Minimum SDK", form.minSdk, ProjectConfig.LOWEST_MIN_SDK..ProjectConfig.HIGHEST_TARGET_SDK, vm::onMinSdkChange, Modifier.weight(1f))
                SdkPicker("Target SDK", form.targetSdk, ProjectConfig.LOWEST_MIN_SDK..ProjectConfig.HIGHEST_TARGET_SDK, vm::onTargetSdkChange, Modifier.weight(1f))
            }
            form.sdkError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            Text("Template", style = MaterialTheme.typography.titleSmall)
            ProjectTemplate.entries.forEach { t ->
                OutlinedCard(
                    Modifier.fillMaxWidth().selectable(selected = form.template == t, role = Role.RadioButton, onClick = { vm.onTemplateChange(t) }),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = form.template == t, onClick = null)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(t.displayName, style = MaterialTheme.typography.titleSmall)
                            Text(t.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            form.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = vm::submit, enabled = form.canSubmit, modifier = Modifier.fillMaxWidth()) {
                if (form.submitting) CircularProgressIndicator(Modifier.padding(end = 8.dp).size(18.dp), strokeWidth = 2.dp)
                Text(if (form.submitting) "Creating…" else "Create project")
            }
        }
        }
    }
}

private fun sdkLabel(api: Int): String {
    val name = when (api) {
        21 -> "5.0"; 22 -> "5.1"; 23 -> "6"; 24 -> "7.0"; 25 -> "7.1"; 26 -> "8.0"; 27 -> "8.1"; 28 -> "9"
        29 -> "10"; 30 -> "11"; 31 -> "12"; 32 -> "12L"; 33 -> "13"; 34 -> "14"; 35 -> "15"; 36 -> "16"
        else -> null
    }
    return if (name != null) "API $api (Android $name)" else "API $api"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SdkPicker(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = sdkLabel(value),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            singleLine = true,
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            range.reversed().forEach { api ->
                DropdownMenuItem(text = { Text(sdkLabel(api)) }, onClick = { onChange(api); expanded = false })
            }
        }
    }
}
