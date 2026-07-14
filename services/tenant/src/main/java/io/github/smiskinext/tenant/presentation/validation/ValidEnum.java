package io.github.smiskinext.tenant.presentation.validation;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Validates that a string value is either null/blank (allowing optional-default semantics) or
 * matches one of the target enum's constant names in a case-insensitive manner.
 */
@Documented
@Constraint(validatedBy = ValidEnumValidator.class)
@Target({FIELD, PARAMETER})
@Retention(RUNTIME)
public @interface ValidEnum {

    @SuppressWarnings("rawtypes")
    Class<? extends Enum> value();

    String message() default "must be one of {value}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
