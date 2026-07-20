package io.github.smiskinext.meet.presentation.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.ZoneId;
import java.util.Set;

/**
 * Accepts null, blank, or a string that is present in {@link ZoneId#getAvailableZoneIds()}.
 * Unknown zone ids and bare UTC offsets are rejected.
 */
public class IanaZoneIdValidator implements ConstraintValidator<IanaZoneId, String> {

    private static final Set<String> AVAILABLE_ZONE_IDS = ZoneId.getAvailableZoneIds();

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        return AVAILABLE_ZONE_IDS.contains(value);
    }
}
