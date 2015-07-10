package ru.kpfu.itis.cll;

import java.io.File;
import java.io.FileNotFoundException;
import java.sql.*;
import java.util.Scanner;
import java.util.regex.Pattern;

/**
 * Обработка пар понятие-понятие, когда второе понятие ниже первого по иерархии.
 * Второму понятию присваиваем такой же sentiment_id, что и первому, вносим его в базу данных.
 */
public class HierarchyProcessing {

    // не позволяет создавать экземпляры данного класс, класс будет использоваться только как набор
    // static-методов для решения определённой задачи
    private HierarchyProcessing() {}

    /**
     * Находит все понятия, лежащие ниже тех понятий, которые есть в базе данных,
     * по файлу отношений между понятиями resources/ruThesData/relations.xml.
     * Вносит их в базу данных с sentiment_id понятия, которое выше по иерархии.
     * @throws SQLException
     * @throws FileNotFoundException
     */
    public static void verticalRelationsBuilding() throws SQLException, FileNotFoundException {
        Connection cn = DriverManager.getConnection(DataBaseProperties.DATA_BASE_URL, DataBaseProperties.DATA_BASE_USER, DataBaseProperties.DATA_BASE_PASSWORD);
        Statement st1 = cn.createStatement();
        ResultSet allWordsWithConceptId = st1.executeQuery("select concept_id, sentiment_id from words where concept_id is not null");
        // нужно для отметки первого вставленного в words слова, чтобы потом найти name для слов, начиная с этого
        Statement st2 = cn.createStatement();
        ResultSet rsCount = st2.executeQuery("select count(id) from words;");
        rsCount.next();
        int idStart = rsCount.getInt("count") + 1;

        // алгоритм идёт всё ниже и ниже, вглубь, т.к. он продолжает работу и на только что
        // добавленных понятиях, идя ниже от них (за счёт того, что обработка идёт по всем concept_id,
        // а не по файлу relations). но из-за этого придётся много раз пробежаться по файлу relations.xml
        // переводит курсор и возвращает boolean
        while (allWordsWithConceptId.next()) {
            int conceptIdHigher = allWordsWithConceptId.getInt("concept_id");
            Pattern pRelationDown = Pattern.compile(" *<rel from=\"" + conceptIdHigher + "\" to=\".*?\" name=\"НИЖЕ\" asp=\".*?\"/>");
            int sentimentId = allWordsWithConceptId.getInt("sentiment_id");
            // каждый раз проходимся по файлу заново, поэтому переоткрываем файл
            Scanner relations = new Scanner(new File("resources/ruThesData/relations.xml"));
            // единственное условие выхода - конец файла, потому что ищем все подходящие по паттерну отношения,
            // а не одно
            while (relations.hasNextLine()) {
                String requiredRelationSuspect = relations.nextLine();
                if (pRelationDown.matcher(requiredRelationSuspect).matches()) {
                    requiredRelationSuspect = requiredRelationSuspect.split("to=\"")[1].split("\"")[0];
                    int conceptIdLower = Integer.parseInt(requiredRelationSuspect);
                    // проверка - есть ли такое понятие в бд
                    // важно: одному Statement - один ResultSet
                    Statement st3 = cn.createStatement();
                    ResultSet rowExistence = st3.executeQuery("select count(id) from words where concept_id = " + conceptIdLower);
                    rowExistence.next();
                    // если такого понятия нет в БД
                    if (rowExistence.getInt("count") == 0) {
                        insertIntoWords(cn, conceptIdLower, sentimentId);
                        insertIntoVerticalRelations(cn, conceptIdHigher, conceptIdLower);
                    }
                    // если такое понятие уже существует
                    else {
                        Statement st4 = cn.createStatement();
                        ResultSet wordFromDB = st4.executeQuery("select sentiment_id, from_input from words where concept_id = " + conceptIdLower);
                        wordFromDB.next();
                        // слова из исходных словарей не трогаем это уменьшает количество возможных ошибок
                        // (иначе  возможно ошибочное определение целой ветки слов)
                        if (!wordFromDB.getBoolean("from_input")) {
                            // если метки, полученные автоматически, пересеклись (если в БД найденное слово
                            // с fromInput = false и с одним sentiment_id, а алгоритм нашёл для него другой sentiment_id),
                            // то sentiment_id данного слова "positive/negative/neutral"
                            if (sentimentId != wordFromDB.getInt("sentiment_id")) {
                                updateSentimentIdInWords(cn, 3, conceptIdLower);
                            }
                        }
                        st4.close();
                    }
                    st3.close();
                }
            }
        }

        // найти для всех, начиная с idStart, name в concepts.xml
        findNamesToNewWords(cn, idStart);
        cn.close();
        st1.close();
        System.out.println("After method verticalRelationsBuilding in HierarchyProcessing \n");
    }

