@v1 @bonus
Feature: Prices in several currencies, one timeline per currency

  Background:
    * url baseUrl
    * def product = call read('classpath:functional/support/create-product.feature')
    * def id = product.id

  @TC-060
  Scenario: TC-060 The same period can have a price in EUR and in USD; EUR is the default
    Given path 'v1', 'products', id, 'prices'
    And request { value: 99.99, initDate: '2024-01-01', endDate: '2024-06-30' }
    When method post
    Then status 201
    Given path 'v1', 'products', id, 'prices'
    And request { value: 109.99, initDate: '2024-01-01', endDate: '2024-06-30', currency: 'USD' }
    When method post
    Then status 201
    Given path 'v1', 'products', id, 'prices'
    And request { value: 119.99, initDate: '2024-03-01', endDate: '2024-03-31', currency: 'USD' }
    When method post
    Then status 409
    Given path 'v1', 'products', id, 'price'
    And params { date: '2024-04-15', currency: 'USD' }
    When method get
    Then match response == { value: 109.99 }
    Given path 'v1', 'products', id, 'price'
    And param date = '2024-04-15'
    When method get
    Then match response == { value: 99.99 }

  @TC-061
  Scenario Outline: TC-061 Decimals follow the currency: <value> <currency> gives <status>
    Given path 'v1', 'products', id, 'prices'
    And request { value: <value>, initDate: '2024-01-01', currency: '<currency>' }
    When method post
    Then status <status>

    Examples:
      | value  | currency | status |
      | 100    | JPY      | 201    |
      | 100.5  | JPY      | 400    |
      | 1.234  | KWD      | 201    |
      | 1.2345 | KWD      | 400    |
      | 10.25  | USD      | 201    |
      | 10.255 | USD      | 400    |

  @TC-062
  Scenario: TC-062 An unknown currency is rejected in the body and in the query
    Given path 'v1', 'products', id, 'prices'
    And request { value: 10, initDate: '2024-01-01', currency: 'XXZ' }
    When method post
    Then status 400
    And match response.errors[0].field == 'currency'
    Given path 'v1', 'products', id, 'prices'
    And params { date: '2024-01-01', currency: 'ZZZ' }
    When method get
    Then status 400
    And match response.errors[0] == { field: 'currency', message: 'must be an ISO 4217 code' }

  @TC-063
  Scenario: TC-063 The history is per currency
    Given path 'v1', 'products', id, 'prices'
    And request { value: 99.99, initDate: '2024-01-01' }
    When method post
    Then status 201
    Given path 'v1', 'products', id, 'prices'
    And request { value: 109.99, initDate: '2024-01-01', currency: 'USD' }
    When method post
    Then status 201
    Given path 'v1', 'products', id, 'prices'
    And param currency = 'USD'
    When method get
    Then status 200
    And match response.prices == [{ value: 109.99, initDate: '2024-01-01', endDate: null }]
