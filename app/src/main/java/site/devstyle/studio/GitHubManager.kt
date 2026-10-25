package site.devstyle.studio

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class GitHubRepo(
    val fullName: String,
    val name: String,
    val isPrivate: Boolean,
    val defaultBranch: String
)

class GitHubManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("github_secure_store", Context.MODE_PRIVATE)
    private val alias = "dev_style_github_token_key"
    private val host = "https://api.github.com"

    fun saveToken(token: String) {
        require(token.trim().isNotEmpty()) { "Token cannot be empty." }
        val bytes = token.trim().toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(bytes)
        prefs.edit().putString("token_iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("token_data", Base64.encodeToString(encrypted, Base64.NO_WRAP)).apply()
    }

    fun hasToken(): Boolean = prefs.contains("token_data")

    fun readToken(): String? {
        val ivText = prefs.getString("token_iv", null) ?: return null
        val dataText = prefs.getString("token_data", null) ?: return null
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, Base64.decode(ivText, Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(dataText, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (_: Exception) { null }
    }

    fun clearToken() { prefs.edit().remove("token_iv").remove("token_data").apply() }

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build())
        return generator.generateKey()
    }

    private fun request(method: String, path: String, body: JSONObject? = null): String {
        val token = readToken() ?: throw IllegalStateException("Save a GitHub token in Settings first.")
        val connection = (URL("$host$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 25000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("User-Agent", "DEV-STYLE-Studio-Android")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            if (body != null) connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val detail = try { JSONObject(text).optString("message", text) } catch (_: Exception) { text }
                throw IllegalStateException("GitHub API ($code): ${detail.take(350)}")
            }
            return text
        } finally { connection.disconnect() }
    }

    fun testConnection(): String {
        val json = JSONObject(request("GET", "/user"))
        return "Connected as @${json.optString("login")}"
    }

    fun listRepositories(): List<GitHubRepo> {
        val result = mutableListOf<GitHubRepo>()
        var page = 1
        while (page <= 5) {
            val arr = JSONArray(request("GET", "/user/repos?sort=updated&per_page=100&page=$page"))
            if (arr.length() == 0) break
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                result += GitHubRepo(
                    o.optString("full_name"), o.optString("name"),
                    o.optBoolean("private"), o.optString("default_branch", "main")
                )
            }
            if (arr.length() < 100) break
            page++
        }
        return result
    }

    fun createRepository(name: String, isPrivate: Boolean): GitHubRepo {
        val clean = name.trim()
        require(clean.matches(Regex("[A-Za-z0-9_.-]{1,100}"))) {
            "Use 1–100 letters, numbers, dots, underscores, or hyphens."
        }
        val body = JSONObject().put("name", clean).put("private", isPrivate)
            .put("auto_init", true).put("description", "Created with DEV STYLE Studio")
        val o = JSONObject(request("POST", "/user/repos", body))
        return GitHubRepo(o.optString("full_name"), o.optString("name"), o.optBoolean("private"), o.optString("default_branch", "main"))
    }

    fun deleteRepository(fullName: String) {
        val parts = fullName.split("/")
        require(parts.size == 2) { "Invalid repository selection." }
        request("DELETE", "/repos/${enc(parts[0])}/${enc(parts[1])}")
    }

    fun pushProject(project: StudioProject, repoFullName: String, commitMessage: String): String {
        val parts = repoFullName.split("/")
        require(parts.size == 2) { "Select a valid repository." }
        val owner = enc(parts[0]); val repo = enc(parts[1])
        val meta = JSONObject(request("GET", "/repos/$owner/$repo"))
        val branch = meta.optString("default_branch").ifBlank { "main" }
        val branchPath = enc(branch)
        val ref = JSONObject(request("GET", "/repos/$owner/$repo/git/ref/heads/$branchPath"))
        val parentSha = ref.getJSONObject("object").getString("sha")
        val parentCommit = JSONObject(request("GET", "/repos/$owner/$repo/git/commits/$parentSha"))
        val baseTree = parentCommit.getJSONObject("tree").getString("sha")
        val root = File(project.path).canonicalFile
        val ignored = setOf(".git", "node_modules", "build", ".gradle", ".idea", "dist")
        val files = root.walkTopDown().onEnter { dir ->
            dir == root || (dir.name !in ignored && !dir.name.startsWith(".devstyle-temp"))
        }.filter { it.isFile && it.name != ".DS_Store" }
            .filter { f -> f.relativeTo(root).path.split(File.separatorChar).none { it in ignored } }
            .filter { f ->
                val n = f.name.lowercase()
                n != "local.properties" && n != "credentials.json" && n != "secrets.json" &&
                    !n.startsWith(".env") && !n.endsWith(".jks") && !n.endsWith(".keystore") &&
                    !n.endsWith(".p12") && !n.endsWith(".pfx") && !n.endsWith(".pem") &&
                    !n.endsWith(".key") && !n.endsWith(".apk") && !n.endsWith(".aab")
            }
            .take(1000).toList()
        require(files.isNotEmpty()) { "There are no project files to push." }
        var totalBytes = 0L
        val entries = JSONArray()
        for (file in files) {
            val relative = file.relativeTo(root).path.replace(File.separatorChar, '/')
            require(!relative.startsWith("/") && relative.split('/').none { it == ".." }) { "Unsafe project path." }
            require(file.length() <= 2_000_000L) { "File is too large for mobile API push: $relative" }
            totalBytes += file.length()
            require(totalBytes <= 15_000_000L) { "Project exceeds the 15 MB push safety limit." }
            val content = file.readText(Charsets.UTF_8)
            require(!content.contains("\u0000")) { "Binary file detected: $relative. Remove it before pushing." }
            val secretPatterns = listOf(
                Regex("-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----"),
                Regex("(?i)gh[pousr]_[A-Za-z0-9]{20,}"),
                Regex("(?i)AIza[0-9A-Za-z_-]{30,}"),
                Regex("""(?i)(api[_-]?key|access[_-]?token|client[_-]?secret|password)\s*[:=]\s*["'][^"']{8,}["']""")
            )
            require(secretPatterns.none { it.containsMatchIn(content) }) {
                "Potential secret detected in $relative. Remove credentials and review the file before pushing."
            }
            val blob = JSONObject(request("POST", "/repos/$owner/$repo/git/blobs",
                JSONObject().put("content", content).put("encoding", "utf-8")))
            entries.put(JSONObject().put("path", relative).put("mode", "100644").put("type", "blob").put("sha", blob.getString("sha")))
        }
        val tree = JSONObject(request("POST", "/repos/$owner/$repo/git/trees",
            JSONObject().put("base_tree", baseTree).put("tree", entries)))
        val message = commitMessage.trim().ifBlank { "Update project with DEV STYLE Studio" }.take(120)
        val commit = JSONObject(request("POST", "/repos/$owner/$repo/git/commits",
            JSONObject().put("message", message).put("tree", tree.getString("sha"))
                .put("parents", JSONArray().put(parentSha))))
        request("PATCH", "/repos/$owner/$repo/git/refs/heads/$branchPath",
            JSONObject().put("sha", commit.getString("sha")).put("force", false))
        return "Pushed ${files.size} files to $repoFullName ($branch). Commit ${commit.getString("sha").take(7)}"
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
