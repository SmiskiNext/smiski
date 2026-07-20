package io.github.smiskinext.meet.presentation.validation;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Validates that the annotated string is a valid IANA region-based time-zone id
 * present in {@link java.time.ZoneId#getAvailableZoneIds()}.
 *
 * <p>Null and blank values are not validated by this constraint (use {@code @NotBlank} separately).
 */
@Documented
@Constraint(validatedBy = IanaZoneIdValidator.class)
@Target({FIELD, PARAMETER})
@Retention(RUNTIME)
public @interface IanaZoneId {

    String message() default "must be a valid IANA time zone id";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
