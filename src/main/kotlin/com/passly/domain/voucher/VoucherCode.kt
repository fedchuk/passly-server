package com.passly.domain.voucher

import com.passly.domain.common.ValidationException
import java.util.*

@JvmInline
value class VoucherCode private constructor(val value: String) {

    val label: String get() = "${value.take(HALF)}-${value.drop(HALF)}"

    override fun toString(): String = label

    companion object {
        const val LENGTH = 8
        private const val HALF = LENGTH / 2
        private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

        fun generate(random: Random): VoucherCode =
            VoucherCode(String(CharArray(LENGTH) { ALPHABET[random.nextInt(ALPHABET.length)] }))

        fun parse(text: String): VoucherCode {
            val normalized = text.filterNot { it.isWhitespace() || it == '-' }.uppercase()
            if (normalized.length != LENGTH || normalized.any { it !in ALPHABET }) {
                throw ValidationException("Enter a voucher code like ABCD-2345", "code")
            }
            return VoucherCode(normalized)
        }

        fun fromStorage(value: String) = VoucherCode(value)
    }
}
