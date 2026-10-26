@ignore
Feature: Get an OAuth 2.0 access token with the client credentials flow

  Scenario: token
    Given url tokenUrl
    And form field grant_type = 'client_credentials'
    And form field client_id = clientId
    And form field client_secret = clientSecret
    When method post
    Then status 200
    * def token = response.access_token
