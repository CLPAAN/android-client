package org.tokend.template.features.tfa.view

import android.content.Context
import org.tokend.sdk.api.tfa.model.TfaFactor
import org.tokend.sdk.tfa.NeedTfaException
import org.tokend.sdk.tfa.TfaVerifier
import org.tokend.template.logic.persistance.CredentialsPersistor
import org.tokend.template.util.errorhandler.ErrorHandler
import org.tokend.template.view.ToastManager

class TfaDialogFactory(private val context: Context,
                       private val errorHandler: ErrorHandler,
                       private val credentialsPersistor: CredentialsPersistor?,
                       private val toastManager: ToastManager?) {
    /**
     * @return verification dialog for specified exception.
     * If there is no special dialog for given TFA factor type
     * then [TfaDefaultDialog] will be returned
     */
    @JvmOverloads
    fun getForException(tfaException: NeedTfaException,
                        verifierInterface: TfaVerifier.Interface,
                        email: String? = null): TfaDialog? {
        return when (tfaException.factorType) {
            TfaFactor.Type.PASSWORD -> {
                if (email != null)
                    TfaPasswordDialog(context, errorHandler, verifierInterface,
                            credentialsPersistor, tfaException, email, toastManager)
                else
                    null
            }
            TfaFactor.Type.TOTP -> TfaTotpDialog(context, errorHandler, verifierInterface)
            TfaFactor.Type.EMAIL -> TfaEmailOtpDialog(context, errorHandler, verifierInterface)
            else -> TfaDefaultDialog(context, errorHandler, verifierInterface)
        }
    }
}