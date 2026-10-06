package kz.arctan.grepractice

import kz.arctan.grepractice.data.ImportSummary
import kz.arctan.grepractice.data.Repository
import kz.arctan.grepractice.data.SampleQuestions
import kz.arctan.grepractice.data.dataLocation
import kz.arctan.grepractice.model.PracticeMode
import kz.arctan.grepractice.practice.PracticeConfig
import kz.arctan.grepractice.practice.PracticeSession
import java.io.File
import java.nio.file.Files
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RepositoryTest {
    companion object {
        // The JVM data directory is resolved lazily from user.home, so point it at a temp dir
        // before anything touches storage.
        init {
            System.setProperty("user.home", Files.createTempDirectory("gre-test").toString())
        }
    }

    @BeforeTest
    fun clean() {
        File(dataLocation()).listFiles()?.forEach { it.delete() }
    }

    @Test
    fun seedsSampleQuestionsOnFirstRun() {
        val repo = Repository()
        assertEquals(SampleQuestions.all.size, repo.questions.size)
        assertTrue(File(dataLocation(), "questions.json").exists())
    }

    @Test
    fun sampleQuestionsAreWellFormed() {
        assertEquals(SampleQuestions.all.size, SampleQuestions.all.map { it.id }.toSet().size)
        SampleQuestions.all.forEach { q ->
            assertTrue(q.correctIndex in q.choices.indices, q.id)
            assertEquals(q.choices.size, q.choices.toSet().size, "duplicate choices in ${q.id}")
        }
    }

    @Test
    fun importAcceptsLetterOrIndexAndPersists() {
        val repo = Repository()
        val before = repo.questions.size
        val n = repo.importQuestionsJson(
            """
            [
              {"topic": "Calculus", "text": "1+1", "choices": ["1", "2", "3"], "answer": "b"},
              {"topic": "Topology", "text": "2+2", "choices": ["4", "5"], "correctIndex": 0, "extra": "ignored"}
            ]
            """,
        )
        assertEquals(ImportSummary(imported = 2, skippedDuplicates = 0), n)
        val reloaded = Repository()
        assertEquals(before + 2, reloaded.questions.size)
        assertEquals(1, reloaded.questions.first { it.text == "1+1" }.correctIndex)
    }

    @Test
    fun removesDuplicatesKeepingPracticeTestCopy() {
        val repo = Repository()
        val sampleCount = repo.questions.size
        repo.importQuestionsJson(
            """
            [
              {"topic": "Calculus", "text": "${'$'}x^2${'$'} at 3", "choices": ["6", "9"], "answer": "B"},
              {"id": "practice7-01", "topic": "Calculus", "text": "${'$'}x^2${'$'}  at 3", "choices": ["6", " 9"], "answer": "B"},
              {"topic": "Calculus", "text": "same text", "choices": ["1", "2"], "answer": "A", "explanation": "kept"},
              {"topic": "Calculus", "text": "same text", "choices": ["1", "2"], "answer": "B"}
            ]
            """,
        )
        // The practice-test copy replaced the random-id one during import; "same text" differs in its answer.
        assertEquals(sampleCount + 3, repo.questions.size)
        assertTrue(repo.duplicateGroups().isEmpty())

        // Duplicates that predate the import check are still found and removed, keeping the practice-test copy.
        repo.upsertQuestion(repo.questions.first { it.id == "practice7-01" }.copy(id = "random-copy"))
        assertEquals(listOf("practice7-01", "random-copy"), repo.duplicateGroups().single().map { it.id })
        assertEquals(1, repo.removeDuplicates())
        assertTrue(repo.questions.any { it.id == "practice7-01" } && repo.questions.none { it.id == "random-copy" })

        // Re-importing something already in the bank is skipped.
        val again = repo.importQuestionsJson("""[{"topic": "Calculus", "text": "same text", "choices": ["1", "2"], "answer": "A"}]""")
        assertEquals(ImportSummary(imported = 0, skippedDuplicates = 1), again)
        assertEquals(sampleCount + 3, Repository().questions.size)
    }

    @Test
    fun importRejectsBadAnswers() {
        val repo = Repository()
        assertFailsWith<IllegalArgumentException> {
            repo.importQuestionsJson("""[{"topic": "X", "text": "t", "choices": ["a", "b"], "answer": "E"}]""")
        }
        assertFailsWith<IllegalStateException> {
            repo.importQuestionsJson("""[{"topic": "X", "text": "t", "choices": ["a", "b"]}]""")
        }
    }

    @Test
    fun resultsPersistAndFeedTopicStats() {
        val repo = Repository()
        val qs = repo.questions.take(3)
        val s = PracticeSession(PracticeConfig(PracticeMode.EXAM, "Exam", qs, null, instantFeedback = false, shuffleChoices = false))
        s.select(qs[0].correctIndex)
        repo.addResult(s.finish())

        val reloaded = Repository()
        assertEquals(1, reloaded.results.size)
        assertEquals(1, reloaded.results[0].correct)
        assertEquals(3, reloaded.topicStats().sumOf { it.answered })
    }

    @Test
    fun corruptFileIsBackedUp() {
        File(dataLocation()).mkdirs()
        File(dataLocation(), "results.json").writeText("{ not json")
        val repo = Repository()
        assertTrue(repo.loadError != null)
        assertTrue(File(dataLocation(), "results.json.broken").exists())
    }
}
