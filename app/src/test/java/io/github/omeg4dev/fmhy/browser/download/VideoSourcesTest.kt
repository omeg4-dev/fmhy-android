package io.github.omeg4dev.fmhy.browser.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoSourcesTest {

    @Test
    fun testMp4Accepted() {
        val json = """[{"src": "https://example.com/video.mp4", "type": "video/mp4"}]"""
        val sources = VideoSources.parse(json, "https://example.com")
        assertEquals(1, sources.size)
        assertEquals("https://example.com/video.mp4", sources[0].url)
        assertEquals("video/mp4", sources[0].mimeType)
        assertEquals("MP4", sources[0].label)
    }

    @Test
    fun testRelativeSrcResolvedAgainstPageUrl() {
        val json = """[{"src": "media/video.mp4"}]"""
        val sources = VideoSources.parse(json, "https://example.com/page/")
        assertEquals(1, sources.size)
        assertEquals("https://example.com/page/media/video.mp4", sources[0].url)
    }

    @Test
    fun testBlobAndDataDropped() {
        val json = """
            [
                {"src": "blob:https://example.com/1234-5678"},
                {"src": "data:video/mp4;base64,AAAA"},
                {"src": "mediasource:https://example.com/ms"}
            ]
        """.trimIndent()
        val sources = VideoSources.parse(json, "https://example.com")
        assertTrue(sources.isEmpty())
    }

    @Test
    fun testStreamedFormatsDropped() {
        val json = """
            [
                {"src": "https://example.com/stream.m3u8"},
                {"src": "https://example.com/stream.m3u8?token=xyz#live"},
                {"src": "https://example.com/manifest.mpd"},
                {"src": "https://example.com/stream.ism"},
                {"src": "https://example.com/video.f4m"},
                {"src": "https://example.com/custom_stream", "type": "application/x-mpegURL"},
                {"src": "https://example.com/dash_stream", "type": "application/dash+xml"},
                {"src": "https://example.com/apple_stream", "type": "application/vnd.apple.mpegurl"}
            ]
        """.trimIndent()
        val sources = VideoSources.parse(json, "https://example.com")
        assertTrue(sources.isEmpty())
    }

    @Test
    fun testDedupeByUrlIgnoringFragment() {
        val json = """
            [
                {"src": "https://example.com/video.mp4#t=10"},
                {"src": "https://example.com/video.mp4#t=20"},
                {"src": "https://example.com/other.mp4"}
            ]
        """.trimIndent()
        val sources = VideoSources.parse(json, "https://example.com")
        assertEquals(2, sources.size)
        assertEquals("https://example.com/video.mp4#t=10", sources[0].url)
        assertEquals("https://example.com/other.mp4", sources[1].url)
    }

    @Test
    fun testMalformedJsonReturnsEmpty() {
        assertEquals(emptyList<VideoSource>(), VideoSources.parse("", "https://example.com"))
        assertEquals(emptyList<VideoSource>(), VideoSources.parse("not json", "https://example.com"))
        assertEquals(emptyList<VideoSource>(), VideoSources.parse("{\"not\": \"array\"}", "https://example.com"))
    }

    @Test
    fun testMax20EntriesCapped() {
        val items = (1..30).joinToString(",") { i ->
            """{"src": "https://example.com/video$i.mp4"}"""
        }
        val json = "[$items]"
        val sources = VideoSources.parse(json, "https://example.com")
        assertEquals(20, sources.size)
        assertEquals("https://example.com/video1.mp4", sources.first().url)
        assertEquals("https://example.com/video20.mp4", sources.last().url)
    }

    @Test
    fun testLabelExamples() {
        assertEquals("720p · MP4", VideoSources.label(1280, 720, null, "video/mp4", "https://a/x.mp4"))
        assertEquals("HD · WEBM", VideoSources.label(0, 0, "HD", "video/webm", "https://a/v"))
        assertEquals("Video", VideoSources.label(0, 0, null, "", "https://a/v"))
        assertEquals("720p · MP4", VideoSources.label(0, 0, "720", "video/mp4", "https://a/x.mp4"))
        assertEquals("1080p", VideoSources.label(1920, 1080, null, "", "https://a/v"))
        assertEquals("MP4", VideoSources.label(0, 0, null, "video/mp4", "https://a/v"))
    }

    @Test
    fun testMimeFromExtension() {
        val json = """
            [
                {"src": "https://example.com/video.webm"},
                {"src": "https://example.com/movie.mkv"},
                {"src": "https://example.com/clip.mov"},
                {"src": "https://example.com/anim.ogv"},
                {"src": "https://example.com/track.ogg"},
                {"src": "https://example.com/old.avi"},
                {"src": "https://example.com/mobile.3gp"},
                {"src": "https://example.com/standard.m4v"}
            ]
        """.trimIndent()
        val sources = VideoSources.parse(json, "https://example.com")
        assertEquals(8, sources.size)
        assertEquals("video/webm", sources[0].mimeType)
        assertEquals("video/x-matroska", sources[1].mimeType)
        assertEquals("video/quicktime", sources[2].mimeType)
        assertEquals("video/ogg", sources[3].mimeType)
        assertEquals("video/ogg", sources[4].mimeType)
        assertEquals("video/x-msvideo", sources[5].mimeType)
        assertEquals("video/3gpp", sources[6].mimeType)
        assertEquals("video/mp4", sources[7].mimeType)
    }

    @Test
    fun testIsSingleFileDirectly() {
        assertTrue(VideoSources.isSingleFile("https://example.com/video.mp4", "video/mp4"))
        assertTrue(VideoSources.isSingleFile("http://example.com/video.mp4", ""))
        assertFalse(VideoSources.isSingleFile("ftp://example.com/video.mp4", "video/mp4"))
        assertFalse(VideoSources.isSingleFile("blob:https://example.com/id", "video/mp4"))
        assertFalse(VideoSources.isSingleFile("data:video/mp4;base64,xyz", "video/mp4"))
        assertFalse(VideoSources.isSingleFile("https://example.com/stream.m3u8", ""))
        assertFalse(VideoSources.isSingleFile("https://example.com/stream.m3u", ""))
        assertFalse(VideoSources.isSingleFile("https://example.com/stream.mpd", ""))
        assertFalse(VideoSources.isSingleFile("https://example.com/stream.ism", ""))
        assertFalse(VideoSources.isSingleFile("https://example.com/stream.f4m", ""))
        assertFalse(VideoSources.isSingleFile("https://example.com/stream", "application/x-mpegurl"))
        assertFalse(VideoSources.isSingleFile("https://example.com/stream", "application/vnd.apple.mpegurl"))
        assertFalse(VideoSources.isSingleFile("https://example.com/stream", "application/dash+xml"))
    }

    @Test
    fun mergeKeepsTopFirstAndDedupes() {
        val a = VideoSource("https://a.com/x.mp4", "video/mp4", "MP4")
        val b = VideoSource("https://b.com/y.mp4", "video/mp4", "MP4")
        val aFrag = VideoSource("https://a.com/x.mp4#t=5", "video/mp4", "MP4")
        assertEquals(listOf(a, b), VideoSources.merge(listOf(a), listOf(listOf(aFrag, b))))
        assertEquals(listOf(b), VideoSources.merge(emptyList(), listOf(listOf(b))))
        val many = (1..30).map { VideoSource("https://c.com/$it.mp4", "video/mp4", "MP4") }
        assertEquals(20, VideoSources.merge(many.take(15), listOf(many.drop(15))).size)
    }

    @Test
    fun nonHttpBaseOrSourceIsDropped() {
        val json = """[{"src":"video.mp4"},{"src":"file:///sdcard/a.mp4"},{"src":"content://x/a.mp4"}]"""
        assertTrue(VideoSources.parse(json, "file:///data/data/app/").isEmpty())
        assertEquals(listOf("https://a.com/v/video.mp4"), VideoSources.parse(json, "https://a.com/v/page").map { it.url })
    }
}
