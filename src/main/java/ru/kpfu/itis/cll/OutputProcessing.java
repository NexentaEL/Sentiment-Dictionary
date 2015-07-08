package ru.kpfu.itis.cll;

import java.sql.*;

/**
 * Вывод полученного словаря с тональностью и иерархией.
 */
public class OutputProcessing {

    // не позволяет создавать экземпляры данного класс, класс будет использоваться только как набор
    // static-методов для решения определённой задачи
    private OutputProcessing() {}

    /**
     * Выводит все слова из таблицы words c указанием их sentiment и со словами, которые находятся ниже
     * по иерархии (и, соответственно, получили такой же sentiment_id).
     */
    public static void sentimentDictionaryOutput() throws ClassNotFoundException, SQLException {
        Class.forName("org.postgresql.Driver");
        Connection cn = DriverManager.getConnection(DataBaseProperties.DATA_BASE_URL, DataBaseProperties.DATA_BASE_USER, DataBaseProperties.DATA_BASE_PASSWORD);
        Statement st1 = cn.createStatement();
        ResultSet rs1 = st1.executeQuery("select words.id as id, " +
                                              "words.concept_id as concept_id, " +
                                              "words.name as word_name, " +
                                              "words.from_input as from_input, " +
                                              "sentiments.name as sentiment_name, " +
                                              "vertical_relations.words_id_lower as word_id_lower from words " +
                "left join sentiments on sentiments.id = words.sentiment_id " +
                "left join vertical_relations on words.id = vertical_relations.words_id_higher order by id;");
        while(rs1.next()) {
            String wordLower = "нет слов";
            // в предыдущей выборке (rs1) мы получили только id слова, ниходящего ниже, теперь находим само слово
            if (rs1.getInt("word_id_lower") != 0) {
                Statement st2 = cn.createStatement();
                ResultSet rs2 = st2.executeQuery("select name from words where id = " + rs1.getInt("word_id_lower") + ";");
                rs2.next();
                wordLower = rs2.getString("name");
                st2.close();
            }
            // вместо boolean метки from_input выводим то, что она значит
            String fromInput = "слово было добавлено из исходных словарей";
            if (!rs1.getBoolean("from_input")) {
                fromInput = "слово было добавлено автоматически";
            }
            System.out.println("id = " + rs1.getInt("id") + ", concept_id = " + rs1.getInt("concept_id")
                    + ", name = " + rs1.getString("word_name") + ", " + fromInput + ", sentiment = " +
                    rs1.getString("sentiment_name") + ", ниже по иерархии = " + wordLower);
        }
        cn.close();
        st1.close();
    }
}
