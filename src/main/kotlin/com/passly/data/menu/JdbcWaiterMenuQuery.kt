package com.passly.data.menu

import com.passly.application.menu.*
import com.passly.domain.venue.VenueId
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.util.*

@Repository
class JdbcWaiterMenuQuery(private val jdbc: JdbcClient) : WaiterMenuQuery {

    override fun menuVersion(venueId: VenueId): Long? =
        jdbc.sql("select menu_version from venue where id = :venueId")
            .param("venueId", venueId.value)
            .query { rs, _ -> rs.getLong("menu_version") }
            .optional()
            .orElse(null)

    override fun menu(venueId: VenueId): WaiterMenu? {
        val header = header(venueId) ?: return null
        val allCategories = categoryRows(venueId)
        val items = activeItems(venueId, effectiveTaxRateIds(allCategories), taxRates(venueId))
        val categories = allCategories.filter { it.active }
        val childrenByParent = categories.groupBy { it.parentId }

        fun subtree(parentId: UUID?): List<WaiterMenuCategory> =
            childrenByParent[parentId].orEmpty().map { row ->
                WaiterMenuCategory(row.id, row.name, items[row.id].orEmpty(), subtree(row.id))
            }

        return WaiterMenu(venueId.value, header.menuVersion, header.currency, subtree(null), modifierGroups(venueId))
    }

    private fun header(venueId: VenueId): MenuHeader? =
        jdbc.sql("select menu_version, currency from venue where id = :venueId")
            .param("venueId", venueId.value)
            .query { rs, _ -> MenuHeader(rs.getLong("menu_version"), rs.getString("currency")) }
            .optional()
            .orElse(null)

    private data class MenuHeader(val menuVersion: Long, val currency: String)

    private fun categoryRows(venueId: VenueId): List<CategoryRow> =
        jdbc.sql("select id, parent_id, name, active, tax_rate_id from category where venue_id = :venueId order by sort_order")
            .param("venueId", venueId.value)
            .query { rs, _ ->
                CategoryRow(
                    id = rs.getObject("id", UUID::class.java),
                    parentId = rs.getObject("parent_id", UUID::class.java),
                    name = rs.getString("name"),
                    active = rs.getBoolean("active"),
                    taxRateId = rs.getObject("tax_rate_id", UUID::class.java),
                )
            }
            .list()

    private fun taxRates(venueId: VenueId): Map<UUID, BigDecimal> =
        jdbc.sql("select id, rate from venue_tax_rate where venue_id = :venueId")
            .param("venueId", venueId.value)
            .query { rs, _ -> rs.getObject("id", UUID::class.java) to rs.getBigDecimal("rate") }
            .list()
            .toMap()

    private fun effectiveTaxRateIds(categories: List<CategoryRow>): Map<UUID, UUID> {
        val byId = categories.associateBy { it.id }
        fun rateOf(row: CategoryRow): UUID? = row.taxRateId ?: row.parentId?.let { byId[it] }?.let { rateOf(it) }
        return categories.mapNotNull { row -> rateOf(row)?.let { row.id to it } }.toMap()
    }

    private fun activeItems(
        venueId: VenueId,
        categoryTaxRateIds: Map<UUID, UUID>,
        taxRates: Map<UUID, BigDecimal>,
    ): Map<UUID, List<WaiterMenuItem>> {
        val groupsByItem = itemModifierGroups(venueId)
        return jdbc.sql(
            """
            select id, category_id, name, description, price, tax_rate_id, available
            from menu_item
            where venue_id = :venueId and not archived
            order by sort_order
            """,
        )
            .param("venueId", venueId.value)
            .query { rs, _ ->
                val id = rs.getObject("id", UUID::class.java)
                val categoryId = rs.getObject("category_id", UUID::class.java)
                val taxRateId = rs.getObject("tax_rate_id", UUID::class.java) ?: categoryTaxRateIds[categoryId]
                categoryId to WaiterMenuItem(
                    id = id,
                    name = rs.getString("name"),
                    description = rs.getString("description"),
                    price = rs.getBigDecimal("price"),
                    taxRate = checkNotNull(taxRates[taxRateId]) { "Menu item $id has no tax rate" },
                    available = rs.getBoolean("available"),
                    modifierGroupIds = groupsByItem[id].orEmpty(),
                )
            }
            .list()
            .groupBy({ it.first }, { it.second })
    }

    private fun itemModifierGroups(venueId: VenueId): Map<UUID, List<UUID>> =
        jdbc.sql(
            """
            select l.menu_item_id, l.group_id
            from menu_item_modifier_group l
            join menu_item i on i.id = l.menu_item_id
            where i.venue_id = :venueId and not i.archived
            order by l.menu_item_id, l.position
            """,
        )
            .param("venueId", venueId.value)
            .query { rs, _ -> rs.getObject("menu_item_id", UUID::class.java) to rs.getObject("group_id", UUID::class.java) }
            .list()
            .groupBy({ it.first }, { it.second })

    private fun modifierGroups(venueId: VenueId): List<WaiterMenuModifierGroup> {
        val optionsByGroup = jdbc.sql(
            """
            select m.id, m.group_id, m.name, m.price_delta
            from modifier m
            join modifier_group g on g.id = m.group_id
            where g.venue_id = :venueId
            order by m.group_id, m.position
            """,
        )
            .param("venueId", venueId.value)
            .query { rs, _ ->
                rs.getObject("group_id", UUID::class.java) to WaiterMenuOption(
                    id = rs.getObject("id", UUID::class.java),
                    name = rs.getString("name"),
                    priceDelta = rs.getBigDecimal("price_delta"),
                )
            }
            .list()
            .groupBy({ it.first }, { it.second })

        return jdbc.sql("select id, name, min_select, max_select from modifier_group where venue_id = :venueId order by lower(name)")
            .param("venueId", venueId.value)
            .query { rs, _ ->
                val id = rs.getObject("id", UUID::class.java)
                WaiterMenuModifierGroup(
                    id = id,
                    name = rs.getString("name"),
                    minSelect = rs.getInt("min_select"),
                    maxSelect = rs.getInt("max_select"),
                    options = optionsByGroup[id].orEmpty(),
                )
            }
            .list()
    }

    private data class CategoryRow(val id: UUID, val parentId: UUID?, val name: String, val active: Boolean, val taxRateId: UUID?)
}
