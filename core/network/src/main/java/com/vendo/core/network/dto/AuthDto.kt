package com.vendo.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class LoginIn(val login_id: String, val password: String)

@Serializable
data class SalesmanOut(
    val login_id: String,
    val name: String,
    val email: String? = null,
    val role: String = "salesman",
    val is_active: Boolean = true,
)

@Serializable
data class LoginOut(
    val login_id: String,
    val name: String,
    val email: String? = null,
    val role: String = "salesman",
    val is_active: Boolean = true,
    val token: String,
)

@Serializable
data class AccountUpdateIn(val name: String? = null, val email: String? = null)

@Serializable
data class ChangePasswordIn(val old_password: String, val new_password: String)

/** Admin-only account provisioning (backend: POST /auth/register, gated
 * by an authenticated admin caller as well as the shared API key). */
@Serializable
data class RegisterIn(
    val login_id: String,
    val password: String,
    val name: String,
    val email: String? = null,
    val role: String = "salesman",
)

/** Admin-only activate/deactivate (backend: PATCH /salesmen/{login_id}) -
 * deliberately narrow, same as the backend schema it mirrors: no
 * password/role edit here. */
@Serializable
data class SalesmanUpdateIn(val is_active: Boolean? = null)
