package site.devstyle.studio

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream

private val Lime = Color(0xFFB7FF35)
private val DeepLime = Color(0xFF78C91F)
private val Ink = Color(0xFF24301C)
private val Muted = Color(0xFF788071)
private val Line = Color(0xFFE7EDE0)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.WHITE
        window.navigationBarColor = android.graphics.Color.WHITE
        window.decorView.systemUiVisibility =
            android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
            android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        setContent { StudioApp() }
    }
}

data class StudioProject(val id: String, val name: String, val type: String, val path: String, val updated: Long)

private class ProjectStore(private val context: Context) {
    private val base = File(context.filesDir, "projects").apply { mkdirs() }
    private val index = File(context.filesDir, "projects-index.json")

    fun all(): List<StudioProject> {
        if (!index.exists()) return emptyList()
        return try {
            val arr = JSONArray(index.readText())
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val path = o.optString("path")
                if (!File(path).exists()) null else StudioProject(
                    o.getString("id"), o.getString("name"), o.optString("type", "Source"),
                    path, o.optLong("updated", 0L)
                )
            }.sortedByDescending { it.updated }
        } catch (_: Exception) { emptyList() }
    }

    private fun saveIndex(items: List<StudioProject>) {
        val arr = JSONArray()
        items.forEach { p ->
            arr.put(JSONObject().put("id", p.id).put("name", p.name).put("type", p.type)
                .put("path", p.path).put("updated", p.updated))
        }
        index.writeText(arr.toString())
    }

    fun create(nameRaw: String, type: String): StudioProject {
        val name = nameRaw.trim().ifBlank { "New Project" }.take(60)
        val id = UUID.randomUUID().toString()
        val dir = File(base, id).apply { mkdirs() }
        when (type) {
            "HTML Website" -> File(dir, "index.html").writeText("""<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>$name</title><style>body{font:16px system-ui;margin:0;padding:3rem;background:#fff;color:#24301c}h1{color:#78c91f}button{background:#b7ff35;border:0;border-radius:12px;padding:12px 18px}</style></head>
<body><h1>$name</h1><p>Your project is ready to edit.</p><button onclick="document.querySelector('p').textContent='It works!'">Test interaction</button></body></html>""")
            "React / Vite" -> {
                File(dir, "package.json").writeText("""{"name":"${name.lowercase().replace(Regex("[^a-z0-9-]"), "-")}","version":"1.0.0","type":"module","scripts":{"dev":"vite --host 127.0.0.1","build":"vite"},"dependencies":{"@vitejs/plugin-react":"latest","vite":"latest","react":"latest","react-dom":"latest"},"devDependencies":{}}""")
                File(dir, "index.html").writeText("""<!doctype html><html><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>$name</title></head><body><div id="root"></div><script type="module" src="/src/main.jsx"></script></body></html>""")
                File(dir, "src").mkdirs()
                File(dir, "src/main.jsx").writeText("""import React from 'react';import{createRoot}from'react-dom/client';import './style.css';function App(){return <main><h1>$name</h1><p>Welcome to your Vite project.</p></main>}createRoot(document.getElementById('root')).render(<App/>);""")
                File(dir, "src/style.css").writeText("body{font-family:system-ui;margin:0;padding:3rem;color:#24301c}h1{color:#78c91f}")
            }
            else -> {
                File(dir, "README.md").writeText("# $name\n\nProject created with DEV STYLE Studio.\n\nAdd your source files here.")
                File(dir, ".gitignore").writeText("build/\n.gradle/\nnode_modules/\n.env\n*.jks\n*.keystore\n")
            }
        }
        val p = StudioProject(id, name, type, dir.absolutePath, System.currentTimeMillis())
        saveIndex(all() + p)
        return p
    }

    fun importZip(context: Context, uri: Uri): StudioProject {
        val id = UUID.randomUUID().toString()
        val dir = File(base, id).apply { mkdirs() }
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Cannot open selected archive." }
                ZipInputStream(input).use { zip ->
                    var entry = zip.nextEntry
                    var count = 0
                    while (entry != null) {
                        count++
                        require(count <= 20000) { "Archive contains too many entries." }
                        val raw = entry.name.replace('\\', '/')
                        require(!raw.startsWith("/") && !Regex("^[A-Za-z]:").containsMatchIn(raw)) { "Unsafe absolute path in ZIP." }
                        val parts = raw.split('/')
                        require(parts.none { it == ".." || it == "." }) { "Unsafe path traversal entry in ZIP." }
                        val out = File(dir, raw).canonicalFile
                        require(out.toPath().startsWith(dir.canonicalFile.toPath())) { "ZIP entry escaped project directory." }
                        if (entry.isDirectory) out.mkdirs() else {
                            out.parentFile?.mkdirs()
                            out.outputStream().use { dest -> zip.copyTo(dest) }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }
            val type = detectType(dir)
            val displayName = dir.listFiles()?.firstOrNull()?.name?.substringBeforeLast('.', "Imported Project") ?: "Imported Project"
            val p = StudioProject(id, displayName, type, dir.absolutePath, System.currentTimeMillis())
            saveIndex(all() + p)
            return p
        } catch (e: Exception) {
            dir.deleteRecursively()
            throw e
        }
    }

    private fun detectType(dir: File): String {
        val files = dir.walkTopDown().take(5000).map { it.name }.toSet()
        return when {
            "settings.gradle" in files || "settings.gradle.kts" in files || "AndroidManifest.xml" in files -> "Android / Gradle"
            "package.json" in files -> "JavaScript / Node"
            "index.html" in files -> "HTML Website"
            else -> "Source Code"
        }
    }

    fun files(project: StudioProject): List<File> =
        File(project.path).walkTopDown().filter { it.isFile }.take(3000).toList().sortedBy { it.relativeTo(File(project.path)).path }

    fun save(project: StudioProject) { saveIndex(all().map { if (it.id == project.id) it.copy(updated = System.currentTimeMillis()) else it }) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudioApp() {
    val context = LocalContext.current
    val store = remember { ProjectStore(context) }
    val scope = rememberCoroutineScope()
    var projects by remember { mutableStateOf(store.all()) }
    var selected by remember { mutableStateOf<StudioProject?>(null) }
    var currentFile by remember { mutableStateOf<File?>(null) }
    var editorText by remember { mutableStateOf("") }
    var tab by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf(false) }
    var projectName by remember { mutableStateOf("") }
    var projectType by remember { mutableStateOf("HTML Website") }
    var message by remember { mutableStateOf<String?>(null) }
    var showPreview by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val p = withContext(Dispatchers.IO) { store.importZip(context, uri) }
                projects = store.all(); selected = p; tab = 1
                message = "Imported ${p.name}"
            } catch (e: Exception) { message = "Import failed: ${e.message ?: "invalid ZIP"}" }
        }
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = DeepLime, onPrimary = Ink, secondary = Lime, background = Color.White,
            surface = Color.White, onSurface = Ink, onBackground = Ink
        )
    ) {
        Scaffold(
            containerColor = Color.White,
            topBar = {
                if (selected == null) TopAppBar(
                    title = { Column { Text("DEV STYLE", fontWeight = FontWeight.Black, letterSpacing = 1.4.sp); Text("STUDIO", color = DeepLime, fontSize = 11.sp, letterSpacing = 3.sp) } },
                    actions = { IconButton(onClick = { importLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) }) { Icon(Icons.Default.FileOpen, "Import ZIP") } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                ) else TopAppBar(
                    title = { Column { Text(selected!!.name, fontWeight = FontWeight.Bold); Text(selected!!.type, fontSize = 11.sp, color = Muted) } },
                    navigationIcon = { IconButton(onClick = { selected = null; currentFile = null; showPreview = false; projects = store.all() }) { Icon(Icons.Default.ArrowBack, "Back") } },
                    actions = {
                        IconButton(onClick = { if (selected != null) { currentFile = File(selected!!.path, "index.html"); if (!currentFile!!.exists()) message = "No index.html found. Open a file from the project explorer."; else { editorText = currentFile!!.readText(); showPreview = true } } }) { Icon(Icons.Default.Visibility, "Preview") }
                        IconButton(onClick = {
                            val f = currentFile
                            if (f != null && selected != null) scope.launch {
                                try { withContext(Dispatchers.IO) { f.writeText(editorText); store.save(selected!!) }; message = "Saved ${f.name}" }
                                catch (e: Exception) { message = "Save failed: ${e.message}" }
                            } else message = "Select a file first."
                        }) { Icon(Icons.Default.Save, "Save") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
            },
            bottomBar = {
                if (selected == null) NavigationBar(containerColor = Color.White, tonalElevation = 4.dp) {
                    val labels = listOf("Home", "Projects", "AI Chat", "Automations", "Settings")
                    val icons = listOf(Icons.Default.Home, Icons.Default.Folder, Icons.Default.SmartToy, Icons.Default.Bolt, Icons.Default.Settings)
                    labels.forEachIndexed { i, label ->
                        NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(icons[i], label) },
                            label = { Text(label, fontSize = 10.sp) }, colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Ink, selectedTextColor = Ink, indicatorColor = Lime,
                                unselectedIconColor = Muted, unselectedTextColor = Muted
                            ))
                    }
                } else {
                    Surface(color = Color.White, shadowElevation = 8.dp) {
                        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { selected = null; currentFile = null; showPreview = false }, modifier = Modifier.weight(1f)) { Text("Files") }
                            Button(onClick = {
                                val f = currentFile
                                if (f != null) scope.launch { try { withContext(Dispatchers.IO) { f.writeText(editorText); store.save(selected!!) }; message = "Saved" } catch (e: Exception) { message = e.message } }
                            }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Ink)) { Text("Save") }
                            Button(onClick = { showPreview = true }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = DeepLime, contentColor = Color.White)) { Text("Preview") }
                        }
                    }
                }
            },
            snackbarHost = { SnackbarHost(hostState = remember { SnackbarHostState() }) }
        ) { padding ->
            if (selected != null) {
                if (showPreview) {
                    val html = File(selected!!.path, "index.html")
                    if (html.exists()) AndroidView(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        factory = { ctx -> WebView(ctx).apply {
                            settings.javaScriptEnabled = false
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            webViewClient = WebViewClient()
                            loadUrl("file://${html.absolutePath}")
                        } }
                    ) else Column(Modifier.fillMaxSize().padding(24.dp).padding(padding), verticalArrangement = Arrangement.Center) {
                        Text("This project has no index.html entry point.", fontWeight = FontWeight.Bold)
                        Text("Android projects need a build/install or resource preview pipeline; they cannot run inside WebView.", color = Muted)
                    }
                } else if (currentFile != null) {
                    Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
                        Text(currentFile!!.relativeTo(File(selected!!.path)).path, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = DeepLime, fontSize = 12.sp)
                        BasicTextField(
                            value = editorText, onValueChange = { editorText = it },
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(color = Ink, fontSize = 14.sp, lineHeight = 21.sp)
                        )
                    }
                } else {
                    val files = remember(selected) { store.files(selected!!) }
                    Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                        Text("Project files", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("Tap a file to edit. Source files remain in app storage.", color = Muted, fontSize = 12.sp)
                        Spacer(Modifier.height(12.dp))
                        LazyColumn {
                            items(files, key = { it.absolutePath }) { f ->
                                Row(Modifier.fillMaxWidth().clickable {
                                    try { currentFile = f; editorText = f.readText(); showPreview = false }
                                    catch (e: Exception) { message = "Cannot open file: ${e.message}" }
                                }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.InsertDriveFile, null, tint = DeepLime)
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(f.relativeTo(File(selected!!.path)).path, fontSize = 14.sp)
                                        Text("${f.length()} bytes", color = Muted, fontSize = 11.sp)
                                    }
                                    Icon(Icons.Default.ChevronRight, null, tint = Muted)
                                }
                                HorizontalDivider(color = Line)
                            }
                        }
                    }
                }
            } else when (tab) {
                0 -> HomeScreen(
                    projects = projects,
                    onCreate = { dialog = true },
                    onImport = { importLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
                    onOpen = { selected = it; currentFile = null; showPreview = false }
                )
                1 -> ProjectsScreen(projects, onOpen = { selected = it }, onImport = { importLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) }, onCreate = { dialog = true })
                2 -> InfoScreen("AI Chat", "Connect an AI provider in a future implementation phase. No fake chat or generated patch is presented as working.", Icons.Default.SmartToy)
                3 -> InfoScreen("Automations", "Email automation and deployment integrations are not implemented in this starter phase. No background action runs without configuration.", Icons.Default.Bolt)
                else -> GitHubSettingsScreen(
                    projects = projects,
                    onRefreshProjects = { projects = store.all() },
                    onMessage = { message = it }
                )
            }
        }
        if (dialog) AlertDialog(
            onDismissRequest = { dialog = false },
            title = { Text("Create project", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(projectName, { projectName = it }, label = { Text("Project name") }, singleLine = true)
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                        OutlinedTextField(projectType, {}, readOnly = true, label = { Text("Project type") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.menuAnchor())
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            listOf("HTML Website", "React / Vite", "Android / Gradle", "General Source").forEach { option ->
                                DropdownMenuItem(text = { Text(option) }, onClick = { projectType = option; expanded = false })
                            }
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = {
                try { val p = store.create(projectName, projectType); projects = store.all(); selected = p; currentFile = null; dialog = false; projectName = ""; message = "Project created" }
                catch (e: Exception) { message = "Create failed: ${e.message}" }
            }, colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Ink)) { Text("Create") } },
            dismissButton = { TextButton(onClick = { dialog = false }) { Text("Cancel") } }
        )
        message?.let { msg ->
            AlertDialog(onDismissRequest = { message = null }, title = { Text("DEV STYLE Studio") }, text = { Text(msg) }, confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } })
        }
    }
}

