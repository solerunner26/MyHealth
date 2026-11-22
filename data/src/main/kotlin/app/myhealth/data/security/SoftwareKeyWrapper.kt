package app.myhealth.data.security

/**
 * In-memory wrapper for JVM/Robolectric tests (the AndroidKeyStore provider does not exist there).
 * Never used by production code paths; [failWith] lets tests simulate keystore failures.
 */
class SoftwareKeyWrapper : KeyWrapper {
    private var key: ByteArray? = null
    var failWith: SecureFailure? = null

    override fun exists(): Boolean { failWith?.let { throw SecureStorageException(it) }; return key != null }
    override fun create() { failWith?.let { throw SecureStorageException(it) }; key = Crypto.randomBytes(32) }
    override fun wrap(plain: ByteArray): ByteArray = Crypto.seal(Crypto.aesKey(requireKey()), plain)
    override fun unwrap(wrapped: ByteArray): ByteArray = try {
        Crypto.open(Crypto.aesKey(requireKey()), wrapped)
    } catch (e: SecureStorageException) { throw e } catch (e: Exception) { throw SecureStorageException(SecureFailure.KeyMaterialCorrupt, e) }
    override fun destroy() { key = null }
    private fun requireKey(): ByteArray { failWith?.let { throw SecureStorageException(it) }; return key ?: throw SecureStorageException(SecureFailure.KeyInvalidated) }
}
