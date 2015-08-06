package ru.kpfu.itis.cll.version2;

import ru.kpfu.itis.cll.DataBaseProperties;

import java.io.File;
import java.io.FileNotFoundException;
import java.sql.*;
import java.util.Scanner;
import java.util.regex.Pattern;

/**
 * Сопоставляет текстовые входы из базы данных концептам, вычисляет для каждого концепта его sentiment_id
 * по количеству связанных с ним текстовых входов с позитивным/негативным sentiment.
 */
class FindingSentimentsForConcepts {

    // не позволяет создавать экземпляры данного класс, класс будет использоваться только как набор
    // static-методов дл¤ решения определённой задачи
    private FindingSentimentsForConcepts() {}

    /**
     * Метод-обёртка методов textEntryToConceptsMapping и derivingSentimentForConcepts (последовательно
     * вызывает эти методы).
     * @throws FileNotFoundException
     * @throws SQLException
     */
    public static void mappingAndDerivingSentimentForConcepts() throws FileNotFoundException, SQLException {
        textEntryToConceptsMapping();
        derivingSentimentIdForConcepts();
    }

    /**
     * Сопоставление имеющихся в базе данных текстовых входов с концептами по файлу synonyms.xml, запись
     * найденных концептов в таблицу concepts, запись найденной связи текстовый вход-концепт в таблицу entry_rel.
     * @throws FileNotFoundException
     * @throws SQLException
     */
    public static void textEntryToConceptsMapping() throws FileNotFoundException, SQLException {
        Connection cn = DriverManager.getConnection(DataBaseProperties.DATA_BASE_URL, DataBaseProperties.DATA_BASE_USER, DataBaseProperties.DATA_BASE_PASSWORD);
        Statement st = cn.createStatement();
        ResultSet textEntries = st.executeQuery("select id, entry_id from text_entries;");

        while (textEntries.next()) {
            int entryIdPrimaryKey = textEntries.getInt("id");
            int entryId = textEntries.getInt("entry_id");
            Pattern entryRelForEntryId = Pattern.compile(" *<entry_rel concept_id=\".*?\" entry_id=\"" + entryId + "\"/>");
            // для каждого текстового входа переоткрываем файл отношений, чтобы идти каждый раз сначала,
            // потому что файл synonyms.xml отсортирован по концептам, а не текстовым входам
            Scanner synonyms = new Scanner(new File("resources/ruThesData/forVersion2/synonyms.xml"));
            while (synonyms.hasNextLine()) {
                String entryRel = synonyms.nextLine();
                if (entryRelForEntryId.matcher(entryRel).matches()) {
                    int conceptId = Integer.parseInt(entryRel.split("concept_id=\"")[1].split("\"")[0]);
                    // ищем само имя найденного концепта
                    Pattern conceptIdInConcepts = Pattern.compile(" *<concept id=\"" + conceptId + "\">");
                    Scanner concepts = new Scanner(new File("resources/ruThesData/concepts.xml"));
                    boolean conceptFound = false;
                    String conceptName = "";
                    while (concepts.hasNextLine() && !conceptFound) {
                        String conceptIdSuspect = concepts.nextLine();
                        if (conceptIdInConcepts.matcher(conceptIdSuspect).matches()) {
                            conceptName = concepts.nextLine().split(">")[1].split("<")[0];
                            conceptFound = true;
                        }
                    }
                    // записываем концепт в БД, если такого концепта там ещё нет, возвращаем id существующего
                    // или только что добавленного концепта (нужно, потому что мы не знаем id концепта, если
                    // он уже существует в БД)
                    int conceptIdFromDB = insertIntoConcepts(cn, conceptId, conceptName);
                    // записываем связь текстовый вход-найденный концепт в БД
                    insertIntoEntryRel(cn, entryIdPrimaryKey, conceptIdFromDB);
                }
            }
        }
        cn.close();
        st.close();
        System.out.println("After method textEntryToConceptsMapping in FindingSentimentsForConcepts \n");
    }

