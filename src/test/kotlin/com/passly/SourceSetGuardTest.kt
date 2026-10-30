package com.passly

import org.apache.kafka.shaded.com.google.protobuf.LazyStringArrayList.emptyList
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.readLines
import kotlin.test.Test
import kotlin.test.assertEquals

class SourceSetGuardTest {

    private val beanAnnotation = Regex("^@(Controller|RestController|ControllerAdvice|RestControllerAdvice|Service|Repository|Component|Configuration)\\b")

    @Test
    fun `production beans live in the main source set`() {
        val misplaced = Files.walk(Path.of("src/test/kotlin")).use { paths ->
            paths.filter { it.extension == "kt" }
                .filter { file -> file.readLines().any { beanAnnotation.containsMatchIn(it.trim()) } }
                .map { it.toString() }
                .toList()
        }

        assertEquals(emptyList(), misplaced, "These files declare Spring beans but are only compiled into tests")
    }
}
