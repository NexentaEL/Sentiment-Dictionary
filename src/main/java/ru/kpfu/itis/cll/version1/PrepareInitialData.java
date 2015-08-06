package ru.kpfu.itis.cll.version1;

import ru.kpfu.itis.cll.DataBaseProperties;

import java.io.File;
import java.io.FileNotFoundException;
import java.sql.*;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Pattern;

/**
 * Подготовка в базе данных информации об уже имеющихся словах из словарей. Рассматривает исходные слова
 * как концепты, записывает все соответствующие исходным словам концепты в базу данных.
 */
class PrepareInitialData {

    // не позволяет создавать экземпляры данного класс, класс будет использоваться только как набор
    // static-методов дл¤ решения определённой задачи
    private PrepareInitialData() {}

    /**
     * Кладёт исходные словари в базу данных в таблицу words, заранее созданные словари находятся
     * в resources/inputDictionaries.
     * Находит для каждого слова соответствующие ему концепты в файле resources/ruThesData/concepts.xml,
     * сохраняет их в таблице concepts.
     * @param dictionariesWithSentimentId коллекция исходных словарей с указанием их sentiment_id
     * @throws FileNotFoundException
     * @throws SQLException
     */
    public static void insertInitialDataInDB(Map<String, Integer> dictionariesWithSentimentId) throws FileNotFoundException, SQLException {
        int wordId = 1;
        Pattern pСonceptId = Pattern.compile(" *<concept id=\".*?\">");
        Pattern pAnyName = Pattern.compile(" *<name>.*?</name>");

        // обрабатываем все поданные на вход методу словари
        for (Map.Entry<String, Integer> dictionariesWithSentimentIdEntry : dictionariesWithSentimentId.entrySet()) {
            Scanner dictionary = new Scanner(new File(dictionariesWithSentimentIdEntry.getKey()));
            Scanner concepts = new Scanner(new File("resources/ruThesData/concepts.xml"));
            int sentimentId = dictionariesWithSentimentIdEntry.getValue();
            String conceptIdSuspect = "", nameSuspect;
            boolean dictionaryEnded = false;

            // берём первое слово из словаря
            String word = dictionary.nextLine();
            insertIntoWords(wordId, word, sentimentId);
            // оба файла (и словарь, и concepts.xml) берём отсортированными, тогда возможно их "наложение"
            // друг на друга, уменьшается сложность алгоритма
            // мы продолжаем идти по файлу concepts, а не идём каждый раз с самого начала,
            // если словарь будет не отсортирован, алгоритм отработает неверно.
            // пока не дошли до конца concepts.xml
            while(concepts.hasNextLine() && !dictionaryEnded) {
                // нужно проверить на наличие двух строк, чтобы проверить conceptId и name, которые расположены
                // в двух строках друг за другом
                if (concepts.hasNextLine()) {
                    // в файле сначала пишется conceptId, а на следующей строке name
                    // если после предыдущей итерации у нас не оставался необработанный conceptId,
                    // то обрабатываем следующий, иначе в строке conceptIdSuspect остаётся необработанный conceptId
                    if (conceptIdSuspect.equals("")) {
                        conceptIdSuspect = concepts.nextLine();
                    }
                    nameSuspect = concepts.nextLine();

                    if (pAnyName.matcher(nameSuspect).matches()) {
                        // нужно для проверки выхода за лексикографические границы наложения двух файлов
                        // в concepts.xml слова иногда имеют пояснения в скобках после самого слова,
                        // а также могут идти через запятую
                        // например: БОРОЗДИТЬ; БОРОЗДИТЬ (ПЕРЕСЕКАТЬ В РАЗНЫХ НАПРАВЛЕНИЯХ); БОРОЗДИТЬ, ПРОПАХАТЬ БОРОЗДЫ
                        // берём первое слово в <name></name>
                        String firstPathOfName = nameSuspect.split(">")[1].split("<")[0].split(" \\(")[0].split(", ")[0];
                        // здесь происходит итерация по словам в словаре.
                        // проверка по первому слову в <name></name>, не вышли ли мы лексикографически за границы
                        // concepts в поисках концептов для данного word, если вышли, то
                        while (firstPathOfName.compareTo(word.toUpperCase()) > 0 && dictionary.hasNextLine()) {
                            // переходим на следующее слово в словаре
                            word = dictionary.nextLine();
                            wordId++;
                            // записываем данное слово в таблицу words
                            insertIntoWords(wordId, word, sentimentId);
                        }
                        if (!dictionary.hasNextLine()) {
                            // с помощью этого флага цикл перейдёт на следующий словарь
                            dictionaryEnded = true;
                            // это гарантирует увеличение wordId, когда нужно будет записать первое слово
                            // из следующего словаря с новым id, а не тем, на котором остановились
                            wordId++;
                        }

                        // в concepts.txt слова написаны большими буквами, а в исходных словарях маленькими
                        // если найден концепт, начало которого соответствует данному слову из словаря
                        if (firstPathOfName.equals(word.toUpperCase())) {
                            // в предыдущей строке перед name всегда пишется его conceptId
                            String [] stringsWithId = conceptIdSuspect.split("\"");
                            int conceptId = Integer.parseInt(stringsWithId[1]);
                            String name = nameSuspect.split(">")[1].split("<")[0].toLowerCase();
                            // в концепты записываем не сами слова из словарей, а то, как их концепты записаны в concepts.xml
                            insertIntoConcepts(conceptId, name, wordId, sentimentId);
                            conceptIdSuspect = "";
                        }
                        // если conceptId пока не был найден и nameSuspect не содержит следующий conceptId,
                        // то обработка начнётся с новых строк
                        else {
                            conceptIdSuspect = "";
                        }
                    }
                    // вызвали дважды nextLine для scanner, он назад не вернётся,
                    // придётся проверить nameSuspect на conceptId;
                    // можно было бы использовать LineNumberReader, но это не сильно проще
                    else if (pСonceptId.matcher(nameSuspect).matches()){
                        conceptIdSuspect = nameSuspect;
                    }
                    // если conceptId пока не был найден и nameSuspect не содержит следующий conceptId,
                    // то обработка начнётся с новых строк
                    else {
                        conceptIdSuspect = "";
                    }
                }
            }
        }
        System.out.println("After method insertInitialDataInDB in PrepareInitialData \n");
    }

