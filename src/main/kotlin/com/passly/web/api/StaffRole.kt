package com.passly.web.api

import com.passly.domain.role.RolePreset

enum class StaffRole(val preset: RolePreset?) {
    OWNER(null),
    MANAGER(RolePreset.MANAGER),
    WAITER(RolePreset.WAITER),
    KITCHEN(RolePreset.KITCHEN),
}
