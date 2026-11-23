package com.connecta.backend.auth.controller;

import com.connecta.backend.auth.service.AccountNotAvailableException;
import com.connecta.backend.auth.service.EmailAlreadyUsedException;
import com.connecta.backend.auth.service.InvalidCredentialsException;
import com.connecta.backend.auth.service.InvalidTokenException;
import com.connecta.backend.auth.service.PasswordUnchangedException;
import com.connecta.backend.auth.service.SessionNotFoundException;
import com.connecta.backend.auth.service.SetupRequiredException;
import com.connecta.backend.auth.service.WrongPasswordException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = AuthController.class)
class AuthExceptionHandler {

    @ExceptionHandler({EmailAlreadyUsedException.class, SetupRequiredException.class})
    ProblemDetail conflict(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(InvalidTokenException.class)
    ProblemDetail invalidToken(InvalidTokenException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(AccountNotAvailableException.class)
    ProblemDetail accountNotAvailable(AccountNotAvailableException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ProblemDetail invalidCredentials(InvalidCredentialsException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    // 422, not 401: the request is well-formed and the login is valid, but the password rule is broken.
    @ExceptionHandler({WrongPasswordException.class, PasswordUnchangedException.class})
    ProblemDetail passwordRule(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    @ExceptionHandler(SessionNotFoundException.class)
    ProblemDetail sessionNotFound(SessionNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }
}
