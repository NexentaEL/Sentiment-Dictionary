package ru.kpfu.itis.cll;

import java.io.File;
import java.io.FileNotFoundException;
import java.sql.*;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Pattern;

/**
 * Подготовка в базе данных информации об уже имеющихся словах из словарей.
 * Рассматривает исходные слова как понятия, а не текстовые входы.
 */
public class PrepareInitialData {

    // не позволяет создавать экземпляры данного класс, класс будет использоваться только как набор
    // static-методов дл¤ решения определённой задачи
    private PrepareInitialData() {}

    /**
     * Кладёт исходные словари лексики в базу данных в таблицу words, заранее созданные словари находятся
     * в resources/inputDictionaries.
     * Находит для каждого слова его concept_id в файле resources/ruThesData/concepts.xml.
     * @param dictionariesWithSentimentId коллекция исходных словарей с указанием их sentiment_id
     * @throws FileNotFoundException
     * @throws SQLException
     */
    public static void insertInitialDataInDB(Map<String, Integer> dictionariesWithSentimentId) throws FileNotFoundException, SQLException {
        // обрабатываем все поданные на вход методу словари
        for (Map.Entry<String, Integer> dictionariesWithSentimentIdEntry : dictionariesWithSentimentId.entrySet()) {
            Scanner dictionary = new Scanner(new File(dictionariesWithSentimentIdEntry.getKey()));
            Scanner concepts = new Scanner(new File("resources/ruThesData/concepts.xml"));
            Pattern pСonceptId = Pattern.compile(" *<concept id=\".*?\">");
            Pattern pAnyName = Pattern.compile(" *<name>.*?</name>");
            String conceptIdSuspect = "", nameSuspect;

            // берём словарь
            while (dictionary.hasNextLine()) {
                String name = dictionary.nextLine();
                Integer conceptId = null;
                boolean conceptIdFound = false;
                // оба файла (и словарь, и concepts.xml) берём отсортированными, тогда возможно их "наложение"
                // друг на друга, уменьшается сложность алгоритма
                // мы продолжаем идти по файлу concepts, а не идём каждый раз с самого начала,
                // если словарь будет не отсортирован, алгоритм отработает неверно.
                // пока не дошли до конца файла или не нашли conceptId или не убедились, что он не существует
                while(concepts.hasNextLine() && !conceptIdFound) {
                    // нужно проверить на наличие двух строк, чтобы проверить conceptId и name, которые расположение
                    // в двух строках друг за другом
                    if (concepts.hasNextLine()) {
                        // в файле сначала пишется conceptId, а на следующей строке name
                        // если после предыдущей итерации у нас не оставался необработанный conceptId,
                        // то обрабатываем следующий, иначе в строке idSuspect остаётся необработанный conceptId
                        if (conceptIdSuspect.equals("")) {
                            conceptIdSuspect = concepts.nextLine();
                        }
                        nameSuspect = concepts.nextLine();

                        if (pAnyName.matcher(nameSuspect).matches()) {
                            // нужно для проверки выхода за лексикографические границы наложения двух файлов
                            // в concepts.xml слова иногда имеют пояснения в скобках после самого слова,
                            // а также могут идти через запятую
                            // например: БОРОЗДИТЬ; БОРОЗДИТЬ (ПЕРЕСЕКАТЬ В РАЗНЫХ НАПРАВЛЕНИЯХ);
                            // БОРОЗДИТЬ, ПРОПАХАТЬ БОРОЗДЫ
                            String stringWithName = nameSuspect.split(">")[1].split("<")[0].split(" \\(")[0].split(", ")[0];
                            // если мы лексикографически вышли за границы concepts в поисках conceptId для name
                            while (stringWithName.compareTo(name.toUpperCase()) > 0 && dictionary.hasNextLine()) {
                                insertIntoWords(null, name, dictionariesWithSentimentIdEntry.getValue());
                                // переходим на следующее слово в словаре
                                name = dictionary.nextLine();
                            }

                            // в concepts.txt слова написаны большими буквами, а в исходных словарях маленькими
                            String nameUpperCase = name.toUpperCase();
                            // если нашли данное слово в файле с понятиями
                            if (stringWithName.equals(nameUpperCase)) {
                                // в предыдущей строке перед name всегда пишется его conceptId
                                String [] stringsWithId = conceptIdSuspect.split("\"");
                                conceptId = Integer.parseInt(stringsWithId[1]);
                                conceptIdFound = true;
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

                insertIntoWords(conceptId, name, dictionariesWithSentimentIdEntry.getValue());
            }
        }
        System.out.println("After method insertInitialDataInDB in PrepareInitialData");
    }

    /**
     * Записывает в базу данных в таблицу words слово из словаря с найденным concept_id из файла concepts.xml,
     * с установленным заранее sentiment_id и с пометкой from_input = true.
     * @param conceptId concept_id нового слова, найденный в файле concepts.xml
     * @param name само слово из исходного словаря
     * @param sentimentId sentiment_id слова
     * @throws SQLException
     */
    public static void insertIntoWords(Integer conceptId, String name, Integer sentimentId) throws SQLException {
        Connection cn = DriverManager.getConnection(DataBaseProperties.DATA_BASE_URL, DataBaseProperties.DATA_BASE_USER, DataBaseProperties.DATA_BASE_PASSWORD);
        Statement st = cn.createStatement();

        // если не нашли conceptId, поле concept_id заполнится как null
        ResultSet rs = st.executeQuery("insert into words (concept_id, name, sentiment_id, from_input) values " +
                "(" + conceptId + ", '" + name + "', " + sentimentId + ", true) " +
                "returning id, concept_id, \"name\", sentiment_id;");
        rs.next();
        System.out.println("id = " + rs.getInt("id") + ", concept_id = " + rs.getInt("concept_id") +
                ", name = " + rs.getString("name") + ", sentiment_id = " + rs.getString("sentiment_id"));

        cn.close();
        st.close();
    }
}
