package ru.kpfu.itis.cll.version2;

import ru.kpfu.itis.cll.DataBaseProperties;

import java.io.File;
import java.io.FileNotFoundException;
import java.sql.*;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Pattern;

/**
 * Подготовка в базе данных информации об уже имеющихся словах из словарей. Рассматривает исходные слова
 * как текстовые входы, записывает все соответствующие исходным словам текстовые входы в базу данных.
 */
class PrepareInitialData {

    // не позволяет создавать экземпляры данного класс, класс будет использоваться только как набор
    // static-методов дл¤ решения определённой задачи
    private PrepareInitialData() {}

    /**
     * Кладёт исходные словари в базу данных в таблицу words, заранее созданные словари находятся
     * в resources/inputDictionaries.
     * Находит для каждого слова соответствующий ему текстовый вход в файле resources/ruThesData/forVersion2/text_entry.xml,
     * сохраняет их в таблице text_entries.
     * @param dictionariesWithSentimentId коллекция исходных словарей с указанием их sentiment_id
     * @throws FileNotFoundException
     * @throws SQLException
     */
    public static void insertInitialDataInDB(Map<String, Integer> dictionariesWithSentimentId) throws FileNotFoundException, SQLException {
        int wordId = 0;
        Pattern pEntryId = Pattern.compile(" *<entry id=\".*?\">");
        Pattern pAnyName = Pattern.compile(" *<name>.*?</name>");

        // обрабатываем все поданные на вход методу словари
        for (Map.Entry<String, Integer> dictionariesWithSentimentIdEntry : dictionariesWithSentimentId.entrySet()) {
            Scanner dictionary = new Scanner(new File(dictionariesWithSentimentIdEntry.getKey()));
            Scanner entries = new Scanner(new File("resources/ruThesData/forVersion2/text_entry.xml"));
            int sentimentId = dictionariesWithSentimentIdEntry.getValue();
            String entryIdSuspect = "", nameSuspect;

            // берём словарь
            while (dictionary.hasNextLine()) {
                wordId++;
                String word = dictionary.nextLine();
                boolean idFound = false;
                // оба файла (и словарь, и text_entry.xml) берём отсортированными, тогда возможно их "наложение"
                // друг на друга, уменьшается сложность алгоритма
                // мы продолжаем идти по файлу text_entry, а не идём каждый раз с самого начала,
                // если словарь будет не отсортирован, алгоритм отработает неверно.
                // пока не дошли до конца файла или не нашли entry_id или не убедились, что он не существует
                while(entries.hasNextLine() && !idFound) {
                    // нужно проверить на наличие двух строк, чтобы проверить id и name, которые расположение
                    // в двух строках друг за другом
                    if (entries.hasNextLine()) {
                        // в файле сначала пишется id, а на следующей строке name
                        // если после предыдущей итерации у нас не оставался необработанный id,
                        // то обрабатываем следующий, иначе в строке entryIdSuspect остаётся необработанный id
                        if (entryIdSuspect.equals("")) {
                            entryIdSuspect = entries.nextLine();
                        }
                        nameSuspect = entries.nextLine();

                        if (pAnyName.matcher(nameSuspect).matches()) {
                            // нужно для проверки выхода за лексикографические границы наложения двух файлов
                            // берем из строки <name>....</name> только то, что между > <
                            // в text_entry.xml слова написаны большими буквами, а в исходных словарях маленькими
                            String name = nameSuspect.split(">")[1].split("<")[0].toLowerCase();
                            // если мы лексикографически вышли за границы text_entry в поисках id для name
                            while (name.compareTo(word) > 0 && dictionary.hasNextLine()) {
                                insertIntoWords(wordId, word, sentimentId);
                                // переходим на следующее слово в словаре
                                word = dictionary.nextLine();
                                wordId++;
                            }

                            // если нашли данное слово в файле с концептами
                            if (name.equals(word)) {
                                // в предыдущей строке перед name всегда пишется его id
                                String [] stringsWithId = entryIdSuspect.split("\"");
                                int id = Integer.parseInt(stringsWithId[1]);
                                insertIntoWords(wordId, word, sentimentId);
                                insertIntoEntries(id, name, wordId, sentimentId);
                                idFound = true;
                                entryIdSuspect = "";
                            }
                            // если id пока не был найден и nameSuspect не содержит следующий id,
                            // то обработка начнётся с новых строк
                            else {
                                entryIdSuspect = "";
                            }
                        }
                        // вызвали дважды nextLine для scanner, он назад не вернётся,
                        // придётся проверить nameSuspect на id
                        // можно было бы использовать LineNumberReader, но это не сильно проще
                        else if (pEntryId.matcher(nameSuspect).matches()){
                            entryIdSuspect = nameSuspect;
                        }
                        // если id пока не был найден и nameSuspect не содержит следующий id,
                        // то обработка начнётся с новых строк
                        else {
                            entryIdSuspect = "";
                        }
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

        ResultSet rs = st.executeQuery("insert into words values (" + id + ", '" + word + "', "
                + sentimentId + ") returning id, word, sentiment_id;");
        rs.next();
        System.out.println("id = " + rs.getInt("id") + ", word = " + rs.getString("word") +
                ", sentiment_id = " + rs.getString("sentiment_id"));

        cn.close();
        st.close();
    }

    /**
     * Записывает в базу данных в таблицу text_entries найденный текстовый вход из файла text_entry.xml,
     * соответствующий слову с wordId.
     * @param entryId entry_id нового слова, найденный в файле text_entry.xml
     * @param name само слово из исходного словаря
     * @param wordId id слова из word, соответствующего этому текстовому входу
     * @param sentimentId sentiment_id концепта (соответствует sentiment_id соответствующего слова)
     * @throws SQLException
     */
    public static void insertIntoEntries(int entryId, String name, int wordId, int sentimentId) throws SQLException {
        Connection cn = DriverManager.getConnection(DataBaseProperties.DATA_BASE_URL, DataBaseProperties.DATA_BASE_USER, DataBaseProperties.DATA_BASE_PASSWORD);
        Statement st = cn.createStatement();

        ResultSet rs = st.executeQuery("insert into text_entries (entry_id, name, word_id, sentiment_id) values " +
                "(" + entryId + ", '" + name + "', " + wordId + ", " + sentimentId + ") " +
                "returning id, entry_id, \"name\", word_id, sentiment_id;");
        rs.next();
        System.out.println("id = " + rs.getInt("id") + ", entry_id = " + rs.getInt("entry_id") +
                ", name = " + rs.getString("name") + ", word_id = " + rs.getString("word_id") +
                ", sentiment_id = " + rs.getString("sentiment_id"));

        cn.close();
        st.close();
    }
}
