package io.github.smiskinext.tenant.presentation.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Accepts null, blank, or a case-insensitive match against the target enum's constant names.
 * Unknown values are rejected so they surface as {@code 400 VALIDATION_ERROR} rather than
 * propagating an {@link IllegalArgumentException} deeper in the call stack.
 */
public class ValidEnumValidator implements ConstraintValidator<ValidEnum, String> {

    private Set<String> acceptedValues;

    @Override
    public void initialize(ValidEnum annotation) {
        acceptedValues = Stream.of(annotation.value().getEnumConstants())
                .map(e -> e.name().toUpperCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        return acceptedValues.contains(value.toUpperCase(Locale.ROOT));
    }
}
