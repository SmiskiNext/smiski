package io.github.smiskinext.usermanagement.application.command;

public record VerifyOtpCommand(String email, String otp) {}
