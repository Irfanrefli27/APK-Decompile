package dev.apkdecompiler

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val vm: DecompilerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // jadx kadang baca user.home; di Android nilainya kosong
        System.setProperty("user.home", filesDir.absolutePath)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().systemBarsPadding()) { App(vm) }
                }
            }
        }
    }
}

@Composable
private fun App(vm: DecompilerViewModel) {
    BackHandler(enabled = vm.openedTitle != null || vm.stage is Stage.Browse) {
        if (vm.openedTitle != null) vm.closeFile() else vm.goHome()
    }
    when (val s = vm.stage) {
        Stage.Home -> HomeScreen(vm)
        is Stage.Loading -> LoadingScreen(s.message)
        Stage.Browse -> if (vm.openedTitle != null) ViewerScreen(vm) else BrowseScreen(vm)
        is Stage.Error -> ErrorScreen(s.message) { vm.goHome() }
    }
}

@Composable
private fun HomeScreen(vm: DecompilerViewModel) {
    val ctx = LocalContext.current
    var showApps by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.openUri(uri, displayName(ctx, uri))
    }

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("APK Decompiler", style = MaterialTheme.typography.headlineMedium)
        Text("Decompile APK ke Java langsung di HP (jadx-core).")
        Button(onClick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
            Text("Pilih file APK")
        }
        OutlinedButton(
            onClick = { vm.loadInstalledApps(); showApps = true },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Pilih dari app terpasang") }
        Text(
            "Catatan: hanya kode Java/Kotlin (DEX) yang bisa dibaca. Kode native (.so) " +
                "tidak ikut, jadi game Unity/Unreal isinya hampir kosong. APK besar bisa " +
                "kehabisan memori.",
            style = MaterialTheme.typography.bodySmall
        )
    }

    if (showApps) {
        AppPickerDialog(
            apps = vm.installed,
            onPick = { showApps = false; vm.openInstalled(it) },
            onDismiss = { showApps = false }
        )
    }
}

@Composable
private fun AppPickerDialog(apps: List<AppEntry>, onPick: (AppEntry) -> Unit, onDismiss: () -> Unit) {
    var q by remember { mutableStateOf("") }
    val shown = remember(apps, q) {
        apps.filter { it.label.contains(q, true) || it.packageName.contains(q, true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } },
        title = { Text("App terpasang") },
        text = {
            Column {
                OutlinedTextField(
                    value = q, onValueChange = { q = it }, singleLine = true,
                    label = { Text("Cari") }, modifier = Modifier.fillMaxWidth()
                )
                if (apps.isEmpty()) Text("Memuat…", Modifier.padding(top = 8.dp))
                LazyColumn(Modifier.height(360.dp)) {
                    items(shown, key = { it.packageName }) { a ->
                        Column(
                            Modifier.fillMaxWidth().clickable { onPick(a) }.padding(vertical = 8.dp)
                        ) {
                            Text(a.label)
                            Text(a.packageName, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun LoadingScreen(message: String) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Text(message, Modifier.padding(top = 16.dp), textAlign = TextAlign.Center)
    }
}

@Composable
private fun ErrorScreen(message: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Gagal", style = MaterialTheme.typography.headlineSmall)
        Text(message, color = MaterialTheme.colorScheme.error)
        Button(onClick = onBack) { Text("Kembali") }
    }
}

@Composable
private fun BrowseScreen(vm: DecompilerViewModel) {
    var query by remember { mutableStateOf("") }
    val names = vm.classNames
    val shown = remember(query, names) {
        if (query.isBlank()) names else names.filter { it.contains(query.trim(), ignoreCase = true) }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                vm.apkName, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium
            )
            TextButton(onClick = vm::openManifest) { Text("Manifest") }
            TextButton(onClick = vm::goHome) { Text("Tutup") }
        }
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            label = { Text("Cari class (${shown.size}/${names.size})") },
            modifier = Modifier.fillMaxWidth()
        )
        LazyColumn(Modifier.fillMaxSize()) {
            items(shown, key = { it }) { n ->
                Text(
                    n,
                    Modifier.fillMaxWidth().clickable { vm.openClass(n) }.padding(vertical = 10.dp),
                    fontFamily = FontFamily.Monospace, fontSize = 13.sp
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ViewerScreen(vm: DecompilerViewModel) {
    val title = vm.openedTitle ?: return
    val code = vm.openedCode
    val clipboard = LocalClipboardManager.current
    val lines by produceState<List<AnnotatedString>?>(initialValue = null, key1 = code) {
        value = if (code == null) null
        else withContext(Dispatchers.Default) { Highlighter.highlight(code, plain = title.endsWith(".xml")) }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = vm::closeFile) { Text("← Kembali") }
            Text(
                title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontFamily = FontFamily.Monospace, fontSize = 12.sp
            )
            TextButton(onClick = { code?.let { clipboard.setText(AnnotatedString(it)) } }) { Text("Salin") }
        }
        val ls = lines
        if (ls == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Box(
                Modifier.fillMaxSize().background(Color(0xFF2B2B2B))
                    .horizontalScroll(rememberScrollState())
            ) {
                LazyColumn {
                    itemsIndexed(ls) { i, l ->
                        Row {
                            Text(
                                "${i + 1}", Modifier.width(48.dp).padding(end = 8.dp),
                                color = Color(0xFF606366), fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp, textAlign = TextAlign.End
                            )
                            Text(
                                l, color = Color(0xFFA9B7C6), fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp, softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun displayName(ctx: Context, uri: Uri): String =
    ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "app.apk"
