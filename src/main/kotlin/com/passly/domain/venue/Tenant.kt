package com.passly.domain.venue

class Tenant(
    val id: TenantId,
    val name: String,
) {
    companion object {
        fun create(name: String) = Tenant(TenantId.new(), validName(name, "Business"))
    }
}
