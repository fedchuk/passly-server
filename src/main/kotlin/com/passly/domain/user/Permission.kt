package com.passly.domain.user

enum class Permission {
    VIEW_MENU,
    EDIT_MENU,
    MANAGE_STOP_LIST,
    MANAGE_STAFF,
    MANAGE_TABLES,
    TAKE_ORDERS,
    CLOSE_ORDERS,
    PREPARE_ORDERS,
    MANAGE_VENUE,
    MANAGE_ROLES;

    companion object {
        val EVERY_ROLE: Set<Permission> = setOf(VIEW_MENU)
        val OWNER_ONLY: Set<Permission> = setOf(MANAGE_ROLES)
        val GRANTABLE: List<Permission> = entries - EVERY_ROLE - OWNER_ONLY
    }
}
