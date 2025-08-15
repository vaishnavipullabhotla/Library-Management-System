# Library-Management-System
Library Management System built with Java and MySQL to manage books, members, and borrow/return records. Implements OOP concepts and CRUD operations with persistent storage. Features book/member management, transaction history, and due date tracking for efficient library operations.
A simple Java + MySQL application to manage books, members, and transactions in a library.  
Supports borrowing, returning, tracking late fees, and maintaining book stock.

## Features
- Add, update, delete, and search books
- Manage members and their borrowing history
- Borrow and return books
- Auto-calculate late fees
- Persistent data storage using MySQL

## Tech Stack
- Java
- MySQL
- MySQL Connector/J

Install MySQL and create library_db.

Update DB credentials in LibraryApp.java:

private static final String DB_URL = "jdbc:mysql://localhost:3306/library_db?serverTimezone=UTC";
private static final String USER = "root";
private static final String PASS = "yourpassword";

javac -cp .;mysql-connector-j-9.4.0.jar LibraryApp.java
java -cp .;mysql-connector-j-9.4.0.jar LibraryApp

Database Setup
CREATE DATABASE library_db;
USE library_db;

CREATE TABLE books (
  id INT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(255) NOT NULL,
  author VARCHAR(255) NOT NULL,
  quantity INT NOT NULL
);

CREATE TABLE members (
  id INT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(255) NOT NULL,
  email VARCHAR(255) UNIQUE
);

CREATE TABLE transactions (
  id INT PRIMARY KEY AUTO_INCREMENT,
  member_id INT,
  book_id INT,
  borrow_date DATE,
  return_date DATE,
  late_fee DOUBLE,
  FOREIGN KEY (member_id) REFERENCES members(id),
  FOREIGN KEY (book_id) REFERENCES books(id)
);
