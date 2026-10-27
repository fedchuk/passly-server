package com.passly.application.venue

import com.passly.domain.common.ConflictException
import com.passly.domain.menu.StationList
import com.passly.domain.menu.StationRepository
import com.passly.domain.role.RoleList
import com.passly.domain.role.RoleRepository
import com.passly.domain.tax.TaxRateList
import com.passly.domain.tax.TaxRatePreset
import com.passly.domain.tax.TaxRateRepository
import com.passly.domain.user.PasswordHasher
import com.passly.domain.user.User
import com.passly.domain.user.UserRepository
import com.passly.domain.venue.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class OnboardingService(
    private val tenants: TenantRepository,
    private val venues: VenueRepository,
    private val users: UserRepository,
    private val passwordHasher: PasswordHasher,
    private val stations: StationRepository,
    private val taxRates: TaxRateRepository,
    private val roles: RoleRepository,
) {

    fun registerBusiness(command: RegisterBusinessCommand): RegisteredBusiness {
        if (users.findByEmail(command.ownerEmail) != null) {
            throw ConflictException("A user with email ${command.ownerEmail} already exists")
        }
        val tenant = Tenant.create(command.businessName)
        val venue = Venue.create(tenant.id, command.venueName)
        val owner = User.create(command.ownerEmail, command.ownerName, passwordHasher.hash(command.ownerPassword))
        owner.becomeOwnerOf(tenant.id)

        val defaultStations = StationList(venue.id, emptyList()).let { list ->
            StationList.DEFAULT_NAMES.map { list.add(tenant.id, it) }
        }

        val defaultTaxRates = TaxRateList(venue.id, emptyList())
            .addMissing(tenant.id, TaxRatePreset.forCountry(CountryCode.FRANCE))

        val defaultRoles = RoleList(venue.id, emptyList()).addPresets(tenant.id)

        tenants.save(tenant)
        venues.save(venue)
        stations.saveAll(defaultStations)
        taxRates.saveAll(defaultTaxRates)
        roles.saveAll(defaultRoles)
        users.save(owner)
        return RegisteredBusiness(tenant.id, venue.id, owner.id)
    }
}
