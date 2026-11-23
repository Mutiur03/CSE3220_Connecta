package com.connecta.backend.auth.service;

import java.util.Optional;

/** Turns a client IP into a human place name such as "Dhaka, Bangladesh". Empty when it cannot tell. */
public interface IpLocator {

    Optional<String> locate(String ip);
}
