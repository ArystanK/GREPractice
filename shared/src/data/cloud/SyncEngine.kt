package kz.arctan.grepractice.data.cloud

import kz.arctan.grepractice.data.Repository

data class SyncReport(val uploaded: Int, val downloaded: Int, val finishedAt: Long)

/** Pulls the signed-in user's cloud data, merges it into [repo], and pushes local changes back. */
class SyncEngine(private val repo: Repository, private val cloud: CloudBackend) {
    suspend fun sync(now: () -> Long): SyncReport {
        if (cloud.user.value == null) throw CloudException("Sign in to sync.")
        val remoteQuestions = cloud.list(QUESTIONS_COLLECTION)
        val remoteResults = cloud.list(RESULTS_COLLECTION)
        val plan = repo.mergeRemote(remoteQuestions, remoteResults)
        plan.uploads.chunked(MAX_BATCH_WRITES).forEach { cloud.write(it) }
        return SyncReport(uploaded = plan.uploads.size, downloaded = plan.downloaded, finishedAt = now())
    }
}
