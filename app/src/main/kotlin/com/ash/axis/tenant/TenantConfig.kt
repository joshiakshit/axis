package com.ash.axis.tenant

data class TenantConfig(
    val id: String,
    val name: String,
    val apiBase: String,
    val authApiBase: String,
    val clientCode: String,
)

object Tenants {
    val GU =
        TenantConfig(
            id = "gu",
            name = "Galgotias University",
            apiBase = "https://gustudentapp.icloudems.com/",
            authApiBase = "https://api.icloudems.com/",
            clientCode = "GUSTUDENTAPP",
        )
}
