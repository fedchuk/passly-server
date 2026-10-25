package com.passly.domain.user

enum class Role {
    OWNER, MANAGER, WAITER, KITCHEN;

    val permissions: Set<Permission>
        get() = when (this) {
            OWNER, MANAGER -> Permission.entries.toSet()
            WAITER -> setOf(Permission.VIEW_MENU, Permission.TAKE_ORDERS)
            KITCHEN -> setOf(Permission.VIEW_MENU, Permission.MANAGE_STOP_LIST)
        }
}
