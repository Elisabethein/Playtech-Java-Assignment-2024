# Java Developer Test Assignment

This is the repository for the Playtech Java Developer test assignment. Please find the original task description in the Java Developer Test Assignment md file. This is description of my implementation:
The structure of this project is reading data from different csv files containing information about users, transactions and bin mappings. Then processing the transactions and validating each transaction. Then creating output files containing each transactions description (decline or approved and the message for the decision) and the users' new balances.

## My implementation

The code is in the TransactionProcessorSample.java file. I used the sample code and implemented the following methods:
- 'readCountryCodes' - reads the alpha-2 and alpha-3 country codes from a file 'country_codes.txt' and stores them in a map for later validation
- 'readUsers' - reads users from the csv file and creates a User object for each user and stores them in a list
- 'readTransactions' - reads transactions from the csv file and creates a Transaction object for each transaction and stores them in a list
- 'readBinMappings' - reads bin mappings from the csv file and creates a BinMapping object for each bin mapping and stores them in a list
- 'processTransactions' - uses the previously read data to process the transactions and creates a list of events, uses lists for approved and all processed transactions.
  The logic in this method:
  * validates the transaction id is unique
  * validates the user exists and is not frozen
  * calls the amountAndTypeValidation method to validate the amount and type of transaction
- 'amountAndTypeValidation' - validates the amount and type of transaction
  The logic in this method:
  * validates the amount is a valid positive number and within deposit/withdraw limits for the user
  * for withdrawals, validates the user has a sufficient balance for a withdrawal
  * allows withdrawals only with the same payment account that has previously been successfully used for deposit (this uses approvedTransactions list)
  * chooses a validation method to call based on the transaction method:
    * 'transferValidation' for transfer method
    * 'cardValidation' for card method
- 'transferValidation' - validates the transfer account iban
  The logic in this method:
  * uses method 'isValid' to validate the iban
  * confirms that the country of the account used for the transaction matches the user's country (uses the aplha-2 code in iban account)
  * calls method 'validateAccountIsUsedByOneUser' to validate the account
- 'cardValidation' - validates the card number
  The logic in this method:
  * validates the card number using the bin mappings and the card type is DC
  * confirms that the country of the card used for the transaction matches the user's country (uses the country codes' map)
  * calls method 'validateAccountIsUsedByOneUser' to validate the account
- 'validateAccountIsUsedByOneUser' - validates the user does not share iban/card
  The logic in this method:
  * validates the payment account has not successfully been used by another user
  * if we reach this point, the account is valid and can be used for the transaction
  * adds the transaction to the list of approved transactions
  * updates the user balance
- 'isValid' - uses the iban validation algorithm from the wikipedia page to validate the iban, returns a boolean
- 'writeBalances' - writes the user balances to the csv file
- 'writeEvents' - writes the events to the csv file

## Notes

- I used the sample code and implemented the methods as described above. The validation process is ordered as the output examples show. For example, we check if the amount is valid before checking the type of transaction or accounts/cards.
- I thought it would be more optimal to check the amount before using algorithms to validate the iban or card number.
- The users are written in the same order they were read from the file, which is different from the order in some example outputs, but I think it is more logical to keep the order consistent.
- I am using a text file I created for the country codes, to make the country check more dynamic and factually correct.
- The outputs for incorrect card type may differ from the examples, because I wanted to include the status and message both in the output. Some examples were missing one or another.

  ## Running the project

  To run the project, you can use terminal and specify all paths of input and output files.

  Example:
  
  java TransactionProcessorSample.java <users-input.csv> <transactions-input.csv> <bins-input.csv> <balances-output.csv> <events-output.csv>

