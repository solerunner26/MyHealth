package app.myhealth.data.security

/** Why secure storage could not be opened. The app fails closed: it never falls back to plaintext. */
enum class SecureFailure { KeystoreUnavailable, KeyInvalidated, KeyMaterialCorrupt, DatabaseUnavailable, NewerSchema }

class SecureStorageException(val failure: SecureFailure, cause: Throwable? = null) : Exception(failure.name, cause)

/**
 * Wraps (encrypts) small secrets with a hardware-backed, non-exportable key. Android implementation uses the
 * AndroidKeyStore; tests use a software key.
 */
interface KeyWrapper {
    /** True if the wrapping key exists. */
    fun exists(): Boolean
    /** Creates the wrapping key; must only be called when no wrapped material exists yet. */
    fun create()
    fun wrap(plain: ByteArray): ByteArray
    fun unwrap(wrapped: ByteArray): ByteArray
    /** Irreversibly deletes the wrapping key (secure erase). */
    fun destroy()
}