    /**
     * Вычисление для каждого концепта его sentiment_id по количеству связанных с ним текстовых входов с
     * позитивным/негативным sentiment.
     * @throws SQLException
     */
    public static void derivingSentimentIdForConcepts() throws SQLException {
        // нашли первый концепт, соответствующий текстовому входу... нужно хранить все sentiment_id
        // или в виде связи в бд хранить, а потом пересчитать количество связей, которые ведут к
        // положительным текстовым входам, отрицательным...
        Connection cn = DriverManager.getConnection(DataBaseProperties.DATA_BASE_URL, DataBaseProperties.DATA_BASE_USER, DataBaseProperties.DATA_BASE_PASSWORD);
        Statement st1 = cn.createStatement();
        ResultSet concepts = st1.executeQuery("select id from concepts;");

        while (concepts.next()) {
            int conceptId = concepts.getInt("id");
            // для каждого концепта находим все связанные с ним текстовые входы по таблице entry_rel
            Statement st2 = cn.createStatement();
            ResultSet entriesCountRs = st2.executeQuery("select count(text_entry_id) from entry_rel where " +
                    "concept_id = " + conceptId + ";");
            Statement st3 = cn.createStatement();
            ResultSet entriesId = st3.executeQuery("select text_entry_id from entry_rel " +
                    "where concept_id = " + conceptId + ";");
            entriesCountRs.next();
            int entriesCount = entriesCountRs.getInt("count");
            int sentimentIdSum = 0;
            // для каждого найденного текстового входа находим его sentiment_id по таблице text_entries,
            // складываем все sentimentId
            while (entriesId.next()) {
                Statement st4 = cn.createStatement();
                ResultSet entrySentimentId = st4.executeQuery("select sentiment_id from text_entries where " +
                        "id = " + entriesId.getInt("text_entry_id") + ";");
                entrySentimentId.next();
                sentimentIdSum += entrySentimentId.getInt("sentiment_id");
                st4.close();
            }
            // делим полученную сумму тональностей всех связанных с данным концептом текстовых входов на
            // количество связанных текстовых входов
            double approximateSentimentId = (double) sentimentIdSum / entriesCount;
            int sentimentId;
            if (approximateSentimentId < 1.5) {
                sentimentId = 1;
            }
            else if (approximateSentimentId > 1.5) {
                sentimentId = 2;
            }
            else {
                // approximateSentimentId = 1.5, sentiment = positive/negative, такое случается при одинаковом
                // количестве позитивных и негативных текстовых входов, связанных с концептом
                sentimentId = 3;
            }
            // записываем полученный sentimentId в таблицу concepts
            updateSentimentIdForConcept(cn, conceptId, sentimentId);
            st2.close();
            st3.close();
        }
        cn.close();
        st1.close();
        System.out.println("After method derivingSentimentIdForConcepts in FindingSentimentsForConcepts \n");
    }

    /**
     * Записывает в базу данных в таблицу concepts новый найденный концепт, если такого концепта ещё нет в БД.
     * @param cn соединение с базой данных
     * @param conceptId id концепта в РуТез
     * @param name сам концепт
     * @return id добавленного или уже существующего концепта
     * @throws SQLException
     */
    public static int insertIntoConcepts(Connection cn, int conceptId, String name) throws SQLException {
        Statement st1 = cn.createStatement();
        ResultSet rs1 = st1.executeQuery("select id from concepts where concept_id = " + conceptId + ";");
        int conceptIdFromDB;
        if (rs1.next()) {
            conceptIdFromDB = rs1.getInt("id");
            System.out.println("insertIntoConcepts: концепт с id = " + conceptIdFromDB + " уже существует " +
                    "в таблице concepts");
        }
        else {
            Statement st2 = cn.createStatement();
            ResultSet rs2 = st2.executeQuery("insert into concepts (concept_id, name) values (" + conceptId
                    + ", '" + name + "') returning id, concept_id, name;");
            rs2.next();
            conceptIdFromDB = rs2.getInt("id");
            System.out.println("insertIntoConcepts: id = " + conceptIdFromDB + ", concept_id = " +
                    rs2.getString("concept_id") + ", name = " + rs2.getString("name"));
            st2.close();
        }
        st1.close();
        return conceptIdFromDB;
    }

    /**
     * Записывает в базу данных таблицу entry_rel новую найденную связь текстовый вход-концепт.
     * @param cn соединение с базой данных
     * @param textEntryId id текстового входа из таблицы text_entries
     * @param conceptId id концепта из таблицы concepts
     * @throws SQLException
     */
    public static void insertIntoEntryRel(Connection cn, int textEntryId, int conceptId) throws SQLException {
        Statement st = cn.createStatement();
        ResultSet rs = st.executeQuery("insert into entry_rel (text_entry_id, concept_id) values " +
                "(" + textEntryId + ", " + conceptId + ") returning id, text_entry_id, concept_id;");
        rs.next();
        System.out.println("insertIntoEntryRel: id = " + rs.getInt("id") + ", text_entry_id = " +
                rs.getString("text_entry_id") + ", concept_id = " + rs.getInt("concept_id"));
        st.close();
    }

    /**
     * Обновляет значение метки sentiment_id существующего концепта.
     * @param cn соединение с базой данных
     * @param id id концепта, sentiment_id которого обновляем
     * @param sentimentId новый sentiment_id
     * @throws SQLException
     */
    public static void updateSentimentIdForConcept(Connection cn, int id, int sentimentId) throws SQLException {
        Statement st = cn.createStatement();
        boolean success = st.execute("update concepts set sentiment_id = " + sentimentId +
                " where id = " + id + ";");
        if (success) {
            System.out.println("updateSentimentIdForConcept: Concept with concept_id = " + id +
                    "updated with new sentiment_id = " + sentimentId);
        }
        st.close();
    }
}
