package ru.kpfu.itis.cll.version1;

import ru.kpfu.itis.cll.DataBaseProperties;

import java.io.File;
import java.io.FileNotFoundException;
import java.sql.*;
import java.util.Scanner;
import java.util.regex.Pattern;

/**
 * Обработка пар концепт-концепт, когда второй концепт ниже первого по иерархии.
 * Второму концепту присваиваем такой же sentiment_id, что и первому, вносим его в базу данных.
 */
class HierarchyProcessing {

    // не позволяет создавать экземпляры данного класс, класс будет использоваться только как набор
    // static-методов для решения определённой задачи
    private HierarchyProcessing() {}

    /**
     * Находит все концепты, лежащие ниже тех концептов, которые есть в базе данных,
     * по файлу отношений между концептами resources/ruThesData/forVersion1/relations.xml.
     * Вносит их в базу данных с sentiment_id концепта, которое выше по иерархии.
     * @throws SQLException
     * @throws FileNotFoundException
     */
    public static void verticalRelationsBuilding() throws SQLException, FileNotFoundException {
        Connection cn = DriverManager.getConnection(DataBaseProperties.DATA_BASE_URL, DataBaseProperties.DATA_BASE_USER, DataBaseProperties.DATA_BASE_PASSWORD);
        Statement st1 = cn.createStatement();
        ResultSet allConceptsWithConceptId = st1.executeQuery("select concept_id, sentiment_id from concepts");
        // нужно для отметки первого вставленного в concepts слова, чтобы потом найти name для концептов, начиная с этого
        Statement st2 = cn.createStatement();
        ResultSet rsCount = st2.executeQuery("select count(id) from concepts;");
        rsCount.next();
        int idStart = rsCount.getInt("count") + 1;

        // алгоритм идёт всё ниже и ниже, вглубь, т.к. он продолжает работу и на только что
        // добавленных концептах, идя ниже от них (за счёт того, что обработка идёт по всем concept_id,
        // а не по файлу relations). но из-за этого придётся много раз пробежаться по файлу relations.xml
        // переводит курсор и возвращает boolean
        while (allConceptsWithConceptId.next()) {
            int conceptIdHigher = allConceptsWithConceptId.getInt("concept_id");
            Pattern pRelationDown = Pattern.compile(" *<rel from=\"" + conceptIdHigher + "\" to=\".*?\" name=\"НИЖЕ\" asp=\".*?\"/>");
            int sentimentId = allConceptsWithConceptId.getInt("sentiment_id");
            // каждый раз проходимся по файлу заново, поэтому переоткрываем файл
            Scanner relations = new Scanner(new File("resources/ruThesData/forVersion1/relations.xml"));
            // единственное условие выхода - конец файла, потому что ищем все подходящие по паттерну отношения,
            // а не одно
            while (relations.hasNextLine()) {
                String requiredRelationSuspect = relations.nextLine();
                if (pRelationDown.matcher(requiredRelationSuspect).matches()) {
                    requiredRelationSuspect = requiredRelationSuspect.split("to=\"")[1].split("\"")[0];
                    int conceptIdLower = Integer.parseInt(requiredRelationSuspect);
                    // проверка - есть ли такой концепт в бд
                    // важно: одному Statement - один ResultSet
                    Statement st3 = cn.createStatement();
                    ResultSet rowExistence = st3.executeQuery("select count(id) from concepts where concept_id = " + conceptIdLower);
                    rowExistence.next();
                    // если такого концепта нет в БД
                    if (rowExistence.getInt("count") == 0) {
                        insertIntoConcepts(cn, conceptIdLower, sentimentId);
                        insertIntoVerticalRelations(cn, conceptIdHigher, conceptIdLower);
                    }
                    // если такой концепт уже существует
                    else {
                        Statement st4 = cn.createStatement();
                        ResultSet conceptFromDB = st4.executeQuery("select sentiment_id, word_id from concepts where concept_id = " + conceptIdLower);
                        conceptFromDB.next();
                        // концепты, полученные из слов исходных словарей не трогаем, это уменьшает количество возможных ошибок
                        // (иначе возможно ошибочное определение целой ветки слов, которые находятся ниже)
                        if (conceptFromDB.getInt("word_id") != 0) {
                            // если метки, полученные автоматически, пересеклись (если в БД найденное слово
                            // с word_id != 0 (в случае бд оно != null) и с одним sentiment_id, а алгоритм нашёл для него
                            // другой sentiment_id), то sentiment_id данного слова "positive/negative"
                            if (sentimentId != conceptFromDB.getInt("sentiment_id")) {
                                updateSentimentIdForConcept(cn, 3, conceptIdLower);
                            }
                        }
                        st4.close();
                    }
                    st3.close();
                }
            }
        }

        // найти для всех, начиная с idStart, name в concepts.xml
        findNamesToNewConcepts(cn, idStart);
        cn.close();
        st1.close();
        System.out.println("After method verticalRelationsBuilding in HierarchyProcessing \n");
    }

