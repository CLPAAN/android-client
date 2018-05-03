package org.tokend.template.base.logic

import com.google.common.io.BaseEncoding
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.rxkotlin.toSingle
import io.reactivex.schedulers.Schedulers
import org.tokend.sdk.api.models.WalletData
import org.tokend.sdk.api.models.WalletRelation
import org.tokend.sdk.keyserver.KeyStorage
import org.tokend.sdk.keyserver.models.KdfAttributes
import org.tokend.sdk.keyserver.models.LoginParamsResponse
import org.tokend.template.BuildConfig
import org.tokend.wallet.Account
import java.security.SecureRandom

object SignUpManager {
    private const val SEED_LENGTH = 16

    private class SignUpFlowData {
        lateinit var loginParams: LoginParamsResponse
    }

    fun signUp(email: String, password: String,
               rootAccount: Account, recoveryAccount: Account,
               derivationSalt: ByteArray = SecureRandom.getSeed(SEED_LENGTH)): Completable {

        val flowData = SignUpFlowData()

        return getDefaultLoginParams()
                .flatMap { loginParams ->
                    // Complete default login params with derivation salt.
                    loginParams.kdfAttributes.encodedSalt =
                            BaseEncoding.base64().encode(derivationSalt)
                    flowData.loginParams = loginParams

                    getWalletKeys(email, password, loginParams.kdfAttributes)
                }
                .flatMap { (walletIdHex, walletKey) ->
                    getWallet(email, walletIdHex, walletKey,
                            rootAccount, recoveryAccount,
                            flowData.loginParams)
                }
                .flatMapCompletable { wallet ->
                    saveWallet(wallet)
                }
    }

    private fun getDefaultLoginParams(): Single<LoginParamsResponse> {
        return {
            KeyStorage(BuildConfig.API_URL).getApiLoginParams()
        }.toSingle()
    }

    private fun getWalletKeys(email: String, password: String, kdfAttributes: KdfAttributes)
            : Single<Pair<String, ByteArray>> {
        return {
            Pair(
                    KeyStorage.getWalletIdHex(email, password, kdfAttributes),
                    KeyStorage.getWalletKey(email, password, kdfAttributes)
            )
        }.toSingle().subscribeOn(Schedulers.computation())
    }

    private fun getWallet(email: String,
                          walletId: String,
                          walletKey: ByteArray,
                          rootAccount: Account,
                          recoveryAccount: Account,
                          loginParams: LoginParamsResponse): Single<WalletData> {
        return {
            val derivationSalt = loginParams.kdfAttributes.salt
            val rootSeed = rootAccount.secretSeed
                    ?: throw IllegalStateException("Provided account has no private key")

            // Base wallet has only KDF relation, we need more.
            val wallet = KeyStorage(BuildConfig.API_URL)
                    .createBaseWallet(email, derivationSalt, walletId, walletKey,
                            rootSeed, rootAccount.accountId, loginParams.id)

            // Add recovery relation.
            // Recovery seed must be encrypted with itself.
            val recoverySeed = recoveryAccount.secretSeed
                    ?: throw IllegalStateException("Provided recovery account has no private key")
            val (recoveryWalletId, recoveryKey) =
                    getWalletKeys(email, recoverySeed, loginParams.kdfAttributes).blockingGet()
            val encryptedRecovery = KeyStorage.encryptWalletKey(email, recoverySeed,
                    recoveryAccount.accountId, recoveryKey, derivationSalt)
            wallet.addRelation(WalletRelation(WalletRelation.RELATION_RECOVERY,
                    WalletRelation.RELATION_RECOVERY, recoveryWalletId,
                    recoveryAccount.accountId, encryptedRecovery))

            // Add password factor relation.
            val passwordFactorAccount = Account.random()
            val passwordFactorSeed = passwordFactorAccount.secretSeed!!
            val encryptedPasswordFactor = KeyStorage.encryptWalletKey(email, passwordFactorSeed,
                    passwordFactorAccount.accountId, walletKey, derivationSalt)
            wallet.addRelation(WalletRelation(WalletRelation.RELATION_PASSWORD_FACTOR,
                    WalletRelation.RELATION_PASSWORD, walletId,
                    passwordFactorAccount.accountId, encryptedPasswordFactor))

            wallet
        }.toSingle().subscribeOn(Schedulers.computation())
    }

    private fun saveWallet(walletData: WalletData): Completable {
        return {
            KeyStorage(BuildConfig.API_URL).saveWallet(walletData)
        }.toSingle().toCompletable()
    }
}