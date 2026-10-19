package com.passly.web.menu

import com.passly.TestcontainersConfiguration
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.util.UUID
import kotlin.test.Test

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class MenuItemControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbc: JdbcClient

    private val venueId = UUID.randomUUID()
    private val venue get() = "/api/v1/venues/$venueId"

    @BeforeEach
    fun createVenue() {
        val tenantId = UUID.randomUUID()
        jdbc.sql("insert into tenant (id, name) values (?, ?)").params(tenantId, "Test tenant").update()
        jdbc.sql("insert into venue (id, tenant_id, name) values (?, ?, ?)").params(venueId, tenantId, "Test venue").update()
    }

    @Test
    fun `created items are listed in order with money as strings`() {
        val wine = createCategory("Wine")
        createItem(wine, "Rioja", price = "4.5")
        createItem(wine, "Albariño", price = "5")

        mockMvc.get("$venue/categories/$wine/items").andExpect {
            status { isOk() }
            jsonPath("$[0].name") { value("Rioja") }
            jsonPath("$[0].price") { value("4.50") }
            jsonPath("$[0].taxRate") { value("10.00") }
            jsonPath("$[1].name") { value("Albariño") }
            jsonPath("$[1].price") { value("5.00") }
        }
    }

    @Test
    fun `invalid price is rejected with field errors`() {
        val wine = createCategory("Wine")

        mockMvc.post("$venue/categories/$wine/items") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "Rioja", "price": "4.555", "taxRate": "10"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.errors.price") { exists() }
        }
    }

    @Test
    fun `item can be put on the stop list`() {
        val wine = createCategory("Wine")
        val rioja = createItem(wine, "Rioja")

        mockMvc.put("$venue/items/$rioja/availability") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"available": false}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.available") { value(false) }
        }
    }

    @Test
    fun `update with a stale version is a conflict`() {
        val wine = createCategory("Wine")
        val rioja = createItem(wine, "Rioja")
        val body = { version: Int -> """{"name": "Rioja Crianza", "price": "4.80", "taxRate": "10", "version": $version}""" }

        mockMvc.put("$venue/items/$rioja") {
            contentType = MediaType.APPLICATION_JSON
            content = body(0)
        }.andExpect {
            status { isOk() }
            jsonPath("$.version") { value(1) }
        }

        mockMvc.put("$venue/items/$rioja") {
            contentType = MediaType.APPLICATION_JSON
            content = body(0)
        }.andExpect { status { isConflict() } }
    }

    @Test
    fun `item can be moved to another category`() {
        val red = createCategory("Red")
        val white = createCategory("White")
        val albarino = createItem(red, "Albariño")

        mockMvc.post("$venue/items/$albarino/move") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"categoryId": "$white"}"""
        }.andExpect { status { isNoContent() } }

        mockMvc.get("$venue/items/$albarino").andExpect {
            jsonPath("$.categoryId") { value(white) }
        }
    }

    @Test
    fun `category with items can be deleted only after they are archived`() {
        val wine = createCategory("Wine")
        val rioja = createItem(wine, "Rioja")

        mockMvc.delete("$venue/categories/$wine").andExpect { status { isConflict() } }

        mockMvc.delete("$venue/items/$rioja").andExpect { status { isNoContent() } }
        mockMvc.get("$venue/items/$rioja").andExpect { status { isNotFound() } }
        mockMvc.delete("$venue/categories/$wine").andExpect { status { isNoContent() } }
    }

    @Test
    fun `moving an item to an unknown category is not found`() {
        val wine = createCategory("Wine")
        val rioja = createItem(wine, "Rioja")

        mockMvc.post("$venue/items/$rioja/move") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"categoryId": "${UUID.randomUUID()}"}"""
        }.andExpect { status { isNotFound() } }

        mockMvc.get("$venue/items/$rioja").andExpect {
            jsonPath("$.categoryId") { value(wine) }
        }
    }

    @Test
    fun `modifier groups can be attached and then block deleting the group`() {
        val ribeye = createItem(createCategory("Steaks"), "Ribeye")
        val doneness = createGroup("Doneness")

        mockMvc.put("$venue/items/$ribeye/modifier-groups") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"groupIds": ["$doneness"], "version": 0}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.modifierGroupIds[0]") { value(doneness) }
            jsonPath("$.version") { value(1) }
        }

        mockMvc.delete("$venue/modifier-groups/$doneness").andExpect { status { isConflict() } }
    }

    private fun createGroup(name: String): String =
        mockMvc.post("$venue/modifier-groups") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "$name", "minSelect": 1, "maxSelect": 1, "options": [{"name": "Option"}]}"""
        }.andExpect {
            status { isCreated() }
        }.andReturn().response.getHeader("Location")!!.substringAfterLast('/')

    private fun createCategory(name: String): String =
        mockMvc.post("$venue/categories") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "$name"}"""
        }.andExpect {
            status { isCreated() }
        }.andReturn().response.getHeader("Location")!!.substringAfterLast('/')

    private fun createItem(category: String, name: String, price: String = "10.00"): String =
        mockMvc.post("$venue/categories/$category/items") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "$name", "price": "$price", "taxRate": "10"}"""
        }.andExpect {
            status { isCreated() }
        }.andReturn().response.getHeader("Location")!!.substringAfterLast('/')
}
