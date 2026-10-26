@v1
Feature: Versioned product endpoints under /v1

  Background:
    * url baseUrl

  @TC-050
  Scenario: TC-050 Create under /v1 and follow the Location to the product
    Given path 'v1', 'products'
    And request { name: 'Zapatillas deportivas', description: 'Modelo 2025 edición limitada' }
    When method post
    Then status 201
    * def created = response
    * def location = karate.response.header('Location')
    * assert location.endsWith('/v1/products/' + created.id)
    Given url location
    When method get
    Then status 200
    And match response == created

  @TC-051
  Scenario: TC-051 GET of an unknown product answers 404
    Given path 'v1', 'products', '000000000000000000000000'
    When method get
    Then status 404
    And match response == problem

  @TC-052
  Scenario: TC-052 The singular /price is the preferred form of the price in force
    * def product = call read('classpath:functional/support/create-product.feature')
    * match karate.call('classpath:functional/support/add-price.feature', { id: product.id, price: { value: 99.99, initDate: '2024-01-01', endDate: '2024-06-30' } }).status == 201
    Given path 'v1', 'products', product.id, 'price'
    And param date = '2024-04-15'
    When method get
    Then status 200
    And match response == { value: 99.99 }
    Given path 'v1', 'products', product.id, 'price'
    When method get
    Then status 400
    And match response.errors[0] == { field: 'date', message: 'date is required' }

  @TC-053
  Scenario: TC-053 The statement paths and /v1 share the same data
    * def product = call read('classpath:functional/support/create-product.feature') { name: 'Shared' }
    Given path 'v1', 'products', product.id, 'prices'
    And request { value: 19.99, initDate: '2024-01-01' }
    When method post
    Then status 201
    Given path 'products', product.id, 'prices'
    When method get
    Then status 200
    And match response == { name: 'Shared', description: '#string', prices: [{ value: 19.99, initDate: '2024-01-01', endDate: null }] }
