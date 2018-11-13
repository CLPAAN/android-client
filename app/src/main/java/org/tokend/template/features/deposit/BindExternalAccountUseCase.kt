package org.tokend.template.features.deposit

import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.rxkotlin.toMaybe
import io.reactivex.rxkotlin.toSingle
import org.tokend.sdk.api.transactions.model.SubmitTransactionResponse
import org.tokend.sdk.api.transactions.model.TransactionFailedException
import org.tokend.template.base.logic.di.providers.AccountProvider
import org.tokend.template.base.logic.di.providers.WalletInfoProvider
import org.tokend.template.base.logic.repository.AccountRepository
import org.tokend.template.base.logic.repository.SystemInfoRepository
import org.tokend.template.base.logic.repository.balances.BalancesRepository
import org.tokend.template.base.logic.transactions.TxManager
import org.tokend.wallet.NetworkParams
import org.tokend.wallet.Transaction
import org.tokend.wallet.xdr.Operation
import org.tokend.wallet.xdr.op_extensions.BindExternalAccountOp
import org.tokend.wallet.xdr.op_extensions.CreateBalanceOp

class BindExternalAccountUseCase(
        private val asset: String,
        private val externalSystemType: Int,
        private val walletInfoProvider: WalletInfoProvider,
        private val systemInfoRepository: SystemInfoRepository,
        private val balancesRepository: BalancesRepository,
        private val accountRepository: AccountRepository,
        private val accountProvider: AccountProvider,
        private val txManager: TxManager
) {
    class NoAvailableExternalAccountsException : Exception()

    private lateinit var accountId: String
    private lateinit var networkParams: NetworkParams
    private var isBalanceCreationRequired: Boolean = false
    private lateinit var transaction: Transaction

    fun perform(): Completable {
        return updateData()
                .flatMap {
                    getAccountId()
                }
                .doOnSuccess { account ->
                    this.accountId = account
                }
                .flatMap {
                    getNetworkParams()
                }
                .doOnSuccess { networkParams ->
                    this.networkParams = networkParams
                }
                .flatMap {
                    getIsBalanceCreationRequired()
                }
                .doOnSuccess { isBalanceCreationRequired ->
                    this.isBalanceCreationRequired = isBalanceCreationRequired
                }
                .flatMap {
                    getTransaction()
                }
                .doOnSuccess { transaction ->
                    this.transaction = transaction
                }
                .flatMap {
                    submitTransaction()
                }
                .doOnSuccess {
                    updateRepositories()
                }
                .toCompletable()
    }

    private fun updateData(): Single<Boolean> {
        return balancesRepository
                .updateIfNotFreshDeferred()
                .toSingleDefault(true)
    }

    private fun getAccountId(): Single<String> {
        return walletInfoProvider
                .getWalletInfo()
                ?.accountId
                .toMaybe()
                .switchIfEmpty(Single.error(IllegalStateException("Missing account ID")))
    }

    private fun getNetworkParams(): Single<NetworkParams> {
        return systemInfoRepository
                .getNetworkParams()
    }

    private fun getIsBalanceCreationRequired(): Single<Boolean> {
        return balancesRepository
                .itemsSubject
                .value
                .find { it.asset == asset }
                .let { it == null }
                .toSingle()
    }

    private fun getTransaction(): Single<Transaction> {
        val operations = mutableListOf<Operation.OperationBody>()

        if (isBalanceCreationRequired) {
            val createBalanceOp = CreateBalanceOp(accountId, asset)
            operations.add(Operation.OperationBody.ManageBalance(createBalanceOp))
        }

        val bindOp = BindExternalAccountOp(externalSystemType)
        operations.add(Operation.OperationBody.BindExternalSystemAccountId(bindOp))

        val account = accountProvider.getAccount()
                ?: throw IllegalStateException("Cannot obtain current account")

        return TxManager.createSignedTransaction(networkParams, accountId, account,
                *operations.toTypedArray())
    }

    private fun submitTransaction(): Single<SubmitTransactionResponse> {
        return txManager
                .submit(transaction)
                // Throw special error if there are no available addresses.
                .onErrorResumeNext { e: Throwable ->
                    if (e is TransactionFailedException &&
                            e.operationResultCodes.contains(
                                    TransactionFailedException.OP_NO_AVAILABLE_EXTERNAL_ACCOUNTS
                            )) {
                        Single.error(NoAvailableExternalAccountsException())
                    } else {
                        Single.error(e)
                    }
                }
    }

    private fun updateRepositories() {
        if (isBalanceCreationRequired) {
            balancesRepository.update()
        }

        accountRepository.update()
    }
}