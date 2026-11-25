package com.connecta.backend.realtime.service;

/** A user asked for realtime tickets faster than allowed, or holds too many unredeemed ones. */
public class TooManyTicketsException extends RuntimeException {

    public TooManyTicketsException() {
        super("Too many realtime tickets requested, try again shortly");
    }
}
