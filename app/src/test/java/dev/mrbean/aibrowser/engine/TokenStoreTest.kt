package dev.mrbean.aibrowser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TokenStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `add list and remove round trip`() {
        val dataDir = tmp.newFolder("data")
        val store = TokenStore(dataDir)

        val added = store.add("agents")

        assertEquals(listOf(added), store.list())
        assertEquals(64, added.token.length)
        assertTrue(added.token.all { it in '0'..'9' || it in 'a'..'f' })
        assertTrue(File(dataDir, "mcp.tokens").exists())

        store.remove(added.token)

        assertEquals(emptyList<ApiToken>(), store.list())
    }

    @Test
    fun `adds several tokens and removes only the matching one`() {
        val store = TokenStore(tmp.newFolder("data"))

        val a = store.add("a")
        val b = store.add("b")

        assertEquals(listOf(a, b), store.list())

        store.remove(a.token)

        assertEquals(listOf(b), store.list())
    }

    @Test
    fun `sanitises labels to A-Za-z0-9_- and defaults to token`() {
        val store = TokenStore(tmp.newFolder("data"))

        assertEquals("mylabel-label_2", store.add("my label!@-label_2#").label)
        assertEquals("A1_b-c", store.add("A1_b-c").label)
        assertEquals("token", store.add("").label)
        assertEquals("token", store.add("!!!").label)
    }

    @Test
    fun `skips blank and comment lines on read`() {
        val dataDir = tmp.newFolder("data")
        File(dataDir, "mcp.tokens").writeText("# comment\n\n  \nfirst abc123\n")

        val store = TokenStore(dataDir)

        assertEquals(listOf(ApiToken("first", "abc123")), store.list())
    }

    @Test
    fun `starts empty when the file is missing`() {
        val store = TokenStore(tmp.newFolder("data"))

        assertEquals(emptyList<ApiToken>(), store.list())
    }
}