package com.passly.web.api

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.media.ArraySchema
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.IntegerSchema
import io.swagger.v3.oas.models.media.MapSchema
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.ObjectSchema
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.media.StringSchema
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springdoc.core.customizers.OpenApiCustomizer
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {


    @Bean
    fun passlyOpenApi(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("Passly API")
                .version("v1")
                .description("REST API for the Passly waiter and kitchen apps. Log in, then press Authorize and paste the access token."),
        )
        .components(
            Components()
                .addSecuritySchemes(
                    BEARER,
                    SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"),
                ),
        )
        .addSecurityItem(SecurityRequirement().addList(BEARER))

    @Bean
    fun problemResponses(): OpenApiCustomizer = OpenApiCustomizer { openApi ->
        val components = openApi.components ?: Components().also { openApi.components = it }
        components.addSchemas(PROBLEM, problemDetailSchema())
        openApi.paths.orEmpty().forEach { (path, item) ->
            item.readOperationsMap().forEach { (method, operation) ->
                val responses = operation.responses ?: ApiResponses().also { operation.responses = it }
                if (operation.requestBody != null) responses.addProblem("400", "The request is invalid")
                if (operation.security == null) {
                    responses.addProblem("401", "The access token is missing, invalid or expired")
                    responses.addProblem("403", "No access to this venue or action")
                }
                if ("{" in path) responses.addProblem("404", "The venue or the resource does not exist")
                if (method != PathItem.HttpMethod.GET && operation.security == null) {
                    responses.addProblem("409", "Conflict with the current state, for example a stale version")
                }
            }
        }
    }

    private fun ApiResponses.addProblem(code: String, description: String) {
        if (containsKey(code)) return
        val content = Content().addMediaType(
            "application/problem+json",
            MediaType().schema(Schema<Any>().`$ref`("#/components/schemas/$PROBLEM")),
        )
        addApiResponse(code, ApiResponse().description(description).content(content))
    }

    private fun problemDetailSchema(): Schema<Any> = ObjectSchema()
        .description("Error in RFC 9457 Problem Details format")
        .addProperty("type", StringSchema().example("about:blank"))
        .addProperty("title", StringSchema().example("Conflict"))
        .addProperty("status", IntegerSchema().example(409))
        .addProperty("detail", StringSchema().example("Category Wine was changed by someone else. Reload and try again."))
        .addProperty("instance", StringSchema().example("/api/v1/venues/00000000-0000-0000-0000-000000000010/categories"))
        .addProperty(
            "errors",
            MapSchema().additionalProperties(ArraySchema().items(StringSchema()))
                .description("Field errors, present only for validation failures"),
        )

    companion object {
        const val BEARER = "bearerAuth"
        const val PROBLEM = "ProblemDetail"
    }
}
