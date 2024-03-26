package com.playtech.assignment;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.*;


// This template shows input parameters format.
// It is otherwise not mandatory to use, you can write everything from scratch if you wish.
public class TransactionProcessorSample {

    public static void main(final String[] args) throws IOException {
        List<User> users = TransactionProcessorSample.readUsers(Paths.get(args[0]));
        List<Transaction> transactions = TransactionProcessorSample.readTransactions(Paths.get(args[1]));
        List<BinMapping> binMappings = TransactionProcessorSample.readBinMappings(Paths.get(args[2]));
        // Read country codes from file
        Map<String, String> countryCodes = TransactionProcessorSample.readCountryCodes(Path.of("country_codes.txt"));

        List<Event> events = TransactionProcessorSample.processTransactions(users, transactions, binMappings, countryCodes);

        TransactionProcessorSample.writeBalances(Paths.get(args[3]), users);
        TransactionProcessorSample.writeEvents(Paths.get(args[4]), events);
    }

    // Read country codes from text file
    private static Map<String, String> readCountryCodes(final Path filePath) {
        Map<String, String> countryCodes = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath.toString()))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split("\t");
                countryCodes.put(parts[1], parts[2]);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return countryCodes;
    }

    // Read users from csv file
    private static List<User> readUsers(final Path filePath) {
        List<User> users = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath.toString()))) {
            String line;
            br.readLine();
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                users.add(new User(parts[0], parts[1], Double.parseDouble(parts[2]), parts[3], !Objects.equals(parts[4], "0"), Double.parseDouble(parts[5]), Double.parseDouble(parts[6]), Double.parseDouble(parts[7]), Double.parseDouble(parts[8])));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return users;
    }

    // Read transactions from csv file
    private static List<Transaction> readTransactions(final Path filePath) {
        List<Transaction> transactions = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath.toString()))) {
            String line;
            br.readLine();
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                transactions.add(new Transaction(parts[0], parts[1], parts[2], Double.parseDouble(parts[3]), parts[4], parts[5]));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return transactions;
    }

    // Read bin mappings from csv file
    private static List<BinMapping> readBinMappings(final Path filePath) {
        List<BinMapping> binMappings = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath.toString()))) {
            String line;
            br.readLine();
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                binMappings.add(new BinMapping(parts[0], Long.parseLong(parts[1]), Long.parseLong(parts[2]), parts[3], parts[4]));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return binMappings;
    }

    /**
     * The first validation method to whether the user exist and is not frozen and whether the transaction is unique.
     * Uses other validation methods and creates lists for events, approved transactions and processed transactions.
     * @param users - list of users
     * @param transactions - list of transactions
     * @param binMappings - list of bin mappings
     * @param countryCodes - map of country codes
     * @return list of events
     */
    private static List<Event> processTransactions(final List<User> users, final List<Transaction> transactions, final List<BinMapping> binMappings, final Map<String, String> countryCodes) {
        List<Event> events = new ArrayList<>();
        List<Transaction> approvedTransactions = new ArrayList<>();
        List<Transaction> processedTransactions = new ArrayList<>();
        for (Transaction transaction : transactions) {
            try {
                // - Validate that the transaction ID is unique (not used before).
                if (processedTransactions.stream().anyMatch(approvedTransaction -> approvedTransaction.transactionId.equals(transaction.transactionId))) {
                    events.add(new Event(transaction.transactionId, Event.STATUS_DECLINED, "Transaction " + transaction.transactionId + " already processed (id non-unique)"));
                    processedTransactions.add(transaction);
                    continue;
                }
                // - Validate that the user exists and is not frozen.
                if (users.stream().noneMatch(user -> user.userId.equals(transaction.userId))) {
                    events.add(new Event(transaction.transactionId, Event.STATUS_DECLINED, "User " + transaction.userId + " not found in Users"));
                    processedTransactions.add(transaction);
                    continue;
                }
                if (users.stream().anyMatch(user -> user.userId.equals(transaction.userId) && user.frozen)) {
                    events.add(new Event(transaction.transactionId, Event.STATUS_DECLINED, "User " + transaction.userId + " is frozen"));
                    processedTransactions.add(transaction);
                    continue;
                }
                User user = users.stream().filter(u -> u.userId.equals(transaction.userId)).findFirst().get();
                // - Validate the transaction amount and type
                amountAndTypeValidation(events, approvedTransactions, processedTransactions, transaction, user, binMappings, countryCodes);
            } catch (Exception e) {
                // - In case of unexpected errors with processing transactions, skip the transaction. Do not interrupt processing of the remaining transactions
                events.add(new Event(transaction.transactionId, Event.STATUS_DECLINED, "Error processing transaction " + transaction.transactionId));
                processedTransactions.add(transaction);
            }
        }
        return events;
    }


    /**
     * The second validation method to validate the transaction amount and type.
     * Checks if the amount is positive and between the user's min and max deposit/withdraw limits.
     * Checks if the user has enough balance for a withdrawal.
     * Checks if the account has been used for a deposit before a withdrawal.
     */
    private static void amountAndTypeValidation(List<Event> events, List<Transaction> approvedTransactions, List<Transaction> processedTransactions, Transaction transaction, User user, List<BinMapping> binMappings, Map<String, String> countryCodes) {
        Locale.setDefault(Locale.US);

        // - Validate the transaction amount is positive
        if (transaction.amount <= 0) {
            events.add(new Event(transaction.transactionId, Event.STATUS_DECLINED, "Amount " + String.format("%.2f", transaction.amount) + " is invalid"));
            processedTransactions.add(transaction);
            return;
        }
        // - Validate the transaction type and amount
        if (transaction.type.equals("DEPOSIT")) {
            if (transaction.amount < user.minDeposit) {
                events.add(new Event(transaction.transactionId, Event.STATUS_DECLINED, "Amount " + String.format("%.2f", transaction.amount) + " is under the deposit limit of " + String.format("%.2f", user.minDeposit)));
                processedTransactions.add(transaction);
                return;
            } else if (transaction.amount > user.maxDeposit) {
                events.add(new Event(transaction.transactionId, Event.STATUS_DECLINED, "Amount " + String.format("%.2f", transaction.amount) + " is over the deposit limit of " + String.format("%.2f", user.maxDeposit)));
                processedTransactions.add(transaction);
                return;
            }
        } else if (transaction.type.equals("WITHDRAW")) {
            if (transaction.amount < user.minWithdraw) {
                events.add(new Event(transaction.transactionId, Event.STATUS_DECLINED, "Amount " + String.format("%.2f", transaction.amount) + " is under the withdraw limit of " + String.format("%.2f", user.minWithdraw)));
                processedTransactions.add(transaction);
                return;
            } else if (transaction.amount > user.maxWithdraw) {
                events.add(new Event(transaction.transactionId, Event.STATUS_DECLINED, "Amount " + String.format("%.2f", transaction.amount) + " is over the withdraw limit of " + String.format("%.2f", user.maxWithdraw)));
                processedTransactions.add(transaction);
                return;
            }
            // - For withdrawals, validate that the user has a sufficient balance for a withdrawal.
            if (transaction.amount > user.balance) {
                events.add(new Event(transaction.transactionId, Event.STATUS_DECLINED, "Not enough balance for withdrawal " + String.format("%.2f", transaction.amount) + " - balance is too low at " + String.format("%.2f", user.getBalance())));
                processedTransactions.add(transaction);
                return;
            }
            // - Allow withdrawals only with the same payment account that has previously been successfully used for deposit
            // no need to check for approved transaction type as only deposit transactions are approved with the same account number before withdraw
            if (approvedTransactions.stream().noneMatch(approvedTransaction -> approvedTransaction.accountNumber.equals(transaction.accountNumber))) {
                events.add(new Event(transaction.transactionId, Event.STATUS_DECLINED, "Cannot withdraw with a new account " + transaction.accountNumber));
                processedTransactions.add(transaction);
                return;
            }
        }
        // - Transaction type that isn't deposit or withdrawal should be declined
        else {
            events.add(new Event(transaction.transactionId, Event.STATUS_DECLINED, "Type " + transaction.type + " is not supported"));
            processedTransactions.add(transaction);
            return;
        }
        // - Validate payment method:
        if (transaction.method.equals("TRANSFER")) {
            transferValidation(events, processedTransactions, transaction, user, approvedTransactions);
        } else if (transaction.method.equals("CARD")) {
            cardValidation(binMappings, countryCodes, transaction, events, processedTransactions, user, approvedTransactions);
        } else {
            events.add(new Event(transaction.transactionId, Event.STATUS_DECLINED, "Method " + transaction.method + " is not supported"));
        }
    }


    /**
     * Third validation method to validate that the account used for the transaction is used by only one user.
     * If the transaction passes all the validations, the transaction is approved and added to the approvedTransactions list.
     * The user's balance is updated accordingly.
     */
    private static void validateAccountIsUsedByOneUser(List<Event> events, List<Transaction> approvedTransactions, List<Transaction> processedTransactions, Transaction transaction, User user) {
        String accountNumber = transaction.accountNumber;
        if (approvedTransactions.stream().anyMatch(approvedTransaction -> approvedTransaction.accountNumber.equals(accountNumber) && !approvedTransaction.userId.equals(transaction.userId))) {
            events.add(new Event(transaction.transactionId, Event.STATUS_DECLINED, "Account " + accountNumber + " is in use by another user"));
            processedTransactions.add(transaction);
            return;
        }
        events.add(new Event(transaction.transactionId, Event.STATUS_APPROVED, "OK"));
        processedTransactions.add(transaction);
        if (transaction.type.equals("DEPOSIT")) {
            user.balance += transaction.amount;
        }
        if (transaction.type.equals("WITHDRAW")) {
            user.balance -= transaction.amount;
        }
        approvedTransactions.add(transaction);
    }

    /**
     * Validation method for method type transfer.
     * Uses the isIbanValid method to validate the IBAN number and validates the country of the account used for the transaction matches the user's country.
     */
    private static void transferValidation(List<Event> events, List<Transaction> processedTransactions, Transaction transaction, User user, List<Transaction> approvedTransactions) {
        String iban = transaction.accountNumber;
        if (iban.length() > 34) {
            events.add(new Event(transaction.transactionId, Event.STATUS_DECLINED, "Invalid iban " + transaction.accountNumber));
            processedTransactions.add(transaction);
            return;
        }
        boolean isValid = isIbanValid(iban);
        if (!isValid) {
            events.add(new Event(transaction.transactionId, Event.STATUS_DECLINED, "Invalid iban " + transaction.accountNumber));
            processedTransactions.add(transaction);
            return;
        }
        // - Confirm that the country of the card or account used for the transaction matches the user's country
        if (!user.country.equals(iban.substring(0, 2))) {
            events.add(new Event(transaction.transactionId, Event.STATUS_DECLINED, "Country of the account used for the transaction doesn't match the user's country, expected " + user.country));
            processedTransactions.add(transaction);
            return;
        }
        // - Users cannot share iban/card; payment account used by one user can no longer be used by another
        validateAccountIsUsedByOneUser(events, approvedTransactions, processedTransactions, transaction, user);
    }

    /**
     * Validation method for method type card.
     * Uses the binMappings to validate the card type.
     * Checks if the country of the card or account used for the transaction matches the user's country using the countryCodes map.
     */
    private static void cardValidation(List<BinMapping> binMappings, Map<String, String> countryCodes, Transaction transaction, List<Event> events, List<Transaction> processedTransactions, User user, List<Transaction> approvedTransactions) {
        String accountNumber = transaction.accountNumber;
        long number = Long.parseLong(accountNumber.substring(0, 10));
        for (BinMapping binMapping : binMappings) {
            if (number >= binMapping.rangeFrom && number <= binMapping.rangeTo) {
                // validate that card type=DC
                if (!binMapping.type.equals("DC")) {
                    events.add(new Event(transaction.transactionId, Event.STATUS_DECLINED, "Only DC cards allowed. Got " + binMapping.type + " card type."));
                    processedTransactions.add(transaction);
                    return;
                }
                // - Confirm that the country of the card or account used for the transaction matches the user's country
                String binCountryCode = binMapping.country;
                if (!binCountryCode.equals(countryCodes.get(user.country))) {
                    events.add(new Event(transaction.transactionId, Event.STATUS_DECLINED, "Country of the card used for the transaction doesn't match the user's country, expected " + user.country));
                    processedTransactions.add(transaction);
                    return;
                }
            }
        }
        // - Users cannot share iban/card; payment account used by one user can no longer be used by another
        validateAccountIsUsedByOneUser(events, approvedTransactions, processedTransactions, transaction, user);
    }

    //Validate the IBAN number. Uses algorithm from given Wikipedia page to validate the IBAN number.
    private static boolean isIbanValid(String iban) {
        String rearrangedIban = iban.substring(4) + iban.substring(0, 4);
        StringBuilder expandedIban = new StringBuilder();
        for (char ch : rearrangedIban.toCharArray()) {
            if (Character.isLetter(ch)) {
                int numericValue = Character.toUpperCase(ch) - 'A' + 10;
                expandedIban.append(numericValue);
            } else {
                expandedIban.append(ch);
            }
        }
        // validate the transfer account number's check digit validity
        BigInteger bigInteger = new BigInteger(expandedIban.toString());
        return bigInteger.mod(BigInteger.valueOf(97)).intValue() == 1;
    }

    // Writes the new balances of the users to a csv file
    private static void writeBalances(final Path filePath, final List<User> users) throws IOException {
        Locale.setDefault(Locale.US);
        try (final FileWriter writer = new FileWriter(filePath.toFile(), false)) {
            writer.append("user_id,balance\n");
            DecimalFormat df = new DecimalFormat("#0.00"); // Format to ensure at least two decimal places
            for (User user : users) {
                writer.append(user.userId).append(",").append(df.format(user.balance)).append("\n");
            }
        }
    }

    // Writes the events to a csv file
    private static void writeEvents(final Path filePath, final List<Event> events) throws IOException {
        try (final FileWriter writer = new FileWriter(filePath.toFile(), false)) {
            writer.append("transaction_id,status,message\n");
            for (final var event : events) {
                writer.append(event.transactionId).append(",").append(event.status).append(",").append(event.message).append("\n");
            }
        }
    }
}


