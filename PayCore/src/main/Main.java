package main;

import ui.PayrollMenu;

public class Main {

    public static void main(String[] args) {
        System.out.println("==================================");
        System.out.println("   PAYCORE PAYROLL SYSTEM");
        System.out.println("==================================");

        PayrollMenu menu = new PayrollMenu();
        menu.start();
    }
}