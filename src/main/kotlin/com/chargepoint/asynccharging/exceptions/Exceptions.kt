package com.chargepoint.asynccharging.exceptions

class ValidationException(message: String, cause: Throwable? = null) : Exception(message, cause)
class QueueException(message: String, cause: Throwable? = null) : Exception(message, cause)
class AuthorizationException(message: String, cause: Throwable? = null) : Exception(message, cause)
class CallbackException(message: String, cause: Throwable? = null) : Exception(message, cause)
