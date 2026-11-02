package com.mrjis.medicinetime.util

import java.util.UUID

/** All ids are UUID strings so data from different devices can merge later. */
fun newId(): String = UUID.randomUUID().toString()