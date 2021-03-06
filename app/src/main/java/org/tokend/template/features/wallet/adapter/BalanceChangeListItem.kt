package org.tokend.template.features.wallet.adapter

import org.tokend.template.data.model.history.BalanceChange
import org.tokend.template.data.model.history.BalanceChangeAction
import org.tokend.template.data.model.history.details.BalanceChangeCause
import org.tokend.template.util.DateProvider
import java.math.BigDecimal
import java.util.*

class BalanceChangeListItem(
        val action: Action,
        val amount: BigDecimal,
        val assetCode: String,
        val isReceived: Boolean?,
        override val date: Date,
        val counterparty: String?,
        val source: BalanceChange? = null
) : DateProvider {
    enum class Action {
        LOCKED,
        UNLOCKED,
        WITHDRAWN,
        MATCHED,
        ISSUED,
        RECEIVED,
        SENT,
        CHARGED
    }

    constructor(balanceChange: BalanceChange,
                accountId: String) : this(
            action = getAction(balanceChange),
            amount = balanceChange.amount,
            assetCode = balanceChange.assetCode,
            isReceived = balanceChange.isReceived,
            date = balanceChange.date,
            counterparty = getCounterparty(balanceChange, accountId),
            source = balanceChange
    )

    private companion object {
        private fun getAction(balanceChange: BalanceChange): Action {
            return when (balanceChange.action) {
                BalanceChangeAction.LOCKED -> BalanceChangeListItem.Action.LOCKED
                BalanceChangeAction.CHARGED_FROM_LOCKED -> BalanceChangeListItem.Action.CHARGED
                BalanceChangeAction.CHARGED ->
                    if (balanceChange.cause is BalanceChangeCause.Payment)
                        BalanceChangeListItem.Action.SENT
                    else
                        BalanceChangeListItem.Action.CHARGED
                BalanceChangeAction.UNLOCKED -> BalanceChangeListItem.Action.UNLOCKED
                BalanceChangeAction.WITHDRAWN -> BalanceChangeListItem.Action.WITHDRAWN
                BalanceChangeAction.MATCHED -> BalanceChangeListItem.Action.MATCHED
                BalanceChangeAction.ISSUED -> BalanceChangeListItem.Action.ISSUED
                BalanceChangeAction.FUNDED -> BalanceChangeListItem.Action.RECEIVED
            }
        }

        private fun getCounterparty(balanceChange: BalanceChange, accountId: String): String? {
            val details = balanceChange.cause

            if (balanceChange.action == BalanceChangeAction.LOCKED
                    || balanceChange.action == BalanceChangeAction.UNLOCKED) {
                // Do not show "Locked to ole21@mail.com"
                return null
            }

            return when (details) {
                is BalanceChangeCause.Payment ->
                    details.getCounterpartyName(accountId)
                            ?: details.getCounterpartyAccountId(accountId)
                is BalanceChangeCause.Withdrawal ->
                    details.destinationAddress
                else -> null
            }
        }
    }
}