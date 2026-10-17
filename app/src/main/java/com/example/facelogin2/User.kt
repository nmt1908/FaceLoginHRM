package com.example.facelogin2

import java.io.Serializable

data class User(
    var name: String? = null,
    var cardId: String? = null,
    var similarity: String? = null
) : Serializable {

    // Giữ lại setter này vì project Java cũ có thể đang gọi nôm na là setDepartment
    fun setDepartment(similarity: String?) {
        this.similarity = similarity
    }

    override fun toString(): String {
        return "User(name='$name', cardId='$cardId', similarity='$similarity')"
    }
}
