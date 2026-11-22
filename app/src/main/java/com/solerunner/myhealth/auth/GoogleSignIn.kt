package com.solerunner.myhealth.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.solerunner.myhealth.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The tiny slice of a Google account the app keeps: a stable id, a name, an email and a picture. */
data class GoogleIdentity(
    val accountId: String,
    val displayName: String?,
    val email: String?,
    val photoUrl: String?,
)

/**
 * Optional Google sign-in through Credential Manager.
 *
 * The app never asks for scopes and never uploads health data; the identity is used only to prefill the
 * profile. When no OAuth client id is configured at build time the button is hidden and the offline path
 * ("continue without an account") is the only one — exactly as documented in AGENTS.md.
 */
class GoogleSignIn(private val context: Context) {

    val isConfigured: Boolean = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    suspend fun signIn(): GoogleIdentity? = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext null
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val manager = CredentialManager.create(context)
        val result = manager.getCredential(context, request)
        val credential = result.credential
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val token = GoogleIdTokenCredential.createFrom(credential.data)
            GoogleIdentity(
                accountId = token.id,
                displayName = token.displayName,
                email = token.id.takeIf { it.contains('@') },
                photoUrl = token.profilePictureUri?.toString(),
            )
        } else {
            null
        }
    }
}
