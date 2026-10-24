package com.passly.application.user

import com.passly.domain.user.UserId

data class RenameProfileCommand(
    val userId: UserId,
    val displayName: String,
)
