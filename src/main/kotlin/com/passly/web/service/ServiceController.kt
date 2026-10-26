package com.passly.web.service

import com.passly.application.menu.WaiterMenuService
import com.passly.application.order.*
import com.passly.application.venue.VenueSettingsService
import com.passly.domain.common.ConflictException
import com.passly.domain.common.Money
import com.passly.domain.common.UnavailableItemsException
import com.passly.domain.common.ValidationException
import com.passly.domain.floor.DiningTableId
import com.passly.domain.order.OrderId
import com.passly.domain.order.OrderStatus
import com.passly.domain.user.UserId
import com.passly.domain.venue.CurrencyCode
import com.passly.domain.venue.VenueId
import com.passly.security.PasslyUserPrincipal
import com.passly.web.admin.AdminVenues
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.math.BigDecimal
import java.time.Clock
import java.util.*

@Controller
@RequestMapping("/service/venues/{venueId}")
class ServiceController(
    private val venues: AdminVenues,
    private val floorStatus: FloorStatusQuery,
    private val menus: WaiterMenuService,
    private val orders: OrderService,
    private val settings: VenueSettingsService,
    private val clock: Clock,
) {

    @GetMapping
    fun floor(
        @PathVariable venueId: UUID,
        @AuthenticationPrincipal principal: PasslyUserPrincipal,
        model: Model,
    ): String {
        model.addAttribute("venue", venues.find(principal, venueId))
        val areas = floorStatus.floorOf(VenueId(venueId)).toAreaViews(clock.instant())
        model.addAttribute("areas", areas)
        model.addAttribute("summary", areas.summary())
        return "service/floor"
    }

    @GetMapping("/tables/{tableId}")
    fun table(
        @PathVariable venueId: UUID,
        @PathVariable tableId: UUID,
        @AuthenticationPrincipal principal: PasslyUserPrincipal,
        model: Model,
    ): String {
        model.addAttribute("venue", venues.find(principal, venueId))
        val status = floorStatus.tableOf(VenueId(venueId), DiningTableId(tableId))
        model.addAttribute("table", status.toPageView(clock.instant()))
        model.addAttribute("orderId", status.order?.id?.value)
        val menu = menus.menu(VenueId(venueId))
        model.addAttribute("sections", menu.toSectionViews())
        model.addAttribute("modifierGroups", menu.toModifierGroupViews())
        model.addAttribute("paymentCurrencies", settings.get(VenueId(venueId)).toPaymentCurrencies())
        return "service/table"
    }

    @PostMapping("/tables/{tableId}/rounds")
    fun send(
        @PathVariable venueId: UUID,
        @PathVariable tableId: UUID,
        @ModelAttribute form: RoundForm,
        binding: BindingResult,
        @AuthenticationPrincipal principal: PasslyUserPrincipal,
        flash: RedirectAttributes,
    ): String {
        val tablePage = "redirect:/service/venues/$venueId/tables/$tableId"
        if (binding.hasErrors()) {
            flash.addFlashAttribute("error", "The round could not be read. Reload the page and try again.")
            return tablePage
        }
        val waiterId = UserId(principal.userId)
        val orderId = form.orderId
        try {
            if (orderId == null) {
                orders.open(
                    OpenOrderCommand(
                        VenueId(venueId),
                        DiningTableId(tableId),
                        waiterId,
                        form.guests,
                        form.requests()
                    )
                )
                flash.addFlashAttribute("message", "Table opened")
            } else {
                val order = orders.addRound(
                    AddRoundCommand(
                        VenueId(venueId),
                        OrderId(orderId),
                        waiterId,
                        form.requests()
                    )
                )
                flash.addFlashAttribute("message", "Round ${order.rounds} sent")
            }
            flash.addFlashAttribute("sent", true)
        } catch (e: UnavailableItemsException) {
            flash.addFlashAttribute("error", e.message)
            flash.addFlashAttribute("unavailable", e.itemIds.joinToString(","))
        } catch (e: ValidationException) {
            flash.addFlashAttribute("error", e.message)
        } catch (e: ConflictException) {
            flash.addFlashAttribute("error", e.message)
        }
        return tablePage
    }

    @PostMapping("/tables/{tableId}/payments")
    fun pay(
        @PathVariable venueId: UUID,
        @PathVariable tableId: UUID,
        @ModelAttribute form: PaymentForm,
        binding: BindingResult,
        @AuthenticationPrincipal principal: PasslyUserPrincipal,
        flash: RedirectAttributes,
    ): String {
        val tablePage = "redirect:/service/venues/$venueId/tables/$tableId"
        val orderId = form.orderId
        val method = form.method
        val amount = parseAmount(form.amount)
        if (binding.hasErrors() || orderId == null || method == null) {
            flash.addFlashAttribute("error", "The payment could not be read. Reload the page and try again.")
            return tablePage
        }
        if (amount == null) {
            flash.addFlashAttribute("error", "Enter an amount like 12.50")
            return tablePage
        }
        return try {
            val order = orders.pay(
                PayOrderCommand(
                    VenueId(venueId),
                    OrderId(orderId),
                    UserId(principal.userId),
                    method,
                    amount,
                    form.currency?.takeIf { it.isNotBlank() }?.let(CurrencyCode::of),
                )
            )
            val payment = order.payments.last()
            val change = if (payment.change.isZero) "" else ", change ${payment.change}"
            if (order.status == OrderStatus.CLOSED) {
                flash.addFlashAttribute("message", "Table closed, ${order.paid} paid$change")
                "redirect:/service/venues/$venueId"
            } else {
                flash.addFlashAttribute("message", "${payment.takenLabel()} taken$change, ${order.due} left to pay")
                tablePage
            }
        } catch (e: ValidationException) {
            flash.addFlashAttribute("error", e.message)
            tablePage
        } catch (e: ConflictException) {
            flash.addFlashAttribute("error", e.message)
            tablePage
        }
    }

    @PostMapping("/tables/{tableId}/close")
    fun close(
        @PathVariable venueId: UUID,
        @PathVariable tableId: UUID,
        @RequestParam orderId: UUID,
        @AuthenticationPrincipal principal: PasslyUserPrincipal,
        flash: RedirectAttributes,
    ): String =
        try {
            orders.close(CloseOrderCommand(VenueId(venueId), OrderId(orderId), UserId(principal.userId)))
            flash.addFlashAttribute("message", "Table closed")
            "redirect:/service/venues/$venueId"
        } catch (e: ConflictException) {
            flash.addFlashAttribute("error", e.message)
            "redirect:/service/venues/$venueId/tables/$tableId"
        }

    private fun parseAmount(text: String): Money? =
        try {
            Money.of(BigDecimal(text.trim().replace(',', '.')))
        } catch (e: NumberFormatException) {
            null
        } catch (e: ValidationException) {
            null
        }
}
