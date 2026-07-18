package io.github.isht1008.opensmsbackup.gmail.auth

import com.google.android.gms.auth.api.identity.AuthorizationResult

data class GmailProfileAuthorizationResult(

    val profileId: String,

    val accountEmail: String,

    val authorizationResult: AuthorizationResult
)
