package org.tokend.template.base.logic.repository.transactions

import io.reactivex.Single
import org.tokend.sdk.api.models.transactions.PaymentTransaction
import org.tokend.sdk.api.models.transactions.Transaction
import org.tokend.sdk.utils.PaymentRecordConverter
import org.tokend.template.base.logic.di.providers.ApiProvider
import org.tokend.template.base.logic.di.providers.WalletInfoProvider
import org.tokend.template.base.logic.repository.AccountDetailsRepository
import org.tokend.template.base.logic.repository.base.pagination.DataPage
import org.tokend.template.base.logic.repository.base.pagination.PageParams
import org.tokend.template.base.logic.repository.base.pagination.PagedDataRepository
import org.tokend.template.base.logic.repository.base.pagination.PagedRequestParams
import org.tokend.template.extensions.toSingle

class TxRepository(
        private val apiProvider: ApiProvider,
        private val walletInfoProvider: WalletInfoProvider,
        private val asset: String,
        private val accountDetailsRepository: AccountDetailsRepository? = null
) : PagedDataRepository<Transaction,
        TxRepository.RequestParams>() {
    class RequestParams(val asset: String,
                        pageParams: PageParams = PageParams()) : PagedRequestParams(pageParams)

    override val itemsCache = TxCache()

    override fun getItems(): Single<List<Transaction>> = Single.just(emptyList())

    override fun getPage(requestParams: RequestParams): Single<DataPage<Transaction>> {
        val signedApi = apiProvider.getSignedApi()
                ?: return Single.error(IllegalStateException("No signed API instance found"))
        val accountId = walletInfoProvider.getWalletInfo()?.accountId
                ?: return Single.error(IllegalStateException("No wallet info found"))
        val accountsToLoad = mutableListOf<String>()
        var nextCursor = ""
        var isLast = false

        return signedApi.getPayments(
                accountId = accountId,
                limit = requestParams.pageParams.limit,
                cursor = requestParams.pageParams.cursor,
                order = "desc",
                completedOnly = false,
                asset = requestParams.asset)
                .toSingle()
                .map { page ->
                    nextCursor = DataPage.getNextCursor(page) ?: ""
                    isLast = DataPage.isLast(page)

                    page.records
                }
                .map { paymentRecords ->
                    PaymentRecordConverter.toTransactions(
                            items = paymentRecords,
                            contextAsset = asset,
                            contextAccountId = accountId
                    )
                }
                .doOnSuccess { transactions ->
                    transactions.forEach { transaction ->
                        if (transaction is PaymentTransaction) {
                            accountsToLoad.add(transaction.sourceAccount)
                            accountsToLoad.add(transaction.destAccount)
                        }
                    }
                }
                .flatMap { transactions ->
                    accountDetailsRepository
                            ?.getDetails(accountsToLoad.toList())
                            ?.onErrorReturnItem(emptyMap())
                            ?.map { detailsMap ->
                                transactions.forEach {
                                    if (it is PaymentTransaction) {
                                        if (it.isSent) {
                                            it.counterpartyNickname =
                                                    detailsMap[it.destAccount]?.email
                                        } else {
                                            it.counterpartyNickname =
                                                    detailsMap[it.sourceAccount]?.email
                                        }
                                    }
                                }

                                transactions
                            }
                            ?: Single.just(transactions)
                }
                .map { DataPage(nextCursor, it, isLast) }
    }

    override fun getNextPageRequestParams(): RequestParams {
        return RequestParams(asset, PageParams(cursor = nextCursor))
    }
}