package com.scorpk.assistant.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

/** Enlaces oficiales del ecosistema Scorpk. */
object ScorpkLinks {
    const val WEBSITE = "https://scorpk.tech"
    const val PRICING = "https://scorpk.tech/pricing"
    const val TERMS = "https://scorpk.tech/terms"
    const val PRIVACY = "https://scorpk.tech/privacy"
}

/** Abre [url] en una Custom Tab (sin salir de la app); si no hay navegador compatible, con un intent normal. */
fun openUrl(context: Context, url: String) {
    val uri = Uri.parse(url)
    try {
        CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setColorScheme(CustomTabsIntent.COLOR_SCHEME_DARK)
            .build()
            .launchUrl(context, uri)
    } catch (e: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (ignored: ActivityNotFoundException) {
            // Sin navegador instalado no hay nada más que hacer.
        }
    }
}
