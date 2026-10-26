@ignore
Feature: Read a product's price history

  Scenario: history
    Given url baseUrl
    And path 'products', id, 'prices'
    When method get
    Then status 200
