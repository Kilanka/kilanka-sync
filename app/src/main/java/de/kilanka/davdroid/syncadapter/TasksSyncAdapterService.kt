/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package de.kilanka.davdroid.syncadapter

import android.accounts.Account
import android.content.*
import android.database.DatabaseUtils
import android.os.Bundle
import de.kilanka.davdroid.AccountSettings
import de.kilanka.davdroid.log.Logger
import de.kilanka.davdroid.model.CollectionInfo
import de.kilanka.davdroid.model.ServiceDB
import de.kilanka.davdroid.model.ServiceDB.Collections
import de.kilanka.davdroid.model.ServiceDB.Services
import de.kilanka.davdroid.resource.LocalTaskList
import de.kilanka.davdroid.settings.ISettings
import at.bitfire.ical4android.AndroidTaskList
import at.bitfire.ical4android.TaskProvider
import org.dmfs.provider.tasks.TaskContract
import java.util.logging.Level

/**
 * Synchronization manager for CalDAV collections; handles tasks ({@code VTODO}).
 */
class TasksSyncAdapterService: SyncAdapterService() {

    override fun syncAdapter() = TasksSyncAdapter(this)


	class TasksSyncAdapter(
            context: Context
    ): SyncAdapter(context) {

        override fun sync(settings: ISettings, account: Account, extras: Bundle, authority: String, provider: ContentProviderClient, syncResult: SyncResult) {
            try {
                val taskProvider = TaskProvider.fromProviderClient(provider)
                val accountSettings = AccountSettings(context, settings, account)
                /* don't run sync if
                   - sync conditions (e.g. "sync only in WiFi") are not met AND
                   - this is is an automatic sync (i.e. manual syncs are run regardless of sync conditions)
                 */
                if (!extras.containsKey(ContentResolver.SYNC_EXTRAS_MANUAL) && !checkSyncConditions(accountSettings))
                    return

                updateLocalTaskLists(taskProvider, account, accountSettings)

                for (taskList in AndroidTaskList.find(account, taskProvider, LocalTaskList.Factory, "${TaskContract.TaskLists.SYNC_ENABLED}!=0", null)) {
                    Logger.log.info("Synchronizing task list #${taskList.id} [${taskList.syncId}]")
                    TasksSyncManager(context, settings, account, accountSettings, extras, authority, syncResult, taskProvider, taskList).use {
                        it.performSync()
                    }
                }
            } catch (e: Exception) {
                Logger.log.log(Level.SEVERE, "Couldn't sync task lists", e)
            }

            Logger.log.info("Task sync complete")
        }

        private fun updateLocalTaskLists(provider: TaskProvider, account: Account, settings: AccountSettings) {
            ServiceDB.OpenHelper(context).use { dbHelper ->
                val db = dbHelper.readableDatabase

                fun getService() =
                        db.query(Services._TABLE, arrayOf(Services.ID),
                                "${Services.ACCOUNT_NAME}=? AND ${Services.SERVICE}=?",
                                arrayOf(account.name, Services.SERVICE_CALDAV), null, null, null)?.use { c ->
                            if (c.moveToNext())
                                c.getLong(0)
                            else
                                null
                        }

                fun remoteTaskLists(service: Long?): MutableMap<String, CollectionInfo> {
                    val collections = mutableMapOf<String, CollectionInfo>()
                    service?.let {
                        db.query(Collections._TABLE, null,
                                "${Collections.SERVICE_ID}=? AND ${Collections.SUPPORTS_VTODO}!=0 AND ${Collections.SYNC}",
                                arrayOf(service.toString()), null, null, null)?.use { cursor ->
                            while (cursor.moveToNext()) {
                                val values = ContentValues(cursor.columnCount)
                                DatabaseUtils.cursorRowToContentValues(cursor, values)
                                val info = CollectionInfo(values)
                                collections[info.url] = info
                            }
                        }
                    }
                    return collections
                }

                // enumerate remote and local task lists
                val service = getService()
                val remote = remoteTaskLists(service)

                // delete/update local task lists
                val updateColors = settings.getManageCalendarColors()

                for (list in AndroidTaskList.find(account, provider, LocalTaskList.Factory, null, null))
                    list.syncId?.let { url ->
                        val info = remote[url]
                        if (info == null) {
                            Logger.log.fine("Deleting obsolete local task list $url")
                            list.delete()
                        } else {
                            // remote CollectionInfo found for this local collection, update data
                            Logger.log.log(Level.FINE, "Updating local task list $url", info)
                            list.update(info, updateColors)
                            // we already have a local task list for this remote collection, don't take into consideration anymore
                            remote -= url
                        }
                    }

                // create new local task lists
                for ((_,info) in remote) {
                    Logger.log.log(Level.INFO, "Adding local task list", info)
                    LocalTaskList.create(account, provider, info)
                }
            }
        }

    }

}
