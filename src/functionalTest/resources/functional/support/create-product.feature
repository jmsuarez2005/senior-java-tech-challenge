@ignore
Feature: Create a product to use as test data

  Scenario: create
    * def productName = karate.get('name', 'QA product')
    Given url baseUrl
    And path 'products'
    And request { name: '#(productName)', description: 'Created by the functional suite' }
    When method post
    Then status 201
    * def id = response.id