@Composable
private fun HomeScreen(projects: List<StudioProject>, onCreate: () -> Unit, onImport: () -> Unit, onOpen: (StudioProject) -> Unit) {
    val pulse = androidx.compose.animation.core.rememberInfiniteTransition(label = "studioPulse")
    val glow by pulse.animateFloat(initialValue = 0.25f, targetValue = 0.75f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1300, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse), label = "glow")
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFFFAFCF5)).padding(12.dp)) {
        val wide = maxWidth > 650.dp
        if (wide) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1.9f).fillMaxHeight()) {
                StudioBrandHeader(glow)
                Spacer(Modifier.height(12.dp))
                FlowMapCard(glow, onCreate, onImport)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionPill("▶  Start Preview", onClick = { projects.firstOrNull()?.let(onOpen) }, Modifier.weight(1f))
                    ActionPill("⌕  Scan Project", onClick = onImport, Modifier.weight(1f))
                    ActionPill("＋  New Project", onClick = onCreate, Modifier.weight(1f))
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight().background(Color.White, RoundedCornerShape(24.dp)).padding(14.dp)) {
                Text("APPLICATION MODULES", fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 1.sp)
                Spacer(Modifier.height(10.dp))
                ModuleRow(Icons.Default.Extension, "Features & API Routes", "Project structure and file routing")
                ModuleRow(Icons.Default.Memory, "Local Workspace", "Files stored privately on device")
                ModuleRow(Icons.Default.Security, "ZIP Safety", "Path traversal checks on import")
                ModuleRow(Icons.Default.CloudUpload, "GitHub Manager", "Secure token · Repo · Push")
                ModuleRow(Icons.Default.Visibility, "Preview Engine", "Static HTML preview supported")
                Spacer(Modifier.height(10.dp))
                Text("RECENT PROJECTS  ·  ${projects.size}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = DeepLime)
                Spacer(Modifier.height(6.dp))
                if (projects.isEmpty()) Text("No projects yet. Import a ZIP or create a workspace.", color = Muted, fontSize = 12.sp)
                else LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) { items(projects.take(5), key = { it.id }) { ProjectRow(it, onOpen) } }
            }
        } else Column(Modifier.fillMaxSize()) {
            StudioBrandHeader(glow)
            Spacer(Modifier.height(12.dp))
            FlowMapCard(glow, onCreate, onImport)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionPill("＋ Create", onCreate, Modifier.weight(1f))
                ActionPill("⇧ Import ZIP", onImport, Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Recent projects", fontWeight = FontWeight.Black, fontSize = 19.sp, modifier = Modifier.weight(1f))
                Text("${projects.size} total", color = Muted, fontSize = 12.sp)
            }
            Spacer(Modifier.height(8.dp))
            if (projects.isEmpty()) Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    StudioLogo(54.dp, glow)
                    Spacer(Modifier.height(10.dp)); Text("Your workspace is ready", fontWeight = FontWeight.Bold)
                    Text("Create a project or import source ZIP.", color = Muted, fontSize = 12.sp)
                }
            } else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(projects.take(8), key = { it.id }) { ProjectRow(it, onOpen) } }
        }
    }
}

