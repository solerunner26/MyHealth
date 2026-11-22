package com.solerunner.myhealth.data.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import javax.crypto.AEADBadTagException

class BackupCodecTest {

    private val passphrase = "correct horse battery".toCharArray()

    @Test
    fun `round trips a payload`() {
        val payload = "payload bytes".toByteArray()
        val sealed = BackupCodec.encrypt(payload, passphrase)
        assertArrayEquals(payload, BackupCodec.decrypt(sealed, passphrase))
    }

    @Test
    fun `rejects a wrong passphrase`() {
        val sealed = BackupCodec.encrypt("secret".toByteArray(), passphrase)
        assertThrows(AEADBadTagException::class.java) {
            BackupCodec.decrypt(sealed, "wrong passphrase".toCharArray())
        }
    }

    @Test
    fun `rejects a tampered file`() {
        val sealed = BackupCodec.encrypt("secret".toByteArray(), passphrase)
        sealed[sealed.size - 1] = (sealed[sealed.size - 1] + 1).toByte()
        assertThrows(AEADBadTagException::class.java) {
            BackupCodec.decrypt(sealed, passphrase)
        }
    }

    @Test
    fun `rejects a file that is not a backup`() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.decrypt(ByteArray(100), passphrase)
        }
    }
}
