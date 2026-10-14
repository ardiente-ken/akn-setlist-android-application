package com.aknevs.set.domain.model;

public enum RequestsConnection {
    CONNECTING, // opening the connection / loading the queue
    LIVE,       // connected and receiving changes in real time
    OFFLINE     // no connection right now; retrying
}
