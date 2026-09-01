package com.vendo.admin.navigation

object AdminDestinations {
    const val LOGIN = "login"
    const val CUSTOMERS = "customers"
    const val SALESMEN = "salesmen"
    const val QUEUE = "queue"
    const val REQUEST_ARG = "requestId"
    const val REQUEST = "request/{$REQUEST_ARG}"
    const val ACTIVITY = "activity"
    const val ORDER_HISTORY = "orderhistory"
    const val ACCOUNT = "menu/account"
    const val CHANGE_PASSWORD = "menu/changepassword"

    fun requestRoute(requestId: Int) = "request/$requestId"
}
