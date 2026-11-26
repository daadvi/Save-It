package br.com.saveit

import org.junit.Assert.*
import org.junit.Test

class LibraryTest {
    @Test fun migratesWebBackupAndPreservesHierarchyAndStatus() {
        val json = """{"folders":[{"id":"a","name":"Estudos","parentId":null},{"id":"b","name":"Kotlin","parentId":"a"}],"bookmarks":[{"id":"c","url":"https://kotlinlang.org","title":"Kotlin","folderId":"b","done":true,"note":"Lido","completedAt":15}]}"""
        val imported = Library.parseJson(json)
        val merged = Library().merge(imported)
        val restored = Library.parseJson(merged.json())
        val link = restored.links.single()
        assertEquals(listOf("Estudos","Kotlin"), restored.path(link.folder).map { it.name })
        assertTrue(link.done)
        assertEquals("Lido", link.note)
        assertEquals(15L, link.completed)
        assertEquals(1, restored.merge(imported).links.size)
    }

    @Test fun browserHtmlRoundTripPreservesNestedAndLooseLinks() {
        val state = Library(listOf(Folder("a","A & B",null),Folder("b","Filha","a")),
            listOf(Link("1","https://example.org/?a=1&b=2","Um <dois>","b"),Link("2","https://example.com","Solto",null)))
        val result = Library.parseHtml(state.html())
        val nested = result.links.single { it.title == "Um <dois>" }
        assertEquals(listOf("A & B","Filha"),result.path(nested.folder).map { it.name })
        assertEquals(state.links.first().url,nested.url)
        assertNull(result.links.single { it.title == "Solto" }.folder)
    }

    @Test fun rejectsCyclicFoldersAndExecutableUrls() {
        assertTrue(runCatching { Library.parseJson("""{"folders":[{"id":"a","name":"A","parentId":"a"}],"bookmarks":[]}""") }.isFailure)
        assertNull(webUrl("javascript:alert(1)"))
    }
}
