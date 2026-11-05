package ui;

import connection.DBConnection;

import java.sql.*;
import java.time.LocalDate;
import java.util.Scanner;

public class PayrollMenu {

    private final Scanner sc = new Scanner(System.in);

    public void start() {
        while (true) {
            System.out.println("\n========== PAYCORE MENU ==========");
            System.out.println("1. Add Department");
            System.out.println("2. Add Employee");
            System.out.println("3. View Employees");
            System.out.println("4. Add/Update Salary Structure");
            System.out.println("5. Generate Payslip");
            System.out.println("6. View Payslips");
            System.out.println("7. Record Payment");
            System.out.println("8. View Payment History");
            System.out.println("9. Department Payroll Report");
            System.out.println("10. Change Employee Status");
            System.out.println("0. Exit");
            System.out.print("Enter choice: ");

            int choice = readInt();

            try {
                switch (choice) {
                    case 1 -> addDepartment();
                    case 2 -> addEmployee();
                    case 3 -> viewEmployees();
                    case 4 -> saveSalaryStructure();
                    case 5 -> generatePayslip();
                    case 6 -> viewPayslips();
                    case 7 -> recordPayment();
                    case 8 -> paymentHistory();
                    case 9 -> departmentReport();
                    case 10 -> changeStatus();
                    case 0 -> {
                        System.out.println("PayCore closed.");
                        return;
                    }
                    default -> System.out.println("Invalid choice.");
                }
            } catch (SQLException e) {
                System.out.println("Database error: " + e.getMessage());
            } catch (IllegalArgumentException e) {
                System.out.println("Input error: " + e.getMessage());
            }
        }
    }

    private int readInt() {
        while (!sc.hasNextInt()) {
            System.out.print("Enter a valid number: ");
            sc.next();
        }
        int value = sc.nextInt();
        sc.nextLine();
        return value;
    }

    private double readAmount(String message) {
        System.out.print(message);
        double amount = sc.nextDouble();
        sc.nextLine();

        if (!Double.isFinite(amount) || amount < 0) {
            throw new IllegalArgumentException(
                    "Amount must be a non-negative number.");
        }
        return amount;
    }

