package app.myhealth.data.backup

import app.myhealth.data.security.Crypto
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class BackupFailure { WrongPassword, Corrupt, Truncated, NotABackup, NewerVersion, Io }

class BackupException(val failure: BackupFailure, cause: Throwable? = null) : Exception(failure.name, cause)

/**
 * `.myhealth` container:
 *   "MYHB" | u8 formatVersion | u32 pbkdf2Iterations | 16 B salt | 8 B noncePrefix | 16 B verifier | chunks…
 *   verifier = GCM tag over an empty message (nonce prefix ‖ 0xFFFFFFFF, AAD = header): tells a wrong password apart
 *   from a damaged file.
 *   chunk  = u32 ciphertextLength | AES-256-GCM(ciphertext ‖ tag)
 * Each chunk's nonce is noncePrefix ‖ u32 index; AAD = header ‖ u32 index ‖ u8 isLast. Reordering, truncation,
 * tampering and a wrong password are all detected; the password-derived key never touches disk.
 */
object BackupCrypto {
    private val MAGIC = "MYHB".toByteArray()
    const val FORMAT = 1
    const val ITERATIONS = 600_000
    const val CHUNK = 1 shl 20

    class Header(val iterations: Int, val salt: ByteArray, val noncePrefix: ByteArray) {
        fun bytes(): ByteArray = ByteBuffer.allocate(4 + 1 + 4 + 16 + 8).put(MAGIC).put(FORMAT.toByte()).putInt(iterations).put(salt).put(noncePrefix).array()
    }

    fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKey = Crypto.aesKey(Crypto.pbkdf2(password, salt, iterations))

    fun encrypting(out: OutputStream, password: CharArray, iterations: Int = ITERATIONS): OutputStream {
        val header = Header(iterations, Crypto.randomBytes(16), Crypto.randomBytes(8))
        val key = deriveKey(password, header.salt, iterations)
        out.write(header.bytes())
        out.write(verifier(key, header))
        return ChunkedEncryptStream(out, key, header)
    }

    fun decrypting(input: InputStream, password: CharArray): InputStream {
        val din = DataInputStream(input)
        val magic = ByteArray(4)
        try { din.readFully(magic) } catch (e: EOFException) { throw BackupException(BackupFailure.NotABackup, e) }
        if (!magic.contentEquals(MAGIC)) throw BackupException(BackupFailure.NotABackup)
        val format = din.readUnsignedByte()
        if (format > FORMAT) throw BackupException(BackupFailure.NewerVersion)
        val iterations = din.readInt()
        if (iterations !in 10_000..10_000_000) throw BackupException(BackupFailure.Corrupt)
        val salt = ByteArray(16).also { din.readFully(it) }
        val prefix = ByteArray(8).also { din.readFully(it) }
        val header = Header(iterations, salt, prefix)
        val stored = ByteArray(16).also { try { din.readFully(it) } catch (e: EOFException) { throw BackupException(BackupFailure.Truncated, e) } }
        val key = deriveKey(password, salt, iterations)
        if (!Crypto.equalsCt(stored, verifier(key, header))) throw BackupException(BackupFailure.WrongPassword)
        return ChunkedDecryptStream(din, key, header)
    }

    private fun verifier(key: SecretKey, header: Header): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce(header.noncePrefix, -1)))
        c.updateAAD(header.bytes())
        return c.doFinal()
    }

    internal fun nonce(prefix: ByteArray, index: Int) = ByteBuffer.allocate(12).put(prefix).putInt(index).array()
    internal fun aad(header: Header, index: Int, last: Boolean) = ByteBuffer.allocate(header.bytes().size + 5).put(header.bytes()).putInt(index).put(if (last) 1 else 0).array()

    private class ChunkedEncryptStream(private val out: OutputStream, private val key: SecretKey, private val header: Header) : OutputStream() {
        private val buf = ByteArray(CHUNK); private var len = 0; private var index = 0; private var closed = false
        private val dout = DataOutputStream(out)
        override fun write(b: Int) { if (len == CHUNK) flushChunk(false); buf[len++] = b.toByte() }
        override fun write(b: ByteArray, off: Int, n: Int) {
            var o = off; var r = n
            while (r > 0) { if (len == CHUNK) flushChunk(false); val k = minOf(r, CHUNK - len); System.arraycopy(b, o, buf, len, k); len += k; o += k; r -= k }
        }
        private fun flushChunk(last: Boolean) {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce(header.noncePrefix, index)))
            c.updateAAD(aad(header, index, last))
            val ct = c.doFinal(buf, 0, len)
            dout.writeInt(ct.size); dout.write(ct)
            index++; len = 0
        }
        override fun close() { if (closed) return; closed = true; flushChunk(true); dout.flush(); out.close() }
    }

    private class ChunkedDecryptStream(private val din: DataInputStream, private val key: SecretKey, private val header: Header) : InputStream() {
        private var plain = ByteArray(0); private var pos = 0; private var index = 0; private var done = false
        private fun next(): Boolean {
            if (done) return false
            val n = try { din.readInt() } catch (e: EOFException) { throw BackupException(BackupFailure.Truncated, e) }
            if (n !in 16..(CHUNK + 16)) throw BackupException(BackupFailure.Corrupt)
            val ct = ByteArray(n)
            try { din.readFully(ct) } catch (e: EOFException) { throw BackupException(BackupFailure.Truncated, e) }
            // A chunk is final iff its AAD says so; try "not last" first unless the stream is exhausted.
            plain = tryOpen(ct, last = false) ?: tryOpen(ct, last = true)?.also { done = true }
                ?: throw BackupException(BackupFailure.Corrupt)
            if (done && din.read() != -1) throw BackupException(BackupFailure.Corrupt)
            index++; pos = 0
            return true
        }
        private fun tryOpen(ct: ByteArray, last: Boolean): ByteArray? = try {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, nonce(header.noncePrefix, index)))
            c.updateAAD(aad(header, index, last))
            c.doFinal(ct)
        } catch (e: AEADBadTagException) { null }
        override fun read(): Int { while (pos >= plain.size) { if (!next()) return -1 }; return plain[pos++].toInt() and 0xFF }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            while (pos >= plain.size) { if (!next()) return -1 }
            val k = minOf(len, plain.size - pos); System.arraycopy(plain, pos, b, off, k); pos += k; return k
        }
    }
}
