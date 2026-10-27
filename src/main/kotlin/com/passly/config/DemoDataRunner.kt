package com.passly.config

import com.passly.application.floor.CreateAreaCommand
import com.passly.application.floor.CreateTablesCommand
import com.passly.application.floor.FloorPlanService
import com.passly.application.floor.UpdateTableCommand
import com.passly.application.menu.*
import com.passly.application.role.RoleService
import com.passly.application.tax.TaxRateService
import com.passly.application.user.HireStaffCommand
import com.passly.application.user.StaffService
import com.passly.application.venue.OnboardingService
import com.passly.application.venue.RegisterBusinessCommand
import com.passly.domain.common.Money
import com.passly.domain.menu.*
import com.passly.domain.role.RolePreset
import com.passly.domain.role.VenueRoleId
import com.passly.domain.tax.VenueTaxRateId
import com.passly.domain.user.Email
import com.passly.domain.user.RawPassword
import com.passly.domain.user.UserRepository
import com.passly.domain.venue.VenueId
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.*

@Component
@Profile("demo & !prod")
class DemoDataRunner(
    private val onboarding: OnboardingService,
    private val users: UserRepository,
    private val categories: CategoryService,
    private val items: MenuItemService,
    private val modifierGroups: ModifierGroupService,
    private val floors: FloorPlanService,
    private val staff: StaffService,
    private val stations: StationService,
    private val taxRates: TaxRateService,
    private val roles: RoleService,
    @Value("\${passly.demo.password:}") private val configuredPassword: String,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(DemoDataRunner::class.java)

    override fun run(args: ApplicationArguments) {
        if (users.findByEmail(OWNER_EMAIL) != null) {
            log.info("Demo data skipped: {} already exists", OWNER_EMAIL)
            return
        }
        val password = configuredPassword.ifBlank { randomPassword() }
        val business = onboarding.registerBusiness(
            RegisterBusinessCommand(
                businessName = "Centro Group",
                venueName = "Bistro Centro",
                ownerEmail = OWNER_EMAIL,
                ownerName = "Elena Ortiz",
                ownerPassword = RawPassword.of(password),
            ),
        )
        val venueId = business.venueId
        val defaultStations = stations.list(venueId).associate { it.name to it.id }
        val kitchen = defaultStations.getValue("Kitchen")
        val bar = defaultStations.getValue("Bar")
        val alcohol = taxRates.list(venueId).single { it.name == ALCOHOL_TAX }.id

        createMenu(venueId, kitchen, bar, alcohol, createModifierGroups(venueId))
        createFloor(venueId)
        hireStaff(venueId, password)

        log.info("Demo venue 'Bistro Centro' is ready. Log in as {} with password {}", OWNER_EMAIL, password)
    }

    private fun createModifierGroups(venueId: VenueId) = DemoModifierGroups(
        doneness = group(venueId, "Doneness", 1, 1, "Rare" to "0", "Medium rare" to "0", "Medium" to "0", "Well done" to "0"),
        side = group(venueId, "Side", 1, 1, "Fries" to "0", "Mashed potatoes" to "0", "Grilled vegetables" to "1.50", "Green salad" to "0"),
        extras = group(venueId, "Burger extras", 0, 3, "Bacon" to "1.50", "Cheddar" to "1.00", "Fried egg" to "1.20", "Jalapeños" to "0.80"),
        milk = group(venueId, "Milk", 0, 1, "Oat milk" to "0.40", "Soy milk" to "0.40", "Lactose-free" to "0.30"),
        coffeeSize = group(venueId, "Coffee size", 1, 1, "Regular" to "0", "Large" to "0.60"),
        beerSize = group(venueId, "Beer size", 1, 1, "Caña" to "0", "Tercio" to "0.80", "Pinta" to "1.80"),
    )

    private fun createMenu(venueId: VenueId, kitchen: StationId, bar: StationId, alcohol: VenueTaxRateId, groups: DemoModifierGroups) {
        val food = category(venueId, "Food", station = kitchen)

        val starters = category(venueId, "Starters", food)
        item(venueId, starters, "Patatas bravas", "6.50", description = "Crispy potatoes, spicy tomato sauce, aioli")
        item(venueId, starters, "Ham croquettes", "8.00", description = "Six homemade croquetas de jamón")
        item(venueId, starters, "Burrata", "11.50", description = "With heirloom tomatoes and basil oil")

        val salads = category(venueId, "Salads", food)
        item(venueId, salads, "Caesar salad", "10.50")
        item(venueId, salads, "Greek salad", "9.50")

        val mains = category(venueId, "Mains", food)
        val meat = category(venueId, "Meat", mains)
        item(venueId, meat, "Ribeye steak 300 g", "26.00", groups = listOf(groups.doneness, groups.side))
        item(venueId, meat, "Chicken breast", "15.50", groups = listOf(groups.side))
        item(venueId, meat, "Bistro burger", "14.00", description = "Beef patty, brioche bun, fries", groups = listOf(groups.doneness, groups.extras))

        val fish = category(venueId, "Fish", mains)
        item(venueId, fish, "Grilled salmon", "19.00", groups = listOf(groups.side))
        val seaBass = item(venueId, fish, "Sea bass", "21.00", groups = listOf(groups.side))
        items.changeAvailability(venueId, seaBass, available = false)

        val rice = category(venueId, "Rice", mains)
        item(venueId, rice, "Paella valenciana for two", "32.00", description = "Chicken, rabbit and green beans. About 30 minutes")
        item(venueId, rice, "Black rice for two", "28.00", description = "Squid ink, cuttlefish, aioli")

        val desserts = category(venueId, "Desserts", food)
        item(venueId, desserts, "Tiramisu", "7.00")
        item(venueId, desserts, "Crema catalana", "6.50")
        item(venueId, desserts, "Basque cheesecake", "7.50")

        val drinks = category(venueId, "Drinks", station = bar)

        val coffee = category(venueId, "Coffee and tea", drinks)
        item(venueId, coffee, "Espresso", "1.80", groups = listOf(groups.milk))
        item(venueId, coffee, "Cappuccino", "2.80", groups = listOf(groups.coffeeSize, groups.milk))
        item(venueId, coffee, "Latte", "3.20", groups = listOf(groups.coffeeSize, groups.milk))
        item(venueId, coffee, "Green tea", "2.50")

        val soft = category(venueId, "Soft drinks", drinks)
        item(venueId, soft, "Coca-Cola", "2.80")
        item(venueId, soft, "Fresh orange juice", "3.50")
        item(venueId, soft, "Still water 0.5 l", "2.00")

        val beer = category(venueId, "Beer", drinks, taxRate = alcohol)
        item(venueId, beer, "Estrella Damm", "2.50", groups = listOf(groups.beerSize))
        item(venueId, beer, "Alhambra Reserva 1925", "3.80")

        val wine = category(venueId, "Wine", drinks, taxRate = alcohol)
        item(venueId, wine, "Rioja Crianza, glass", "4.50")
        item(venueId, wine, "Albariño, glass", "4.80")
        item(venueId, wine, "Cava brut, glass", "4.50")

        val cocktails = category(venueId, "Cocktails", drinks, taxRate = alcohol)
        item(venueId, cocktails, "Agua de Valencia", "8.00", description = "Cava, orange juice, gin and vodka")
        item(venueId, cocktails, "Aperol spritz", "8.00")
        item(venueId, cocktails, "Mojito", "8.50")

        val seasonal = category(venueId, "Autumn specials", station = kitchen)
        item(venueId, seasonal, "Pumpkin soup", "7.50")
        categories.update(UpdateCategoryCommand(venueId, seasonal, "Autumn specials", kitchen, active = false))
    }

    private fun createFloor(venueId: VenueId) {
        val hall = floors.createArea(CreateAreaCommand(venueId, "Main hall"))
        floors.createTables(CreateTablesCommand(venueId, hall.id, 1..12, seats = 4))

        val terrace = floors.createArea(CreateAreaCommand(venueId, "Terrace"))
        val terraceTables = floors.createTables(CreateTablesCommand(venueId, terrace.id, 1..8, labelPrefix = "T", seats = 2))
        val closed = terraceTables.last()
        floors.updateTable(
            UpdateTableCommand(
                venueId,
                closed.id,
                terrace.id,
                closed.label,
                closed.seats,
                active = false
            )
        )

        val counter = floors.createArea(CreateAreaCommand(venueId, "Bar counter"))
        floors.createTables(CreateTablesCommand(venueId, counter.id, 1..4, labelPrefix = "Bar ", seats = 1))
    }

    private fun hireStaff(venueId: VenueId, password: String) {
        val presets = roles.list(venueId).associate { it.role.name to it.role.id }
        fun role(preset: RolePreset): VenueRoleId = presets.getValue(preset.roleName)
        hire(venueId, "lucia", "Lucía Martínez", role(RolePreset.MANAGER), password)
        hire(venueId, "pablo", "Pablo García", role(RolePreset.WAITER), password)
        hire(venueId, "marta", "Marta López", role(RolePreset.WAITER), password)
        hire(venueId, "diego", "Diego Sánchez", role(RolePreset.KITCHEN), password)
    }

    private fun group(
        venueId: VenueId,
        name: String,
        minSelect: Int,
        maxSelect: Int,
        vararg options: Pair<String, String>,
    ): ModifierGroupId = modifierGroups.create(
        CreateModifierGroupCommand(
            venueId = venueId,
            name = name,
            minSelect = minSelect,
            maxSelect = maxSelect,
            options = options.map { (option, priceDelta) -> ModifierDraft(option, Money.of(priceDelta)) },
        ),
    ).id

    private fun category(
        venueId: VenueId,
        name: String,
        parentId: CategoryId? = null,
        station: StationId? = null,
        taxRate: VenueTaxRateId? = null,
    ): CategoryId = categories.create(CreateCategoryCommand(venueId, name, parentId, station, taxRate)).id

    private fun item(
        venueId: VenueId,
        categoryId: CategoryId,
        name: String,
        price: String,
        description: String? = null,
        groups: List<ModifierGroupId> = emptyList(),
    ): MenuItemId {
        val details = MenuItemDetails(name, description, Money.of(price))
        val created = items.create(CreateMenuItemCommand(venueId, categoryId, details))
        if (groups.isNotEmpty()) items.attachModifierGroups(AttachModifierGroupsCommand(venueId, created.id, groups))
        return created.id
    }

    private fun hire(venueId: VenueId, login: String, name: String, roleId: VenueRoleId, password: String) {
        staff.hire(HireStaffCommand(venueId, Email.of("$login@$DOMAIN"), name, RawPassword.of(password), roleId))
    }

    private fun randomPassword(): String {
        val bytes = ByteArray(12).also { SecureRandom().nextBytes(it) }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private data class DemoModifierGroups(
        val doneness: ModifierGroupId,
        val side: ModifierGroupId,
        val extras: ModifierGroupId,
        val milk: ModifierGroupId,
        val coffeeSize: ModifierGroupId,
        val beerSize: ModifierGroupId,
    )

    companion object {
        const val DOMAIN = "demo.passly"
        val OWNER_EMAIL: Email = Email.of("owner@$DOMAIN")

        private const val ALCOHOL_TAX = "Alcohol"
    }
}
