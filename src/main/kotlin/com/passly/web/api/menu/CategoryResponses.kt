package com.passly.web.api.menu

import com.passly.domain.menu.Category
import com.passly.domain.menu.CategoryId
import com.passly.domain.menu.MenuTree
import java.security.MessageDigest
import java.util.*

data class CategoryNodeResponse(
    val id: UUID,
    val name: String,
    val stationId: UUID?,
    val effectiveStationId: UUID?,
    val active: Boolean,
    val version: Long,
    val children: List<CategoryNodeResponse>,
)

data class CategoryResponse(
    val id: UUID,
    val parentId: UUID?,
    val name: String,
    val sortOrder: Int,
    val stationId: UUID?,
    val effectiveStationId: UUID?,
    val active: Boolean,
    val version: Long,
)

fun MenuTree.toResponse(): List<CategoryNodeResponse> = children(null).map { toNode(it) }

fun MenuTree.toCategoryResponse(id: CategoryId): CategoryResponse {
    val category = get(id)
    return CategoryResponse(
        id = category.id.value,
        parentId = category.parentId?.value,
        name = category.name,
        sortOrder = category.sortOrder,
        stationId = category.stationId?.value,
        effectiveStationId = effectiveStation(category.id)?.value,
        active = category.active,
        version = category.storedVersion,
    )
}

fun MenuTree.etag(): String {
    val fingerprint = categories
        .sortedBy { it.id.value }
        .joinToString("|") { "${it.id}:${it.version}" }
    return MessageDigest.getInstance("SHA-256").digest(fingerprint.toByteArray()).toHexString()
}

private fun MenuTree.toNode(category: Category): CategoryNodeResponse = CategoryNodeResponse(
    id = category.id.value,
    name = category.name,
    stationId = category.stationId?.value,
    effectiveStationId = effectiveStation(category.id)?.value,
    active = category.active,
    version = category.storedVersion,
    children = children(category.id).map { toNode(it) },
)

private val Category.storedVersion: Long
    get() = checkNotNull(version) { "Category $id is not stored yet" }
