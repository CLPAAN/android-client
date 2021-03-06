package org.tokend.template.data.model.history.details

import org.tokend.sdk.api.generated.resources.*
import org.tokend.template.data.model.history.SimpleFeeRecord
import java.io.Serializable
import java.math.BigDecimal

/**
 * Financial action resulted in the balance change
 */
sealed class BalanceChangeCause : Serializable {
    object Unknown : BalanceChangeCause()

    // ------- AML Alert -------- //

    class AmlAlert(
            val reason: String?
    ) : BalanceChangeCause() {
        constructor(op: OpCreateAMLAlertRequestDetailsResource) : this(
                reason = op.creatorDetails?.get("reason")?.asText()?.takeIf { it.isNotEmpty() }
        )
    }

    // ------- Offer -------- //

    open class Offer(
            val offerId: Long?,
            val orderBookId: Long,
            val price: BigDecimal,
            val isBuy: Boolean,
            val baseAmount: BigDecimal,
            val baseAssetCode: String,
            val quoteAssetCode: String,
            val fee: SimpleFeeRecord
    ) : BalanceChangeCause() {
        constructor(op: OpManageOfferDetailsResource) : this(
                offerId = op.offerId,
                orderBookId = op.orderBookId,
                price = op.price,
                isBuy = op.isBuy,
                baseAmount = op.baseAmount,
                baseAssetCode = op.baseAsset.id,
                quoteAssetCode = op.quoteAsset.id,
                fee = SimpleFeeRecord(op.fee)
        )

        val quoteAmount: BigDecimal = baseAmount * price
    }

    // ------- Offer Match -------- //

    open class MatchedOffer(
            offerId: Long,
            orderBookId: Long,
            price: BigDecimal,
            isBuy: Boolean,
            baseAmount: BigDecimal,
            baseAssetCode: String,
            quoteAssetCode: String,
            fee: SimpleFeeRecord,
            val charged: ParticularBalanceChangeDetails,
            val funded: ParticularBalanceChangeDetails

    ) : Offer(offerId, orderBookId, price, isBuy, baseAmount, baseAssetCode, quoteAssetCode, fee) {
        constructor(op: OpManageOfferDetailsResource,
                    effect: EffectMatchedResource) : this(
                offerId = effect.offerId,
                orderBookId = effect.orderBookId,
                price = effect.price,
                isBuy = op.isBuy,
                baseAmount = op.baseAmount,
                baseAssetCode = op.baseAsset.id,
                quoteAssetCode = op.quoteAsset.id,
                fee = SimpleFeeRecord(op.fee),
                charged = ParticularBalanceChangeDetails(effect.charged),
                funded = ParticularBalanceChangeDetails(effect.funded)
        )

        /**
         * @return true if this match has funded in given asset
         */
        fun isReceivedByAsset(assetCode: String): Boolean {
            return funded.assetCode == assetCode
        }
    }

    // ------- Investment -------- //

    class Investment(
            offerId: Long,
            orderBookId: Long,
            price: BigDecimal,
            baseAmount: BigDecimal,
            baseAssetCode: String,
            quoteAssetCode: String,
            fee: SimpleFeeRecord,
            charged: ParticularBalanceChangeDetails,
            funded: ParticularBalanceChangeDetails
    ) : MatchedOffer(offerId, orderBookId, price, true,
            baseAmount, baseAssetCode, quoteAssetCode, fee, charged, funded) {
        constructor(effect: EffectMatchedResource) : this(
                offerId = effect.offerId,
                orderBookId = effect.orderBookId,
                price = effect.price,
                baseAmount = effect.funded.amount, // Investments are always fund in base asset
                baseAssetCode = effect.funded.assetCode,
                quoteAssetCode = effect.charged.assetCode, // Investments always charge in quote asset
                fee = SimpleFeeRecord(effect.charged.fee), // Fee is always paid in quote asset
                charged = ParticularBalanceChangeDetails(effect.charged),
                funded = ParticularBalanceChangeDetails(effect.funded)
        )
    }

    // ------- Sale cancellation -------- //

    object SaleCancellation: BalanceChangeCause()

    // ------- Issuance -------- //

    class Issuance(
            val cause: String?,
            val reference: String?
    ) : BalanceChangeCause() {
        constructor(op: OpCreateIssuanceRequestDetailsResource) : this(
                cause = op.creatorDetails?.get("cause")?.asText(),
                reference = op.reference
        )
    }

    // ------- Payment -------- //

    class Payment(
            val sourceAccountId: String,
            val destAccountId: String,
            val sourceFee: SimpleFeeRecord,
            val destFee: SimpleFeeRecord,
            val isDestFeePaidBySource: Boolean,
            val subject: String?,
            var sourceName: String?,
            var destName: String?
    ) : BalanceChangeCause() {
        constructor(op: OpPaymentDetailsResource) : this(
                sourceAccountId = op.accountFrom.id,
                destAccountId = op.accountTo.id,
                destFee = SimpleFeeRecord(op.destinationFee),
                sourceFee = SimpleFeeRecord(op.sourceFee),
                isDestFeePaidBySource = op.sourcePayForDestination(),
                subject = op.subject.takeIf { it.isNotEmpty() },
                destName = null,
                sourceName = null
        )

        /**
         * @return true if given [accountId] is the receiver of the payment
         */
        fun isReceived(accountId: String): Boolean {
            return destAccountId == accountId
        }

        /**
         * @return receiver or sender account ID on [yourAccountId]
         *
         * @see isReceived
         */
        fun getCounterpartyAccountId(yourAccountId: String): String {
            return if (isReceived(yourAccountId))
                sourceAccountId
            else
                destAccountId
        }

        /**
         * @return receiver or sender name if it's set based on [yourAccountId]
         *
         * @see isReceived
         */
        fun getCounterpartyName(yourAccountId: String): String? {
            return if (isReceived(yourAccountId))
                sourceName
            else
                destName
        }
    }

    // ------- Payout -------- //

    class Payout(
            /**
             *  Max amount of asset, that owner wants to pay out
             */
            val maxPayoutAmount: BigDecimal,
            /**
             * Min tokens amount which will be payed for one balance
             */
            val minPayoutAmount: BigDecimal,
            /**
             * Min tokens amount for which holder will received dividends
             */
            val minAssetHolderAmount: BigDecimal,

            /**
             * Amount of tokens that were actually payed out
             */
            val actualPayoutAmount: BigDecimal,

            val expectedFee: SimpleFeeRecord,
            val actualFee: SimpleFeeRecord,
            val assetCode: String,
            val sourceAccountId: String
    ) : BalanceChangeCause() {
        constructor(op: OpPayoutDetailsResource) : this(
                maxPayoutAmount = op.maxPayoutAmount,
                minPayoutAmount = op.minPayoutAmount,
                minAssetHolderAmount = op.minAssetHolderAmount,
                expectedFee = SimpleFeeRecord(op.expectedFee),
                actualFee = SimpleFeeRecord(op.actualFee),
                actualPayoutAmount = op.actualPayoutAmount,
                assetCode = op.asset.id,
                sourceAccountId = op.sourceAccount.id
        )

        /**
         * @return true if account with given ID is an issuer of the payout
         */
        fun isIssuer(accountId: String): Boolean {
            return sourceAccountId == accountId
        }
    }

    // ------- Withdrawal -------- //

    class Withdrawal(
            val destinationAddress: String
    ) : BalanceChangeCause() {
        constructor(op: OpCreateWithdrawRequestDetailsResource) : this(
                destinationAddress = op.creatorDetails.get("address").asText()
        )
    }
}