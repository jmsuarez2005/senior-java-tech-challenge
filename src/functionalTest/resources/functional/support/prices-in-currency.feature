@ignore
Feature: Read a product's history in one currency

  Scenario: history
    Given url baseUrl
    And path 'v1', 'products', id, 'prices'
    And param currency = currency
    When method get
    Then status 200
