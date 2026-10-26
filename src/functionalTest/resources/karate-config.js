function fn() {
  var authUrl = karate.properties['authUrl'] || 'http://localhost:8180';
  return {
    baseUrl: karate.properties['baseUrl'] || 'http://localhost:8080',
    tokenUrl: authUrl + '/realms/products/protocol/openid-connect/token',
    writer: { clientId: 'products-writer', clientSecret: 'products-writer-secret' },
    reader: { clientId: 'products-reader', clientSecret: 'products-reader-secret' },
    // RFC 9457 body every error must have; errors is present only for validation failures
    problem: { title: '#string', status: '#number', detail: '#string', instance: '#string', errors: '##array' }
  };
}
