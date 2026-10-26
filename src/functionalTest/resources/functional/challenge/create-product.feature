@challenge
Feature: POST /products creates a product

  Background:
    * url baseUrl
    * def text = function(length) { var s = ''; for (var i = 0; i < length; i++) s += 'a'; return s }

  @TC-001 @smoke
  Scenario: TC-001 Create a product with the statement's body
    Given path 'products'
    And request { name: 'Zapatillas deportivas', description: 'Modelo 2025 edición limitada' }
    When method post
    Then status 201
    And match header Content-Type contains 'application/json'
    And match response == { id: '#regex [0-9a-f]{24}', name: 'Zapatillas deportivas', description: 'Modelo 2025 edición limitada' }
    * def location = karate.response.header('Location')
    * assert location.endsWith('/products/' + response.id)

  @TC-002
  Scenario: TC-002 The description is optional
    Given path 'products'
    And request { name: 'Camiseta básica' }
    When method post
    Then status 201
    And match response == { id: '#string', name: 'Camiseta básica', description: null }

  @TC-003
  Scenario Outline: TC-003 Reject a product whose name is <case>
    Given path 'products'
    And request <body>
    When method post
    Then status 400
    And match header Content-Type contains 'application/problem+json'
    And match response == problem
    And match response.errors[0] == { field: 'name', message: '#string' }

    Examples:
      | case    | body                              |
      | missing | { description: 'Without a name' } |
      | empty   | { name: '' }                      |
      | blank   | { name: '   ' }                   |
      | null    | { name: null }                    |

  @TC-004
  Scenario Outline: TC-004 Text limits: <field> of <length> characters gives <status>
    * def body = { name: 'Limits' }
    * body[field] = text(length)
    Given path 'products'
    And request body
    When method post
    Then status <status>

    Examples:
      | field       | length | status |
      | name        | 255    | 201    |
      | name        | 256    | 400    |
      | description | 2000   | 201    |
      | description | 2001   | 400    |

  @TC-005
  Scenario: TC-005 Reject an empty body
    Given path 'products'
    And header Content-Type = 'application/json'
    And request ''
    When method post
    Then status 400
    And match response == problem
    And match response.errors[0].field == 'body'

  @TC-006
  Scenario: TC-006 Reject malformed JSON
    Given path 'products'
    And header Content-Type = 'application/json'
    And request '{"name":'
    When method post
    Then status 400
    And match header Content-Type contains 'application/problem+json'
    And match response == problem
    And match response.detail == 'Malformed request body'

  @TC-007
  Scenario: TC-007 Reject a body that is not JSON
    Given path 'products'
    And header Content-Type = 'text/plain'
    And request 'Zapatillas deportivas'
    When method post
    Then status 415
    And match response == problem

  @TC-008
  Scenario: TC-008 Unknown fields are ignored
    Given path 'products'
    And request { name: 'Gorra', colour: 'red' }
    When method post
    Then status 201
    And match response == { id: '#string', name: 'Gorra', description: null }

  @TC-009
  Scenario: TC-009 The Location header leads to the new product
    Given path 'products'
    And request { name: 'Sudadera' }
    When method post
    Then status 201
    * def location = karate.response.header('Location')
    Given url location
    And path 'prices'
    When method get
    Then status 200
    And match response == { name: 'Sudadera', description: null, prices: [] }
