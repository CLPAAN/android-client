package org.tokend.template.base.fragments.settings

import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.preference.SwitchPreferenceCompat
import android.support.v7.widget.Toolbar
import android.view.View
import android.widget.LinearLayout
import io.reactivex.Completable
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.subjects.BehaviorSubject
import kotlinx.android.synthetic.main.layout_progress.*
import kotlinx.android.synthetic.main.toolbar.*
import org.jetbrains.anko.browse
import org.jetbrains.anko.clipboardManager
import org.tokend.sdk.api.tfa.model.TfaFactor
import org.tokend.template.R
import org.tokend.template.base.fragments.ToolbarProvider
import org.tokend.template.base.logic.repository.tfa.TfaBackendsRepository
import org.tokend.template.base.view.util.LoadingIndicatorManager
import org.tokend.template.util.Navigator
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.util.ToastManager

class GeneralSettingsFragment : SettingsFragment(), ToolbarProvider {
    override val toolbarSubject: BehaviorSubject<Toolbar> = BehaviorSubject.create<Toolbar>()

    override fun getScreenKey(): String? = null

    private val TFA_BACKEND_TYPE = TfaFactor.Type.TOTP

    private val tfaRepository: TfaBackendsRepository
        get() = repositoryProvider.tfaBackends()
    private var tfaPreference: SwitchPreferenceCompat? = null
    private val tfaBackend: TfaFactor?
        get() = tfaRepository.itemsSubject.value.find { it.type == TFA_BACKEND_TYPE }
    private val isTfaEnabled: Boolean
        get() = tfaBackend != null && tfaBackend?.attributes?.priority?.let { it > 0 } ?: false

    private val loadingIndicator = LoadingIndicatorManager(
            showLoading = { progress?.show() },
            hideLoading = { progress?.hide() }
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Include toolbar and progress.
        if (view is LinearLayout) {
            val appbar = layoutInflater.inflate(R.layout.appbar, view, false)
            view.addView(appbar, 0)

            toolbar.title = getString(R.string.settings_title)
            toolbarSubject.onNext(toolbar)

            val progress = layoutInflater.inflate(R.layout.layout_progress, view, false)
            view.addView(progress, 1)
        }
    }

    override fun reloadPreferences() {
        super.reloadPreferences()

        initAccountCategory()
        initSecurityCategory()
        initInfoCategory()
    }

    // region Account
    private fun initAccountCategory() {
        initAccountIdItem()
        initKycItem()
    }

    private fun initAccountIdItem() {
        val accountIdPreference = findPreference("account_id")
        accountIdPreference?.setOnPreferenceClickListener {
            val accountId = walletInfoProvider.getWalletInfo()?.accountId
                    ?: getString(R.string.error_try_again)
            activity?.let { parentActivity ->
                Navigator.openQrShare(parentActivity,
                        data = accountId,
                        title = getString(R.string.account_id_title),
                        shareLabel = getString(R.string.share_account_id)
                )
            }

            true
        }
    }

    private fun initKycItem() {
        val kycPreference = findPreference("kyc")
        kycPreference?.setOnPreferenceClickListener {
            activity?.browse(urlConfigProvider.getConfig().kyc, true)
            true
        }
    }
    // endregion

    // region Security
    private fun initSecurityCategory() {
        initTfaItem()
        initChangePasswordItem()
    }

    private fun initTfaItem() {
        tfaPreference = findPreference("tfa") as? SwitchPreferenceCompat
        tfaPreference?.setOnPreferenceClickListener {
            tfaPreference?.isChecked = isTfaEnabled
            switchTfa()
            false
        }

        tfaRepository.updateIfNotFresh()
        subscribeToTfaBackends()
    }

    private fun initChangePasswordItem() {
        val changePasswordPreference = findPreference("change_password")
        changePasswordPreference?.setOnPreferenceClickListener {
            activity?.let { parentActivity ->
                Navigator.openPasswordChange(parentActivity, 3597)
            }

            true
        }
    }
    // endregion

    // region TFA
    private fun subscribeToTfaBackends() {
        tfaRepository.itemsSubject
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe {
                    updateTfaPreference()
                }

        tfaRepository.loadingSubject
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe {
                    loadingIndicator.setLoading(it, "tfa")
                    updateTfaPreference()
                }
                .addTo(compositeDisposable)
    }

    private fun updateTfaPreference() {
        tfaPreference?.isEnabled = !tfaRepository.isLoading
        tfaPreference?.isChecked = isTfaEnabled
    }

    private fun switchTfa() {
        if (isTfaEnabled) {
            disableTfa()
        } else {
            addAndEnableNewTfaBackend()
        }
    }

    private fun disableTfa() {
        tfaRepository.deleteBackend(tfaBackend!!.id!!)
                .compose(ObservableTransformers.defaultSchedulersCompletable())
                .subscribeBy(
                        onError = { errorHandlerFactory.getDefault().handle(it) }
                )
                .addTo(compositeDisposable)
    }

    private fun addAndEnableNewTfaBackend() {
        (tfaBackend?.id?.let { oldTfaBackendId ->
            tfaRepository.deleteBackend(oldTfaBackendId)
        } ?: Completable.complete())
                .andThen(tfaRepository.addBackend(TFA_BACKEND_TYPE))
                .compose(ObservableTransformers.defaultSchedulersSingle())
                .subscribeBy(
                        onSuccess = { tryToEnableTfaBackend(it) },
                        onError = { errorHandlerFactory.getDefault().handle(it) }
                )
                .addTo(compositeDisposable)
    }

    private fun tryToEnableTfaBackend(backend: TfaFactor) {
        val secret = backend.attributes?.secret
        val id = backend.id
        val seed = backend.attributes?.seed

        if (secret == null || id == null || seed == null) {
            errorHandlerFactory.getDefault().handle(IllegalStateException())
            return
        }

        val dialog = AlertDialog.Builder(requireContext(), R.style.AlertDialogStyle)
                .setTitle(R.string.tfa_add_dialog_title)
                .setMessage(getString(R.string.template_tfa_add_dialog_message, secret))
                .setPositiveButton(R.string.continue_action) { _, _ ->
                    enableTfaBackend(id)
                }
                .setNegativeButton(R.string.open_action, null)
                .setNeutralButton(R.string.copy_action, null)
                .show()

        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            context?.clipboardManager?.text = secret
            ToastManager(requireContext()).short(R.string.tfa_key_copied)
        }

        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
            activity?.browse(seed)
        }
    }

    private fun enableTfaBackend(id: Long) {
        tfaRepository.setBackendAsMain(id)
                .compose(ObservableTransformers.defaultSchedulersCompletable())
                .subscribeBy(
                        onError = { errorHandlerFactory.getDefault().handle(it) }
                )
                .addTo(compositeDisposable)
    }
    // endregion

    // region Info
    private fun initInfoCategory() {
        initTermsItem()
        initOpenSourceLicensesItem()
    }

    private fun initTermsItem() {
        val termsPreference = findPreference("terms")
        termsPreference?.setOnPreferenceClickListener {
            requireContext().browse(urlConfigProvider.getConfig().terms)
        }
    }

    private fun initOpenSourceLicensesItem() {
        val openSourceLicensesPreference = findPreference("open_source_licenses")
        openSourceLicensesPreference?.setOnPreferenceClickListener {
            OpenSourceLicensesDialog(requireContext(), R.style.AlertDialogStyle)
                    .show()

            true
        }
    }
    // endregion
}