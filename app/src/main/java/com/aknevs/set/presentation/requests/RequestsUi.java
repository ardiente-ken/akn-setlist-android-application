package com.aknevs.set.presentation.requests;

import android.content.Context;

import androidx.core.content.ContextCompat;

import com.aknevs.set.R;
import com.aknevs.set.domain.model.AdminException;
import com.aknevs.set.domain.model.RequestsAvailability;
import com.aknevs.set.domain.model.RequestsConnection;

/** Shared wording and colours for the requests status. */
public final class RequestsUi {

    private RequestsUi() {}

    public static String statusText(Context context, RequestsAvailability availability) {
        switch (availability) {
            case OPEN:   return context.getString(R.string.requests_open);
            case CLOSED: return context.getString(R.string.requests_closed);
            case LOADING:
            default:     return context.getString(R.string.requests_checking);
        }
    }

    public static String settingText(Context context, RequestsAvailability availability) {
        switch (availability) {
            case OPEN:   return context.getString(R.string.setting_requests_open);
            case CLOSED: return context.getString(R.string.setting_requests_closed);
            case LOADING:
            default:     return context.getString(R.string.setting_requests_checking);
        }
    }

    public static String connectionText(Context context, RequestsConnection connection) {
        switch (connection) {
            case LIVE:    return context.getString(R.string.conn_live);
            case OFFLINE: return context.getString(R.string.conn_offline);
            case CONNECTING:
            default:      return context.getString(R.string.conn_connecting);
        }
    }

    /** Accent = live, red = offline, grey while connecting. */
    public static int connectionColor(Context context, RequestsConnection connection) {
        int res;
        switch (connection) {
            case LIVE:    res = R.color.akn_accent; break;
            case OFFLINE: res = R.color.akn_error; break;
            default:      res = R.color.akn_text_secondary; break;
        }
        return ContextCompat.getColor(context, res);
    }

    /** Accent = open and live, red = connection down (the status may be stale), grey otherwise. */
    public static int dotColor(Context context, RequestsAvailability availability,
                               RequestsConnection connection) {
        int res;
        if (connection == RequestsConnection.OFFLINE) res = R.color.akn_error;
        else if (availability == RequestsAvailability.OPEN) res = R.color.akn_accent;
        else res = R.color.akn_text_tertiary;
        return ContextCompat.getColor(context, res);
    }

    /** Friendly message for a failed remove attempt. */
    public static int removeErrorMessage(Exception error) {
        if (error instanceof AdminException) {
            switch (((AdminException) error).getReason()) {
                case NOT_ADMIN:     return R.string.requests_error_not_admin;
                case NOT_SIGNED_IN: return R.string.requests_error_signin;
                case NETWORK:       return R.string.request_error_network;
                default:            break;
            }
        }
        return R.string.request_error_remove;
    }

    /** Friendly message for a failed open/close attempt. */
    public static int adminErrorMessage(Exception error) {
        if (error instanceof AdminException) {
            switch (((AdminException) error).getReason()) {
                case NOT_ADMIN:      return R.string.requests_error_not_admin;
                case NOT_SIGNED_IN:  return R.string.requests_error_signin;
                case NETWORK:        return R.string.requests_error_network;
                default:             break;
            }
        }
        return R.string.requests_error_server;
    }
}
