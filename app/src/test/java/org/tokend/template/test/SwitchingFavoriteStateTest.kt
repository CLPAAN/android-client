package org.tokend.template.test

import junit.framework.Assert
import org.junit.Test
import org.tokend.sdk.factory.JsonApiToolsProvider
import org.tokend.template.data.model.FavoriteRecord
import org.tokend.template.di.providers.AccountProviderFactory
import org.tokend.template.di.providers.ApiProviderFactory
import org.tokend.template.di.providers.RepositoryProviderImpl
import org.tokend.template.di.providers.WalletInfoProviderFactory
import org.tokend.template.features.invest.logic.SwitchFavoriteUseCase
import org.tokend.template.logic.Session

class SwitchingFavoriteStateTest {

    @Test
    fun switchState() {
        val urlConfigProvider = Util.getUrlConfigProvider()
        val session = Session(
                WalletInfoProviderFactory().createWalletInfoProvider(),
                AccountProviderFactory().createAccountProvider()
        )
        val apiProvider = ApiProviderFactory().createApiProvider(urlConfigProvider, session)

        val email = Util.getEmail()
        val password = Config.DEFAULT_PASSWORD

        val repositoryProvider = RepositoryProviderImpl(apiProvider, session, urlConfigProvider,
                JsonApiToolsProvider.getObjectMapper())

        Util.getVerifiedWallet(
                email, password, apiProvider, session, repositoryProvider
        )

        val favoritesRepository = repositoryProvider.favorites()

        val favoriteEntry = FavoriteRecord.assetPair("ETH", "BTC")

        val useCase = SwitchFavoriteUseCase(favoriteEntry, favoritesRepository)

        useCase.perform().blockingAwait()

        Assert.assertTrue("Favorites repository must contain a newly created entry",
                favoritesRepository.itemsList.isNotEmpty())

        useCase.perform().blockingAwait()

        Assert.assertTrue("Favorites repository must be empty after switching the same favorite entry",
                favoritesRepository.itemsList.isEmpty())
    }
}