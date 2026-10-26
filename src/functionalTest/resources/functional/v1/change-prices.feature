@v1 @bonus @oauth
Feature: Changing and deleting prices needs an OAuth 2.0 token with the products:write scope

  Background:
    * url baseUrl
    * def writerToken = callonce read('classpath:functional/support/access-token.feature') writer
    * def readerToken = callonce read('classpath:functional/support/access-token.feature') reader
    * def product = call read('classpath:functional/support/create-product.feature')
    * def id = product.id
    * def addPrice = function(price) { return karate.call('classpath:functional/support/add-price.feature', { id: id, price: price }) }
    * match addPrice({ value: 99.99, initDate: '2024-01-01', endDate: '2024-06-30' }).status == 201
    * match addPrice({ value: 199.99, initDate: '2025-01-01', endDate: '2025-06-30' }).status == 201
    * def priceUrl = baseUrl + '/v1/products/' + id + '/prices/EUR/2024-01-01'

  @TC-080 @smoke
  Scenario: TC-080 Change the value and end date with a writer token
    Given url priceUrl
    And header Authorization = 'Bearer ' + writerToken.token
    And request { value: 89.99, endDate: '2024-03-31' }
    When method put
    Then status 200
    And match response == { value: 89.99, initDate: '2024-01-01', endDate: '2024-03-31' }
    Given url baseUrl
    And path 'products', id, 'prices'
    And param date = '2024-03-31'
    When method get
    Then match response == { value: 89.99 }
    Given path 'products', id, 'prices'
    And param date = '2024-04-01'
    When method get
    Then status 404

  @TC-081
  Scenario Outline: TC-081 <method> with <case> answers <status>
    * def token = <token>
    * if (token != null) karate.configure('headers', { Authorization: 'Bearer ' + token })
    Given url priceUrl
    And request { value: 89.99 }
    When method <method>
    Then status <status>
    * configure headers = null
    Given url baseUrl
    And path 'products', id, 'prices'
    And param date = '2024-04-15'
    When method get
    Then match response == { value: 99.99 }

    Examples:
      | method | case                       | token                | status |
      | put    | no token                   | null                 | 401    |
      | put    | a token that is not a JWT  | 'not-a-token'        | 401    |
      | put    | a token without the scope  | readerToken.token    | 403    |
      | delete | no token                   | null                 | 401    |
      | delete | a token that is not a JWT  | 'not-a-token'        | 401    |
      | delete | a token without the scope  | readerToken.token    | 403    |

  @TC-082
  Scenario: TC-082 A change that would overlap the next price is rejected and changes nothing
    Given url priceUrl
    And header Authorization = 'Bearer ' + writerToken.token
    And request { value: 99.99, endDate: '2025-02-01' }
    When method put
    Then status 409
    And match response == problem
    Given url baseUrl
    And path 'products', id, 'prices'
    When method get
    Then match response.prices[0] == { value: 99.99, initDate: '2024-01-01', endDate: '2024-06-30' }

  @TC-083
  Scenario: TC-083 A price can be extended over its own period, up to the next one
    Given url priceUrl
    And header Authorization = 'Bearer ' + writerToken.token
    And request { value: 99.99, endDate: '2024-12-31' }
    When method put
    Then status 200
    And match response.endDate == '2024-12-31'

  @TC-084
  Scenario Outline: TC-084 <method> of <case> answers 404
    Given path 'v1', 'products', <productId>, 'prices', 'EUR', '<initDate>'
    And header Authorization = 'Bearer ' + writerToken.token
    And request { value: 10 }
    When method <method>
    Then status 404
    And match response == problem

    Examples:
      | method | case                          | productId                | initDate   |
      | put    | a day where no price starts   | id                         | 2024-02-01 |
      | delete | a day where no price starts   | id                         | 2024-02-01 |
      | put    | an unknown product            | '000000000000000000000000' | 2024-01-01 |
      | delete | an unknown product            | '000000000000000000000000' | 2024-01-01 |

  @TC-085
  Scenario Outline: TC-085 Reject a change with <case>
    Given url baseUrl + '/v1/products/' + id + '/prices/EUR/<initDate>'
    And header Authorization = 'Bearer ' + writerToken.token
    And request <body>
    When method put
    Then status 400
    And match response == problem
    And match response.errors[0].field == '<field>'

    Examples:
      | case                        | initDate   | body                                | field    |
      | a zero value                | 2024-01-01 | { value: 0 }                        | value    |
      | an end before the start     | 2024-01-01 | { value: 10, endDate: '2023-12-31' } | endDate  |
      | no value                    | 2024-01-01 | { endDate: '2024-03-31' }           | value    |
      | a start day in another format | 01-01-2024 | { value: 10 }                     | initDate |

  @TC-086
  Scenario: TC-086 A deleted price disappears, and deleting it again answers 404
    Given url priceUrl
    And header Authorization = 'Bearer ' + writerToken.token
    When method delete
    Then status 204
    Given url baseUrl
    And path 'products', id, 'prices'
    And param date = '2024-04-15'
    When method get
    Then status 404
    Given url baseUrl
    And path 'products', id, 'prices'
    When method get
    Then match response.prices[*].initDate == ['2025-01-01']
    Given url priceUrl
    And header Authorization = 'Bearer ' + writerToken.token
    When method delete
    Then status 404

  @TC-087
  Scenario: TC-087 After a delete the period is free again
    Given url priceUrl
    And header Authorization = 'Bearer ' + writerToken.token
    When method delete
    Then status 204
    * match addPrice({ value: 79.99, initDate: '2024-01-01', endDate: '2024-06-30' }).status == 201

  @TC-088
  Scenario: TC-088 The authorization server grants products:write to the writer client only
    * def Base64 = Java.type('java.util.Base64')
    * def claims = function(token) { return JSON.parse(new java.lang.String(Base64.getUrlDecoder().decode(token.split('.')[1]), 'UTF-8')) }
    * match claims(writerToken.token).scope contains 'products:write'
    * match claims(readerToken.token).scope !contains 'products:write'
