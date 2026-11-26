package com.connecta.backend.inbox.controller;

import com.connecta.backend.inbox.service.AssigneeScope;
import java.util.Locale;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/** Reads the {@code assignee} query value in lower case ({@code me}, {@code none}); anything else is a 400. */
@Component
class AssigneeScopeConverter implements Converter<String, AssigneeScope> {

    @Override
    public AssigneeScope convert(String source) {
        return AssigneeScope.valueOf(source.strip().toUpperCase(Locale.ROOT));
    }
}
