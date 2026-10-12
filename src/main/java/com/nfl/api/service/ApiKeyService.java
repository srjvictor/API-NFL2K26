package com.nfl.api.service;

import com.nfl.api.domain.ApiKey;
import com.nfl.api.domain.enums.ApiKeyRole;
import com.nfl.api.dto.ApiKeyDtos.ApiKeyCreatedResponse;
import com.nfl.api.dto.ApiKeyDtos.ApiKeyResponse;
import com.nfl.api.exception.BusinessRuleException;
import com.nfl.api.exception.ResourceNotFoundException;
import com.nfl.api.repository.ApiKeyRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Service
@Transactional
public class ApiKeyService {

    private static final String PREFIX = "nfl_";
    private final SecureRandom random = new SecureRandom();
    private final ApiKeyRepository repository;

    public ApiKeyService(ApiKeyRepository repository) {
        this.repository = repository;
    }

    /** Gera uma nova chave aleatória; o valor em texto puro só é devolvido aqui. */
    public ApiKeyCreatedResponse generate(String owner, String email, ApiKeyRole role) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String rawKey = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        ApiKey key = store(rawKey, owner, email, role);
        return new ApiKeyCreatedResponse(key.getId(), key.getOwner(), key.getRole(), rawKey,
                "Guarde esta chave: ela não será exibida novamente. Envie-a no header X-API-Key.");
    }

    /** Registra uma chave com valor conhecido (usado para a chave ADMIN inicial). */
    public void registerIfAbsent(String rawKey, String owner, ApiKeyRole role) {
        if (!repository.existsByKeyHash(sha256(rawKey))) {
            store(rawKey, owner, null, role);
        }
    }

    /** Valida a chave recebida no header e atualiza a data de último uso. */
    public Optional<ApiKey> authenticate(String rawKey) {
        Optional<ApiKey> key = repository.findByKeyHash(sha256(rawKey)).filter(ApiKey::isActive);
        key.ifPresent(k -> k.setLastUsedAt(Instant.now()));
        return key;
    }

    @Transactional(readOnly = true)
    public Page<ApiKeyResponse> findAll(String owner, Pageable pageable) {
        return repository.findByOwnerContainingIgnoreCase(owner == null ? "" : owner, pageable)
                .map(ApiKeyResponse::from);
    }

    @Transactional(readOnly = true)
    public ApiKeyResponse findById(Long id) {
        return ApiKeyResponse.from(get(id));
    }

    public ApiKeyResponse revoke(Long id, Long currentKeyId) {
        if (id.equals(currentKeyId)) {
            throw new BusinessRuleException("Você não pode revogar a própria chave em uso");
        }
        ApiKey key = get(id);
        key.setActive(false);
        return ApiKeyResponse.from(key);
    }

    public void delete(Long id, Long currentKeyId) {
        if (id.equals(currentKeyId)) {
            throw new BusinessRuleException("Você não pode excluir a própria chave em uso");
        }
        repository.delete(get(id));
    }

    private ApiKey get(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Chave de API", id));
    }

    private ApiKey store(String rawKey, String owner, String email, ApiKeyRole role) {
        ApiKey key = new ApiKey();
        key.setKeyHash(sha256(rawKey));
        key.setKeyPrefix(rawKey.substring(0, Math.min(12, rawKey.length())));
        key.setOwner(owner);
        key.setEmail(email);
        key.setRole(role);
        return repository.save(key);
    }

    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
