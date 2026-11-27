package com.connecta.backend.org.service;

/** The requested report dates are reversed or span more days than a report may cover. */
public class InvalidReportRangeException extends RuntimeException {

    public InvalidReportRangeException(String message) {
        super(message);
    }
}
