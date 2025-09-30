package com.varun.grabit

data class Items(
    val id: String = "",
    val name: String = "",
    val quantity: Int = 0,
    val price: Double = 0.0,
    var isChecked: Boolean = false
)
