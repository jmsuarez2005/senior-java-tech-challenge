@catalog
Feature: The dummy catalogue loaded at startup, read through the API

  The products in seed/catalog.json are loaded on every docker compose up with fixed ids. These cases only read
  them; cases that write create their own products.

  Background:
    * url baseUrl
    * def catalog = read('classpath:catalog.json')
    * def seeded = function(key) { return karate.filter(catalog.products, function(p) { return p.key == key })[0] }
    * def currenciesOf = function(product) { var all = karate.map(product.prices, function(p) { return p.currency }); return karate.distinct(all.length ? all : ['EUR']) }
    * def expectedIn = function(product, currency) { return karate.map(karate.filter(product.prices, function(p) { return p.currency == currency }), function(p) { return { value: p.value, initDate: p.initDate, endDate: p.endDate } }) }

  @TC-100
  Scenario Outline: TC-100 The seeded product <key> reads back exactly as in the catalogue
    * def product = seeded('<key>')
    Given path 'v1', 'products', product.id
    When method get
    Then status 200
    And match response == { id: '#(product.id)', name: '#(product.name)', description: '#(product.description)' }
    * def currencies = currenciesOf(product)
    * def histories = karate.map(currencies, function(c) { return karate.call('classpath:functional/support/prices-in-currency.feature', { id: product.id, currency: c }).response.prices })
    * def expected = karate.map(currencies, function(c) { return expectedIn(product, c) })
    * match histories == expected

    Examples:
      | key                |
      | statement-sneakers |
      | linen-shirt        |
      | wool-coat          |
      | leather-bag        |
      | slim-jeans         |
      | knit-hat           |
      | cotton-tee         |
      | suit-blazer        |

  @TC-101 @smoke
  Scenario: TC-101 The statement's example, on seeded data, through the statement paths
    * def product = seeded('statement-sneakers')
    Given path 'products', product.id, 'prices'
    And param date = '2024-04-15'
    When method get
    Then status 200
    And match response == { value: 99.99 }
    Given path 'products', product.id, 'prices'
    When method get
    Then status 200
    And match response ==
      """
      {
        "name": "Zapatillas deportivas",
        "description": "Modelo 2025 edición limitada",
        "prices": [
          { "value": 99.99, "initDate": "2024-01-01", "endDate": "2024-06-30" },
          { "value": 199.99, "initDate": "2025-01-01", "endDate": "2025-06-30" }
        ]
      }
      """

  @TC-102
  Scenario Outline: TC-102 <key> on <date> in <currency> costs <value>
    Given path 'v1', 'products', seeded('<key>').id, 'price'
    And params { date: '<date>', currency: '<currency>' }
    When method get
    Then status 200
    And match response == { value: <value> }

    Examples:
      | key         | date       | currency | value  |
      | linen-shirt | 2024-06-30 | EUR      | 39.99  |
      | linen-shirt | 2024-07-01 | EUR      | 29.99  |
      | linen-shirt | 2024-07-31 | EUR      | 29.99  |
      | linen-shirt | 2024-08-01 | EUR      | 39.99  |
      | linen-shirt | 2030-01-01 | EUR      | 39.99  |
      | wool-coat   | 2025-02-28 | USD      | 169.99 |
      | wool-coat   | 2025-03-01 | EUR      | 119.99 |
      | wool-coat   | 2026-06-01 | GBP      | 129.99 |
      | leather-bag | 2024-05-01 | JPY      | 14900  |
      | leather-bag | 2024-05-01 | KWD      | 32.5   |
      | leather-bag | 2024-05-01 | EUR      | 89.99  |
      | cotton-tee  | 2023-12-31 | EUR      | 12.99  |
      | cotton-tee  | 2025-01-01 | EUR      | 15.99  |
      | suit-blazer | 2024-02-29 | EUR      | 129.99 |
      | suit-blazer | 2024-03-01 | EUR      | 99.99  |

  @TC-103
  Scenario Outline: TC-103 <key> has no price on <date> in <currency>
    Given path 'v1', 'products', seeded('<key>').id, 'price'
    And params { date: '<date>', currency: '<currency>' }
    When method get
    Then status 404
    And match response == problem

    Examples:
      | key         | date       | currency | reason                       |
      | linen-shirt | 2024-02-29 | EUR      | before its first price       |
      | wool-coat   | 2025-03-01 | USD      | the USD price ended the day before |
      | leather-bag | 2023-12-31 | JPY      | before its first price       |
      | cotton-tee  | 2024-06-15 | EUR      | the year without price       |
      | knit-hat    | 2024-06-15 | EUR      | it has no prices             |

  @TC-104
  Scenario: TC-104 Paging, sorting and filtering over the twelve monthly prices
    * def id = seeded('slim-jeans').id
    Given path 'v1', 'products', id, 'prices'
    And params { page: 2, size: 5 }
    When method get
    Then status 200
    And match response.prices[*].initDate == ['2024-11-01', '2024-12-01']
    And match karate.response.header('X-Total-Count') == '12'
    Given path 'v1', 'products', id, 'prices'
    And params { sort: 'initDate,desc', size: 3 }
    When method get
    Then match response.prices[*].initDate == ['2024-12-01', '2024-11-01', '2024-10-01']
    Given path 'v1', 'products', id, 'prices'
    And params { from: '2024-06-15', to: '2024-08-15' }
    When method get
    Then match response.prices[*].value == [39.99, 35.99, 39.99]
