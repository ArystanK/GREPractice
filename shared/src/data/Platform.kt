package kz.arctan.grepractice.data

/** Reads a file from the app's data directory, or null if it doesn't exist. */
expect fun readDataFile(name: String): String?

/** Atomically writes a file into the app's data directory. */
expect fun writeDataFile(name: String, content: String)

/** Binary counterparts of [readDataFile]/[writeDataFile]; [name] may include a subdirectory, e.g. `images/x.png`. */
expect fun readDataBytes(name: String): ByteArray?

expect fun writeDataBytes(name: String, bytes: ByteArray)

/** Lowercase hex SHA-256 of [bytes]. */
expect fun sha256Hex(bytes: ByteArray): String

/** Human-readable location of the data directory, shown in the UI. */
expect fun dataLocation(): String

expect fun nowMillis(): Long

/** e.g. "Oct 5, 2026 14:03". */
expect fun formatDateTime(millis: Long): String
