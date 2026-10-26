package com.passly.application.idempotency

class IdempotencyKeyReusedException :
    RuntimeException("This Idempotency-Key was already used for a different request. Send a new key for a new request.")
