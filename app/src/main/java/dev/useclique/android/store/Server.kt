package dev.useclique.android.store

data class Server(
    val id: String,
    val name: String,
    val baseUrl: String,
    val caPem: String = "",
)
