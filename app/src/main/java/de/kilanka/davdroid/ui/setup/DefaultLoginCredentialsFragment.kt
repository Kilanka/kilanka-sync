/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package de.kilanka.davdroid.ui.setup

import android.app.Fragment
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import at.bitfire.dav4android.Constants
import de.kilanka.davdroid.R
import kotlinx.android.synthetic.main.login_credentials_fragment.view.*
import java.net.IDN
import java.net.URI
import java.net.URISyntaxException
import java.util.logging.Level

class DefaultLoginCredentialsFragment: Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val v = inflater.inflate(R.layout.login_credentials_fragment, container, false)

        v.login.setOnClickListener({ _ ->
            validateLoginData()?.let { credentials ->
                DetectConfigurationFragment.newInstance(credentials).show(fragmentManager, null)
            }
        })


        return v
    }

    private fun validateLoginData(): LoginCredentials? {
        var valid = true

        val tenant = view.tenant_name.getText().toString()
        if (tenant.isEmpty()) {
            view.tenant_name.setError(getString(R.string.login_tenant_name_required))
            valid = false
        }

        val user = view.user_name.getText().toString()
        if (user.isEmpty()) {
            view.user_name.setError(getString(R.string.login_user_name_required))
            valid = false
        }

        val password = view.user_password.getText().toString()
        if (password.isEmpty()) {
            view.user_password.setError(getString(R.string.login_password_required))
            valid = false
        }

        if (valid) {
            return LoginCredentials(
                    URI("https://" + tenant + ".kilanka.de/dav/principals/users/" + user + "/"),
                    user, password)
        }

        return null
    }

}
