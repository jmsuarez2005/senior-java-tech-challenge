@ignore
Feature: Add a price without asserting the outcome, so callers can check rejections

  Scenario: add
    Given url baseUrl
    And path 'products', id, 'prices'
    And request price
    When method post
    * def status = responseStatus
    * def body = response
