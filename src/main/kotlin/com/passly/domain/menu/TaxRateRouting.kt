package com.passly.domain.menu

import com.passly.domain.tax.VenueTaxRateId

fun MenuTree.taxRateFor(item: MenuItem): VenueTaxRateId? =
    item.taxRateId ?: item.categoryId?.let { effectiveTaxRate(it) }
