package org.tokend.template.features.withdraw

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.activity_details.*
import org.tokend.template.R
import org.tokend.template.activities.BaseActivity
import org.tokend.template.features.withdraw.logic.ConfirmWithdrawalRequestUseCase
import org.tokend.template.features.withdraw.model.WithdrawalRequest
import org.tokend.template.logic.transactions.TxManager
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.view.InfoCard
import org.tokend.template.view.util.ProgressDialogFactory

class WithdrawalConfirmationActivity : BaseActivity() {
    private lateinit var request: WithdrawalRequest

    override fun onCreateAllowed(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_details)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        request =
                (intent.getSerializableExtra(WITHDRAWAL_REQUEST_EXTRA) as? WithdrawalRequest)
                ?: return

        displayDetails()
    }

    private fun displayDetails() {
        displayDestinationAddress()
        displayToPay()
        displayToReceive()
    }

    private fun displayDestinationAddress() {
        InfoCard(cards_layout)
                .setHeading(R.string.tx_withdrawal_destination, null)
                .addRow(request.destinationAddress, null)
    }

    private fun displayToPay() {
        val toPay = request.amount + request.fee.total
        val minDecimals = amountFormatter.getDecimalDigitsCount(request.asset)

        InfoCard(cards_layout)
                .setHeading(R.string.to_pay,
                        amountFormatter.formatAssetAmount(toPay, request.asset, minDecimals))
                .addRow(R.string.amount,
                        "+${amountFormatter.formatAssetAmount(request.amount,
                                request.asset, minDecimals)}")
                .addRow(R.string.fixed_fee,
                        "+${amountFormatter.formatAssetAmount(request.fee.fixed,
                                request.asset, minDecimals)}")
                .addRow(R.string.percent_fee,
                        "+${amountFormatter.formatAssetAmount(request.fee.percent,
                                request.asset, minDecimals)}")
    }

    private fun displayToReceive() {
        InfoCard(cards_layout)
                .setHeading(R.string.to_receive, amountFormatter.formatAssetAmount(request.amount,
                        request.asset, amountFormatter.getDecimalDigitsCount(request.asset)))
                .addRow(getString(R.string.template_withdrawal_fee_warning, request.asset),
                        null)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.confirmation, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.confirm -> confirm()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun confirm() {
        val progress = ProgressDialogFactory.getTunedDialog(this)

        ConfirmWithdrawalRequestUseCase(
                request,
                accountProvider,
                repositoryProvider,
                TxManager(apiProvider)
        )
                .perform()
                .compose(ObservableTransformers.defaultSchedulersCompletable())
                .doOnSubscribe {
                    progress.show()
                }
                .doOnTerminate {
                    progress.dismiss()
                }
                .subscribeBy(
                        onComplete = {
                            progress.dismiss()
                            toastManager.long(R.string.withdrawal_request_created)
                            finishWithSuccess()
                        },
                        onError = {
                            errorHandlerFactory.getDefault().handle(it)
                        }
                )
    }

    private fun finishWithSuccess() {
        setResult(Activity.RESULT_OK,
                Intent().putExtra(WITHDRAWAL_REQUEST_EXTRA, request))
        finish()
    }

    companion object {
        const val WITHDRAWAL_REQUEST_EXTRA = "withdrawal_request"
    }
}
