@challenge
Feature: GET /products/{id}/prices?date= returns the price in force on a date

  Background:
    * url baseUrl
    * def product = call read('classpath:functional/support/create-product.feature')
    * def id = product.id
    * def addPrice = function(price) { return karate.call('classpath:functional/support/add-price.feature', { id: id, price: price }) }
    * match addPrice({ value: 99.99, initDate: '2024-01-01', endDate: '2024-06-30' }).status == 201
    * match addPrice({ value: 199.99, initDate: '2025-01-01', endDate: '2025-06-30' }).status == 201
    * match addPrice({ value: 299.99, initDate: '2026-01-01' }).status == 201

  @TC-030 @smoke
  Scenario: TC-030 The statement's example answers exactly {"value": 99.99}
    Given path 'products', id, 'prices'
    And param date = '2024-04-15'
    When method get
    Then status 200
    And match response == { value: 99.99 }

  @TC-031
  Scenario Outline: TC-031 Both ends of a period are included: <date> gives <value>
    Given path 'products', id, 'prices'
    And param date = '<date>'
    When method get
    Then status 200
    And match response == { value: <value> }

    Examples:
      | date       | value  |
      | 2024-01-01 | 99.99  |
      | 2024-06-30 | 99.99  |
      | 2025-01-01 | 199.99 |
      | 2025-06-30 | 199.99 |
      | 2026-01-01 | 299.99 |

  @TC-032
  Scenario Outline: TC-032 No price in force <case> answers 404
    Given path 'products', id, 'prices'
    And param date = '<date>'
    When method get
    Then status 404
    And match header Content-Type contains 'application/problem+json'
    And match response == problem

    Examples:
      | case                      | date       |
      | before the first price    | 2023-12-31 |
      | the day after a period    | 2024-07-01 |
      | in the gap between prices | 2024-10-15 |
      | the day before a period   | 2024-12-31 |

  @TC-033
  Scenario: TC-033 An open-ended price is in force up to the last representable day
    Given path 'products', id, 'prices'
    And param date = '9999-12-31'
    When method get
    Then status 200
    And match response == { value: 299.99 }

  @TC-035
  Scenario Outline: TC-035 Reject the date <date>
    Given path 'products', id, 'prices'
    And param date = '<date>'
    When method get
    Then status 400
    And match response == problem
    And match response.errors[0].field == 'date'

    Examples:
      | date       |
      | 2024-13-01 |
      | 2024-02-30 |
      | 15-04-2024 |
      | yesterday  |

  @TC-036
  Scenario: TC-036 Unknown product answers 404
    Given path 'products', '000000000000000000000000', 'prices'
    And param date = '2024-04-15'
    When method get
    Then status 404
    And match response == problem

  @TC-037
  Scenario: TC-037 A price is visible right after it is added, even when the product was just read
    Given path 'products', id, 'prices'
    And param date = '2027-01-01'
    When method get
    Then status 200
    And match response == { value: 299.99 }

    * def other = call read('classpath:functional/support/create-product.feature')
    Given path 'products', other.id, 'prices'
    And param date = '2024-04-15'
    When method get
    Then status 404
    * match karate.call('classpath:functional/support/add-price.feature', { id: other.id, price: { value: 59.99, initDate: '2024-01-01' } }).status == 201
    Given path 'products', other.id, 'prices'
    And param date = '2024-04-15'
    When method get
    Then status 200
    And match response == { value: 59.99 }
