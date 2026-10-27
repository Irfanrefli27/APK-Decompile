package dev.apkdecompiler

import android.app.Application
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import jadx.api.JadxArgs
import jadx.api.JadxDecompiler
import jadx.api.JavaClass
import jadx.api.ResourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class AppEntry(val label: String, val packageName: String, val sourceDir: String)

sealed interface Stage {
    data object Home : Stage
    data class Loading(val message: String) : Stage
    data object Browse : Stage
    data class Error(val message: String) : Stage
}

class DecompilerViewModel(app: Application) : AndroidViewModel(app) {

    var stage by mutableStateOf<Stage>(Stage.Home)
        private set
    var apkName by mutableStateOf("")
        private set
    var classNames by mutableStateOf<List<String>>(emptyList())
        private set
    var openedTitle by mutableStateOf<String?>(null)
        private set
    var openedCode by mutableStateOf<String?>(null)
        private set
    var codeLoading by mutableStateOf(false)
        private set
    var installed by mutableStateOf<List<AppEntry>>(emptyList())
        private set

    private var jadx: JadxDecompiler? = null
    private var classMap: Map<String, JavaClass> = emptyMap()

    private val ctx get() = getApplication<Application>()

    fun loadInstalledApps() {
        if (installed.isNotEmpty()) return
        viewModelScope.launch {
            installed = withContext(Dispatchers.IO) {
                val pm = ctx.packageManager
                pm.getInstalledApplications(PackageManager.GET_META_DATA)
                    .map { AppEntry(pm.getApplicationLabel(it).toString(), it.packageName, it.sourceDir) }
                    .sortedBy { it.label.lowercase() }
            }
        }
    }

    fun openUri(uri: Uri, name: String) = launchLoad(name) { dest ->
        ctx.contentResolver.openInputStream(uri)!!.use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
    }

    fun openInstalled(e: AppEntry) = launchLoad(e.label) { dest ->
        File(e.sourceDir).inputStream().use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
    }

    private fun launchLoad(name: String, copyTo: (File) -> Unit) {
        viewModelScope.launch {
            stage = Stage.Loading("Menyalin APK…")
            apkName = name
            try {
                val file = withContext(Dispatchers.IO) {
                    closeJadx()
                    File(ctx.cacheDir, "input.apk").also { copyTo(it) }
                }
                stage = Stage.Loading("Parsing DEX & menyusun daftar class… (APK besar bisa lama)")
                val (d, map) = withContext(Dispatchers.Default) {
                    val args = JadxArgs()
                    args.setInputFile(file)
                    args.setOutDir(File(ctx.cacheDir, "jadx_out").apply { mkdirs() })
                    args.setThreadsCount(2)
                    args.setDebugInfo(false)
                    args.setShowInconsistentCode(true)
                    args.setDeobfuscationOn(false)
                    val dec = JadxDecompiler(args)
                    dec.load()
                    val m = dec.classes.sortedBy { it.fullName }.associateBy { it.fullName }
                    dec to m
                }
                jadx = d
                classMap = map
                classNames = map.keys.toList()
                openedTitle = null
                openedCode = null
                stage = Stage.Browse
            } catch (t: Throwable) {
                closeJadx()
                stage = Stage.Error(
                    if (t is OutOfMemoryError) "Memori habis. APK terlalu besar untuk di-load penuh."
                    else "${t.javaClass.simpleName}: ${t.message}"
                )
            }
        }
    }

    fun openClass(name: String) {
        val cls = classMap[name] ?: return
        openedTitle = name
        openedCode = null
        codeLoading = true
        viewModelScope.launch {
            val code = withContext(Dispatchers.Default) {
                try { cls.code } catch (t: Throwable) { "// Gagal decompile class ini:\n// $t" }
            }
            openedCode = code
            codeLoading = false
        }
    }

    fun openManifest() {
        val d = jadx ?: return
        openedTitle = "AndroidManifest.xml"
        openedCode = null
        codeLoading = true
        viewModelScope.launch {
            val text = withContext(Dispatchers.Default) {
                try {
                    val res = d.resources.firstOrNull { it.type == ResourceType.MANIFEST }
                    res?.loadContent()?.text?.codeStr ?: "<!-- Manifest tidak ditemukan -->"
                } catch (t: Throwable) {
                    "<!-- Gagal decode manifest: $t -->"
                }
            }
            openedCode = text
            codeLoading = false
        }
    }

    fun closeFile() {
        openedTitle = null
        openedCode = null
        codeLoading = false
    }

    fun goHome() {
        closeFile()
        closeJadx()
        classNames = emptyList()
        stage = Stage.Home
    }

    private fun closeJadx() {
        try { jadx?.close() } catch (_: Throwable) {}
        jadx = null
        classMap = emptyMap()
    }

    override fun onCleared() {
        closeJadx()
    }
}
