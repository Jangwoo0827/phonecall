package com.example.superdialer.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Switches the optional http/https intent filter (the ExternalLinkAlias in the manifest) on and off.
 * Off by default: while on, SuperDialer appears in "Open with" for every web link on the phone.
 */
object ExternalLinks {
    private fun alias(context: Context) = ComponentName(context, "com.example.superdialer.ExternalLinkAlias")

    fun isEnabled(context: Context): Boolean =
        context.packageManager.getComponentEnabledSetting(alias(context)) ==
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED

    fun setEnabled(context: Context, enabled: Boolean) {
        context.packageManager.setComponentEnabledSetting(
            alias(context),
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }
}
