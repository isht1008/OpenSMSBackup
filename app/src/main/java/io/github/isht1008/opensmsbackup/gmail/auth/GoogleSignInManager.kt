package io.github.isht1008.opensmsbackup.gmail.auth

import android.content.Context
import androidx.credentials.CredentialManager

class GoogleSignInManager(
    context: Context
) {

    private val credentialManager =
        CredentialManager.create(context)

}