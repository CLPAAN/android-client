package org.tokend.template.features.signup.logic

import io.reactivex.Single
import io.reactivex.functions.BiFunction
import org.tokend.rx.extensions.createAndSaveWalletSingle
import org.tokend.rx.extensions.randomSingle
import org.tokend.sdk.keyserver.KeyStorage
import org.tokend.sdk.keyserver.models.WalletCreateResult
import org.tokend.wallet.Account

/**
 * Creates wallet with given credentials and submits it
 */
class SignUpUseCase(
        private val email: String,
        private val password: CharArray,
        private val keyStorage: KeyStorage
) {
    private lateinit var rootAccount: Account
    private lateinit var recoveryAccount: Account

    fun perform(): Single<WalletCreateResult> {
        return getAccounts()
                .doOnSuccess { (rootAccount, recoveryAccount) ->
                    this.rootAccount = rootAccount
                    this.recoveryAccount = recoveryAccount
                }
                .flatMap {
                    createAndSaveWallet()
                }
    }

    private fun getAccounts(): Single<Pair<Account, Account>> {
        return Single.zip(
                generateNewAccount(),
                generateNewAccount(),
                BiFunction { x: Account, y: Account -> x to y }
        )
    }

    private fun generateNewAccount(): Single<Account> {
        return Account.randomSingle()
    }

    private fun createAndSaveWallet(): Single<WalletCreateResult> {
        return keyStorage.createAndSaveWalletSingle(
                email,
                password,
                rootAccount,
                recoveryAccount
        )
    }
}