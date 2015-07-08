package ru.kpfu.itis.cll;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.sql.*;

/**
 * Создание всех таблиц и заполнение таблицы sentiments.
 */
public class DataBaseCreating {

    // не позволяет создавать экземпляры данного класс, класс будет использоваться только как набор
    // static-методов для решения определённой задачи
    private DataBaseCreating() {}

    /**
     * Метод-обёртка методов createTables и insertIntoSentimentsTable (последовательно вызывает эти методы).
     */
    public static void create() throws SQLException, IOException, ClassNotFoundException {
        createTables();
        insertIntoSentimentsTable();
    }

    /**
     * Создание всех таблиц, скрипты для создания берутся из create.sql в resources/dbScripts.
     */
    public static void createTables() throws SQLException, ClassNotFoundException, IOException {
        Class.forName("org.postgresql.Driver");
        Connection cn = DriverManager.getConnection(DataBaseProperties.DATA_BASE_URL, DataBaseProperties.DATA_BASE_USER, DataBaseProperties.DATA_BASE_PASSWORD);
        Statement st = cn.createStatement();

        File SQLForCreating = new File("resources/dbScripts/create.sql");
        st.execute(FileUtils.readFileToString(SQLForCreating));
        System.out.println("Tables in data base created");

        cn.close();
        st.close();
    }

    /**
     * Заполнение таблицы sentiments, скрипты для вставки берутся из insert.sql в resources/dbScripts.
     */
    public static void insertIntoSentimentsTable() throws SQLException, ClassNotFoundException, IOException {
        Class.forName("org.postgresql.Driver");
        Connection cn = DriverManager.getConnection(DataBaseProperties.DATA_BASE_URL, DataBaseProperties.DATA_BASE_USER, DataBaseProperties.DATA_BASE_PASSWORD);
        Statement st = cn.createStatement();

        File SQLForInserting = new File("resources/dbScripts/insert.sql");
        st.execute(FileUtils.readFileToString(SQLForInserting));
        System.out.println("Sentiments defined in sentiments table");

        cn.close();
        st.close();
    }
}
