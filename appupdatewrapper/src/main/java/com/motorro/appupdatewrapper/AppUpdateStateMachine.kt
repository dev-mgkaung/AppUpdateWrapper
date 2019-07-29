package com.motorro.appupdatewrapper

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Lifecycle.Event.*
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.OnLifecycleEvent
import com.google.android.play.core.appupdate.AppUpdateManager

/**
 * App update state machine
 */
internal interface AppUpdateStateMachine {
    /**
     * [AppUpdateManager] instance
     */
    val updateManager: AppUpdateManager

    /**
     * Update view
     */
    val view: AppUpdateView

    /**
     * Sets new update state
     */
    fun setUpdateState(newState: AppUpdateState)
}

/**
 * Manages state transition and component lifecycle
 * @param lifecycleOwner Component lifecycle owner
 * @param updateManager AppUpdateManager instance
 * @param view Application update view interface
 */
internal class AppUpdateLifecycleStateMachine(
    private val lifecycleOwner: LifecycleOwner,
    override val updateManager: AppUpdateManager,
    override val view: AppUpdateView
): AppUpdateStateMachine, AppUpdateWrapper {
    /**
     * Current update state
     */
    private var currentUpdateState: AppUpdateState = NONE

    /**
     * Sets new update state
     */
    override fun setUpdateState(newState: AppUpdateState) {
        newState.stateMachine = this
        currentUpdateState = newState

        with(lifecycleOwner.lifecycle.currentState) {
            if (isAtLeast(Lifecycle.State.STARTED)) {
                newState.onStart()
            }
            if (isAtLeast(Lifecycle.State.RESUMED)) {
                newState.onResume()
            }
        }
    }

    @OnLifecycleEvent(ON_START)
    fun onStart() {
        currentUpdateState.onStart()
    }

    @OnLifecycleEvent(ON_RESUME)
    fun onResume() {
        currentUpdateState.onResume()
    }

    @OnLifecycleEvent(ON_RESUME)
    fun onPause() {
        currentUpdateState.onPause()
    }

    @OnLifecycleEvent(ON_STOP)
    fun onStop() {
        currentUpdateState.onStop()
    }

    /**
     * Checks activity result and returns `true` if result is an update result and was handled
     * Use to check update activity result in [android.app.Activity.onActivityResult]
     */
    override fun checkActivityResult(requestCode: Int, resultCode: Int): Boolean =
        currentUpdateState.checkActivityResult(resultCode, requestCode)

    /**
     * Cancels update installation
     * Call when update is downloaded and user cancelled app restart
     * Effective if update is called with [com.google.android.play.core.install.model.AppUpdateType.FLEXIBLE]
     */
    override fun userCanceledUpdate() {
        currentUpdateState.userCanceledUpdate()
    }

    /**
     * Completes update
     * Call when update is downloaded and user confirmed app restart
     * Effective if update is called with [com.google.android.play.core.install.model.AppUpdateType.FLEXIBLE]
     */
    override fun userConfirmedUpdate() {
        currentUpdateState.userConfirmedUpdate()
    }
}