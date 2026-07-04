package io.github.smiskinext.usermanagement.application.command;

import java.util.Map;

/** Command for fully replacing a user's preferences document. */
public record PutPreferencesCommand(Map<String, Object> settings) {}
