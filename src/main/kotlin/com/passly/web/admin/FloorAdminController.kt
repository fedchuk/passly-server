package com.passly.web.admin

import com.passly.application.floor.*
import com.passly.domain.common.ConflictException
import com.passly.domain.common.ValidationException
import com.passly.domain.floor.DiningAreaId
import com.passly.domain.floor.DiningTableId
import com.passly.domain.floor.FloorPlan
import com.passly.domain.venue.VenueId
import com.passly.security.PasslyUserPrincipal
import jakarta.validation.Valid
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.util.*

@Controller
@RequestMapping("/admin/venues/{venueId}/floor")
class FloorAdminController(
    private val floors: FloorPlanService,
    private val venues: AdminVenues,
    private val messages: MessageSource,
) {

    @GetMapping
    fun floor(
        @PathVariable venueId: UUID,
        @AuthenticationPrincipal principal: PasslyUserPrincipal,
        model: Model,
    ): String {
        val plan = floors.get(VenueId(venueId))
        model.addAttribute("venue", venues.find(principal, venueId))
        model.addAttribute("areas", plan.toAreaViews())
        model.addAttribute("nextNumber", plan.nextTableNumber())
        return "admin/floor"
    }

    @PostMapping("/areas")
    fun createArea(
        @PathVariable venueId: UUID,
        @Valid @ModelAttribute form: AreaForm,
        binding: BindingResult,
        flash: RedirectAttributes,
    ): String = flash.redirectAfter(venueId, binding) {
        val area = floors.createArea(CreateAreaCommand(VenueId(venueId), form.name))
        "Area '${area.name}' added"
    }

    @PostMapping("/areas/{areaId}/rename")
    fun renameArea(
        @PathVariable venueId: UUID,
        @PathVariable areaId: UUID,
        @Valid @ModelAttribute form: AreaForm,
        binding: BindingResult,
        flash: RedirectAttributes,
    ): String = flash.redirectAfter(venueId, binding) {
        val area = floors.renameArea(RenameAreaCommand(VenueId(venueId), DiningAreaId(areaId), form.name, form.version))
        "Area renamed to '${area.name}'"
    }

    @PostMapping("/areas/{areaId}/shift")
    fun shiftArea(
        @PathVariable venueId: UUID,
        @PathVariable areaId: UUID,
        @RequestParam delta: Int,
        flash: RedirectAttributes,
    ): String = flash.redirectAfter(venueId) {
        requireOneStep(delta)
        floors.shiftArea(VenueId(venueId), DiningAreaId(areaId), delta)
        null
    }

    @PostMapping("/areas/{areaId}/delete")
    fun deleteArea(
        @PathVariable venueId: UUID,
        @PathVariable areaId: UUID,
        flash: RedirectAttributes,
    ): String = flash.redirectAfter(venueId) {
        val name = floors.get(VenueId(venueId)).area(DiningAreaId(areaId)).name
        floors.deleteArea(VenueId(venueId), DiningAreaId(areaId))
        "Area '$name' deleted"
    }

    @PostMapping("/areas/{areaId}/tables")
    fun addTable(
        @PathVariable venueId: UUID,
        @PathVariable areaId: UUID,
        @Valid @ModelAttribute form: SingleTableForm,
        binding: BindingResult,
        flash: RedirectAttributes,
    ): String = flash.redirectAfter(venueId, binding) {
        val table = floors.createTable(
            CreateTableCommand(
                VenueId(venueId),
                DiningAreaId(areaId),
                form.label,
                form.seats
            )
        )
        "Table ${table.label} added"
    }

    @PostMapping("/areas/{areaId}/numbered-tables")
    fun addNumberedTables(
        @PathVariable venueId: UUID,
        @PathVariable areaId: UUID,
        @Valid @ModelAttribute form: NumberedTablesForm,
        binding: BindingResult,
        flash: RedirectAttributes,
    ): String = flash.redirectAfter(venueId, binding) {
        val tables = floors.createTables(
            CreateTablesCommand(
                venueId = VenueId(venueId),
                areaId = DiningAreaId(areaId),
                numbers = form.from..form.to,
                labelPrefix = form.labelPrefix,
                seats = form.seats,
            ),
        )
        if (tables.size == 1) "Table ${tables.single().label} added"
        else "Tables ${tables.first().label}–${tables.last().label} added"
    }

    @GetMapping("/tables/{tableId}/edit")
    fun editTable(
        @PathVariable venueId: UUID,
        @PathVariable tableId: UUID,
        @AuthenticationPrincipal principal: PasslyUserPrincipal,
        model: Model,
    ): String {
        val plan = floors.get(VenueId(venueId))
        model.addAttribute("form", EditTableForm.of(plan.table(DiningTableId(tableId))))
        return tablePage(venueId, tableId, plan, principal, model)
    }

    @PostMapping("/tables/{tableId}")
    fun updateTable(
        @PathVariable venueId: UUID,
        @PathVariable tableId: UUID,
        @Valid @ModelAttribute("form") form: EditTableForm,
        binding: BindingResult,
        @AuthenticationPrincipal principal: PasslyUserPrincipal,
        model: Model,
        flash: RedirectAttributes,
    ): String {
        if (!binding.hasErrors()) {
            try {
                val table = floors.updateTable(
                    UpdateTableCommand(
                        venueId = VenueId(venueId),
                        tableId = DiningTableId(tableId),
                        areaId = DiningAreaId(checkNotNull(form.areaId)),
                        label = form.label,
                        seats = form.seats,
                        active = form.active,
                        expectedVersion = form.version,
                    ),
                )
                flash.addFlashAttribute("message", "Table ${table.label} saved")
                return floorPage(venueId)
            } catch (e: ValidationException) {
                val field = e.field
                if (field != null) binding.rejectValue(field, "invalid", e.message ?: "Invalid value")
                else binding.reject("invalid", e.message ?: "Invalid data")
            } catch (e: ConflictException) {
                binding.reject("conflict", e.message ?: "Conflict")
            } catch (e: OptimisticLockingFailureException) {
                binding.reject("conflict", "The table was changed by someone else. Reload and try again.")
            }
        }
        return tablePage(venueId, tableId, floors.get(VenueId(venueId)), principal, model)
    }

    @PostMapping("/tables/{tableId}/shift")
    fun shiftTable(
        @PathVariable venueId: UUID,
        @PathVariable tableId: UUID,
        @RequestParam delta: Int,
        flash: RedirectAttributes,
    ): String = flash.redirectAfter(venueId) {
        requireOneStep(delta)
        floors.shiftTable(VenueId(venueId), DiningTableId(tableId), delta)
        null
    }

    @PostMapping("/tables/{tableId}/delete")
    fun deleteTable(
        @PathVariable venueId: UUID,
        @PathVariable tableId: UUID,
        flash: RedirectAttributes,
    ): String = flash.redirectAfter(venueId) {
        val label = floors.get(VenueId(venueId)).table(DiningTableId(tableId)).label
        floors.deleteTable(VenueId(venueId), DiningTableId(tableId))
        "Table $label deleted"
    }

    private fun tablePage(
        venueId: UUID,
        tableId: UUID,
        plan: FloorPlan,
        principal: PasslyUserPrincipal,
        model: Model,
    ): String {
        model.addAttribute("venue", venues.find(principal, venueId))
        model.addAttribute("tableId", tableId)
        model.addAttribute("tableLabel", plan.table(DiningTableId(tableId)).label)
        model.addAttribute("areaOptions", plan.areaOptions())
        return "admin/table-form"
    }

    private fun requireOneStep(delta: Int) {
        if (delta != -1 && delta != 1) throw ValidationException("Move one step at a time")
    }

    private fun RedirectAttributes.redirectAfter(
        venueId: UUID,
        binding: BindingResult? = null,
        action: () -> String?,
    ): String {
        val bindingError = binding?.allErrors?.firstOrNull()
        if (bindingError != null) {
            addFlashAttribute("error", messages.getMessage(bindingError, LocaleContextHolder.getLocale()))
            return floorPage(venueId)
        }
        try {
            action()?.let { addFlashAttribute("message", it) }
        } catch (e: ConflictException) {
            addFlashAttribute("error", e.message)
        } catch (e: ValidationException) {
            addFlashAttribute("error", e.message)
        } catch (e: OptimisticLockingFailureException) {
            addFlashAttribute("error", "The floor was changed by someone else. Reload and try again.")
        }
        return floorPage(venueId)
    }

    private fun floorPage(venueId: UUID) = "redirect:/admin/venues/$venueId/floor"
}
