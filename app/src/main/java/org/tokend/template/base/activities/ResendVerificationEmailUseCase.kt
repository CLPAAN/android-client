package org.tokend.template.base.activities

import io.reactivex.Completable
import org.tokend.sdk.api.TokenDApi
import org.tokend.template.extensions.toCompletable

class ResendVerificationEmailUseCase(
        private val walletId: String,
        private val api: TokenDApi
) {
    fun perform(): Completable {
        return api
                .wallets
                .requestVerification(walletId)
                .toCompletable()
    }
}