@Composable
private fun StudioBrandHeader(glow: Float) {
    Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp)).padding(horizontal = 15.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        StudioLogo(42.dp, glow); Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("DEV", fontWeight = FontWeight.Black, fontSize = 22.sp, color = Ink, modifier = Modifier.background(Lime.copy(alpha = .65f), RoundedCornerShape(6.dp)).padding(horizontal = 5.dp))
                Text("STYLE Studio", fontWeight = FontWeight.Black, fontSize = 22.sp, color = Ink)
            }
            Text("AI-POWERED MOBILE DEVELOPMENT WORKSPACE", fontSize = 9.sp, letterSpacing = .8.sp, color = Muted)
        }
        Surface(color = Color(0xFFF2F9E4), shape = RoundedCornerShape(20.dp)) {
            Row(Modifier.padding(horizontal = 9.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).background(DeepLime.copy(alpha = glow), RoundedCornerShape(50)))
                Spacer(Modifier.width(5.dp)); Text("READY", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Ink)
            }
        }
    }
}

@Composable
private fun StudioLogo(size: androidx.compose.ui.unit.Dp, glow: Float) {
    Box(Modifier.size(size).background(Lime.copy(alpha = .45f + glow * .25f), RoundedCornerShape(size * .28f)), contentAlignment = Alignment.Center) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(3) { i -> Box(Modifier.width(size * .12f).height(size * (.38f + i * .09f)).background(Ink, RoundedCornerShape(5.dp))) }
        }
    }
}

