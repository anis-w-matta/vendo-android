package com.vendo.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class LoginIn(val login_id: String, val password: String)

@Serializable
data class SalesmanOut(val login_id: String, val name: String, val email: String? = null)

@Serializable
data class LoginOut(
    val login_id: String,
    val name: String,
    val email: String? = null,
    val token: String,
)

@Serializable
data class AccountUpdateIn(val name: String? = null, val email: String? = null)

@Serializable
data class ChangePasswordIn(val old_password: String, val new_password: String)
