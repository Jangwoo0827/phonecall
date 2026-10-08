package com.example.superdialer.browser

import android.webkit.WebView
import com.example.superdialer.account.AccountManager
import com.example.superdialer.account.SyncSnapshot
import com.example.superdialer.browser.data.BrowserDatabase
import org.json.JSONObject

/**
 * Ties the checklist web app (the first start-page tile) to the SuperDialer account.
 *
 * That site has its own password-less "sync id" kept in its localStorage. While signed in to SuperDialer:
 * - if the page is signed in, its id is remembered in the account (and so follows the user to other phones);
 * - if the page is signed out and the account knows an id, it is put into the page once and the page reloads,
 *   which is the automatic login.
 * Only the exact checklist address is touched, and only ids that look like the site's own ids are injected.
 */
internal object ChecklistLink {
    private const val STORAGE_KEY = "checklist_note_user_v1"

    fun isChecklist(url: String?): Boolean = url != null && url.startsWith(BrowserDatabase.CHECKLIST_URL)

    fun onPageFinished(view: WebView, url: String?, tab: BrowserTab) {
        if (!isChecklist(url) || !AccountManager.signedIn) return
        view.evaluateJavascript("(function(){try{return localStorage.getItem('$STORAGE_KEY')}catch(e){return null}})()") { raw ->
            val current = decodeJsString(raw)
            val remembered = AccountManager.checklistId
            when {
                current != null -> AccountManager.rememberChecklistId(current)
                remembered != null && !tab.checklistInjected -> {
                    tab.checklistInjected = true
                    val quoted = JSONObject.quote(remembered)
                    view.evaluateJavascript("(function(){try{localStorage.setItem('$STORAGE_KEY',$quoted);return true}catch(e){return false}})()") { done ->
                        if (done == "true") view.reload()
                    }
                }
            }
        }
    }

    /** `evaluateJavascript` hands back JSON text: `null` or a quoted string. Returns a valid checklist id or null. */
    fun decodeJsString(raw: String?): String? {
        if (raw == null || raw == "null" || raw.length < 2 || raw.first() != '"' || raw.last() != '"') return null
        val value = try {
            JSONObject("{\"v\":$raw}").getString("v")
        } catch (e: org.json.JSONException) {
            return null
        }
        return value.takeIf(SyncSnapshot::isValidChecklistId)
    }
}
