package io.github.smiskinext.meet.infrastructure.shortcode;

import io.github.smiskinext.meet.domain.model.valueobject.ShortCode;
import io.github.smiskinext.meet.domain.port.ShortCodeGenerationSettings;
import io.github.smiskinext.meet.domain.port.ShortCodeGenerator;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * Generates join codes from a configurable alphabet and length using a cryptographically secure
 * random source.
 *
 * <p>Codes are drawn uniformly from {@link ShortCodeGenerationSettings#alphabet()} at {@link
 * ShortCodeGenerationSettings#length()} characters, yielding {@code alphabet^length} distinct
 * values. A single shared {@link SecureRandom} is used; instances are thread-safe.
 */
@Component
public class RandomShortCodeGenerator implements ShortCodeGenerator {

    private final SecureRandom random = new SecureRandom();
    private final String alphabet;
    private final int length;

    public RandomShortCodeGenerator(ShortCodeGenerationSettings settings) {
        this.alphabet = settings.alphabet();
        this.length = settings.length();
    }

    @Override
    public ShortCode generate() {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return ShortCode.of(sb.toString());
    }
}