    private void addDepartment() throws SQLException {
        System.out.print("Department name: ");
        String name = sc.nextLine().trim();

        if (name.isEmpty()) {
            throw new IllegalArgumentException(
                    "Department name cannot be empty.");
        }

        String sql = "INSERT INTO departments (department_name) VALUES (?)";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, name);
            ps.executeUpdate();
            System.out.println("Department added successfully.");
        }
    }

    private void addEmployee() throws SQLException {
        System.out.print("Employee name: ");
        String name = sc.nextLine().trim();

        System.out.print("Email: ");
        String email = sc.nextLine().trim();

        System.out.print("Department ID: ");
        int departmentId = readInt();

        System.out.print("Joining date (YYYY-MM-DD): ");
        LocalDate joiningDate = LocalDate.parse(sc.nextLine().trim());

        if (name.isEmpty() || email.isEmpty() || departmentId <= 0) {
            throw new IllegalArgumentException(
                    "Enter valid employee details.");
        }

        String sql = """
                INSERT INTO employees
                (employee_name, email, department_id, joining_date)
                VALUES (?, ?, ?, ?)
                """;

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, name);
            ps.setString(2, email);
            ps.setInt(3, departmentId);
            ps.setDate(4, Date.valueOf(joiningDate));

            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    System.out.println("Employee added. Employee ID: "
                            + keys.getInt(1));
                }
            }
        }
    }

    private void viewEmployees() throws SQLException {
        String sql = """
                SELECT e.employee_id, e.employee_name, e.email,
                       d.department_name, e.joining_date,
                       e.employment_status
                FROM employees e
                JOIN departments d
                  ON e.department_id = d.department_id
                ORDER BY e.employee_id
                """;

        try (Connection con = DBConnection.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            System.out.println("\n--- EMPLOYEE LIST ---");

            while (rs.next()) {
                System.out.printf(
                        "ID: %d | Name: %s | Email: %s | Department: %s"
                                + " | Joined: %s | Status: %s%n",
                        rs.getInt("employee_id"),
                        rs.getString("employee_name"),
                        rs.getString("email"),
                        rs.getString("department_name"),
                        rs.getDate("joining_date"),
                        rs.getString("employment_status"));
            }
        }
    }

    private void saveSalaryStructure() throws SQLException {
        System.out.print("Employee ID: ");
        int employeeId = readInt();

        double basic = readAmount("Basic salary: ");
        double hra = readAmount("HRA: ");
        double allowances = readAmount("Other allowances: ");
        double deductions = readAmount("Deductions: ");

        if (employeeId <= 0 || basic + hra + allowances < deductions) {
            throw new IllegalArgumentException(
                    "Invalid employee ID or deductions exceed gross salary.");
        }

        String sql = """
                INSERT INTO salary_structures
                (employee_id, basic_salary, hra, other_allowances, deductions)
                VALUES (?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                basic_salary = VALUES(basic_salary),
                hra = VALUES(hra),
                other_allowances = VALUES(other_allowances),
                deductions = VALUES(deductions)
                """;

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, employeeId);
            ps.setDouble(2, basic);
            ps.setDouble(3, hra);
            ps.setDouble(4, allowances);
            ps.setDouble(5, deductions);

            ps.executeUpdate();
            System.out.println("Salary structure saved.");
        }
    }

    private void generatePayslip() throws SQLException {
        System.out.print("Employee ID: ");
        int employeeId = readInt();

        System.out.print("Pay month (YYYY-MM, e.g. 2026-10): ");
        String month = sc.nextLine().trim();

        LocalDate payMonth = LocalDate.parse(month + "-01");

        String sql = """
                INSERT INTO payslips
                (employee_id, pay_month, basic_salary, hra,
                 other_allowances, deductions, gross_salary, net_salary)
                SELECT e.employee_id, ?, s.basic_salary, s.hra,
                       s.other_allowances, s.deductions,
                       s.basic_salary + s.hra + s.other_allowances,
                       s.basic_salary + s.hra + s.other_allowances
                           - s.deductions
                FROM employees e
                JOIN salary_structures s
                  ON e.employee_id = s.employee_id
                WHERE e.employee_id = ?
                  AND e.employment_status = 'ACTIVE'
                """;

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql,
                     Statement.RETURN_GENERATED_KEYS)) {

            ps.setDate(1, Date.valueOf(payMonth));
            ps.setInt(2, employeeId);

            int count = ps.executeUpdate();

            if (count == 0) {
                System.out.println(
                        "Payslip not generated. Check employee status "
                        + "and salary structure.");
                return;
            }

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    System.out.println("Payslip generated. Payslip ID: "
                            + keys.getInt(1));
                }
            }
        }
    }

    private void viewPayslips() throws SQLException {
        String sql = """
                SELECT p.payslip_id, e.employee_name, p.pay_month,
                       p.gross_salary, p.deductions, p.net_salary
                FROM payslips p
                JOIN employees e ON p.employee_id = e.employee_id
                ORDER BY p.pay_month DESC, p.payslip_id DESC
                """;

        try (Connection con = DBConnection.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            System.out.println("\n--- PAYSLIPS ---");

            while (rs.next()) {
                System.out.printf(
                        "Payslip ID: %d | Employee: %s | Month: %s"
                                + " | Gross: %.2f | Deductions: %.2f"
                                + " | Net: %.2f%n",
                        rs.getInt("payslip_id"),
                        rs.getString("employee_name"),
                        rs.getDate("pay_month"),
                        rs.getDouble("gross_salary"),
                        rs.getDouble("deductions"),
                        rs.getDouble("net_salary"));
            }
        }
    }

    private void recordPayment() throws SQLException {
        System.out.print("Payslip ID: ");
        int payslipId = readInt();

        String findSql = """
                SELECT net_salary -
                       COALESCE((
                           SELECT SUM(amount_paid)
                           FROM payments
                           WHERE payslip_id = p.payslip_id
                             AND payment_status = 'PAID'
                       ), 0) AS balance
                FROM payslips p
                WHERE p.payslip_id = ?
                """;

        try (Connection con = DBConnection.getConnection();
             PreparedStatement find = con.prepareStatement(findSql)) {

            find.setInt(1, payslipId);

            double balance;

            try (ResultSet rs = find.executeQuery()) {
                if (!rs.next()) {
                    System.out.println("Payslip not found.");
                    return;
                }
                balance = rs.getDouble("balance");
            }

            if (balance <= 0) {
                System.out.println("No pending balance for this payslip.");
                return;
            }

            System.out.printf("Pending balance: %.2f%n", balance);
            double amount = readAmount("Amount to pay: ");

            if (amount <= 0 || amount > balance) {
                throw new IllegalArgumentException(
                        "Payment must be positive and cannot exceed balance.");
            }

            System.out.print("Payment method (CASH/UPI/BANK): ");
            String method = sc.nextLine().trim().toUpperCase();

            if (!method.matches("CASH|UPI|BANK")) {
                throw new IllegalArgumentException(
                        "Choose CASH, UPI, or BANK.");
            }

            System.out.print("Reference number (or press Enter): ");
            String reference = sc.nextLine().trim();

            String insertSql = """
                    INSERT INTO payments
                    (payslip_id, payment_date, amount_paid,
                     payment_method, payment_status, reference_number)
                    VALUES (?, ?, ?, ?, 'PAID', ?)
                    """;

            try (PreparedStatement ps = con.prepareStatement(insertSql)) {
                ps.setInt(1, payslipId);
                ps.setDate(2, Date.valueOf(LocalDate.now()));
                ps.setDouble(3, amount);
                ps.setString(4, method);
                ps.setString(5, reference.isEmpty() ? null : reference);
                ps.executeUpdate();

                System.out.println("Payment recorded successfully.");
            }
        }
    }

    private void paymentHistory() throws SQLException {

        String sql = """
                SELECT py.payment_id, e.employee_name, p.pay_month,
                       py.payment_date, py.amount_paid,
                       py.payment_method, py.payment_status,
                       py.reference_number
                FROM payments py
                JOIN payslips p
                    ON py.payslip_id = p.payslip_id
                JOIN employees e
                    ON p.employee_id = e.employee_id
                ORDER BY py.payment_id DESC
                """;

        try (Connection con = DBConnection.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            System.out.println("--- PAYMENT HISTORY ---");

            while (rs.next()) {
                System.out.printf(
                        "Payment ID: %d | Employee: %s | Month: %s"
                        + " | Date: %s | Amount: %.2f"
                        + " | Method: %s | Status: %s | Ref: %s%n",
                        rs.getInt("payment_id"),
                        rs.getString("employee_name"),
                        rs.getDate("pay_month"),
                        rs.getDate("payment_date"),
                        rs.getDouble("amount_paid"),
                        rs.getString("payment_method"),
                        rs.getString("payment_status"),
                        rs.getString("reference_number"));
            }
        }
    }

    private void departmentReport() throws SQLException {
        String sql = """
                SELECT d.department_name,
                       COUNT(DISTINCT e.employee_id) AS employee_count,
                       COALESCE(SUM(p.net_salary), 0) AS payroll_total
                FROM departments d
                LEFT JOIN employees e
                  ON d.department_id = e.department_id
                LEFT JOIN payslips p
                  ON e.employee_id = p.employee_id
                GROUP BY d.department_id, d.department_name
                ORDER BY d.department_name
                """;

        try (Connection con = DBConnection.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            System.out.println("\n--- DEPARTMENT PAYROLL REPORT ---");

            while (rs.next()) {
                System.out.printf(
                        "Department: %s | Employees: %d"
                                + " | Total Net Payroll: %.2f%n",
                        rs.getString("department_name"),
                        rs.getInt("employee_count"),
                        rs.getDouble("payroll_total"));
            }
        }
    }

    private void changeStatus() throws SQLException {
        System.out.print("Employee ID: ");
        int employeeId = readInt();

        System.out.print("New status (ACTIVE/INACTIVE): ");
        String status = sc.nextLine().trim().toUpperCase();

        if (!status.matches("ACTIVE|INACTIVE")) {
            throw new IllegalArgumentException(
                    "Status must be ACTIVE or INACTIVE.");
        }

        String sql = """
                UPDATE employees
                SET employment_status = ?
                WHERE employee_id = ?
                """;

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, status);
            ps.setInt(2, employeeId);

            int count = ps.executeUpdate();

            if (count > 0) {
                System.out.println("Employee status updated.");
            } else {
                System.out.println("Employee ID not found.");
            }
        }
    }
}