@Composable
private fun FlowMapCard(glow: Float, onCreate: () -> Unit, onImport: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(24.dp)).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("WORKSPACE FLOW", fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.3.sp, modifier = Modifier.weight(1f))
            Text("LIVE UI", color = DeepLime, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FlowNode("⇧", "IMPORT &\nCREATE", true, glow, Modifier.weight(1f), onImport)
            Text("→", fontSize = 18.sp, color = Muted)
            FlowNode("▤", "ZIP / SAF\nIMPORT", true, glow, Modifier.weight(1f), onImport)
            Text("→", fontSize = 18.sp, color = Muted)
            FlowNode("▧", "10+\nTEMPLATES", false, glow, Modifier.weight(1f), onCreate)
        }
        Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) { Text("↓                         ↓                         ↓", color = Muted, fontSize = 13.sp) }
        Surface(Modifier.fillMaxWidth(), color = Color(0xFFF9FBEF), shape = RoundedCornerShape(20.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
            Column(Modifier.padding(10.dp)) {
                Text("CORE IDE WORKSPACE", fontWeight = FontWeight.Black, fontSize = 11.sp, color = Ink)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FlowNode("✦", "AI ASSISTANT\n& SCANNER", false, glow, Modifier.weight(1f), onImport)
                    FlowNode("✎", "CODE EDITOR\n+ FILE TREE", true, glow, Modifier.weight(1f), onCreate)
                    FlowNode("▣", "PREVIEW\nENGINE", true, glow, Modifier.weight(1f), onImport)
                }
                Spacer(Modifier.height(9.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModuleChip("GitHub · Push / Pull", Modifier.weight(1f))
                    ModuleChip("WebView · Static HTML", Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Working features are connected to real project files. Unsupported runtimes are clearly labelled instead of simulated.", color = Muted, fontSize = 10.sp, lineHeight = 14.sp)
    }
}

@Composable
private fun FlowNode(symbol: String, title: String, active: Boolean, glow: Float, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val bg = if (active) Lime.copy(alpha = .48f + glow * .24f) else Color(0xFFF4F6F0)
    Surface(modifier = modifier.heightIn(min = 66.dp).clickable { onClick() }, shape = RoundedCornerShape(16.dp), color = bg,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (active) DeepLime.copy(alpha = .4f + glow * .3f) else Line), shadowElevation = if (active) 2.dp else 0.dp) {
        Column(Modifier.padding(7.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(symbol, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Ink)
            Spacer(Modifier.height(4.dp)); Text(title, fontSize = 9.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold, color = Ink)
        }
    }
}

@Composable
private fun ModuleChip(text: String, modifier: Modifier = Modifier) {
    Surface(modifier, color = Color.White, shape = RoundedCornerShape(12.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
        Text(text, Modifier.padding(horizontal = 8.dp, vertical = 10.dp), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Ink)
    }
}

@Composable
private fun ModuleRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(35.dp).background(Color(0xFFF1F6E6), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Ink, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.width(9.dp)); Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp); Text(subtitle, fontSize = 10.sp, color = Muted) }
    }
    HorizontalDivider(color = Line)
}

