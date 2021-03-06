/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package de.kilanka.davdroid.syncadapter

import android.accounts.Account
import android.app.PendingIntent
import android.app.Service
import android.content.*
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.support.v4.app.NotificationCompat
import de.kilanka.davdroid.AccountSettings
import de.kilanka.davdroid.App
import de.kilanka.davdroid.Constants
import de.kilanka.davdroid.R
import de.kilanka.davdroid.log.Logger
import de.kilanka.davdroid.settings.ISettings
import de.kilanka.davdroid.settings.Settings
import de.kilanka.davdroid.ui.NotificationUtils
import de.kilanka.davdroid.ui.PermissionsActivity
import java.util.*
import java.util.logging.Level

abstract class SyncAdapterService: Service() {

    companion object {
        val runningSyncs = Collections.synchronizedSet(mutableSetOf<Pair<String, Account>>())
    }

    abstract protected fun syncAdapter(): AbstractThreadedSyncAdapter

    override fun onBind(intent: Intent?) = syncAdapter().syncAdapterBinder!!


    abstract class SyncAdapter(
            context: Context
    ): AbstractThreadedSyncAdapter(context, false) {

        abstract fun sync(settings: ISettings, account: Account, extras: Bundle, authority: String, provider: ContentProviderClient, syncResult: SyncResult)

        override fun onPerformSync(account: Account, extras: Bundle, authority: String, provider: ContentProviderClient, syncResult: SyncResult) {
            Logger.log.log(Level.INFO, "$authority sync of $account has been initiated", extras.keySet().joinToString(", "))

            // prevent multiple syncs of the same authority to be run for the same account
            val currentSync = Pair(authority, account)
            if (!runningSyncs.add(currentSync)) {
                Logger.log.warning("There's already another $authority sync running for $account, aborting")
                return
            }

            try {
                // required for dav4android (ServiceLoader)
                Thread.currentThread().contextClassLoader = context.classLoader

                // load app settings
                Settings.getInstance(context).use { settings ->
                    if (settings == null) {
                        syncResult.databaseError = true
                        Logger.log.severe("Couldn't connect to Settings service, aborting sync")
                        return
                    }

                    sync(settings, account, extras, authority, provider, syncResult)
                }
                Logger.log.info("Sync for $authority complete")
            } finally {
                runningSyncs -= currentSync
            }
        }

        override fun onSecurityException(account: Account, extras: Bundle, authority: String, syncResult: SyncResult) {
            Logger.log.log(Level.WARNING, "Security exception when opening content provider for $authority")
            syncResult.databaseError = true

            val intent = Intent(context, PermissionsActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            val notify = NotificationCompat.Builder(context, NotificationUtils.CHANNEL_SYNC_PROBLEMS)
                    .setSmallIcon(R.drawable.ic_sync_error_notification)
                    .setLargeIcon(App.getLauncherBitmap(context))
                    .setContentTitle(context.getString(R.string.sync_error_permissions))
                    .setContentText(context.getString(R.string.sync_error_permissions_text))
                    .setContentIntent(PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT))
                    .setCategory(NotificationCompat.CATEGORY_ERROR)
                    .build()
            val nm = NotificationUtils.createChannels(context)
            nm.notify(Constants.NOTIFICATION_PERMISSIONS, notify)
        }

        protected fun checkSyncConditions(settings: AccountSettings): Boolean {
            if (settings.getSyncWifiOnly()) {
                val connectivityManager = context.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
                val network = connectivityManager.activeNetworkInfo
                if (network == null || network.type != ConnectivityManager.TYPE_WIFI || !network.isConnected) {
                    Logger.log.info("Not on connected WiFi, stopping")
                    return false
                }

                settings.getSyncWifiOnlySSIDs()?.let { onlySSIDs ->
                    val wifi = context.applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
                    val info = wifi.connectionInfo
                    if (info == null || !onlySSIDs.contains(info.ssid.trim('"'))) {
                        Logger.log.info("Connected to wrong WiFi network (${info.ssid}), ignoring")
                        return false
                    }
                }
            }
            return true
        }

    }

}