    /**
     * Записывает в базу данных полученное в алгоритме слово по его concept_id и sentiment_id.
     * @param cn соединение с базой данных
     * @param conceptIdLower concept_id найденного понятия
     * @param sentimentId sentiment_id найденного понятия
     * @throws SQLException
     */
    public static void insertIntoWords(Connection cn, int conceptIdLower, int sentimentId) throws SQLException {
        Statement st = cn.createStatement();
        ResultSet rs = st.executeQuery("insert into words (concept_id, sentiment_id, from_input) values " +
                "(" + conceptIdLower + ", " + sentimentId + ", false) returning id, concept_id, sentiment_id;");
        rs.next();
        System.out.println("insertIntoWords: id = " + rs.getInt("id") + ", concept_id = " + rs.getInt("concept_id") +
                ", sentiment_id = " + rs.getString("sentiment_id"));
        st.close();
    }

    /**
     * Обновляет значение метки sentiment_id существующего понятия, если в алгоритме пересеклись метки.
     * @param cn соединение с базой данных
     * @param sentimentId новый sentiment_id
     * @param conceptId concept_id понятия, sentiment_id которого обновляем
     * @throws SQLException
     */
    public static void updateSentimentIdInWords(Connection cn, int sentimentId, int conceptId) throws SQLException {
        Statement st = cn.createStatement();
        boolean success = st.execute("update words set sentiment_id = " + sentimentId + " " +
                "where concept_id = " + conceptId + ";");
        if (success) {
            System.out.println("updateSentimentIdInWords: Word with concept_id = " + conceptId + "updated with new sentiment_id = " + sentimentId);
        }
        st.close();
    }

    /**
     * Записывает новую пару понятий по их concept_id, связанную отношением "НИЖЕ".
     * @param cn соединение с базой данных
     * @param conceptIdHigher concept_id понятия, которое находится выше по иерархии
     * @param conceptIdLower concept_id понятия, которое находится ниже по иерархии
     * @throws SQLException
     */
    private static void insertIntoVerticalRelations(Connection cn, int conceptIdHigher, int conceptIdLower) throws SQLException {
        Statement st = cn.createStatement();
        ResultSet rs = st.executeQuery("select id from words where concept_id = " + conceptIdHigher + ";");
        rs.next();
        int idHigher = rs.getInt("id");
        rs = st.executeQuery("select id from words where concept_id = " + conceptIdLower + ";");
        rs.next();
        int idLower = rs.getInt("id");

        rs = st.executeQuery("insert into vertical_relations (words_id_higher, words_id_lower) values " +
                "(" + idHigher + ", " + idLower + ") returning id, words_id_higher, words_id_lower;");
        rs.next();
        System.out.println("insertIntoVerticalRelations: id = " + rs.getInt("id") + ", words_id_higher = " + rs.getInt("words_id_higher") +
                ", words_id_lower = " + rs.getString("words_id_lower"));
        st.close();
    }

    /**
     * Находит для всех добавленных при помощи verticalRelationsBuilding в базу данных слов их name
     * в файле resources/ruThesData/concepts.xml.
     * @param cn соединение с базой данных
     * @param idStart id первого найденного при помощи verticalRelationsBuilding слова
     * @throws SQLException
     * @throws FileNotFoundException
     */
    private static void findNamesToNewWords(Connection cn, int idStart) throws SQLException, FileNotFoundException {
        Statement st1 = cn.createStatement();
        ResultSet rs = st1.executeQuery("select concept_id from words where id >= " + idStart + ";");
        // проходимся по всем новым словам
        while (rs.next()) {
            int conceptId = rs.getInt("concept_id");
            Pattern pId = Pattern.compile(" *<concept id=\"" + conceptId + "\">");
            boolean nameFound = false;
            // проходимся по файлу concepts.xml, пока не надём name для данного concept_id
            Scanner sc = new Scanner(new File("resources/ruThesData/concepts.xml"));
            while (sc.hasNextLine() && !nameFound) {
                String idSuspect = sc.nextLine();
                if (pId.matcher(idSuspect).matches()) {
                    String name = sc.nextLine();
                    name = name.split(">")[1].split("<")[0].split(" \\(")[0].split(", ")[0];
                    name = name.toLowerCase();
                    Statement st2 = cn.createStatement();
                    st2.execute("update words set name = '" + name + "' where concept_id = " + conceptId + ";");
                    System.out.println("findNamesToNewWords: Word with concept_id = " + conceptId + " updated with new name = " + name);
                    nameFound = true;
                    st2.close();
                }
            }
        }
        st1.close();
        System.out.println("After method findNamesToNewWords in HierarchyProcessing \n");
    }
}
