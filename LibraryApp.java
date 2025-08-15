import java.sql.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Scanner;

public class LibraryApp {

    private static final String DB_URL  = "jdbc:mysql://localhost:3306/library_db?serverTimezone=UTC";
    private static final String USER    = "root";
    private static final String PASS    = "123456";

    private static final int LOAN_DAYS = 14;       // grace period
    private static final double LATE_FEE_PER_DAY = 5.0;

    public static void main(String[] args) {
        // Ensure driver is available (optional on newer JDKs but harmless)
        try { Class.forName("com.mysql.cj.jdbc.Driver"); } catch (Exception ignore) {}

        ensureConnectionOrExit();

        Scanner sc = new Scanner(System.in);
        while (true) {
            printMenu();
            int choice = readInt(sc, "Choose: ");
            switch (choice) {
                case 1 -> addBook(sc);
                case 2 -> addMember(sc);
                case 3 -> listBooks();
                case 4 -> listMembers();
                case 5 -> borrowBook(sc);
                case 6 -> returnBook(sc);
                case 7 -> listActiveBorrowings();
                case 8 -> listRecentTransactions();
                case 0 -> { System.out.println("Bye!"); return; }
                default -> System.out.println("Invalid option.");
            }
            System.out.println();
        }
    }

    // --- Simple menu ---
    private static void printMenu() {
        System.out.println("===== Library Management =====");
        System.out.println("1) Add Book");
        System.out.println("2) Add Member");
        System.out.println("3) List Books");
        System.out.println("4) List Members");
        System.out.println("5) Borrow Book");
        System.out.println("6) Return Book");
        System.out.println("7) Active Borrowings");
        System.out.println("8) Recent Transactions");
        System.out.println("0) Exit");
    }

    // --- DB helper ---
    private static Connection getConn() throws SQLException {
        return DriverManager.getConnection(DB_URL, USER, PASS);
    }

    private static void ensureConnectionOrExit() {
        try (Connection c = getConn()) {
            System.out.println("Connected to MySQL ✔");
        } catch (SQLException e) {
            System.out.println("DB connection failed. Update DB_URL/USER/PASS in code.");
            e.printStackTrace();
            System.exit(1);
        }
    }

    // --- I/O helpers ---
    private static int readInt(Scanner sc, String label) {
        System.out.print(label);
        while (!sc.hasNextInt()) {
            System.out.print("Enter a number: ");
            sc.next();
        }
        int v = sc.nextInt();
        sc.nextLine(); // consume newline
        return v;
    }

    private static String readLine(Scanner sc, String label) {
        System.out.print(label);
        return sc.nextLine().trim();
    }

