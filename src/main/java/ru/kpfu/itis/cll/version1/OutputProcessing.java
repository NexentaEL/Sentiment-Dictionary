package ru.kpfu.itis.cll.version1;

import ru.kpfu.itis.cll.DataBaseProperties;

import java.sql.*;

/**
 * Вывод полученного словаря с тональностью и иерархией.
 */
class OutputProcessing {

    // не позволяет создавать экземпляры данного класс, класс будет использоваться только как набор
    // static-методов для решения определённой задачи
    private OutputProcessing() {}

    /**
     * Выводит все слова из таблицы concepts c указанием их sentiment и со словами, которые находятся ниже
     * по иерархии (и, соответственно, получили такой же sentiment_id).
     * @throws SQLException
     */
    public static void sentimentDictionaryOutput() throws SQLException {
        Connection cn = DriverManager.getConnection(DataBaseProperties.DATA_BASE_URL, DataBaseProperties.DATA_BASE_USER, DataBaseProperties.DATA_BASE_PASSWORD);
        Statement st1 = cn.createStatement();
        ResultSet rs1 = st1.executeQuery("select concepts.id as id, " +
                                         "concepts.concept_id as concept_id, " +
                                         "concepts.name as concept_name, " +
                                         "concepts.word_id as word_id, " +
                                         "sentiments.name as sentiment_name, " +
                                         "vertical_relations.concept_id_lower as concept_id_lower from concepts " +
                "left join sentiments on sentiments.id = concepts.sentiment_id " +
                "left join vertical_relations on concepts.id = vertical_relations.concept_id_higher " +
                "order by id;");
        while(rs1.next()) {
            String conceptLower = "нет слов";
            // в предыдущей выборке (rs1) мы получили только id слова, ниходящего ниже, теперь находим само слово
            if (rs1.getInt("concept_id_lower") != 0) {
                Statement st2 = cn.createStatement();
                ResultSet rs2 = st2.executeQuery("select name from concepts where id = " + rs1.getInt("concept_id_lower") + ";");
                rs2.next();
                conceptLower = rs2.getString("name");
                st2.close();
            }
            // есть ли соответствующее данному концепту слово из исходных словарей
            String fromInput = "слово было добавлено из исходных словарей";
            if (rs1.getInt("word_id") == 0) {
                fromInput = "слово было добавлено автоматически";
            }
            System.out.println("id = " + rs1.getInt("id") + ", concept_id = " + rs1.getInt("concept_id")
                    + ", name = " + rs1.getString("concept_name") + ", " + fromInput + ", sentiment = " +
                    rs1.getString("sentiment_name") + ", ниже по иерархии = " + conceptLower);
        }
        cn.close();
        st1.close();
        System.out.println("After method sentimentDictionaryOutput in OutputProcessing \n");
    }
}
