package io.github.smiskinext.usermanagement.application.command;

public record RegisterCommand(String email, String password, String fullName, String username) {}
