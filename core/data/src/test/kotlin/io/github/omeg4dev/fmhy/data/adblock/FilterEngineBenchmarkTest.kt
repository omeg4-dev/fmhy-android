package io.github.omeg4dev.fmhy.data.adblock

import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.random.Random
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test

class FilterEngineBenchmarkTest {

    private fun findFiltersDir(): File? {
        var cur: File? = File(System.getProperty("user.dir")).canonicalFile
        while (cur != null) {
            val target = File(cur, ".cache/ref/filters")
            if (target.exists() && target.isDirectory) return target
            cur = cur.parentFile
        }
        return null
    }

    @Test
    fun benchmarkRealLists() {
        val filtersDir = findFiltersDir()
        assumeNotNull("Skipping benchmark: .cache/ref/filters not found", filtersDir)
        assumeTrue(filtersDir!!.exists())

        val listFiles = listOf(
            "ublock-filters" to File(filtersDir, "ublock-filters.txt"),
            "easylist" to File(filtersDir, "easylist.txt"),
            "easyprivacy" to File(filtersDir, "easyprivacy.txt"),
            "peter-lowe" to File(filtersDir, "peter-lowe.txt"),
            "fmhy-filterlist" to File(filtersDir, "fmhy-filterlist.txt"),
            "fmhy-ublock-only" to File(filtersDir, "fmhy-ublock-only.txt"),
        )

        for ((_, file) in listFiles) {
            assumeTrue("Missing filter file: ${file.path}", file.exists())
        }

        println("=== Starting FilterEngine Benchmark ===")
        val builder = FilterEngineBuilder()
        for ((id, file) in listFiles) {
            builder.addList(id, file.readText())
        }

        val t0 = System.nanoTime()
        val engine = builder.build()
        val buildDurationNs = System.nanoTime() - t0
        val buildDurationMs = buildDurationNs / 1_000_000.0

        val baos = ByteArrayOutputStream()
        engine.writeTo(baos)
        val serializedSize = baos.size()

        val stats = engine.stats
        val totalRules = stats.networkRules + stats.cosmeticRules

        println("Network rules: ${stats.networkRules}")
        println("Cosmetic rules: ${stats.cosmeticRules}")
        println("Scriptlet rules: ${stats.scriptletRules}")
        println("Ignored rules breakdown: ${stats.ignored}")
        println("Build time: ${buildDurationMs} ms (${buildDurationMs / 1000.0} s)")
        println("Serialized size: $serializedSize bytes (${serializedSize / 1024.0 / 1024.0} MB)")

        // Extract hosts from lists' ||host^ rules for realistic test URLs
        val blockedHosts = ArrayList<String>()
        val peterLowe = File(filtersDir, "peter-lowe.txt").readLines()
        for (line in peterLowe) {
            val t = line.trim()
            if (t.startsWith("||") && t.endsWith("^") && !t.contains('/')) {
                blockedHosts.add(t.substring(2, t.length - 1))
            }
        }
        if (blockedHosts.isEmpty()) {
            blockedHosts.add("pagead2.googlesyndication.com")
            blockedHosts.add("google-analytics.com")
        }

        // Generate 20,000 requests: 50% blocked hosts, 50% ordinary clean URLs
        val rng = Random(42)
        val requestPool = ArrayList<FilterRequest>(20_000)
        for (i in 0 until 10_000) {
            val host = blockedHosts[rng.nextInt(blockedHosts.size)]
            val path = "/path/to/resource_${rng.nextInt(1000)}.js"
            requestPool.add(FilterRequest("https://$host$path", "https://example.org/", ResourceType.SCRIPT))
        }
        for (i in 0 until 10_000) {
            val path = "/lib/app_${rng.nextInt(1000)}.js"
            requestPool.add(FilterRequest("https://cdn.example.org$path", "https://example.org/", ResourceType.SCRIPT))
        }
        requestPool.shuffle(rng)

        // Warm up JIT
        for (i in 0 until 2_000) {
            engine.match(requestPool[i])
        }

        // Measure individual request times
        val latenciesNs = LongArray(20_000)
        for (i in 0 until 20_000) {
            val req = requestPool[i]
            val start = System.nanoTime()
            engine.match(req)
            val elapsed = System.nanoTime() - start
            latenciesNs[i] = elapsed
        }

        latenciesNs.sort()
        val medianNs = latenciesNs[10_000]
        val p99Ns = latenciesNs[19_800]
        val medianUs = medianNs / 1_000.0
        val p99Us = p99Ns / 1_000.0
        val p99Ms = p99Ns / 1_000_000.0

        println("Match latency over 20,000 requests:")
        println("  Median: ${medianUs} µs")
        println("  p99:    ${p99Us} µs (${p99Ms} ms)")
        println("========================================")

        // Assert benchmark requirements:
        // 1. network+cosmetic rules > 100,000
        assertTrue("Expected > 100,000 rules but got $totalRules", totalRules > 100_000)

        // 2. build < 20 s
        assertTrue("Expected build < 20s but took ${buildDurationMs / 1000.0} s", buildDurationMs < 20_000.0)

        // 3. median < 100 µs
        assertTrue("Expected median < 100 µs but got ${medianUs} µs", medianUs < 100.0)

        // 4. p99 < 2 ms
        assertTrue("Expected p99 < 2 ms but got ${p99Ms} ms (${p99Us} µs)", p99Ms < 2.0)

        // Real cases assertions:
        // a. https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js (SCRIPT from https://example.org/) -> Block
        val resPagead = engine.match(FilterRequest(
            "https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js",
            "https://example.org/",
            ResourceType.SCRIPT
        ))
        assertTrue("Expected pagead to be blocked, got: $resPagead", resPagead is MatchResult.Block)

        // b. https://www.google-analytics.com/analytics.js -> Block or Redirect
        val resGa = engine.match(FilterRequest(
            "https://www.google-analytics.com/analytics.js",
            "https://example.org/",
            ResourceType.SCRIPT
        ))
        assertTrue(
            "Expected google-analytics to be Block or Redirect, got: $resGa",
            resGa is MatchResult.Block || resGa is MatchResult.Redirect
        )

        // c. https://example.org/style.css -> NoMatch
        val resClean = engine.match(FilterRequest(
            "https://example.org/style.css",
            "https://example.org/",
            ResourceType.STYLESHEET
        ))
        assertTrue("Expected example.org style.css to be NoMatch, got: $resClean", resClean is MatchResult.NoMatch)

        // d. a host from fmhy-filterlist.txt as DOCUMENT -> Block
        val resFmhy = engine.match(FilterRequest(
            "https://igg-games.com/",
            "https://igg-games.com/",
            ResourceType.DOCUMENT
        ))
        assertTrue("Expected fmhy host igg-games.com as DOCUMENT to be Block, got: $resFmhy", resFmhy is MatchResult.Block)
    }
}
