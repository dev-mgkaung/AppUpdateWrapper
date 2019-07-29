package com.motorro.appupdatewrapper

import android.app.Activity
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.testing.FakeAppUpdateManager
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.nhaarman.mockito_kotlin.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class ImmediateUpdateTest: TestAppTest() {
    private lateinit var activity: Activity
    private lateinit var view: AppUpdateView
    private lateinit var stateMachine: AppUpdateStateMachine
    private lateinit var updateManager: FakeAppUpdateManager

    @Before
    fun init() {
        activity = mock()
        view = mock {
            on { activity } doReturn activity
        }
        updateManager = spy(FakeAppUpdateManager(application))
        stateMachine = mock {
            on { view } doReturn view
            on { updateManager } doReturn updateManager
        }
    }

    private fun ImmediateUpdate.init() = this.apply {
        stateMachine = this@ImmediateUpdateTest.stateMachine
    }

    @Test
    fun whenStartedSetsInitialState() {
        ImmediateUpdate.start(stateMachine)
        verify(stateMachine).setUpdateState(check { it is ImmediateUpdate.Initial })
    }

    @Test
    fun initialStateStartsUpdateOnStart() {
        val state = ImmediateUpdate.Initial().init()
        state.onStart()
        verify(stateMachine).setUpdateState(check { it is ImmediateUpdate.Checking })
    }

    @Test
    fun checkingStateWillCheckUpdateOnStart() {
        val state = ImmediateUpdate.Checking().init()
        state.onStart()
        verify(updateManager).appUpdateInfo
    }

    @Test
    fun checkingStateWillSetUpdateStateIfUpdateFound() {
        updateManager.setUpdateAvailable(100500)
        updateManager.partiallyAllowedUpdateType = AppUpdateType.IMMEDIATE
        val state = ImmediateUpdate.Checking().init()
        state.onStart()
        verify(stateMachine).setUpdateState(check { it is ImmediateUpdate.Update })
    }

    @Test
    fun checkingStateWillSetUpdateStateIfAlreadyUpdating() {
        val updateInfo = createUpdateInfo(
            UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS,
            InstallStatus.UNKNOWN
        )
        val testTask = createTestInfoTask()
        val testUpdateManager: AppUpdateManager = mock {
            on { this.appUpdateInfo } doReturn testTask
        }
        whenever(stateMachine.updateManager).thenReturn(testUpdateManager)

        val state = ImmediateUpdate.Checking().init()
        state.onStart()
        testTask.succeed(updateInfo)
        verify(stateMachine).setUpdateState(check { it is ImmediateUpdate.Update })
    }

    @Test
    fun checkingStateWillSetFailedStateIfUpdateCheckFails() {
        val error = RuntimeException("Update failed")
        val testTask = createTestInfoTask()
        val testUpdateManager: AppUpdateManager = mock {
            on { this.appUpdateInfo } doReturn testTask
        }
        whenever(stateMachine.updateManager).thenReturn(testUpdateManager)

        val state = ImmediateUpdate.Checking().init()
        state.onStart()
        testTask.fail(error)
        argumentCaptor<AppUpdateState>().apply {
            verify(stateMachine).setUpdateState(capture())
            val newState = firstValue as ImmediateUpdate.Failed
            val stateError = newState.error
            assertEquals(AppUpdateException.ERROR_UPDATE_FAILED, stateError.message)
            assertEquals(error, stateError.cause)
        }
    }

    @Test
    fun checkingStateWillSetFailedStateIfUpdateTypeNotSupported() {
        val updateInfo = createUpdateInfo(
            UpdateAvailability.UNKNOWN,
            InstallStatus.UNKNOWN,
            immediateAvailable = false
        )
        val testTask = createTestInfoTask()
        val testUpdateManager: AppUpdateManager = mock {
            on { this.appUpdateInfo } doReturn testTask
        }
        whenever(stateMachine.updateManager).thenReturn(testUpdateManager)

        val state = ImmediateUpdate.Checking().init()
        state.onStart()
        testTask.succeed(updateInfo)
        argumentCaptor<AppUpdateState>().apply {
            verify(stateMachine).setUpdateState(capture())
            val newState = firstValue as ImmediateUpdate.Failed
            val stateError = newState.error
            assertEquals(AppUpdateException.ERROR_UPDATE_TYPE_NOT_ALLOWED, stateError.message)
        }
    }

    @Test
    fun checkingStateWillSetFailedStateIfUpdateNotAvailable() {
        val updateInfo = createUpdateInfo(
            UpdateAvailability.UPDATE_NOT_AVAILABLE,
            InstallStatus.UNKNOWN
        )
        val testTask = createTestInfoTask()
        val testUpdateManager: AppUpdateManager = mock {
            on { this.appUpdateInfo } doReturn testTask
        }
        whenever(stateMachine.updateManager).thenReturn(testUpdateManager)

        val state = ImmediateUpdate.Checking().init()
        state.onStart()
        testTask.succeed(updateInfo)
        argumentCaptor<AppUpdateState>().apply {
            verify(stateMachine).setUpdateState(capture())
            val newState = firstValue as ImmediateUpdate.Failed
            val stateError = newState.error
            assertEquals(AppUpdateException.ERROR_NO_IMMEDIATE_UPDATE, stateError.message)
        }
    }

    @Test
    fun checkingStateWillNotProceedIfStoppedBeforeTaskCompletes() {
        val updateInfo = createUpdateInfo(
            UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS,
            InstallStatus.UNKNOWN
        )
        val testTask = createTestInfoTask()
        val testUpdateManager: AppUpdateManager = mock {
            on { this.appUpdateInfo } doReturn testTask
        }
        whenever(stateMachine.updateManager).thenReturn(testUpdateManager)

        val state = ImmediateUpdate.Checking().init()
        state.onStart()
        state.onStop()
        testTask.succeed(updateInfo)
        verify(stateMachine).setUpdateState(any<ImmediateUpdate.Initial>())
        verify(stateMachine, never()).setUpdateState(any<ImmediateUpdate.Update>())
        verify(stateMachine, never()).setUpdateState(any<ImmediateUpdate.Failed>())
    }

    @Test
    fun checkingStateWillNotProceedIfStoppedBeforeTaskFails() {
        val error = RuntimeException("Update failed")
        val testTask = createTestInfoTask()
        val testUpdateManager: AppUpdateManager = mock {
            on { this.appUpdateInfo } doReturn testTask
        }
        whenever(stateMachine.updateManager).thenReturn(testUpdateManager)

        val state = ImmediateUpdate.Checking().init()
        state.onStart()
        state.onStop()
        testTask.fail(error)
        verify(stateMachine).setUpdateState(any<ImmediateUpdate.Initial>())
        verify(stateMachine, never()).setUpdateState(any<ImmediateUpdate.Update>())
        verify(stateMachine, never()).setUpdateState(any<ImmediateUpdate.Failed>())
    }

    @Test
    fun updatingStateWillStartImmediateUpdateOnStart() {
        val updateInfo = createUpdateInfo(
            UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS,
            InstallStatus.UNKNOWN
        )

        val state = ImmediateUpdate.Update(updateInfo).init()
        state.onStart()

        assertTrue(updateManager.isImmediateFlowVisible)
    }

    @Test
    fun failedStateWillFailOnStart() {
        val error = AppUpdateException(AppUpdateException.ERROR_NO_IMMEDIATE_UPDATE)

        val state = ImmediateUpdate.Failed(error).init()
        state.onStart()

        verify(view).fail(error)
    }
}