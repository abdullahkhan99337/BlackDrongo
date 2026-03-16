Feature: Naukri resume upload

  Scenario Outline: User uploads resume on Naukri
    Given user opens naukri login page
    When user opens login dialog
    And user enters email "<email>" and password "<password>"
    And user submits login
    And user updates the profile by uploading resume
    Then user should see success message

    Examples:
      | email           | password     |
      | abdullah.khan17029@gmail.com      | Abbu@1702 |
