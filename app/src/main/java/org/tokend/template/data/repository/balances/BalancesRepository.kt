package org.tokend.template.data.repository.balances

import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import org.tokend.template.data.repository.SystemInfoRepository
import org.tokend.template.data.repository.base.SimpleMultipleItemsRepository
import org.tokend.template.di.providers.AccountProvider
import org.tokend.template.di.providers.ApiProvider
import org.tokend.template.di.providers.WalletInfoProvider
import org.tokend.template.extensions.BalanceDetails
import org.tokend.template.extensions.toSingle
import org.tokend.template.logic.transactions.TxManager
import org.tokend.wallet.*
import org.tokend.wallet.xdr.Operation
import org.tokend.wallet.xdr.op_extensions.CreateBalanceOp

class BalancesRepository(
        private val apiProvider: ApiProvider,
        private val walletInfoProvider: WalletInfoProvider
) : SimpleMultipleItemsRepository<BalanceDetails>() {
    override val itemsCache = BalancesCache()

    override fun getItems(): Single<List<BalanceDetails>> {
        val signedApi = apiProvider.getSignedApi()
                ?: return Single.error(IllegalStateException("No signed API instance found"))
        val accountId = walletInfoProvider.getWalletInfo()?.accountId
                ?: return Single.error(IllegalStateException("No wallet info found"))

        return signedApi
                .accounts
                .getBalancesDetails(accountId)
                .toSingle()
    }

    /**
     * Creates balance for given assets,
     * updates repository on complete
     */
    fun create(accountProvider: AccountProvider,
               systemInfoRepository: SystemInfoRepository,
               txManager: TxManager,
               vararg assets: String): Completable {
        val accountId = walletInfoProvider.getWalletInfo()?.accountId
                ?: return Completable.error(IllegalStateException("No wallet info found"))
        val account = accountProvider.getAccount()
                ?: return Completable.error(IllegalStateException("Cannot obtain current account"))

        return systemInfoRepository.getNetworkParams()
                .flatMap { netParams ->
                    createBalanceCreationTransaction(netParams, accountId, account, assets)
                }
                .flatMap { transition ->
                    txManager.submit(transition)
                }
                .flatMapCompletable {
                    invalidate()
                    updateDeferred()
                }
                .doOnSubscribe {
                    isLoading = true
                }
                .doOnTerminate {
                    isLoading = false
                }
    }

    private fun createBalanceCreationTransaction(networkParams: NetworkParams,
                                                 sourceAccountId: String,
                                                 signer: Account,
                                                 assets: Array<out String>): Single<Transaction> {
        return Single.defer {
            val operations = assets.map {
                CreateBalanceOp(sourceAccountId, it)
            }

            val transaction =
                    TransactionBuilder(networkParams, PublicKeyFactory.fromAccountId(sourceAccountId))
                            .apply {
                                operations.forEach {
                                    addOperation(Operation.OperationBody.ManageBalance(it))
                                }
                            }
                            .build()

            transaction.addSignature(signer)

            Single.just(transaction)
        }.subscribeOn(Schedulers.computation())
    }
}