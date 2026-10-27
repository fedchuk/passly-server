package com.passly.domain.role

import com.passly.domain.user.Permission

enum class RolePreset(val roleName: String, val permissions: Set<Permission>) {
    MANAGER("Manager", Permission.GRANTABLE.toSet() - Permission.MANAGE_STAFF),
    WAITER("Waiter", setOf(Permission.TAKE_ORDERS, Permission.CLOSE_ORDERS)),
    KITCHEN("Kitchen", setOf(Permission.MANAGE_STOP_LIST, Permission.PREPARE_ORDERS)),
}
