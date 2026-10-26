@challenge
Feature: POST /products/{id}/prices adds a price to a product

  Background:
    * url baseUrl
    * def product = call read('classpath:functional/support/create-product.feature')
    * def id = product.id
    * def addPrice = function(price) { return karate.call('classpath:functional/support/add-price.feature', { id: id, price: price }) }
    * def history = function() { return karate.call('classpath:functional/support/history.feature', { id: id }).response }

  @TC-010 @smoke
  Scenario: TC-010 Add the statement's price
    Given path 'products', id, 'prices'
    And request { value: 99.99, initDate: '2024-01-01', endDate: '2024-06-30' }
    When method post
    Then status 201
    And match response == { value: 99.99, initDate: '2024-01-01', endDate: '2024-06-30' }

  @TC-011
  Scenario Outline: TC-011 An open-ended price is accepted when endDate is <case>
    Given path 'products', id, 'prices'
    And request <body>
    When method post
    Then status 201
    And match response == { value: 49.95, initDate: '2024-01-01', endDate: null }

    Examples:
      | case   | body                                                  |
      | null   | { value: 49.95, initDate: '2024-01-01', endDate: null } |
      | absent | { value: 49.95, initDate: '2024-01-01' }              |

  @TC-012
  Scenario Outline: TC-012 Reject a period whose endDate is <case> initDate
    Given path 'products', id, 'prices'
    And request { value: 10, initDate: '2024-06-30', endDate: '<endDate>' }
    When method post
    Then status 400
    And match response == problem
    And match response.errors[0].field == 'endDate'

    Examples:
      | case      | endDate    |
      | equal to  | 2024-06-30 |
      | before    | 2024-06-29 |
      | long before | 2023-01-01 |

  @TC-013
  Scenario Outline: TC-013 Reject a price that overlaps [2024-01-01, 2024-06-30]: <case>
    * match addPrice({ value: 99.99, initDate: '2024-01-01', endDate: '2024-06-30' }).status == 201
    * def rejected = addPrice({ value: 10, initDate: '<initDate>', endDate: <endDate> })
    * match rejected.status == 409
    * match rejected.body == problem
    * match history().prices == [{ value: 99.99, initDate: '2024-01-01', endDate: '2024-06-30' }]

    Examples:
      | case                         | initDate   | endDate      |
      | same period                  | 2024-01-01 | '2024-06-30' |
      | starts before, ends inside   | 2023-12-01 | '2024-01-15' |
      | starts inside, ends after    | 2024-06-01 | '2024-12-31' |
      | contains it                  | 2023-01-01 | '2025-01-01' |
      | inside it                    | 2024-03-01 | '2024-03-31' |
      | starts on its last day       | 2024-06-30 | '2024-07-31' |
      | ends on its first day        | 2023-12-01 | '2024-01-01' |
      | open-ended, starts inside    | 2024-05-01 | null         |
      | open-ended, starts before    | 2020-01-01 | null         |

  @TC-014
  Scenario: TC-014 Adjacent periods do not overlap
    * match addPrice({ value: 99.99, initDate: '2024-01-01', endDate: '2024-06-30' }).status == 201
    * match addPrice({ value: 109.99, initDate: '2024-07-01', endDate: '2024-12-31' }).status == 201
    * match addPrice({ value: 89.99, initDate: '2023-12-01', endDate: '2023-12-31' }).status == 201
    * def prices = history().prices
    * match prices[*].initDate == ['2023-12-01', '2024-01-01', '2024-07-01']

  @TC-015
  Scenario: TC-015 Nothing can start after an open-ended price, but it can be closed before it
    * match addPrice({ value: 99.99, initDate: '2024-01-01' }).status == 201
    * match addPrice({ value: 89.99, initDate: '2030-01-01', endDate: '2030-12-31' }).status == 409
    * match addPrice({ value: 79.99, initDate: '2031-01-01' }).status == 409
    * match addPrice({ value: 69.99, initDate: '2023-01-01', endDate: '2023-12-31' }).status == 201

  @TC-016
  Scenario Outline: TC-016 Reject a value that is <case>
    Given path 'products', id, 'prices'
    And header Content-Type = 'application/json'
    And request '{"value": <value>, "initDate": "2024-01-01"}'
    When method post
    Then status 400
    And match header Content-Type contains 'application/problem+json'
    And match response == problem
    And match response.errors[0] == { field: 'value', message: '<message>' }

    Examples:
      | case                     | value      | message                                       |
      | zero                     | 0          | must be greater than 0                        |
      | negative                 | -5         | must be greater than 0                        |
      | three decimals in EUR    | 9.999      | must have at most 2 decimals for EUR          |
      | larger than 18 digits    | 1e10000000 | must have at most 18 digits in EUR minor units |
      | text                     | "abc"      | has an invalid value or format                |
      | missing                  | null       | must not be null                              |

  @TC-017
  Scenario Outline: TC-017 Reject a price whose <field> is <case>
    Given path 'products', id, 'prices'
    And header Content-Type = 'application/json'
    And request '<body>'
    When method post
    Then status 400
    And match response == problem
    And match response.errors[*].field contains '<field>'

    Examples:
      | case                  | field     | body                                                        |
      | missing               | initDate  | {"value": 10}                                               |
      | in another format     | initDate  | {"value": 10, "initDate": "01/01/2024"}                     |
      | not a calendar day    | initDate  | {"value": 10, "initDate": "2024-02-30"}                     |
      | in another format     | endDate   | {"value": 10, "initDate": "2024-01-01", "endDate": "30-06-2024"} |

  @TC-018
  Scenario Outline: TC-018 Adding a price to <case> answers 404
    Given path 'products', '<productId>', 'prices'
    And request { value: 10, initDate: '2024-01-01' }
    When method post
    Then status 404
    And match response == problem

    Examples:
      | case                         | productId                |
      | an unknown product           | 000000000000000000000000 |
      | an id that is not an ObjectId | not-an-id               |

  @TC-019
  Scenario: TC-019 Reject an empty body
    Given path 'products', id, 'prices'
    And header Content-Type = 'application/json'
    And request ''
    When method post
    Then status 400
    And match response.errors[0].field == 'body'

  @TC-020
  Scenario: TC-020 A rejected price leaves no trace
    * match addPrice({ value: 9.999, initDate: '2024-06-30', endDate: '2024-01-01' }).status == 400
    * match history().prices == []

  @TC-021
  Scenario Outline: TC-021 The value <value> is stored exactly
    * match addPrice({ value: <value>, initDate: '2024-01-01' }).status == 201
    * match history().prices[0].value == <value>

    Examples:
      | value      |
      | 0.01       |
      | 99.99      |
      | 1234567.89 |
      | 100        |
