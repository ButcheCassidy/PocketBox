// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox

/** The one and only place the donation link lives. */
object SupportConfig {
    /**
     * OWNER: replace OWNER/REPO with your GitHub donation page before publishing, e.g.
     * "https://github.com/sponsors/ButcheCassidy". While this still contains OWNER/REPO, the
     * donation reminder and the About screen's "Support" row stay hidden.
     */
    const val DONATION_URL = "https://github.com/OWNER/REPO"

    private const val GITHUB_PREFIX = "https://github.com/"

    /** Only a filled-in GitHub link is ever opened. */
    fun isConfigured(url: String = DONATION_URL): Boolean =
        url.isNotBlank() && !url.contains("OWNER/REPO") && url.startsWith(GITHUB_PREFIX)
}
