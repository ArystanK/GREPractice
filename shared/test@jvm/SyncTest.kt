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

/** In-memory stand-in for Firestore: [docs] are the user's own ("collection/id"), [sharedDocs] the shared bank. */
private class FakeCloud : CloudBackend {
    val docs = linkedMapOf<String, CloudDoc>()
    val sharedDocs = linkedMapOf<String, CloudDoc>()
    var admin = true
    override val user: StateFlow<CloudUser?> = MutableStateFlow(CloudUser("u1", "me@example.com"))
    override suspend fun listShared(collection: String, updatedAfter: Long) =
        sharedDocs.filterKeys { it.startsWith("$collection/") }.values.filter { it.updatedAt > updatedAfter }
    override suspend fun getShared(collection: String, id: String) = sharedDocs["$collection/$id"]
    override suspend fun writeShared(writes: List<CloudWrite>) {
        if (!admin) throw kz.arctan.grepractice.data.cloud.CloudException("Firestore denied access.")
        writes.forEach { sharedDocs["${it.collection}/${it.doc.id}"] = it.doc }
    }
    override suspend fun isAdmin() = admin
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

    private var clock = 1_000L
    private fun tick() = ++clock
    private fun Repository.engine() = SyncEngine(this, cloud)

    @Test
    fun publishedQuestionsReachEveryDevice() {
        // Admin publishes their own questions (with a figure).
        val admin = Repository()
        val image = ImageStore.add(ByteArray(500) { it.toByte() }, "png")
        val q = admin.questions.first { it.id == "sample-01" }
        admin.upsertQuestion(q.copy(text = imageMarkup(image) + "\n" + q.text))
        val ownIds = admin.ownQuestions().map { it.id }.toSet()
        runBlocking { admin.engine().publish(admin.ownQuestions(), ::tick) }
        assertEquals(ownIds, admin.sharedIds)
        assertTrue(admin.ownQuestions().isEmpty(), "published questions leave the admin's own set")
        assertEquals(ownIds.size, admin.questions.size)
        assertTrue(cloud.sharedDocs.containsKey("bankImages/$image"))

        // Another user on a fresh device, not signed in: gets the shared bank and its figure.
        val other = freshDevice()
        val changed = runBlocking { other.engine().syncShared() }
        assertEquals(ownIds.size, changed)
        assertTrue(other.isShared("sample-01"))
        assertTrue(ImageStore.load(image) != null, "shared figure downloaded")
        // Their seeded sample copies have the same ids, so the shared versions win: no doubles.
        assertEquals(ownIds.size, other.questions.size)

        // Later changes arrive incrementally; deletions too.
        runBlocking { admin.engine().unpublish(setOf("sample-02"), ::tick) }
        assertEquals(1, runBlocking { other.engine().syncShared() })
        assertTrue(other.questions.none { it.id == "sample-02" && other.isShared(it.id) })
        assertEquals(0, runBlocking { other.engine().syncShared() }, "nothing new")
    }

    @Test
    fun nonAdminCannotWriteAndSharedIdsAreNotImportedAsOwn() {
        val admin = Repository()
        runBlocking { admin.engine().publish(admin.ownQuestions().take(3), ::tick) }
        val sharedId = admin.sharedIds.first()

        val user = freshDevice()
        runBlocking { user.engine().syncShared() }
        cloud.admin = false
        assertTrue(runCatching { runBlocking { user.engine().publish(user.ownQuestions().take(1), ::tick) } }.isFailure)

        val summary = user.importQuestionsJson(
            """[{"id": "$sharedId", "topic": "X", "text": "replaced?", "choices": ["a", "b"], "answer": "A"}]""",
        )
        assertEquals(0, summary.imported)
        assertEquals(listOf(sharedId), summary.shared.map { it.id })
        assertTrue(user.questions.none { it.text == "replaced?" })
    }

    @Test
    fun duplicateRemovalNeverTouchesSharedQuestions() {
        val admin = Repository()
        runBlocking { admin.engine().publish(admin.ownQuestions().filter { it.id == "sample-01" }, ::tick) }
        val sharedQ = admin.questions.first { it.id == "sample-01" }
        admin.upsertQuestion(sharedQ.copy(id = "my-copy"))
        assertEquals(listOf("sample-01", "my-copy"), admin.duplicateGroups().single().map { it.id })
        assertEquals(1, admin.removeDuplicates())
        assertTrue(admin.isShared("sample-01") && admin.questions.none { it.id == "my-copy" })
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
