package kz.arctan.grepractice

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kz.arctan.grepractice.data.ImageStore
import kz.arctan.grepractice.data.Repository
import kz.arctan.grepractice.data.imageMarkup
import kz.arctan.grepractice.data.SampleQuestions
import kz.arctan.grepractice.data.cloud.CloudBackend
import kz.arctan.grepractice.data.cloud.CloudDoc
import kz.arctan.grepractice.data.cloud.CloudUser
import kz.arctan.grepractice.data.cloud.CloudWrite
import kz.arctan.grepractice.data.cloud.SyncEngine
import kz.arctan.grepractice.data.dataLocation
import kz.arctan.grepractice.model.PracticeMode
import kz.arctan.grepractice.practice.PracticeConfig
import kz.arctan.grepractice.practice.PracticeSession
import java.io.File
import java.nio.file.Files
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** In-memory stand-in for Firestore, keyed by "collection/id". */
private class FakeCloud : CloudBackend {
    val docs = linkedMapOf<String, CloudDoc>()
    override val user: StateFlow<CloudUser?> = MutableStateFlow(CloudUser("u1", "me@example.com"))
    override suspend fun signIn(email: String, password: String) {}
    override suspend fun signUp(email: String, password: String) {}
    override suspend fun signInAnonymously() {}
    override val googleSignInAvailable = false
    override suspend fun signInWithGoogle() {}
    override suspend fun sendPasswordReset(email: String) {}
    override suspend fun signOut() {}
    override suspend fun list(collection: String) = docs.filterKeys { it.startsWith("$collection/") }.values.toList()
    override suspend fun write(writes: List<CloudWrite>) {
        writes.forEach { docs["${it.collection}/${it.doc.id}"] = it.doc }
    }
    override suspend fun get(collection: String, id: String) = docs["$collection/$id"]
}

class SyncTest {
    companion object {
        init {
            System.setProperty("user.home", Files.createTempDirectory("gre-sync-test").toString())
        }
    }

    private val cloud = FakeCloud()

    /** Simulates opening the app on a fresh device: local files are wiped before the repository loads. */
    private fun freshDevice(): Repository {
        File(dataLocation()).listFiles()?.forEach { it.deleteRecursively() }
        return Repository()
    }

    private fun Repository.sync() = runBlocking { SyncEngine(this@sync, cloud).sync { 0L } }

    private fun Repository.practice() {
        val s = PracticeSession(PracticeConfig(PracticeMode.TOPIC, "T", questions.take(2), null, instantFeedback = false, shuffleChoices = false))
        s.select(0)
        addResult(s.finish())
    }

    @BeforeTest
    fun clean() {
        File(dataLocation()).listFiles()?.forEach { it.deleteRecursively() }
    }

    @Test
    fun firstSyncUploadsEverything() {
        val a = Repository()
        a.practice()
        val report = a.sync()
        assertEquals(SampleQuestions.all.size + 1, report.uploaded)
        assertEquals(0, report.downloaded)
        // A second sync with nothing changed is a no-op.
        assertEquals(0, a.sync().uploaded)
    }

    @Test
    fun editsDeletesAndResultsReachAnotherDevice() {
        val a = Repository()
        a.sync()
        val edited = a.questions.first { it.id == "sample-01" }
        a.upsertQuestion(edited.copy(text = "Edited on A"))
        a.deleteQuestion("sample-02")
        a.practice()
        a.sync()

        val b = freshDevice()
        val report = b.sync()
        assertTrue(report.downloaded > 0)
        assertEquals("Edited on A", b.questions.first { it.id == "sample-01" }.text)
        assertTrue(b.questions.none { it.id == "sample-02" }, "deletion should propagate")
        assertEquals(1, b.results.size)
    }

    @Test
    fun newerEditWinsAndResultDeletionPropagates() {
        val a = Repository()
        a.practice()
        a.sync()

        val b = freshDevice()
        b.sync()
        val q = b.questions.first { it.id == "sample-03" }
        Thread.sleep(5)
        b.upsertQuestion(q.copy(text = "Newer edit on B"))
        b.deleteResult(b.results.single().id)
        b.sync()

        val c = freshDevice()
        c.sync()
        assertEquals("Newer edit on B", c.questions.first { it.id == "sample-03" }.text)
        assertTrue(c.results.isEmpty(), "deleted result must not come back")
    }

    @Test
    fun removedDuplicatesStayRemovedAfterSync() {
        val a = Repository()
        a.upsertQuestion(a.questions.first { it.id == "sample-01" }.copy(id = "dup-of-01"))
        a.sync()
        val removedId = a.duplicateGroups().single()[1].id
        assertEquals(1, a.removeDuplicates())
        a.sync()
        assertTrue(cloud.docs.getValue("questions/$removedId").deleted)

        val b = freshDevice()
        b.sync()
        assertTrue(b.questions.none { it.id == removedId })
        assertTrue(b.duplicateGroups().isEmpty())
    }

    @Test
    fun imagesUploadOnceAndDownloadWhereMissing() {
        val a = Repository()
        val bytes = ByteArray(2000) { (it % 251).toByte() }
        val name = ImageStore.add(bytes, "png")
        val q = a.questions.first { it.id == "sample-01" }
        a.upsertQuestion(q.copy(text = q.text + "\n" + imageMarkup(name)))
        assertEquals(SampleQuestions.all.size + 1, a.sync().uploaded)
        assertTrue(cloud.docs.containsKey("images/$name"))
        assertEquals(0, a.sync().uploaded, "an image is uploaded only once")

        val b = freshDevice() // wipes local files, including images
        assertEquals(null, ImageStore.load(name))
        b.sync()
        assertTrue(bytes.contentEquals(ImageStore.load(name)), "missing image is downloaded")
    }

    @Test
    fun locallyDeletedResultIsNotResurrectedByCloud() {
        val a = Repository()
        a.practice()
        a.sync()
        val id = a.results.single().id
        a.deleteResult(id)
        a.sync()
        assertTrue(cloud.docs.getValue("results/$id").deleted)
        a.sync()
        assertTrue(a.results.isEmpty())
    }
}
