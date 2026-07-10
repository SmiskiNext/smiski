package io.github.smiskinext.shared.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.smiskinext.shared.domain.DomainError;
import io.github.smiskinext.shared.domain.ErrorCategory;
import io.github.smiskinext.shared.domain.ErrorCode;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * Verifies that {@link ProblemDetailMapper} maps error categories to HTTP statuses and localizes
 * {@code title}/{@code detail} from the common message bundle according to the request locale.
 */
class ProblemDetailMapperTest {

    private final ProblemDetailMapper mapper =
            new ProblemDetailMapper(messageSource(), properties(""));

    private static ProblemProperties properties(String typeBaseUri) {
        ProblemProperties props = new ProblemProperties();
        props.setTypeBaseUri(typeBaseUri);
        return props;
    }

    private static ReloadableResourceBundleMessageSource messageSource() {
        ReloadableResourceBundleMessageSource source = new ReloadableResourceBundleMessageSource();
        source.setBasenames("classpath:messages/common", "classpath:messages/test-errors");
        source.setDefaultEncoding("UTF-8");
        source.setDefaultLocale(Locale.ENGLISH);
        source.setFallbackToSystemLocale(false);
        source.setUseCodeAsDefaultMessage(true);
        return source;
    }

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void mapsValidationCategoryToBadRequestWithEnglishText() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);

        ProblemDetail problem = mapper.forErrorCode(CommonErrorCode.VALIDATION_ERROR);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getTitle()).isEqualTo("Validation failed");
        assertThat(problem.getDetail()).isEqualTo("One or more fields are invalid.");
        assertThat(problem.getType().toString()).isEqualTo("about:blank");
        assertThat(problem.getProperties()).containsEntry("code", "VALIDATION_ERROR");
    }

    @Test
    void derivesTypeUriFromConfiguredBaseUri() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        ProblemDetailMapper configured =
                new ProblemDetailMapper(messageSource(), properties("https://errors.example.com"));

        ProblemDetail problem = configured.forErrorCode(CommonErrorCode.VALIDATION_ERROR);

        assertThat(problem.getType().toString())
                .isEqualTo("https://errors.example.com/validation-error");
    }

    @Test
    void localizesTitleAndDetailForVietnamese() {
        LocaleContextHolder.setLocale(Locale.forLanguageTag("vi"));

        ProblemDetail problem = mapper.forErrorCode(CommonErrorCode.INTERNAL_ERROR);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(problem.getTitle()).isEqualTo("Lỗi máy chủ nội bộ");
        assertThat(problem.getDetail())
                .isEqualTo("Đã xảy ra lỗi không mong muốn. Vui lòng thử lại sau.");
    }

    @Test
    void interpolatesDomainErrorArgumentsIntoDetail() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);

        ProblemDetail problem = mapper.forDomainError(new SampleNotFound("42"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getDetail()).isEqualTo("Sample 42 was not found.");
        assertThat(problem.getProperties()).containsEntry("code", "SAMPLE_NOT_FOUND");
    }

    @Test
    void attachesFieldViolationsWhenPresent() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        List<Violation> violations =
                List.of(new Violation("email", ViolationCode.INVALID_FORMAT, "bad email"));

        ProblemDetail problem =
                mapper.forErrorCode(CommonErrorCode.VALIDATION_ERROR, new Object[0], violations);

        assertThat(problem.getProperties()).containsEntry("errors", violations);
    }

    private enum SampleErrorCode implements ErrorCode {
        SAMPLE_NOT_FOUND;

        @Override
        public String code() {
            return name();
        }

        @Override
        public ErrorCategory category() {
            return ErrorCategory.NOT_FOUND;
        }
    }

    private record SampleNotFound(String id) implements DomainError {
        @Override
        public ErrorCode errorCode() {
            return SampleErrorCode.SAMPLE_NOT_FOUND;
        }

        @Override
        public Object[] messageArgs() {
            return new Object[] {id};
        }
    }
}
