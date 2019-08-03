package com.motorro.appupdatewrapper

import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.install.model.AppUpdateType.FLEXIBLE
import com.google.android.play.core.install.model.InstallStatus.*
import com.google.android.play.core.install.model.UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
import com.google.android.play.core.install.model.UpdateAvailability.UPDATE_AVAILABLE
import com.motorro.appupdatewrapper.AppUpdateException.Companion.ERROR_UPDATE_TYPE_NOT_ALLOWED

/**
 * Flexible update flow
 */
internal sealed class FlexibleUpdateState(): AppUpdateState() {
    companion object {
        /**
         * Starts flexible update flow
         * @param stateMachine Application update stateMachine state-machine
         */
        fun start(stateMachine: AppUpdateStateMachine) {
            stateMachine.setUpdateState(Initial())
        }
    }

    /**
     * Transfers to update-checking state
     */
    protected fun checking() {
        stateMachine.setUpdateState(Checking())
    }

    /**
     * Transfers to update-consent state
     */
    protected fun updateConsent(appUpdateInfo: AppUpdateInfo) {
        stateMachine.setUpdateState(UpdateConsent(appUpdateInfo))
    }
    
    /**
     * Transfers to downloading state
     */
    protected fun downloading() {
        stateMachine.setUpdateState(Downloading())
    }

    /**
     * Transfers to install-consent state
     */
    protected fun installConsent() {
        stateMachine.setUpdateState(InstallConsent())
    }

    /**
     * Transfers to complete-update state
     */
    protected fun completeUpdate() {
        stateMachine.setUpdateState(CompleteUpdate())
    }

    /**
     * Initial state
     */
    internal class Initial() : FlexibleUpdateState() {
        /**
         * Handles lifecycle `onStart`
         */
        override fun onStart() {
            super.onStart()
            ifNotBroken {
                checking()
            }
        }
    }

    /**
     * Checks for update
     */
    internal class Checking(): FlexibleUpdateState() {
        /*
         * Set to true on [onStop] to prevent view interaction
         * as there is no way to abort task
         */
        private var stopped: Boolean = false

        /**
         * Handles lifecycle `onStart`
         */
        override fun onStart() {
            super.onStart()
            stopped = false
            withUpdateView {
                updateChecking()
            }
            stateMachine.updateManager
                .appUpdateInfo
                .addOnSuccessListener {
                    if (!stopped) {
                        processUpdateInfo(it)
                    }
                }
                .addOnFailureListener {
                    if (!stopped) {
                        reportUpdateCheckFailure(it)
                    }
                }
        }

        /**
         * Handles lifecycle `onStop`
         */
        override fun onStop() {
            super.onStop()
            stopped = true
            complete()
        }

        /**
         * Transfers to failed state
         */
        private fun reportUpdateCheckFailure(appUpdateException: Throwable) {
            reportError(AppUpdateException(AppUpdateException.ERROR_UPDATE_CHECK_FAILED, appUpdateException))
        }

        /**
         * Starts update on success or transfers to failed state
         */
        private fun processUpdateInfo(appUpdateInfo: AppUpdateInfo) {
            with(appUpdateInfo) {
                when (updateAvailability()) {
                    DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> when (installStatus()) {
                        REQUIRES_UI_INTENT -> updateConsent(appUpdateInfo)
                        PENDING, DOWNLOADING -> downloading()
                        DOWNLOADED -> installConsent()
                        INSTALLING -> completeUpdate()
                        else -> complete()
                    }
                    UPDATE_AVAILABLE -> updateConsent(appUpdateInfo)
                    else -> complete()
                }
            }
        }
    }

    /**
     * Watches for update download status
     */
    internal class Downloading(): FlexibleUpdateState() {

    }

    /**
     * Opens update consent
     * @param updateInfo Update info to start flexible update
     */
    internal class UpdateConsent(private val updateInfo: AppUpdateInfo): FlexibleUpdateState() {
        /**
         * Handles lifecycle `onResume`
         */
        override fun onResume() {
            super.onResume()
            if (false == updateInfo.isUpdateTypeAllowed(FLEXIBLE)) {
                reportError(AppUpdateException(ERROR_UPDATE_TYPE_NOT_ALLOWED))
            } else withUpdateView {
                stateMachine.updateManager.startUpdateFlowForResult(
                    updateInfo,
                    FLEXIBLE,
                    activity,
                    REQUEST_CODE_UPDATE
                )
                complete()
            }
        }
    }

    internal class InstallConsent(): FlexibleUpdateState() {
        /**
         * Handles lifecycle `onResume`
         */
        override fun onResume() {
            super.onResume()
            withUpdateView {
                updateInstallUiVisible()
            }
        }
    }

    internal class CompleteUpdate(): FlexibleUpdateState() {

    }
}