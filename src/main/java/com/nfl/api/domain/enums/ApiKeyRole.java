package com.nfl.api.domain.enums;

/**
 * Níveis de acesso de uma chave de API. Cada nível inclui as permissões dos níveis anteriores.
 * <ul>
 *   <li>READ_ONLY: apenas consultas (GET)</li>
 *   <li>READ_WRITE: consultas e escrita (POST, PUT, PATCH)</li>
 *   <li>ADMIN: tudo, inclusive DELETE e gerenciamento de chaves</li>
 * </ul>
 */
public enum ApiKeyRole {
    READ_ONLY,
    READ_WRITE,
    ADMIN;

    public boolean includes(ApiKeyRole required) {
        return this.ordinal() >= required.ordinal();
    }
}
