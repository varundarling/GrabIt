package com.varun.grabit

import com.google.firebase.database.PropertyName

data class Items(
    @PropertyName("id") val id: String = "",
    @PropertyName("name") val name: String = "",
    @PropertyName("quantity") val quantity: Int = 0,
    @PropertyName("price") val price: Double = 0.0,
    @PropertyName("isChecked") var isChecked: Boolean = false
) {
    // No-argument constructor for Firebase
    constructor() : this("", "", 0, 0.0, false)

    @PropertyName("isChecked")
    fun setIsChecked(value: Boolean) {
        isChecked = value
    }
}
