package com.passly.web.admin

import com.passly.domain.user.Permission

data class PermissionView(
    val name: String,
    val label: String,
    val hint: String,
)

fun Permission.toView(): PermissionView = when (this) {
    Permission.VIEW_MENU -> PermissionView(name, "See the menu", "Every role has it")
    Permission.EDIT_MENU -> PermissionView(name, "Edit the menu", "Categories, dishes, modifier groups, stations and the overview")
    Permission.MANAGE_STOP_LIST -> PermissionView(name, "Stop list", "Mark dishes as sold out and back in stock")
    Permission.MANAGE_TABLES -> PermissionView(name, "Tables", "Areas and tables of the venue")
    Permission.TAKE_ORDERS -> PermissionView(name, "Take orders", "Open tables and send rounds to the kitchen")
    Permission.CLOSE_ORDERS -> PermissionView(name, "Take payments", "Payments and closing the bill")
    Permission.PREPARE_ORDERS -> PermissionView(name, "Kitchen screen", "See and prepare the orders")
    Permission.MANAGE_STAFF -> PermissionView(name, "Staff", "Hire people and give them roles they could do themselves")
    Permission.MANAGE_VENUE -> PermissionView(name, "Venue settings", "Country, currencies and tax rates")
    Permission.MANAGE_ROLES -> PermissionView(name, "Roles", "Only owners")
}

val GRANTABLE_PERMISSIONS: List<PermissionView> = Permission.GRANTABLE.map { it.toView() }
