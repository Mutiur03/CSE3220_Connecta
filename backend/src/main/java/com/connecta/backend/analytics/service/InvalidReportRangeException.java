package com.connecta.backend.analytics.service;

/** The requested report range cannot be used: dates in the wrong order, too long a range, or an unknown zone. */
public class InvalidReportRangeException extends RuntimeException {

    public InvalidReportRangeException(String message) {
        super(message);
    }
}
