/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package de.kilanka.davdroid.settings

import de.kilanka.davdroid.App

open class DefaultsProvider(
        private val allowOverride: Boolean = true
): Provider {

    open val booleanDefaults = mapOf<String, Boolean>(
            Pair(App.DISTRUST_SYSTEM_CERTIFICATES, false),
            Pair(App.OVERRIDE_PROXY, false)
    )

    open val intDefaults = mapOf<String, Int>(
            Pair(App.OVERRIDE_PROXY_PORT, App.OVERRIDE_PROXY_PORT_DEFAULT)
    )

    open val longDefaults = mapOf<String, Long>()

    open val stringDefaults = mapOf<String, String>(
            Pair(App.OVERRIDE_PROXY_HOST, App.OVERRIDE_PROXY_HOST_DEFAULT)
    )


    override fun close() {
    }

    override fun forceReload() {
    }


    private fun hasKey(key: String) =
            booleanDefaults.containsKey(key) ||
            intDefaults.containsKey(key) ||
            longDefaults.containsKey(key) ||
            stringDefaults.containsKey(key)

    override fun has(key: String): Pair<Boolean, Boolean> {
        val has = hasKey(key)
        return Pair(has, allowOverride || !has)
    }


    override fun getBoolean(key: String) =
            Pair(booleanDefaults[key], allowOverride || !booleanDefaults.containsKey(key))

    override fun getInt(key: String) =
            Pair(intDefaults[key], allowOverride || !intDefaults.containsKey(key))

    override fun getLong(key: String) =
            Pair(longDefaults[key], allowOverride || !longDefaults.containsKey(key))

    override fun getString(key: String) =
            Pair(stringDefaults[key], allowOverride || !stringDefaults.containsKey(key))


    override fun isWritable(key: String) = Pair(false, allowOverride || !hasKey(key))

    override fun putBoolean(key: String, value: Boolean?) = false
    override fun putInt(key: String, value: Int?) = false
    override fun putLong(key: String, value: Long?) = false
    override fun putString(key: String, value: String?) = false

    override fun remove(key: String) = false

}