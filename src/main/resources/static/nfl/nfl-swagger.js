/*
 * Swagger UI: o header X-Idempotency-Key vem preenchido com "auto". Aqui cada envio troca esse valor por um
 * UUID novo, para o usuário não precisar inventar uma chave a cada POST. Qualquer outro valor digitado
 * é enviado como está (útil para testar a idempotência repetindo a mesma chave).
 */
(function () {
  var HEADER = 'x-idempotency-key';
  var originalFetch = window.fetch;

  function newKey() {
    return (window.crypto && crypto.randomUUID)
      ? crypto.randomUUID()
      : 'key-' + Date.now() + '-' + Math.random().toString(16).slice(2);
  }

  function replaceInHeaders(headers) {
    if (!headers) return headers;
    if (typeof Headers !== 'undefined' && headers instanceof Headers) {
      if ((headers.get(HEADER) || '').trim().toLowerCase() === 'auto') headers.set(HEADER, newKey());
      return headers;
    }
    Object.keys(headers).forEach(function (name) {
      if (name.toLowerCase() === HEADER && String(headers[name]).trim().toLowerCase() === 'auto') {
        headers[name] = newKey();
      }
    });
    return headers;
  }

  window.fetch = function (input, init) {
    try {
      if (input && typeof Request !== 'undefined' && input instanceof Request) {
        replaceInHeaders(input.headers);
      }
      if (init && init.headers) {
        init.headers = replaceInHeaders(init.headers);
      }
    } catch (e) { /* nunca impede a requisição */ }
    return originalFetch.call(this, input, init);
  };
})();
