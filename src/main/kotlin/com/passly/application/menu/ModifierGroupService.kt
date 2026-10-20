package com.passly.application.menu

import com.passly.domain.common.ConflictException
import com.passly.domain.common.NotFoundException
import com.passly.domain.menu.MenuItemRepository
import com.passly.domain.menu.ModifierGroup
import com.passly.domain.menu.ModifierGroupId
import com.passly.domain.menu.ModifierGroupRepository
import com.passly.domain.venue.Venue
import com.passly.domain.venue.VenueId
import com.passly.domain.venue.VenueRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class ModifierGroupService(
    private val venues: VenueRepository,
    private val groups: ModifierGroupRepository,
    private val items: MenuItemRepository,
    private val menuChanges: MenuChangeRecorder,
) {

    @Transactional(readOnly = true)
    fun list(venueId: VenueId): List<ModifierGroup> {
        requireVenue(venueId)
        return groups.findAll(venueId).sortedBy { it.name.lowercase() }
    }

    @Transactional(readOnly = true)
    fun get(venueId: VenueId, id: ModifierGroupId): ModifierGroup = requireGroup(venueId, id)

    fun create(command: CreateModifierGroupCommand): ModifierGroup {
        val venue = requireVenue(command.venueId)
        val group = ModifierGroup.create(
            tenantId = venue.tenantId,
            venueId = venue.id,
            name = command.name,
            minSelect = command.minSelect,
            maxSelect = command.maxSelect,
            drafts = command.options,
        )
        groups.save(group)
        menuChanges.recordChange(venue.id, MenuChangeKind.MODIFIER_GROUPS)
        return group
    }

    fun update(command: UpdateModifierGroupCommand): ModifierGroup {
        val group = requireGroup(command.venueId, command.groupId)
        if (command.expectedVersion != null && command.expectedVersion != group.version) {
            throw ConflictException("Modifier group ${group.name} was changed by someone else. Reload and try again.")
        }
        group.update(command.name, command.minSelect, command.maxSelect, command.options)
        groups.save(group)
        menuChanges.recordChange(command.venueId, MenuChangeKind.MODIFIER_GROUPS)
        return group
    }

    fun delete(venueId: VenueId, id: ModifierGroupId) {
        val group = requireGroup(venueId, id)
        if (items.existsWithModifierGroup(id)) {
            throw ConflictException("Modifier group '${group.name}' is used by menu items. Detach it first.")
        }
        groups.delete(group)
        menuChanges.recordChange(venueId, MenuChangeKind.MODIFIER_GROUPS)
    }

    private fun requireVenue(id: VenueId): Venue =
        venues.findById(id) ?: throw NotFoundException("Venue", id)

    private fun requireGroup(venueId: VenueId, id: ModifierGroupId): ModifierGroup =
        groups.find(venueId, id) ?: throw NotFoundException("Modifier group", id)
}
