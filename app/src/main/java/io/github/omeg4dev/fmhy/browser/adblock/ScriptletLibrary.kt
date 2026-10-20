package io.github.omeg4dev.fmhy.browser.adblock

import android.content.Context
import android.util.Log
import io.github.omeg4dev.fmhy.data.adblock.Scriptlet
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ScriptletLibrary(
    private val assetLoader: (String) -> String?
) {
    constructor(context: Context) : this({ path ->
        try {
            context.assets.open(path).bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            null
        }
    })

    private val loggedUnknowns = ConcurrentHashMap.newKeySet<String>()

    fun build(scriptlets: List<Scriptlet>): String {
        if (scriptlets.isEmpty()) return ""

        val validScriptlets = mutableListOf<Scriptlet>()
        val neededScriptletNames = LinkedHashSet<String>()

        for (s in scriptlets) {
            val scriptletJs = assetLoader("scriptlets/${s.name}.js")
            if (scriptletJs != null) {
                validScriptlets.add(s)
                neededScriptletNames.add(s.name)
            } else {
                if (loggedUnknowns.add(s.name)) {
                    logW(TAG, "Unknown scriptlet: ${s.name} (total unknown so far: ${loggedUnknowns.size})")
                }
            }
        }

        if (validScriptlets.isEmpty()) return ""

        val sb = StringBuilder()
        sb.append("(function() {\n")

        val helpers = assetLoader("scriptlets/helpers.js")
        if (helpers != null) {
            sb.append(helpers).append("\n")
        }

        for (name in neededScriptletNames) {
            val code = assetLoader("scriptlets/$name.js")
            if (code != null) {
                sb.append(code).append("\n")
            }
        }

        for (s in validScriptlets) {
            val fnName = s.name.replace('-', '_')
            val argsStr = s.args.joinToString(", ") { Json.encodeToString(it) }
            sb.append("try { ").append(fnName).append("(").append(argsStr).append("); } catch (e) {}\n")
        }

        sb.append("})();\n")
        return sb.toString()
    }

    companion object {
        private const val TAG = "FmhyAdblock"

        private fun logW(tag: String, msg: String) {
            try {
                Log.w(tag, msg)
            } catch (_: Throwable) {
                println("WARN: [$tag] $msg")
            }
        }

        fun fromDirectory(dir: File): ScriptletLibrary {
            return ScriptletLibrary { path ->
                val f = File(dir, path)
                if (f.exists()) f.readText() else null
            }
        }
    }
}
