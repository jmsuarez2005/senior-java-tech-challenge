@platform
Feature: Health, documentation and tracing

  Background:
    * url baseUrl

  @TC-090 @smoke
  Scenario: TC-090 The health check reports UP with MongoDB
    Given path 'actuator', 'health'
    When method get
    Then status 200
    And match response.status == 'UP'

  @TC-091
  Scenario: TC-091 The caller's request id is echoed, and one is assigned when missing
    Given path 'products'
    And header X-Request-Id = 'qa-trace-0001'
    And request { name: 'Traced' }
    When method post
    Then status 201
    And match karate.response.header('X-Request-Id') == 'qa-trace-0001'
    Given path 'products'
    And request { name: 'Untraced' }
    When method post
    Then status 201
    And match karate.response.header('X-Request-Id') == '#string'

  @TC-092
  Scenario: TC-092 Swagger UI is served and the document lists the statement endpoints first
    Given path 'swagger-ui/'
    When method get
    Then status 200
    Given path 'q', 'openapi'
    And param format = 'json'
    When method get
    Then status 200
    And match response.tags[*].name == ['Challenge endpoints', 'Products']
    And match response.paths contains { '/products': '#object', '/products/{id}/prices': '#object', '/v1/products/{id}/price': '#object' }
    And match response.components.securitySchemes.oauth2.flows.clientCredentials.scopes contains { 'products:write': '#string' }
