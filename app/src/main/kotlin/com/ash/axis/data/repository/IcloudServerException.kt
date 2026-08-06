package com.ash.axis.data.repository

class IcloudServerException(
    val statusCode: Int? = null,
    override val message: String = "iCloudEMS server error",
) : Exception(message)