    /**
     * Записывает в базу данных в таблицу words слово из словаря с установленным заранее sentiment_id.
     * @param id id слова
     * @param word само слово из исходного словаря
     * @param sentimentId sentiment_id слова
     * @throws SQLException
     */
    public static void insertIntoWords(int id, String word, int sentimentId) throws SQLException {
        Connection cn = DriverManager.getConnection(DataBaseProperties.DATA_BASE_URL, DataBaseProperties.DATA_BASE_USER, DataBaseProperties.DATA_BASE_PASSWORD);
        Statement st = cn.createStatement();

        ResultSet rs = st.executeQuery("insert into words values " +
                "(" + id + ", '" + word + "', " + sentimentId + ") returning id, word, sentiment_id;");
        rs.next();
        System.out.println("id = " + rs.getInt("id") + ", word = " + rs.getString("word") +
                ", sentiment_id = " + rs.getString("sentiment_id"));

        cn.close();
        st.close();
    }

    /**
     * Записывает в базу данных в таблицу concepts найденный концепт из файла concepts.xml,
     * соответствующий слову с wordId.
     * @param conceptId concept_id нового слова, найденный в файле concepts.xml
     * @param name само слово из исходного словаря
     * @param wordId id слова из word, соответствующего этому концепту
     * @param sentimentId sentiment_id концепта (соответствует sentiment_id соответствующего слова)
     * @throws SQLException
     */
    public static void insertIntoConcepts(int conceptId, String name, int wordId, int sentimentId) throws SQLException {
        Connection cn = DriverManager.getConnection(DataBaseProperties.DATA_BASE_URL, DataBaseProperties.DATA_BASE_USER, DataBaseProperties.DATA_BASE_PASSWORD);
        Statement st = cn.createStatement();

        ResultSet rs = st.executeQuery("insert into concepts (concept_id, name, word_id, sentiment_id) values " +
                "(" + conceptId + ", '" + name + "', " + wordId + ", " + sentimentId + ") " +
                "returning id, concept_id, \"name\", word_id, sentiment_id;");
        rs.next();
        System.out.println("id = " + rs.getInt("id") + ", concept_id = " + rs.getInt("concept_id") +
                ", name = " + rs.getString("name") + ", word_id = " + rs.getString("word_id") +
                ", sentiment_id = " + rs.getString("sentiment_id"));

        cn.close();
        st.close();
    }
}
