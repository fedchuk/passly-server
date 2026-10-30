package com.passly.application.venue

import com.passly.application.menu.MenuChangeKind
import com.passly.application.menu.MenuChangeRecorder
import com.passly.domain.common.ConflictException
import com.passly.domain.common.NotFoundException
import com.passly.domain.order.OrderRepository
import com.passly.domain.venue.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Service
@Transactional
class VenueSettingsService(
    private val venues: VenueRepository,
    private val settings: VenueSettingsRepository,
    private val orders: OrderRepository,
    private val menuChanges: MenuChangeRecorder,
    private val clock: Clock,
) {

    @Transactional(readOnly = true)
    fun get(venueId: VenueId): VenueSettings = load(venueId)

    @Transactional(readOnly = true)
    fun currencyLocked(venueId: VenueId): Boolean = orders.existsInVenue(venueId)

    fun update(command: UpdateVenueSettingsCommand): VenueSettings {
        venues.findByIdAndLock(command.venueId) ?: throw NotFoundException("Venue", command.venueId)
        val current = load(command.venueId)
        if (command.expectedVersion != null && command.expectedVersion != current.version) {
            throw ConflictException("Settings were changed by someone else. Reload and try again.")
        }
        val previousCurrency = current.currency
        current.changeCountry(command.country)
        command.timeZone?.let(current::changeTimeZone)
        current.changeCurrency(command.currency, venueHasOrders = orders.existsInVenue(command.venueId))
        settings.save(current)
        if (current.currency != previousCurrency) menuChanges.recordChange(command.venueId, MenuChangeKind.CURRENCY)
        return load(command.venueId)
    }

    fun acceptCurrency(command: AcceptCurrencyCommand): AcceptedCurrency =
        change(command.venueId) { it.acceptCurrency(command.currency, command.rate, clock.instant()) }

    fun changeRate(command: ChangeRateCommand): AcceptedCurrency =
        change(command.venueId) { it.changeRate(command.currency, command.rate, clock.instant()) }

    fun stopAccepting(venueId: VenueId, currency: CurrencyCode) {
        change(venueId) { it.stopAccepting(currency) }
    }

    private fun <T> change(venueId: VenueId, action: (VenueSettings) -> T): T {
        venues.findByIdAndLock(venueId) ?: throw NotFoundException("Venue", venueId)
        val current = load(venueId)
        val result = action(current)
        settings.save(current)
        return result
    }

    private fun load(venueId: VenueId): VenueSettings =
        settings.find(venueId) ?: throw NotFoundException("Venue", venueId)
}
