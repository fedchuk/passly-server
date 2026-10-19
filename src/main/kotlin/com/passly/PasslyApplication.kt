package com.passly

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class PasslyApplication

fun main(args: Array<String>) {
    runApplication<PasslyApplication>(*args)
}
