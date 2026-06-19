package com.threadmap.intellij.model

import com.fasterxml.jackson.databind.ObjectMapper
import com.threadmap.core.annotate.AnnotatedNode
import com.threadmap.core.annotate.AnnotatedTree
import com.threadmap.core.annotate.Understanding
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ProgressStoreTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun mergesStateBySignatureAcrossRepeatedNodes() {
        val tree = repeatedSignatureTree()
        val file = tempDir.resolve("progress.json")
        Files.writeString(file, """
            {
              "version": 1,
              "nodes": {
                "B#work()": { "understanding": "mastered" }
              }
            }
        """.trimIndent())

        ProgressStore(file).merge(tree)

        assertEquals(Understanding.MASTERED, tree.root.children[0].understanding)
        assertEquals(Understanding.MASTERED, tree.root.children[1].understanding)
        assertEquals(Understanding.UNKNOWN, tree.root.understanding)
    }

    @Test
    fun updatePersistsAndUpdatesEveryOccurrence() {
        val tree = repeatedSignatureTree()
        val file = tempDir.resolve(".threadmap/progress.json")
        val store = ProgressStore(file)

        store.update(tree, "B#work()", Understanding.HALF)

        assertTrue(Files.isRegularFile(file))
        assertEquals(Understanding.HALF, tree.root.children[0].understanding)
        assertEquals(Understanding.HALF, tree.root.children[1].understanding)
        assertEquals(Understanding.HALF, store.readStates()["B#work()"])
    }

    @Test
    fun unknownRemovesUnderstandingButPreservesFutureFields() {
        val tree = repeatedSignatureTree()
        val file = tempDir.resolve("progress.json")
        Files.writeString(file, """
            {
              "version": 1,
              "nodes": {
                "B#work()": {
                  "understanding": "mastered",
                  "risk": "high"
                }
              }
            }
        """.trimIndent())

        ProgressStore(file).update(tree, "B#work()", Understanding.UNKNOWN)

        val entry = ObjectMapper().readTree(Files.readString(file))
            .get("nodes").get("B#work()")
        assertFalse(entry.has("understanding"))
        assertEquals("high", entry.get("risk").asText())
    }

    private fun repeatedSignatureTree(): AnnotatedTree {
        val root = AnnotatedNode(0, "A#entry()", "A.java", 0, 1)
        root.addChild(AnnotatedNode(1, "B#work()", "B.java", 0, 1))
        root.addChild(AnnotatedNode(2, "B#work()", "B.java", 0, 1))
        return AnnotatedTree("A#entry()", "t", root)
    }
}
