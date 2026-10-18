package com.passly

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class PasslyServerApplication

fun main(args: Array<String>) {
    runApplication<PasslyServerApplication>(*args)
}
