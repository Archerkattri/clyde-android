package dev.kris.clyde.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

/** Opening login/OAuth URLs in the user's REAL default browser. */
object Browser {
    /**
     * Open [url] in the user's default browser. OAuth sign-in (claude login / codex login) must land in
     * a full browser, not an in-app webview or a non-browser app that registered for the auth domain —
     * so we resolve the default browser via a neutral http probe and target it explicitly. The headless
     * CLI can't open a browser itself in the embedded runtime; it prints the URL and the app opens it.
     */
    fun openDefault(ctx: Context, url: String): Boolean {
        val uri = Uri.parse(url)
        val pm = ctx.packageManager
        // Resolve the default browser from a neutral probe (not the OAuth domain, which some non-browser
        // app might claim) so login always lands in an actual browser.
        val probe = Intent(Intent.ACTION_VIEW, Uri.parse("http://example.com"))
            .addCategory(Intent.CATEGORY_BROWSABLE)
        val browserPkg = pm.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
            ?.takeIf { it != "android" && it != ctx.packageName } // skip the disambiguator / ourselves
        val view = Intent(Intent.ACTION_VIEW, uri)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (browserPkg != null) view.setPackage(browserPkg)
        return runCatching { ctx.startActivity(view); true }.getOrElse {
            // Fallback: drop the forced package and let the system pick / show a chooser.
            runCatching {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
            }.getOrDefault(false)
        }
    }
}
