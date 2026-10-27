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

/** Teks error lengkap: rantai penyebab + sebagian stack trace, supaya gampang dilaporkan. */
fun describe(t: Throwable): String = buildString {
    var cur: Throwable? = t
    var depth = 0
    while (cur != null && depth < 6) {
        if (depth > 0) append("Caused by: ")
        append(cur.javaClass.name).append(": ").append(cur.message).append('\n')
        cur.stackTrace.take(8).forEach { append("    at ").append(it).append('\n') }
        val next = cur.cause
        cur = if (next === cur) null else next
        depth++
    }
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
    /** Terisi kalau XML parser jadx gagal init di HP ini (resource jadx dimatikan). */
    var xmlWarning by mutableStateOf<String?>(null)
        private set

    private var jadx: JadxDecompiler? = null
    private var classMap: Map<String, JavaClass> = emptyMap()
    private var apkFile: File? = null

    private val ctx get() = getApplication<Application>()

    companion object {
        private var xmlChecked = false
        private var xmlProblem: String? = null

        /** Dicek sekali per proses supaya penyebab aslinya (bukan NoClassDefFoundError lanjutan) tersimpan. */
        private fun xmlSecurityProblem(): String? {
            if (!xmlChecked) {
                xmlProblem = try {
                    Class.forName("jadx.core.xmlgen.XmlSecurity")
                    null
                } catch (t: Throwable) {
                    describe(t)
                }
                xmlChecked = true
            }
            return xmlProblem
        }
    }

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
                apkFile = file
                stage = Stage.Loading("Parsing DEX & menyusun daftar class… (APK besar bisa lama)")
                val (d, map, problem) = withContext(Dispatchers.Default) {
                    val xmlProblem = xmlSecurityProblem()
                    val args = JadxArgs()
                    args.setInputFile(file)
                    args.setOutDir(File(ctx.cacheDir, "jadx_out").apply { mkdirs() })
                    args.setThreadsCount(2)
                    args.setDebugInfo(false)
                    args.setShowInconsistentCode(true)
                    args.setDeobfuscationOn(false)
                    // Kalau parser XML jadx tidak jalan di Android, lewati pemrosesan resource.
                    args.setSkipResources(xmlProblem != null)
                    val dec = JadxDecompiler(args)
                    dec.load()
                    val m = dec.classes.sortedBy { it.fullName }.associateBy { it.fullName }
                    Triple(dec, m, xmlProblem)
                }
                jadx = d
                classMap = map
                classNames = map.keys.toList()
                xmlWarning = problem
                openedTitle = null
                openedCode = null
                stage = Stage.Browse
            } catch (t: Throwable) {
                closeJadx()
                stage = Stage.Error(
                    if (t is OutOfMemoryError) "Memori habis. APK terlalu besar untuk di-load penuh."
                    else describe(t)
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
                try {
                    cls.code
                } catch (t: Throwable) {
                    "// Gagal decompile class ini:\n" + describe(t).lines().joinToString("\n") { "// $it" }
                }
            }
            openedCode = code
            codeLoading = false
        }
    }

    /** Manifest di-decode pakai decoder sendiri (tidak lewat jadx). */
    fun openManifest() {
        val f = apkFile ?: return
        openedTitle = "AndroidManifest.xml"
        openedCode = null
        codeLoading = true
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                try {
                    ManifestDecoder.decode(f)
                } catch (t: Throwable) {
                    "<!-- Gagal decode manifest:\n${describe(t)}-->"
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
        xmlWarning = null
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
