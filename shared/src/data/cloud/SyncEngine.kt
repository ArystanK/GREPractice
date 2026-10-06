package kz.arctan.grepractice.data.cloud

import kz.arctan.grepractice.data.ImageStore
import kz.arctan.grepractice.data.Repository
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

data class SyncReport(val uploaded: Int, val downloaded: Int, val finishedAt: Long)

/** Pulls the signed-in user's cloud data, merges it into [repo], and pushes local changes back. */
class SyncEngine(private val repo: Repository, private val cloud: CloudBackend) {
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
     * Images never change (they're named by content hash), so instead of listing the collection,
     * each referenced image is uploaded once per account and downloaded only where it's missing.
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
