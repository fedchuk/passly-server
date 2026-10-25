package com.passly.domain.user

enum class Role {
    OWNER, MANAGER, WAITER, KITCHEN;

    val permissions: Set<Permission>
        get() = when (this) {
            OWNER -> Permission.entries.toSet()
            MANAGER -> Permission.entries.toSet() - Permission.MANAGE_STAFF
            WAITER -> setOf(Permission.VIEW_MENU, Permission.TAKE_ORDERS)
            KITCHEN -> setOf(Permission.VIEW_MENU, Permission.MANAGE_STOP_LIST, Permission.PREPARE_ORDERS)
        }
}
