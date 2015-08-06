package ru.kpfu.itis.cll.version2;

import ru.kpfu.itis.cll.DataBaseProperties;

import java.sql.*;

/**
 * Вывод полученного словаря с тональностью.
 */
class OutputProcessing {

    // не позволяет создавать экземпляры данного класс, класс будет использоваться только как набор
    // static-методов для решения определённой задачи
    private OutputProcessing() {}

    /**
     * Выводит все слова из таблицы concepts c указанием их sentiment.
     * @throws SQLException
     */
    public static void sentimentDictionaryOutput() throws SQLException {
        Connection cn = DriverManager.getConnection(DataBaseProperties.DATA_BASE_URL, DataBaseProperties.DATA_BASE_USER, DataBaseProperties.DATA_BASE_PASSWORD);
        Statement st1 = cn.createStatement();
        ResultSet rs1 = st1.executeQuery("select concepts.id as id, " +
                                         "concepts.concept_id as concept_id, " +
                                         "concepts.name as concept_name, " +
                                         "sentiments.name as sentiment_name from concepts " +
                "left join sentiments on sentiments.id = concepts.sentiment_id " +
                "order by id;");
        while(rs1.next()) {
            System.out.println("id = " + rs1.getInt("id") + ", concept_id = " + rs1.getInt("concept_id")
                    + ", name = " + rs1.getString("concept_name") + ", sentiment = " +
                    rs1.getString("sentiment_name"));
        }
        cn.close();
        st1.close();
        System.out.println("After method sentimentDictionaryOutput in OutputProcessing \n");
    }
}
