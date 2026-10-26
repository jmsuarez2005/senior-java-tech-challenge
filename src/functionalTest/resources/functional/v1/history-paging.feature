@v1 @bonus
Feature: Filtering, sorting and paging of the price history

  Background:
    * url baseUrl
    * def product = call read('classpath:functional/support/create-product.feature')
    * def id = product.id
    * def addPrice = function(price) { return karate.call('classpath:functional/support/add-price.feature', { id: id, price: price }) }
    * match addPrice({ value: 99.99, initDate: '2024-01-01', endDate: '2024-06-30' }).status == 201
    * match addPrice({ value: 199.99, initDate: '2025-01-01', endDate: '2025-06-30' }).status == 201
    * match addPrice({ value: 299.99, initDate: '2026-01-01' }).status == 201

  @TC-070
  Scenario: TC-070 The first page has a next link and the total
    Given path 'v1', 'products', id, 'prices'
    And params { page: 0, size: 2 }
    When method get
    Then status 200
    And match response.prices[*].initDate == ['2024-01-01', '2025-01-01']
    And match karate.response.header('X-Total-Count') == '3'
    * def link = karate.response.header('Link')
    * match link contains 'rel="next"'
    * match link !contains 'rel="prev"'

  @TC-071
  Scenario: TC-071 The last page has a prev link and no next
    Given path 'v1', 'products', id, 'prices'
    And params { page: 1, size: 2 }
    When method get
    Then status 200
    And match response.prices[*].initDate == ['2026-01-01']
    * def link = karate.response.header('Link')
    * match link contains 'rel="prev"'
    * match link !contains 'rel="next"'

  @TC-072
  Scenario: TC-072 A page past the end is empty but keeps the total
    Given path 'v1', 'products', id, 'prices'
    And params { page: 5, size: 2 }
    When method get
    Then status 200
    And match response.prices == []
    And match karate.response.header('X-Total-Count') == '3'

  @TC-073
  Scenario: TC-073 Sort by initDate descending
    Given path 'v1', 'products', id, 'prices'
    And param sort = 'initDate,desc'
    When method get
    Then status 200
    And match response.prices[*].initDate == ['2026-01-01', '2025-01-01', '2024-01-01']

  @TC-074
  Scenario Outline: TC-074 Window from <from> to <to> keeps the prices in force in it
    Given path 'v1', 'products', id, 'prices'
    And params { from: '<from>', to: '<to>' }
    When method get
    Then status 200
    And match response.prices[*].initDate == <expected>

    Examples:
      | from       | to         | expected                                   |
      | 2024-07-01 | 2025-12-31 | ['2025-01-01']                             |
      | 2024-06-30 | 2026-01-01 | ['2024-01-01', '2025-01-01', '2026-01-01'] |
      | 2030-01-01 | 2030-12-31 | ['2026-01-01']                             |
      | 2024-07-01 | 2024-12-31 | []                                         |

  @TC-075
  Scenario Outline: TC-075 Reject the query <query>
    Given url baseUrl + '/v1/products/' + id + '/prices?<query>'
    When method get
    Then status 400
    And match response == problem
    And match response.errors[0].field == '<field>'

    Examples:
      | query                         | field |
      | size=-1                       | size  |
      | size=501                      | size  |
      | page=-1&size=2                | page  |
      | sort=value,asc                | sort  |
      | from=2025-01-01&to=2024-01-01 | to    |
      | from=01-01-2024               | from  |

  @TC-076
  Scenario: TC-076 size=0, the documented default, returns the whole history without paging headers
    Given path 'v1', 'products', id, 'prices'
    And param size = 0
    When method get
    Then status 200
    And match response.prices == '#[3]'
    * assert karate.response.header('X-Total-Count') == null
