package io.github.smiskinext.tenant.presentation;

import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.shared.infrastructure.tenancy.TenantContext;
import io.github.smiskinext.shared.infrastructure.web.ResultResponder;
import io.github.smiskinext.tenant.application.command.RegisterTenantCommand;
import io.github.smiskinext.tenant.application.response.TenantResponse;
import io.github.smiskinext.tenant.application.usecase.RegisterTenantUseCase;
import io.github.smiskinext.tenant.domain.TenantError;
import io.github.smiskinext.tenant.presentation.request.RegisterTenantRequest;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
public class TenantController {

    private final RegisterTenantUseCase registerTenantUseCase;
    private final ResultResponder responder;

    public TenantController(
            RegisterTenantUseCase registerTenantUseCase, ResultResponder responder) {
        this.registerTenantUseCase = registerTenantUseCase;
        this.responder = responder;
    }

    @PostMapping("/tenants")
    public ResponseEntity<Object> register(@Valid @RequestBody RegisterTenantRequest request) {
        String cloudId = TenantContext.getCurrentTenant();
        RegisterTenantCommand command = request.toCommand(cloudId);
        Result<TenantResponse, TenantError> result = registerTenantUseCase.execute(command);

        return result.fold(
                response -> {
                    if (response.created()) {
                        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                                .path("/{id}")
                                .buildAndExpand(response.tenantId())
                                .toUri();
                        return ResponseEntity.created(location).body((Object) response);
                    }
                    return ResponseEntity.ok().body((Object) response);
                },
                error -> responder.ok(Result.<TenantResponse, TenantError>failure(error)));
    }
}