class User {
    public String userId;
    public String username;
    public double balance;
    public String country;
    public boolean frozen;
    public double minDeposit;
    public double maxDeposit;
    public double minWithdraw;
    public double maxWithdraw;

    public User(String userId, String username, double balance, String coutry, boolean frozen, double minDeposit, double maxDeposit, double minWithdraw, double maxWithdraw) {
        this.userId = userId;
        this.username = username;
        this.balance = balance;
        this.country = coutry;
        this.frozen = frozen;
        this.minDeposit = minDeposit;
        this.maxDeposit = maxDeposit;
        this.minWithdraw = minWithdraw;
        this.maxWithdraw = maxWithdraw;
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }
}

class Transaction {
    public String transactionId;
    public String userId;
    public String type;
    public double amount;
    public String method;
    public String accountNumber;

    public Transaction(String transactionId, String userId, String type, double amount, String method, String accountNumber) {
        this.transactionId = transactionId;
        this.userId = userId;
        this.type = type;
        this.amount = amount;
        this.method = method;
        this.accountNumber = accountNumber;
    }
}

class BinMapping {
    public String name;
    public long rangeFrom;
    public long rangeTo;
    public String type;
    public String country;

    public BinMapping(String name, long rangeFrom, long rangeTo, String type, String country) {
        this.name = name;
        this.rangeFrom = rangeFrom;
        this.rangeTo = rangeTo;
        this.type = type;
        this.country = country;
    }

    public String getName() {
        return name;
    }
}

class Event {
    public static final String STATUS_DECLINED = "DECLINED";
    public static final String STATUS_APPROVED = "APPROVED";

    public String transactionId;
    public String status;
    public String message;

    public Event(String transactionId, String status, String message) {
        this.transactionId = transactionId;
        this.status = status;
        this.message = message;
    }
}