@Composable
private fun ActionPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.clickable { onClick() }, color = Color.White, shape = RoundedCornerShape(15.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Line), shadowElevation = 1.dp) {
        Text(text, Modifier.padding(horizontal = 8.dp, vertical = 12.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Ink)
    }
}

@Composable
private fun ProjectsScreen(projects: List<StudioProject>, onOpen: (StudioProject) -> Unit, onImport: () -> Unit, onCreate: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Text("Projects", fontSize = 28.sp, fontWeight = FontWeight.Black)
        Text("Your imported and created workspaces", color = Muted)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onCreate, colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Ink)) { Text("New project") }
            OutlinedButton(onClick = onImport) { Text("Import ZIP") }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(projects, key = { it.id }) { ProjectRow(it, onOpen) } }
    }
}

@Composable
private fun ProjectRow(project: StudioProject, onOpen: (StudioProject) -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Line), color = Color.White,
        modifier = Modifier.fillMaxWidth().clickable { onOpen(project) }) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).background(Lime.copy(alpha = 0.35f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(if (project.type.contains("Android")) Icons.Default.Android else if (project.type.contains("HTML")) Icons.Default.Language else Icons.Default.Code, null, tint = Ink)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(project.name, fontWeight = FontWeight.Bold)
                Text(project.type, color = Muted, fontSize = 12.sp)
                Text(File(project.path).name, color = Muted, fontSize = 10.sp)
            }
            Icon(Icons.Default.ChevronRight, null, tint = Muted)
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GitHubSettingsScreen(
    projects: List<StudioProject>,
    onRefreshProjects: () -> Unit,
    onMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val manager = remember { GitHubManager(context) }
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf("") }
    var connected by remember { mutableStateOf(manager.hasToken()) }
    var status by remember { mutableStateOf(if (connected) "Token saved securely on this device." else "GitHub is not connected.") }
    var repos by remember { mutableStateOf<List<GitHubRepo>>(emptyList()) }
    var selectedRepo by remember { mutableStateOf<GitHubRepo?>(null) }
    var selectedProject by remember { mutableStateOf<StudioProject?>(projects.firstOrNull()) }
    var projectMenu by remember { mutableStateOf(false) }
    var repoMenu by remember { mutableStateOf(false) }
    var repoName by remember { mutableStateOf("") }
    var commitMessage by remember { mutableStateOf("Update project with DEV STYLE Studio") }
    var isPrivate by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Text("Settings", fontSize = 28.sp, fontWeight = FontWeight.Black)
        Text("GitHub connection & source publishing", color = Muted)
        Spacer(Modifier.height(16.dp))
        Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFFF8FBEF), border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Code, null, tint = DeepLime)
                    Spacer(Modifier.width(8.dp))
                    Text("GitHub Access", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Spacer(Modifier.weight(1f))
                    Text(if (connected) "TOKEN SAVED" else "NOT CONNECTED", fontSize = 10.sp, color = if (connected) DeepLime else Muted, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                Text("Create a fine-grained token with repository access, or a classic token with the repo scope. The token is encrypted with Android Keystore and is never shown again after saving.", color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = token, onValueChange = { token = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Personal Access Token") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    placeholder = { Text(if (connected) "Enter a new token to replace saved token" else "github_pat_…") }
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        try {
                            manager.saveToken(token); token = ""; connected = true
                            status = "Token encrypted and saved."
                            onMessage("GitHub token saved securely.")
                        } catch (e: Exception) { status = "Save failed: ${e.message}" }
                    }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Ink)) { Text("Save token") }
                    OutlinedButton(onClick = {
                        scope.launch {
                            busy = true
                            try { status = withContext(Dispatchers.IO) { manager.testConnection() }; connected = true }
                            catch (e: Exception) { status = e.message ?: "Connection failed" }
                            finally { busy = false }
                        }
                    }, enabled = connected && !busy, modifier = Modifier.weight(1f)) { Text("Test") }
                }
                if (connected) TextButton(onClick = {
                    manager.clearToken(); connected = false; repos = emptyList(); selectedRepo = null
                    status = "Token removed from this device."
                }) { Text("Disconnect / clear token", color = Color(0xFFB54735)) }
                Text(status, color = Muted, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Repositories", fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = {
                scope.launch {
                    busy = true
                    try { repos = withContext(Dispatchers.IO) { manager.listRepositories() }; status = "Loaded ${repos.size} repositories." }
                    catch (e: Exception) { status = e.message ?: "Could not load repositories." }
                    finally { busy = false }
                }
            }, enabled = connected && !busy) { Text("Refresh") }
        }
        Spacer(Modifier.height(8.dp))
        ExposedDropdownMenuBox(expanded = repoMenu, onExpandedChange = { repoMenu = !repoMenu }) {
            OutlinedTextField(
                value = selectedRepo?.let { "${it.fullName} · ${if (it.isPrivate) "Private" else "Public"}" } ?: "Select a repository",
                onValueChange = {}, readOnly = true, modifier = Modifier.fillMaxWidth().menuAnchor(),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(repoMenu) }
            )
            ExposedDropdownMenu(expanded = repoMenu, onDismissRequest = { repoMenu = false }) {
                repos.forEach { repo ->
                    DropdownMenuItem(text = { Text(repo.fullName + if (repo.isPrivate) " · Private" else " · Public") }, onClick = { selectedRepo = repo; repoMenu = false })
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(repoName, { repoName = it }, modifier = Modifier.fillMaxWidth(), label = { Text("New repository name") }, singleLine = true)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = isPrivate, onCheckedChange = { isPrivate = it })
            Text("Create as private repository")
            Spacer(Modifier.weight(1f))
            Button(onClick = {
                scope.launch {
                    busy = true
                    try {
                        val created = withContext(Dispatchers.IO) { manager.createRepository(repoName, isPrivate) }
                        repos = (listOf(created) + repos.filterNot { it.fullName == created.fullName }).sortedBy { it.fullName.lowercase() }
                        selectedRepo = created; repoName = ""; status = "Created ${created.fullName}"
                    } catch (e: Exception) { status = e.message ?: "Repository creation failed." }
                    finally { busy = false }
                }
            }, enabled = connected && !busy && repoName.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Ink)) { Text("Create") }
        }
        Spacer(Modifier.height(12.dp))
        Text("Push a project", fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        ExposedDropdownMenuBox(expanded = projectMenu, onExpandedChange = { projectMenu = !projectMenu }) {
            OutlinedTextField(
                value = selectedProject?.name ?: "Select an imported project",
                onValueChange = {}, readOnly = true, modifier = Modifier.fillMaxWidth().menuAnchor(),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(projectMenu) }
            )
            ExposedDropdownMenu(expanded = projectMenu, onDismissRequest = { projectMenu = false }) {
                projects.forEach { project ->
                    DropdownMenuItem(text = { Text("${project.name} · ${project.type}") }, onClick = { selectedProject = project; projectMenu = false })
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(commitMessage, { commitMessage = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Commit message") }, singleLine = true)
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            val project = selectedProject
            val repo = selectedRepo
            if (project == null || repo == null) { status = "Select both a project and a repository."; return@Button }
            scope.launch {
                busy = true
                try {
                    status = withContext(Dispatchers.IO) { manager.pushProject(project, repo.fullName, commitMessage) }
                    onMessage(status)
                } catch (e: Exception) { status = e.message ?: "Push failed." }
                finally { busy = false }
            }
        }, enabled = connected && !busy && selectedProject != null && selectedRepo != null,
            modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = DeepLime, contentColor = Color.White)) {
            if (busy) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
            else Icon(Icons.Default.CloudUpload, null)
            Spacer(Modifier.width(8.dp)); Text(if (busy) "Working…" else "Push Project to GitHub")
        }
        OutlinedButton(onClick = { showDeleteConfirm = true }, enabled = connected && selectedRepo != null && !busy, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.DeleteOutline, null, tint = Color(0xFFB54735)); Spacer(Modifier.width(8.dp)); Text("Delete selected repository", color = Color(0xFFB54735))
        }
        Text("Status: $status", color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
        Text("Push safety: skips .git, node_modules, build, .gradle, .idea and dist; max 1,000 files / 15 MB total. Binary files are rejected. Review the selected repository before publishing.", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
    }
    if (showDeleteConfirm) AlertDialog(
        onDismissRequest = { showDeleteConfirm = false },
        title = { Text("Delete GitHub repository?") },
        text = { Text("This permanently deletes ${selectedRepo?.fullName} from GitHub. This cannot be undone. Type confirmation is not supported here, so proceed only if you are certain.") },
        confirmButton = {
            TextButton(onClick = {
                val repo = selectedRepo
                showDeleteConfirm = false
                if (repo != null) scope.launch {
                    busy = true
                    try {
                        withContext(Dispatchers.IO) { manager.deleteRepository(repo.fullName) }
                        repos = repos.filterNot { it.fullName == repo.fullName }; selectedRepo = null
                        status = "Deleted ${repo.fullName}"
                    } catch (e: Exception) { status = e.message ?: "Delete failed." }
                    finally { busy = false }
                }
            }) { Text("Delete permanently", color = Color(0xFFB54735)) }
        },
        dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
    )
}

@Composable
private fun InfoScreen(title: String, body: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(76.dp).background(Lime.copy(alpha = .35f), RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, modifier = Modifier.size(38.dp), tint = Ink) }
        Spacer(Modifier.height(18.dp))
        Text(title, fontSize = 25.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        Text(body, color = Muted, lineHeight = 22.sp)
    }
}
