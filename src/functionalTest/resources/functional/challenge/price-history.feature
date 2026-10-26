@challenge
Feature: GET /products/{id}/prices returns the full price history

  Background:
    * url baseUrl
    * def product = call read('classpath:functional/support/create-product.feature') { name: 'Zapatillas deportivas' }
    * def id = product.id
    * def addPrice = function(price) { return karate.call('classpath:functional/support/add-price.feature', { id: id, price: price }) }

  @TC-040 @smoke
  Scenario: TC-040 The history has the statement's shape, ordered by initDate whatever the insertion order
    * match addPrice({ value: 199.99, initDate: '2025-01-01', endDate: '2025-06-30' }).status == 201
    * match addPrice({ value: 99.99, initDate: '2024-01-01', endDate: '2024-06-30' }).status == 201
    Given path 'products', id, 'prices'
    When method get
    Then status 200
    And match response ==
      """
      {
        name: 'Zapatillas deportivas',
        description: 'Created by the functional suite',
        prices: [
          { value: 99.99, initDate: '2024-01-01', endDate: '2024-06-30' },
          { value: 199.99, initDate: '2025-01-01', endDate: '2025-06-30' }
        ]
      }
      """
    * assert karate.response.header('X-Total-Count') == null

  @TC-041
  Scenario: TC-041 A product without prices has an empty history
    Given path 'products', id, 'prices'
    When method get
    Then status 200
    And match response.prices == []

  @TC-042
  Scenario: TC-042 An open end is returned as null, not left out
    * match addPrice({ value: 99.99, initDate: '2024-01-01' }).status == 201
    Given path 'products', id, 'prices'
    When method get
    Then status 200
    And match response.prices[0] == { value: 99.99, initDate: '2024-01-01', endDate: null }
    And match response.prices[0] contains { endDate: '#present' }

  @TC-043
  Scenario: TC-043 Unknown product answers 404
    Given path 'products', '000000000000000000000000', 'prices'
    When method get
    Then status 404
    And match response == problem

  @TC-044
  Scenario: TC-044 The history shows a new price right after it is added
    * match addPrice({ value: 99.99, initDate: '2024-01-01', endDate: '2024-06-30' }).status == 201
    Given path 'products', id, 'prices'
    When method get
    Then match response.prices == '#[1]'
    * match addPrice({ value: 149.99, initDate: '2024-07-01', endDate: '2024-12-31' }).status == 201
    Given path 'products', id, 'prices'
    When method get
    Then match response.prices[*].value == [99.99, 149.99]