    /**
     * Записывает в базу данных полученный в алгоритме концепт по его concept_id и sentiment_id.
     * @param cn соединение с базой данных
     * @param conceptIdLower concept_id найденного концепта
     * @param sentimentId sentiment_id найденного концепта
     * @throws SQLException
     */
    public static void insertIntoConcepts(Connection cn, int conceptIdLower, int sentimentId) throws SQLException {
        Statement st = cn.createStatement();
        ResultSet rs = st.executeQuery("insert into concepts (concept_id, sentiment_id) values " +
                "(" + conceptIdLower + ", " + sentimentId + ") returning id, concept_id, sentiment_id;");
        rs.next();
        System.out.println("insertIntoConcepts: id = " + rs.getInt("id") + ", concept_id = " + rs.getInt("concept_id") +
                ", sentiment_id = " + rs.getString("sentiment_id"));
        st.close();
    }

    /**
     * Обновляет значение метки sentiment_id существующего концепта, если в алгоритме пересеклись метки.
     * @param cn соединение с базой данных
     * @param sentimentId новый sentiment_id
     * @param conceptId concept_id концепта, sentiment_id которого обновляем
     * @throws SQLException
     */
    public static void updateSentimentIdForConcept(Connection cn, int sentimentId, int conceptId) throws SQLException {
        Statement st = cn.createStatement();
        boolean success = st.execute("update concepts set sentiment_id = " + sentimentId +
                " where concept_id = " + conceptId + ";");
        if (success) {
            System.out.println("updateSentimentIdForConcept: Concept with concept_id = " + conceptId +
                    "updated with new sentiment_id = " + sentimentId);
        }
        st.close();
    }

    /**
     * Записывает новую пару концептов по их concept_id, связанную отношением "НИЖЕ".
     * @param cn соединение с базой данных
     * @param conceptIdHigher concept_id концепта, которое находится выше по иерархии
     * @param conceptIdLower concept_id концепта, которое находится ниже по иерархии
     * @throws SQLException
     */
    private static void insertIntoVerticalRelations(Connection cn, int conceptIdHigher, int conceptIdLower) throws SQLException {
        Statement st = cn.createStatement();
        ResultSet rs = st.executeQuery("select id from concepts where concept_id = " + conceptIdHigher + ";");
        rs.next();
        int idHigher = rs.getInt("id");
        rs = st.executeQuery("select id from concepts where concept_id = " + conceptIdLower + ";");
        rs.next();
        int idLower = rs.getInt("id");

        rs = st.executeQuery("insert into vertical_relations (concept_id_higher, concept_id_lower) values " +
                "(" + idHigher + ", " + idLower + ") returning id, concept_id_higher, concept_id_lower;");
        rs.next();
        System.out.println("insertIntoVerticalRelations: id = " + rs.getInt("id") + ", concept_id_higher = " +
                rs.getInt("concept_id_higher") + ", concept_id_lower = " + rs.getString("concept_id_lower"));
        st.close();
    }

    /**
     * Находит для всех добавленных при помощи verticalRelationsBuilding в базу данных концептов их name
     * в файле resources/ruThesData/concepts.xml.
     * @param cn соединение с базой данных
     * @param idStart id первого найденного при помощи verticalRelationsBuilding концепта
     * @throws SQLException
     * @throws FileNotFoundException
     */
    private static void findNamesToNewConcepts(Connection cn, int idStart) throws SQLException, FileNotFoundException {
        Statement st1 = cn.createStatement();
        ResultSet rs = st1.executeQuery("select concept_id from concepts where id >= " + idStart + ";");
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
                    name = name.split(">")[1].split("<")[0].toLowerCase();
                    Statement st2 = cn.createStatement();
                    st2.execute("update concepts set name = '" + name + "' where concept_id = " + conceptId + ";");
                    System.out.println("findNamesToNewConcepts: Word with concept_id = " + conceptId +
                            " updated with new name = " + name);
                    nameFound = true;
                    st2.close();
                }
            }
        }
        st1.close();
        System.out.println("After method findNamesToNewConcepts in HierarchyProcessing \n");
    }
}
