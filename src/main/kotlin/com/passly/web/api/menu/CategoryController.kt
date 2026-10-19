package com.passly.web.api.menu

import com.passly.application.menu.CategoryService
import com.passly.domain.menu.CategoryId
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
import org.springframework.web.context.request.WebRequest
import java.net.URI
import java.util.UUID

@RestController
@RequestMapping("/api/v1/venues/{venueId}/categories")
class CategoryController(
    private val service: CategoryService,
) {

    @GetMapping
    fun tree(@PathVariable venueId: UUID, request: WebRequest): ResponseEntity<List<CategoryNodeResponse>>? {
        val tree = service.getTree(VenueId(venueId))
        val etag = tree.etag()
        if (request.checkNotModified(etag)) return null
        return ResponseEntity.ok().eTag(etag).body(tree.toResponse())
    }

    @GetMapping("/{id}")
    fun get(@PathVariable venueId: UUID, @PathVariable id: UUID): CategoryResponse =
        service.getTree(VenueId(venueId)).toCategoryResponse(CategoryId(id))

    @PostMapping
    fun create(
        @PathVariable venueId: UUID,
        @Valid @RequestBody request: CreateCategoryRequest,
    ): ResponseEntity<CategoryResponse> {
        val category = service.create(request.toCommand(VenueId(venueId)))
        val location = URI.create("/api/v1/venues/$venueId/categories/${category.id}")
        return ResponseEntity.created(location).body(get(venueId, category.id.value))
    }

    @PutMapping("/{id}")
    fun update(
        @PathVariable venueId: UUID,
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateCategoryRequest,
    ): CategoryResponse {
        service.update(request.toCommand(VenueId(venueId), CategoryId(id)))
        return get(venueId, id)
    }

    @PostMapping("/{id}/move")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun move(
        @PathVariable venueId: UUID,
        @PathVariable id: UUID,
        @Valid @RequestBody request: MoveCategoryRequest,
    ) {
        service.move(request.toCommand(VenueId(venueId), CategoryId(id)))
    }

    @PutMapping("/order")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun reorder(@PathVariable venueId: UUID, @Valid @RequestBody request: ReorderCategoriesRequest) {
        service.reorder(request.toCommand(VenueId(venueId)))
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable venueId: UUID, @PathVariable id: UUID) {
        service.delete(VenueId(venueId), CategoryId(id))
    }
}
