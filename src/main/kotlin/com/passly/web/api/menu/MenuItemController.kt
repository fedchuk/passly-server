package com.passly.web.api.menu

import com.passly.application.menu.CategoryService
import com.passly.application.menu.CreateMenuItemCommand
import com.passly.application.menu.MenuItemService
import com.passly.application.menu.UpdateMenuItemCommand
import com.passly.domain.menu.CategoryId
import com.passly.domain.menu.MenuItemId
import com.passly.domain.venue.VenueId
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.util.UUID

@RestController
@RequestMapping("/api/v1/venues/{venueId}")
class MenuItemController(
    private val items: MenuItemService,
    private val categories: CategoryService,
) {

    @GetMapping("/categories/{categoryId}/items")
    fun list(@PathVariable venueId: UUID, @PathVariable categoryId: UUID): List<MenuItemResponse> {
        val venue = VenueId(venueId)
        val tree = categories.getTree(venue)
        return items.listInCategory(venue, CategoryId(categoryId)).map { it.toResponse(tree) }
    }

    @PostMapping("/categories/{categoryId}/items")
    fun create(
        @PathVariable venueId: UUID,
        @PathVariable categoryId: UUID,
        @Valid @RequestBody request: CreateMenuItemRequest,
    ): ResponseEntity<MenuItemResponse> {
        val command = CreateMenuItemCommand(VenueId(venueId), CategoryId(categoryId), request.toDetails())
        val item = items.create(command)
        val location = URI.create("/api/v1/venues/$venueId/items/${item.id}")
        return ResponseEntity.created(location).body(get(venueId, item.id.value))
    }

    @PutMapping("/categories/{categoryId}/items/order")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun reorder(
        @PathVariable venueId: UUID,
        @PathVariable categoryId: UUID,
        @Valid @RequestBody request: ReorderMenuItemsRequest,
    ) {
        items.reorder(request.toCommand(VenueId(venueId), CategoryId(categoryId)))
    }

    @GetMapping("/items/{id}")
    fun get(@PathVariable venueId: UUID, @PathVariable id: UUID): MenuItemResponse {
        val venue = VenueId(venueId)
        val tree = categories.getTree(venue)
        return items.get(venue, MenuItemId(id)).toResponse(tree)
    }

    @PutMapping("/items/{id}")
    fun update(
        @PathVariable venueId: UUID,
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateMenuItemRequest,
    ): MenuItemResponse {
        items.update(UpdateMenuItemCommand(VenueId(venueId), MenuItemId(id), request.toDetails(), request.version))
        return get(venueId, id)
    }

    @PutMapping("/items/{id}/availability")
    fun changeAvailability(
        @PathVariable venueId: UUID,
        @PathVariable id: UUID,
        @RequestBody request: AvailabilityRequest,
    ): MenuItemResponse {
        items.changeAvailability(VenueId(venueId), MenuItemId(id), request.available)
        return get(venueId, id)
    }

    @PostMapping("/items/{id}/move")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun move(
        @PathVariable venueId: UUID,
        @PathVariable id: UUID,
        @RequestBody request: MoveMenuItemRequest,
    ) {
        items.move(request.toCommand(VenueId(venueId), MenuItemId(id)))
    }

    @DeleteMapping("/items/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun archive(@PathVariable venueId: UUID, @PathVariable id: UUID) {
        items.archive(VenueId(venueId), MenuItemId(id))
    }

    @PutMapping("/items/{id}/modifier-groups")
    fun attachModifierGroups(
        @PathVariable venueId: UUID,
        @PathVariable id: UUID,
        @RequestBody request: AttachModifierGroupsRequest,
    ): MenuItemResponse {
        items.attachModifierGroups(request.toCommand(VenueId(venueId), MenuItemId(id)))
        return get(venueId, id)
    }
}
