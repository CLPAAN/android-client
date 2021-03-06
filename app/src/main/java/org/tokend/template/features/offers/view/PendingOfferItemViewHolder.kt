package org.tokend.template.features.offers.view

import android.view.View
import org.tokend.template.R
import org.tokend.template.view.adapter.base.BaseViewHolder
import org.tokend.template.view.history.HistoryItemView
import org.tokend.template.view.history.HistoryItemViewImpl
import org.tokend.template.view.util.formatter.AmountFormatter

class PendingOfferItemViewHolder(
        view: View,
        private val amountFormatter: AmountFormatter,
        smallIcon: Boolean
) : BaseViewHolder<PendingOfferListItem>(view),
        HistoryItemView by HistoryItemViewImpl(view, smallIcon) {

    override fun bind(item: PendingOfferListItem) {
        displayIcon(item)
        displayAction(item)
        displayCounterparty(item)
        displayAmount(item)
        displayExtraInfo()
    }

    private fun displayIcon(item: PendingOfferListItem) {
        val icon = if (item.isBuy) incomingIcon else outgoingIcon
        iconImageView.setImageDrawable(icon)
    }

    private fun displayAction(item: PendingOfferListItem) {
        val actionStringRes =
                when {
                    item.isInvestment -> R.string.tx_action_investment
                    item.isBuy -> R.string.buy
                    else -> R.string.sell
                }
        actionTextView.setText(actionStringRes)
    }

    private fun displayCounterparty(item: PendingOfferListItem) {
        val counterpartyStringRes =
                if (item.isInvestment)
                    R.string.template_tx_in
                else
                    R.string.template_tx_for
        counterpartyTextView.visibility = View.VISIBLE
        counterpartyTextView.text =
                view.context.getString(counterpartyStringRes, item.counterpartyAssetCode)

    }

    private fun displayAmount(item: PendingOfferListItem) {
        var amountString = amountFormatter.formatAssetAmount(item.amount, item.assetCode)
        var amountColor = incomingColor

        if (item.isInvestment || !item.isBuy) {
            amountString = "-$amountString"
            amountColor = outgoingColor
        }

        amountTextView.text = amountString
        amountTextView.setTextColor(amountColor)
    }

    private fun displayExtraInfo() {
        extraInfoTextView.visibility = View.GONE
    }
}