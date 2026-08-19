package com.vendo.app.navigation

object VendoDestinations {
    const val LOGIN = "login"
    const val RECORD = "record"
    // requestId is optional: -1 (the default) means "show my most recent
    // pending request" (reached from the drawer's REQUEST entry, since
    // there's no separate request-list screen in the 5-screen spec);
    // a real id is passed after RECORD successfully drafts one.
    const val REQUEST_ARG = "requestId"
    const val REQUEST = "request?$REQUEST_ARG={$REQUEST_ARG}"
    const val LOG_QUERY = "logquery"
    const val MENU = "menu"
    const val ACCOUNT = "menu/account"
    const val CHANGE_PASSWORD = "menu/changepassword"

    fun requestRoute(requestId: Int) = "request?$REQUEST_ARG=$requestId"

    /** What the drawer's "Request" entry navigates to: -1 signals "show my
     * most recent pending request" (see RequestViewModel.load). */
    fun requestRouteDefault() = requestRoute(-1)
}
