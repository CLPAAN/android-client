package org.tokend.template.test

import junit.framework.Assert
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.tokend.template.data.repository.SystemInfoRepository
import org.tokend.template.di.providers.AccountProviderFactory
import org.tokend.template.di.providers.ApiProviderFactory
import org.tokend.template.features.recovery.logic.RecoveryUseCase
import org.tokend.template.logic.wallet.WalletUpdateManager

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class RecoveryTest {
    @Test
    fun aRecoveryOfConfirmed() {
        val urlConfigProvider = Util.getUrlConfigProvider()
        val accountProvider = AccountProviderFactory().createAccountProvider()
        val apiProvider = ApiProviderFactory().createApiProvider(urlConfigProvider, accountProvider)

        val email = Util.getEmail()
        val password = Config.DEFAULT_PASSWORD
        val newPassword = "qwerty".toCharArray()

        val (walletData, rootAccount, recoveryAccount)
                = apiProvider.getKeyServer().createAndSaveWallet(email, password).execute().get()
        accountProvider.setAccount(rootAccount)

        val recoverySeed = recoveryAccount.secretSeed!!

        System.out.println("Email is $email")
        System.out.println("Recovery seed is ${recoverySeed.joinToString("")}")

        val useCase = RecoveryUseCase(
                email,
                recoverySeed,
                newPassword,
                WalletUpdateManager(SystemInfoRepository(apiProvider)),
                urlConfigProvider
        )

        useCase.perform().blockingAwait()

        try {
            apiProvider.getKeyServer().getWalletInfo(email, newPassword)
                    .execute().get()
        } catch (e: Exception) {
            Assert.fail("Recovered wallet must be accessible with a new password")
        }
    }
}