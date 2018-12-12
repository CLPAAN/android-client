package org.tokend.template.features.send.model

import org.tokend.sdk.api.fees.model.Fee
import org.tokend.sdk.utils.extentions.encodeBase64String
import java.io.Serializable
import java.math.BigDecimal
import java.security.SecureRandom

data class PaymentRequest(
        val amount: BigDecimal,
        val asset: String,
        val senderAccountId: String,
        val senderBalanceId: String,
        val recipientAccountId: String,
        val recipientNickname: String,
        val senderFee: Fee,
        val recipientFee: Fee,
        val paymentSubject: String?,
        val reference: String = SecureRandom.getSeed(16).encodeBase64String()
) : Serializable {
    var senderPaysRecipientFee = false
}