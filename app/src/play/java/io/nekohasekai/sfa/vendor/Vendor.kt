package io.nekohasekai.sfa.vendor

import android.app.Activity
import android.app.ProgressDialog
import android.content.Context
import android.util.Log
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.queryProductDetails
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.ktx.errorDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

object Vendor : VendorInterface {

    private const val TAG = "Vendor"
    override fun checkUpdateAvailable(): Boolean {
        return true
    }

    override fun checkUpdate(activity: Activity, byUser: Boolean) {
        val appUpdateManager = AppUpdateManagerFactory.create(activity)
        val appUpdateInfoTask = appUpdateManager.appUpdateInfo
        appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
            when (appUpdateInfo.updateAvailability()) {
                UpdateAvailability.UPDATE_NOT_AVAILABLE -> {
                    Log.d(TAG, "checkUpdate: not available")
                    if (byUser) activity.showNoUpdatesDialog()
                }

                UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> {
                    Log.d(TAG, "checkUpdate: in progress, status: ${appUpdateInfo.installStatus()}")
                    when (appUpdateInfo.installStatus()) {
                        InstallStatus.DOWNLOADED -> {
                            appUpdateManager.completeUpdate()
                        }
                    }
                }

                UpdateAvailability.UPDATE_AVAILABLE -> {
                    Log.d(TAG, "checkUpdate: available")
                    if (appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) {
                        appUpdateManager.startUpdateFlow(
                            appUpdateInfo,
                            activity,
                            AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
                        )
                    } else if (appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
                        appUpdateManager.startUpdateFlow(
                            appUpdateInfo,
                            activity,
                            AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build()
                        )
                    }
                }

                UpdateAvailability.UNKNOWN -> {
                    if (byUser) activity.showNoUpdatesDialog()
                }
            }
        }
        appUpdateInfoTask.addOnFailureListener {
            Log.e(TAG, "checkUpdate: ", it)
        }
        appUpdateManager.registerListener { state ->
            if (state.installStatus() == InstallStatus.DOWNLOADED) {
                appUpdateManager.completeUpdate()
            }
        }
    }

    private fun Context.showNoUpdatesDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(io.nekohasekai.sfa.R.string.check_update)
            .setMessage(R.string.no_updates_available)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

}