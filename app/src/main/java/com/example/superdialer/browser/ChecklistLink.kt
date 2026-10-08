package com.example.superdialer.browser

import android.webkit.WebView
import com.example.superdialer.account.AccountManager
import com.example.superdialer.browser.data.BrowserDatabase
import org.json.JSONException
import org.json.JSONObject

/**
 * Opens the checklist web app (the first start-page tile) already signed in with the SuperDialer account.
 *
 * The site keeps its login in localStorage `checklist_note_session_v2`. While signed in to SuperDialer, when the page
 * has no login (or only an expiring one that this app handed over earlier) a fresh short-lived session is written
 * into it and the page reloads once. A login the user made on the page itself is left alone. Only the exact
 * checklist address is touched.
 */
internal object ChecklistLink {
    private const val STORAGE_KEY = "checklist_note_session_v2"
    private const val RENEW_BEFORE_MS = 10 * 60_000L

    fun isChecklist(url: String?): Boolean = url != null && url.startsWith(BrowserDatabase.CHECKLIST_URL)

    fun onPageFinished(view: WebView, url: String?, tab: BrowserTab) {
        if (!isChecklist(url) || !AccountManager.signedIn || tab.checklistInjected) return
        view.evaluateJavascript("(function(){try{return localStorage.getItem('$STORAGE_KEY')}catch(e){return null}})()") { raw ->
            if (!needsSession(decodeJsString(raw), System.currentTimeMillis())) return@evaluateJavascript
            AccountManager.webSessionJson { session ->
                if (session == null || tab.checklistInjected) return@webSessionJson
                tab.checklistInjected = true
                val quoted = JSONObject.quote(session)
                view.evaluateJavascript("(function(){try{localStorage.setItem('$STORAGE_KEY',$quoted);return true}catch(e){return false}})()") { done ->
                    if (done == "true") view.reload()
                }
            }
        }
    }

    /** `evaluateJavascript` hands back JSON text: `null` or a quoted string. Returns the string, or null. */
    fun decodeJsString(raw: String?): String? {
        if (raw == null || raw == "null" || raw.length < 2 || raw.first() != '"' || raw.last() != '"') return null
        return try {
            JSONObject("{\"v\":$raw}").getString("v")
        } catch (e: JSONException) {
            null
        }
    }

    /** True when the page has no usable login, or only an app-managed one that is about to expire. */
    fun needsSession(currentJson: String?, nowMs: Long): Boolean {
        if (currentJson == null) return true
        val o = try {
            JSONObject(currentJson)
        } catch (e: JSONException) {
            return true
        }
        if (o.optString("access_token").isEmpty()) return true
        if (!o.optBoolean("managed", false)) return false
        return o.optLong("expires_at") - nowMs < RENEW_BEFORE_MS
    }
}
