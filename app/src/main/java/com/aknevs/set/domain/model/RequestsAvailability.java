package com.aknevs.set.domain.model;

/** Whether the audience can send requests (public.app_settings 'requests_enabled'). */
public enum RequestsAvailability {
    LOADING, // value not read yet
    OPEN,
    CLOSED   // also used when the first read fails, and when the row is missing or deleted
}