    // --- Features ---
    private static void addBook(Scanner sc) {
        String title = readLine(sc, "Title: ");
        String author = readLine(sc, "Author: ");
        int qty = readInt(sc, "Quantity: ");

        String sql = "INSERT INTO books(title, author, quantity) VALUES (?,?,?)";
        try (Connection con = getConn();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, title);
            ps.setString(2, author);
            ps.setInt(3, qty);
            ps.executeUpdate();
            System.out.println("Book added.");
        } catch (SQLException e) {
            System.out.println("Error adding book.");
            e.printStackTrace();
        }
    }

    private static void addMember(Scanner sc) {
        String name = readLine(sc, "Name: ");
        String email = readLine(sc, "Email: ");

        String sql = "INSERT INTO members(name, email) VALUES (?,?)";
        try (Connection con = getConn();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, email.isEmpty() ? null : email);
            ps.executeUpdate();
            System.out.println("Member added.");
        } catch (SQLException e) {
            System.out.println("Error adding member (email must be unique if provided).");
            e.printStackTrace();
        }
    }

    private static void listBooks() {
        String sql = "SELECT id, title, author, quantity FROM books ORDER BY id";
        try (Connection con = getConn();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            System.out.println("ID | Title | Author | Qty");
            while (rs.next()) {
                System.out.printf("%d | %s | %s | %d%n",
                        rs.getInt("id"), rs.getString("title"),
                        rs.getString("author"), rs.getInt("quantity"));
            }
        } catch (SQLException e) {
            System.out.println("Error listing books.");
            e.printStackTrace();
        }
    }

    private static void listMembers() {
        String sql = "SELECT id, name, email FROM members ORDER BY id";
        try (Connection con = getConn();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            System.out.println("ID | Name | Email");
            while (rs.next()) {
                System.out.printf("%d | %s | %s%n",
                        rs.getInt("id"), rs.getString("name"),
                        rs.getString("email"));
            }
        } catch (SQLException e) {
            System.out.println("Error listing members.");
            e.printStackTrace();
        }
    }

    private static void borrowBook(Scanner sc) {
        int memberId = readInt(sc, "Member ID: ");
        int bookId = readInt(sc, "Book ID: ");

        String checkQty = "SELECT quantity FROM books WHERE id=?";
        String decQty   = "UPDATE books SET quantity = quantity - 1 WHERE id=? AND quantity > 0";
        String insTxn   = "INSERT INTO transactions(member_id, book_id, borrow_date, return_date, late_fee) VALUES (?,?,CURDATE(),NULL,0)";

        try (Connection con = getConn()) {
            con.setAutoCommit(false);
            int qty;

            try (PreparedStatement ps = con.prepareStatement(checkQty)) {
                ps.setInt(1, bookId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) { System.out.println("Book not found."); con.rollback(); return; }
                    qty = rs.getInt(1);
                }
            }

            if (qty <= 0) { System.out.println("Out of stock."); con.rollback(); return; }

            try (PreparedStatement ps1 = con.prepareStatement(decQty);
                 PreparedStatement ps2 = con.prepareStatement(insTxn)) {
                ps1.setInt(1, bookId);
                int updated = ps1.executeUpdate();
                if (updated == 0) { System.out.println("Out of stock."); con.rollback(); return; }

                ps2.setInt(1, memberId);
                ps2.setInt(2, bookId);
                ps2.executeUpdate();
            }

            con.commit();
            System.out.println("Borrowed successfully.");
        } catch (SQLException e) {
            System.out.println("Error borrowing book.");
            e.printStackTrace();
        }
    }

    private static void returnBook(Scanner sc) {
        int txnId = readInt(sc, "Transaction ID: ");

        String getTxn = "SELECT id, book_id, borrow_date, return_date FROM transactions WHERE id=?";
        String updTxn = "UPDATE transactions SET return_date=CURDATE(), late_fee=? WHERE id=?";
        String incQty = "UPDATE books SET quantity = quantity + 1 WHERE id=?";

        try (Connection con = getConn()) {
            con.setAutoCommit(false);

            int bookId;
            Date borrowDate;
            Date returnDate;

            try (PreparedStatement ps = con.prepareStatement(getTxn)) {
                ps.setInt(1, txnId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) { System.out.println("Transaction not found."); con.rollback(); return; }
                    bookId = rs.getInt("book_id");
                    borrowDate = rs.getDate("borrow_date");
                    returnDate = rs.getDate("return_date");
                }
            }

            if (returnDate != null) { System.out.println("Already returned."); con.rollback(); return; }

            long days = ChronoUnit.DAYS.between(borrowDate.toLocalDate(), LocalDate.now());
            long lateDays = Math.max(0, days - LOAN_DAYS);
            double fee = lateDays * LATE_FEE_PER_DAY;

            try (PreparedStatement ps1 = con.prepareStatement(updTxn);
                 PreparedStatement ps2 = con.prepareStatement(incQty)) {
                ps1.setDouble(1, fee);
                ps1.setInt(2, txnId);
                ps1.executeUpdate();

                ps2.setInt(1, bookId);
                ps2.executeUpdate();
            }

            con.commit();
            System.out.printf("Returned. Late fee: ₹%.2f%n", fee);
        } catch (SQLException e) {
            System.out.println("Error returning book.");
            e.printStackTrace();
        }
    }

    private static void listActiveBorrowings() {
        String sql = """
            SELECT t.id, m.name AS member, b.title AS book, t.borrow_date
            FROM transactions t
            JOIN members m ON m.id = t.member_id
            JOIN books b   ON b.id = t.book_id
            WHERE t.return_date IS NULL
            ORDER BY t.id DESC
            """;
        try (Connection con = getConn();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            System.out.println("TxnID | Member | Book | BorrowDate");
            while (rs.next()) {
                System.out.printf("%d | %s | %s | %s%n",
                        rs.getInt("id"),
                        rs.getString("member"),
                        rs.getString("book"),
                        rs.getDate("borrow_date"));
            }
        } catch (SQLException e) {
            System.out.println("Error listing active borrowings.");
            e.printStackTrace();
        }
    }

    private static void listRecentTransactions() {
        String sql = """
            SELECT t.id, m.name AS member, b.title AS book, t.borrow_date, t.return_date, t.late_fee
            FROM transactions t
            JOIN members m ON m.id = t.member_id
            JOIN books b   ON b.id = t.book_id
            ORDER BY t.id DESC LIMIT 20
            """;
        try (Connection con = getConn();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            System.out.println("TxnID | Member | Book | BorrowDate | ReturnDate | LateFee");
            while (rs.next()) {
                System.out.printf("%d | %s | %s | %s | %s | ₹%.2f%n",
                        rs.getInt("id"),
                        rs.getString("member"),
                        rs.getString("book"),
                        rs.getDate("borrow_date"),
                        rs.getDate("return_date"),
                        rs.getDouble("late_fee"));
            }
        } catch (SQLException e) {
            System.out.println("Error listing transactions.");
            e.printStackTrace();
        }
    }
}
