package xavier.pedro.suavez.auth

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import xavier.pedro.suavez.R

sealed interface GoogleSignInOutcome {
    data class Success(val idToken: String) : GoogleSignInOutcome
    data object Cancelled : GoogleSignInOutcome
    data class Error(val message: String) : GoogleSignInOutcome
}

object GoogleAuthHelper {

    suspend fun requestGoogleIdToken(activityContext: Context): GoogleSignInOutcome {
        val credentialManager = CredentialManager.create(activityContext)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(resolveServerClientId(activityContext))
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val result = credentialManager.getCredential(
                context = activityContext,
                request = request,
            )
            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                GoogleSignInOutcome.Success(googleCredential.idToken)
            } else {
                GoogleSignInOutcome.Error(activityContext.getString(R.string.login_google_error))
            }
        } catch (_: GetCredentialCancellationException) {
            GoogleSignInOutcome.Cancelled
        } catch (_: NoCredentialException) {
            GoogleSignInOutcome.Error(activityContext.getString(R.string.login_no_google_account))
        } catch (_: GoogleIdTokenParsingException) {
            GoogleSignInOutcome.Error(activityContext.getString(R.string.login_google_error))
        } catch (e: GetCredentialException) {
            GoogleSignInOutcome.Error(
                e.message?.takeIf { it.isNotBlank() }
                    ?: activityContext.getString(R.string.login_google_error),
            )
        }
    }

    suspend fun clearCredentialState(context: Context) {
        runCatching {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        }
    }

    @Suppress("DiscouragedApi")
    private fun resolveServerClientId(context: Context): String {
        val defaultResId = context.resources.getIdentifier(
            "default_web_client_id",
            "string",
            context.packageName,
        )
        return if (defaultResId != 0) {
            context.getString(defaultResId)
        } else {
            context.getString(R.string.google_web_client_id)
        }
    }
}
