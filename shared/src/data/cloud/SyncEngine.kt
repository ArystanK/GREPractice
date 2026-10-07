package kz.arctan.grepractice.data.cloud

import kz.arctan.grepractice.data.ImageStore
import kz.arctan.grepractice.data.Repository
import kz.arctan.grepractice.data.SHARED_IMAGES_KEY
import kz.arctan.grepractice.data.encodeQuestion
import kz.arctan.grepractice.data.imageRefs
import kz.arctan.grepractice.model.Question
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

data class SyncReport(val uploaded: Int, val downloaded: Int, val finishedAt: Long)

class SyncEngine(private val repo: Repository, private val cloud: CloudBackend) {
    /**
     * Downloads shared-bank changes since the last fetch, plus any figures they need. Works without
     * signing in, since the shared bank is public. Returns how many questions changed.
     */
    @OptIn(ExperimentalEncodingApi::class)
    suspend fun syncShared(): Int {
        val changed = repo.applyShared(cloud.listShared(BANK_COLLECTION, repo.sharedSyncedUpTo))
        for (name in repo.sharedImageRefs()) {
            if (ImageStore.load(name) != null) continue
            val doc = cloud.getShared(BANK_IMAGES_COLLECTION, name) ?: continue
            if (!doc.deleted && doc.payload.isNotEmpty()) {
                runCatching { Base64.decode(doc.payload) }.getOrNull()?.let { ImageStore.put(name, it) }
            }
        }
        return changed
    }

    /** Pulls the signed-in user's own data, merges it into [repo], and pushes local changes back. */
    suspend fun sync(now: () -> Long): SyncReport {
        val uid = cloud.user.value?.uid ?: throw CloudException("Sign in to sync.")
        val remoteQuestions = cloud.list(QUESTIONS_COLLECTION)
        val remoteResults = cloud.list(RESULTS_COLLECTION)
        val plan = repo.mergeRemote(remoteQuestions, remoteResults)
        plan.uploads.chunked(MAX_BATCH_WRITES).forEach { cloud.write(it) }
        val (imagesUp, imagesDown) = syncImages(uid, now)
        return SyncReport(uploaded = plan.uploads.size + imagesUp, downloaded = plan.downloaded + imagesDown, finishedAt = now())
    }

    /**
     * Admin only: writes [questions] (with their figures) to the shared bank and moves them out of
     * the admin's own questions. Firestore rules reject this for anyone else.
     */
    @OptIn(ExperimentalEncodingApi::class)
    suspend fun publish(questions: List<Question>, now: () -> Long): Int {
        if (questions.isEmpty()) return 0
        for (name in questions.flatMap { it.imageRefs() }.toSet()) {
            if (repo.isImageUploaded(SHARED_IMAGES_KEY, name)) continue
            val bytes = ImageStore.load(name) ?: continue
            if (cloud.getShared(BANK_IMAGES_COLLECTION, name) == null) {
                cloud.writeShared(listOf(CloudWrite(BANK_IMAGES_COLLECTION, CloudDoc(name, Base64.encode(bytes), now(), false))))
            }
            repo.markImageUploaded(SHARED_IMAGES_KEY, name)
        }
        val time = now()
        val stamped = questions.map { it.copy(updatedAt = time) }
        stamped.chunked(MAX_BATCH_WRITES).forEach { chunk ->
            cloud.writeShared(chunk.map { CloudWrite(BANK_COLLECTION, CloudDoc(it.id, encodeQuestion(it), time, false)) })
            repo.markPublished(chunk)
        }
        return stamped.size
    }

    /** Admin only: removes questions from the shared bank (as tombstones, so every device drops them). */
    suspend fun unpublish(ids: Set<String>, now: () -> Long) {
        val time = now()
        cloud.writeShared(ids.map { CloudWrite(BANK_COLLECTION, CloudDoc(it, "", time, true)) })
        repo.markUnpublished(ids)
    }

    /**
     * Images never change (they're named by content hash), so instead of listing the collection,
     * each image referenced by the user's own data is uploaded once per account and downloaded only
     * where it's missing.
     */
    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun syncImages(uid: String, now: () -> Long): Pair<Int, Int> {
        var uploaded = 0
        var downloaded = 0
        for (name in repo.referencedImages()) {
            val local = ImageStore.load(name)
            if (local == null) {
                val doc = cloud.get(IMAGES_COLLECTION, name) ?: continue
                if (doc.deleted || doc.payload.isEmpty()) continue
                runCatching { Base64.decode(doc.payload) }.getOrNull()?.let {
                    ImageStore.put(name, it)
                    repo.markImageUploaded(uid, name)
                    downloaded++
                }
            } else if (!repo.isImageUploaded(uid, name)) {
                cloud.write(listOf(CloudWrite(IMAGES_COLLECTION, CloudDoc(name, Base64.encode(local), now(), false))))
                repo.markImageUploaded(uid, name)
                uploaded++
            }
        }
        return uploaded to downloaded
    }
}
