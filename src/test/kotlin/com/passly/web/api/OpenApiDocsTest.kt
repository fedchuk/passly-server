package com.passly.web.api

import com.passly.TestcontainersConfiguration
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.web.context.WebApplicationContext
import kotlin.test.Test

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class OpenApiDocsTest {

    @Autowired
    lateinit var context: WebApplicationContext

    lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = context.securedMockMvc()
    }

    @Test
    fun `api description is public and lists the api endpoints only`() {
        mockMvc.get("/v3/api-docs").andExpect {
            status { isOk() }
            jsonPath("$.info.title") { value("Passly API") }
            jsonPath("$.paths['/api/v1/auth/login'].post") { exists() }
            jsonPath("$.paths['/api/v1/venues/{venueId}/categories'].get") { exists() }
            jsonPath("$.paths['/api/v1/venues/{venueId}/orders'].post") { exists() }
            jsonPath("$.paths['/api/v1/venues/{venueId}/orders/{id}/rounds'].post") { exists() }
            jsonPath("$.paths['/admin']") { doesNotExist() }
            jsonPath("$.paths['/login']") { doesNotExist() }
        }
    }

    @Test
    fun `bearer token is documented and login does not need it`() {
        mockMvc.get("/v3/api-docs").andExpect {
            jsonPath("$.components.securitySchemes.bearerAuth.scheme") { value("bearer") }
            jsonPath("$.security[0].bearerAuth") { exists() }
            jsonPath("$.paths['/api/v1/auth/login'].post.security") { isEmpty() }
        }
    }

    @Test
    fun `errors are documented as problem details`() {
        mockMvc.get("/v3/api-docs").andExpect {
            jsonPath("$.components.schemas.ProblemDetail.properties.detail") { exists() }
            jsonPath("$.components.schemas.ProblemDetail.properties.unavailableItemIds") { exists() }
            jsonPath("$.paths['/api/v1/venues/{venueId}/categories/{id}'].put.responses['409']") { exists() }
            jsonPath("$.paths['/api/v1/venues/{venueId}/categories/{id}'].put.responses['401']") { exists() }
            jsonPath("$.paths['/api/v1/auth/login'].post.responses['403']") { doesNotExist() }
        }
    }

    @Test
    fun `swagger ui is open`() {
        mockMvc.get("/swagger-ui/index.html").andExpect { status { isOk() } }
    }
}
