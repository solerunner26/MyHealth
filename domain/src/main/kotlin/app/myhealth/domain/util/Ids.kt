package app.myhealth.domain.util

import java.util.UUID

fun interface IdGenerator { fun next(): String }

object UuidIds : IdGenerator { override fun next(): String = UUID.randomUUID().toString() }
